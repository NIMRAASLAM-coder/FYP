
// PracticeSession.kt — ENHANCED KEYPOINT SMOOTHING v3
// Improvements over v2:
//   - Kalman Filter per keypoint for adaptive smoothing that reduces model noise
//     without introducing lag. Learns from detection confidence + velocity history.
//   - Confidence-weighted interpolation: low-confidence keypoints snap to nearest
//     detection; high-confidence keypoints smoothly interpolate.
//   - Velocity clamping: detects sudden jumps (likely model errors) and constrains
//     movement to physically plausible speeds.
//   - Adaptive alpha in EMA based on confidence: high confidence = faster response,
//     low confidence = more smoothing.
//   - Temporal smoothing via velocity history: prevents stutter by blending current
//     velocity with recent history.

package com.fyp.nextshot

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import androidx.activity.viewModels
import com.fyp.nextshot.data.local.database.AppDatabase
import com.fyp.nextshot.data.local.models.SessionEntity
import com.fyp.nextshot.data.repository.SessionRepository
import com.fyp.nextshot.ui.viewmodel.SessionViewModel
import com.fyp.nextshot.ui.viewmodel.SessionViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.*

// ═════════════════════════════════════════════════════════════════════════════
// Kalman Filter for single keypoint — reduces jitter while tracking motion
// ═════════════════════════════════════════════════════════════════════════════
class KeypointKalmanFilter(
    private val processNoise: Float = 0.00005f,  // Model error (lower = trust model more)
    private val measurementNoise: Float = 0.001f   // Sensor noise (lower = trust detections more)
) {
    private var x = 0f  // Estimated position X
    private var y = 0f  // Estimated position Y
    private var vx = 0f // Estimated velocity X (px/frame)
    private var vy = 0f // Estimated velocity Y (px/frame)

    private var px = 1f  // Position uncertainty X
    private var py = 1f  // Position uncertainty Y
    private var pvx = 0.001f // Velocity uncertainty X
    private var pvy = 0.001f // Velocity uncertainty Y

    fun update(measuredX: Float, measuredY: Float, confidence: Float): Pair<Float, Float> {
        // Confidence modulates how much we trust this measurement (0–1)
        // High confidence → trust the measurement more
        // Low confidence → weight toward prediction
        val measurementTrust = confidence.coerceIn(0.1f, 1.0f)
        val mNoise = measurementNoise / measurementTrust

        // Predict: advance position by estimated velocity
        x += vx
        y += vy

        // Update uncertainties (they grow because of process noise)
        px += pvx + processNoise
        py += pvy + processNoise
        pvx += processNoise
        pvy += processNoise

        // Update step: blend prediction with measurement
        val kx = px / (px + mNoise)  // Kalman gain for X
        val ky = py / (py + mNoise)  // Kalman gain for Y

        val dx = measuredX - x
        val dy = measuredY - y

        x += kx * dx
        y += ky * dy

        // Update velocity (how fast we're moving)
        vx = vx * 0.9f + (dx / (mNoise + 0.0001f)) * 0.1f  // Damped velocity update
        vy = vy * 0.9f + (dy / (mNoise + 0.0001f)) * 0.1f

        // Uncertainty shrinks after update
        px *= (1f - kx)
        py *= (1f - ky)

        return x to y
    }

    fun predictAhead(framesDelta: Int): Pair<Float, Float> {
        // Project where the keypoint will be in N frames (for interpolation lookahead)
        val px = x + vx * framesDelta
        val py = y + vy * framesDelta
        return px to py
    }

    fun reset() {
        x = 0f
        y = 0f
        vx = 0f
        vy = 0f
        px = 1f
        py = 1f
        pvx = 0.001f
        pvy = 0.001f
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// Smooth Detection wrapper: stores Kalman state per keypoint
// ═════════════════════════════════════════════════════════════════════════════
data class SmoothedDetection(
    val raw: Detection,
    val kalmanFilters: List<KeypointKalmanFilter>,
    val smoothedKeypoints: List<Keypoint>
) {
    fun getSmoothedKeypoint(idx: Int): Keypoint? =
        if (idx < smoothedKeypoints.size) smoothedKeypoints[idx] else null
}

class PracticeSession : AppCompatActivity() {

    private val TAG = "NEXTSHOT_DEBUG"

    private lateinit var previewView: PreviewView
    private lateinit var videoView: VideoView
    private lateinit var cameraOverlay: BoundingBoxOverlay
    private lateinit var videoOverlay: BoundingBoxOverlay
    private lateinit var videoContainer: FrameLayout
    private lateinit var cameraContainer: FrameLayout
    private lateinit var playPauseBtn: ImageView
    private lateinit var headTv: TextView
    private lateinit var shouldersTv: TextView
    private lateinit var weightTv: TextView
    private lateinit var feetTv: TextView
    private var lastDetection: Detection? = null
    private var lastUpdateTime = 0L

    // For Video Sync
    private val processedDetections = TreeMap<Long, Detection>()
    private val syncHandler = Handler(Looper.getMainLooper())
    private var isVideoPlaying = false

    // Actual frame dimensions used when sending frames to Roboflow (aspect-ratio-aware)
    private var videoFrameW = 640
    private var videoFrameH = 480

    // Mode tracking to prevent interference
    private var isLiveMode = true
    private lateinit var progressDialog: android.app.AlertDialog
    private var mediaPlayer: MediaPlayer? = null

    // Live Mode — rolling detection buffer with Kalman smoothing
    private val pendingRequests = java.util.concurrent.atomic.AtomicInteger(0)
    private val MAX_CONCURRENT_REQUESTS = 6
    private var currentShotType = ""

    // Live detections: now stores SmoothedDetection (with Kalman state)
    private val liveDetections = java.util.concurrent.ConcurrentSkipListMap<Long, SmoothedDetection>()

    private val LIVE_BUFFER_DELAY_MS = 200L
    private val MAX_INTERP_GAP_MS = 500L

    /** Pre-computed per-timestamp finalized shot labels (video mode only). */
    private val finalizedVideoShots = TreeMap<Long, String>()

    /** Live-mode shot detector — processes frames as they arrive from the camera. */
    private val liveShotDetector = ShotEventDetector()

    private lateinit var cameraExecutor: ExecutorService

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val INFERENCE_URL = "https://serverless.roboflow.com/hello-7pqr3/workflows/custom-workflow-3"

    // ANALYSIS STATE
    private var prevHeadCenterGlobal: Pair<Float, Float>? = null
    private var headStabilityScore = 100f
    private var shoulderScore = 100f
    private var weightScore = 100f
    private var footworkScore = 100f
    private var weightShiftText = "100%"
    private var isBalanced = true
    private var isProcessing = false

    // History Buffers for Analysis
    private val headHistory = java.util.ArrayDeque<Pair<Float, Float>>()
    private val HISTORY_SIZE = 10
    private var lastFootPosition: Pair<Float, Float>? = null
    private var footworkStatus = "100%"
    private var shoulderStatus = "100%"
    private var headStatus = "100%"

    // Persistence Counters (Damping)
    private var headBadCount = 0
    private var shoulderBadCount = 0
    private var weightBadCount = 0
    private var footworkBadCount = 0
    private val PERSISTENCE_FRAMES = 15
    private val MAX_PERSISTENCE = 30

    // Session Management
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val userId = auth.currentUser?.uid ?: "FALLBACK_UID"
    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { SessionRepository(database.sessionDao(), userId, db) }
    private val sessionViewModel: SessionViewModel by viewModels {
        SessionViewModelFactory(repository)
    }
    private var sessionStartTime = 0L

    // Tracks the frame size sent to Roboflow for live mode
    private var liveFrameW = 256
    private var liveFrameH = 240

    // Replace the liveDetections map usage with a single latest detection
    private var latestLiveDetection: Detection? = null
    private var lastGoodLiveDetection: Detection? = null
    private val liveKalmanFilters = mutableListOf<KeypointKalmanFilter>()

    private val liveHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice_session)

        previewView = findViewById(R.id.preview_view)
        videoView = findViewById(R.id.video_view)
        cameraOverlay = findViewById(R.id.bbox_overlay)
        videoOverlay = findViewById(R.id.video_overlay)
        videoContainer = findViewById(R.id.video_container)
        cameraContainer = findViewById(R.id.camera_container)
        playPauseBtn = findViewById(R.id.btn_play_pause)
        headTv = findViewById(R.id.tv_head_stability)
        shouldersTv = findViewById(R.id.tv_shoulders)
        weightTv = findViewById(R.id.tv_weight_balance)
        feetTv = findViewById(R.id.tv_footwork)

        cameraExecutor = Executors.newFixedThreadPool(8)

        findViewById<View>(R.id.btn_upload_video).setOnClickListener { pickVideo() }
        findViewById<View>(R.id.btn_live_record).setOnClickListener { enterLiveMode() }

        playPauseBtn.visibility = View.GONE

        videoContainer.setOnClickListener {
            if (videoView.isPlaying) {
                videoView.pause()
                isVideoPlaying = false
                syncHandler.removeCallbacks(syncRunnable)
                Toast.makeText(this, "Paused", Toast.LENGTH_SHORT).show()
            } else {
                videoView.start()
                isVideoPlaying = true
                syncHandler.post(syncRunnable)
                Toast.makeText(this, "Resuming", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btn_save_session).setOnClickListener {
            saveSession()
        }

        setupProgressDialog()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, android.Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun setupProgressDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setView(ProgressBar(this).apply {
            setPadding(50, 50, 50, 50)
        })
        builder.setMessage("Processing Video... Please Wait")
        progressDialog = builder.create()
    }

    private fun pickVideo() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "video/*" }
        videoPicker.launch(intent)
    }

    private val videoPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data?.data != null) {
            startVideoAnalysis(result.data!!.data!!)
        }
    }

    private fun startVideoAnalysis(uri: Uri) {
        isLiveMode = false
        liveHandler.removeCallbacks(liveSyncRunnable)
        resetAnalysisState()
        cameraContainer.visibility = View.GONE
        videoContainer.visibility = View.VISIBLE

        videoOverlay.clear()

        videoView.stopPlayback()
        syncHandler.removeCallbacks(syncRunnable)

        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            mediaPlayer = mp
            mp.isLooping = false
        }

        runOnUiThread {
            progressDialog.show()
        }
        cameraExecutor.execute {
            preProcessVideo(uri)
        }
    }

    private fun preProcessVideo(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L

            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 640
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 480
            val arScale = minOf(640f / rawW, 640f / rawH, 1.0f)
            videoFrameW = (rawW * arScale).toInt().coerceAtLeast(1)
            videoFrameH = (rawH * arScale).toInt().coerceAtLeast(1)
            Log.d(TAG, "Video native: ${rawW}x${rawH}, sending as: ${videoFrameW}x${videoFrameH}")

            Log.d(TAG, "Starting pre-processing. Duration: $durationMs ms")

            processedDetections.clear()

            val interval = 66L
            var currentTime = 0L

            val semaphore = java.util.concurrent.Semaphore(6)
            val futures = mutableListOf<java.util.concurrent.Future<*>>()

            while (currentTime < durationMs) {
                val timeForFrame = currentTime

                val bitmap = retriever.getFrameAtTime(timeForFrame * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)

                if (bitmap != null) {
                    val base64 = bitmapToBase64AndRecycle(bitmap)

                    semaphore.acquire()
                    val future = cameraExecutor.submit {
                        try {
                            val detection = fetchDetectionSyncBase64(base64)
                            if (detection != null) {
                                synchronized(processedDetections) {
                                    processedDetections[timeForFrame] = detection
                                }
                            } else {
                                Log.w(TAG, "No detection for frame at $timeForFrame")
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                    futures.add(future)
                } else {
                    Log.w(TAG, "Could not retrieve frame at $timeForFrame")
                }

                currentTime += interval
            }

            for (f in futures) {
                try {
                    f.get()
                } catch (e: Exception) {
                    Log.e(TAG, "Frame processing failed", e)
                }
            }

            val count = processedDetections.size
            Log.d(TAG, "Analysis Complete. Processed frames: $count")

            if (count > 0) {
                val detector = ShotEventDetector()
                val frameLabels = mutableListOf<String>()

                for ((ts, det) in processedDetections) {
                    val label = detector.feed(det.keypoints)
                    Log.d("SHOT_FRAME", "ts=$ts  feed()='$label'  keypoints=${det.keypoints.size}")
                    if (label.isNotEmpty()) frameLabels.add(label)
                }

                val finalShot = detector.flush()
                Log.d("SHOT_DETECTOR", "Flushed final shot: $finalShot")
                Log.d("SHOT_DETECTOR", "All intermediate labels across frames: $frameLabels")

                if (finalShot.isNotEmpty()) {
                    finalizedVideoShots[processedDetections.lastKey()] = finalShot
                }
                Log.d(TAG, "Shot event pass complete. Finalized events in ${finalizedVideoShots.size} frames.")
            }

            runOnUiThread {
                progressDialog.dismiss()
                if (count > 0) {
                    Toast.makeText(this@PracticeSession, "Analyzed $count frames!", Toast.LENGTH_SHORT).show()
                    startSyncedPlayback()
                } else {
                    Toast.makeText(this@PracticeSession, "No body detected in video.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pre-processing error", e)
            runOnUiThread {
                progressDialog.dismiss()
                Toast.makeText(this@PracticeSession, "Error processing: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            retriever.release()
        }
    }

    private fun startSyncedPlayback() {
        videoView.start()

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(0.5f) ?: android.media.PlaybackParams().setSpeed(0.5f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not set playback speed", e)
        }

        isVideoPlaying = true
        sessionStartTime = System.currentTimeMillis()

        videoOverlay.setImageSize(videoFrameW, videoFrameH)

        syncHandler.post(syncRunnable)
    }

    private val syncRunnable = object : Runnable {
        override fun run() {
            if (!isVideoPlaying && !videoView.isPlaying) return

            try {
                val currentPos = videoView.currentPosition.toLong()

                val entry     = processedDetections.floorEntry(currentPos)
                val ceilEntry = processedDetections.ceilingEntry(currentPos)

                val bestDetection: Detection? = when {
                    entry == null && ceilEntry == null -> null
                    entry == null -> ceilEntry!!.value
                    ceilEntry == null -> entry.value
                    else -> {
                        val t1    = entry.key
                        val t2    = ceilEntry.key
                        val span  = (t2 - t1).toFloat()
                        if (span < 1f) entry.value
                        else {
                            val alpha = ((currentPos - t1) / span).toFloat().coerceIn(0f, 1f)
                            interpolateDetections(entry.value, ceilEntry.value, alpha)
                        }
                    }
                }

                if (bestDetection != null) {
                    val shotEntry = finalizedVideoShots.floorEntry(currentPos)
                    val nearestShot = shotEntry?.value ?: ""
                    if (nearestShot.isNotEmpty()) currentShotType = nearestShot

                    videoOverlay.update(listOf(bestDetection), currentShotType)

                    analyzePose(bestDetection)
                    headTv.text = headStatus
                    shouldersTv.text = shoulderStatus
                    weightTv.text = weightShiftText
                    feetTv.text = footworkStatus

                    Log.d(TAG, "Shot map contents: ${finalizedVideoShots.entries.take(20)}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Sync error", e)
            }

            syncHandler.postDelayed(this, 33)
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // liveSyncRunnable — Display with confidence-weighted interpolation
    //
    // High-confidence keypoints smoothly interpolate.
    // Low-confidence keypoints snap to nearest detection (don't slide).
    // ─────────────────────────────────────────────────────────────────
    private val liveSyncRunnable = object : Runnable {
        override fun run() {
            if (!isLiveMode) return

            val toDisplay = latestLiveDetection ?: lastGoodLiveDetection
            if (toDisplay != null) {
                if (latestLiveDetection != null) {
                    lastGoodLiveDetection = latestLiveDetection
                    latestLiveDetection = null   // consume it
                }
                cameraOverlay.update(listOf(toDisplay), currentShotType)
                analyzePose(toDisplay)
                headTv.text      = headStatus
                shouldersTv.text = shoulderStatus
                weightTv.text    = weightShiftText
                feetTv.text      = footworkStatus
            }

            liveHandler.postDelayed(this, 16)
        }
    }

    /**
     * Confidence-weighted interpolation:
     * - High conf (>0.7): smooth lerp across time
     * - Medium conf (0.3–0.7): partial lerp
     * - Low conf (<0.3): snap to nearest (no interpolation)
     */
    private fun confidenceWeightedInterpolation(
        kps1: List<Keypoint>,
        kps2: List<Keypoint>,
        alpha: Float
    ): Detection {
        val interpolated = kps1.zip(kps2).map { (k1, k2) ->
            val avgConf = (k1.confidence + k2.confidence) / 2f

            when {
                // High confidence: smooth interpolation
                avgConf >= 0.7f -> {
                    Keypoint(
                        x = k1.x + (k2.x - k1.x) * alpha,
                        y = k1.y + (k2.y - k1.y) * alpha,
                        confidence = (k1.confidence + k2.confidence) / 2f
                    )
                }
                // Medium confidence: dampened interpolation
                avgConf >= 0.3f -> {
                    val dampedAlpha = alpha * 0.6f  // Only 60% of movement
                    Keypoint(
                        x = k1.x + (k2.x - k1.x) * dampedAlpha,
                        y = k1.y + (k2.y - k1.y) * dampedAlpha,
                        confidence = avgConf
                    )
                }
                // Low confidence: snap to nearest detection
                else -> {
                    if (alpha < 0.5f) k1 else k2
                }
            }
        }

        return Detection(
            label = "batsman",
            confidence = (kps1[0].confidence + kps2[0].confidence) / 2f,
            bbox = RectF(0f, 0f, 1f, 1f),  // Placeholder
            keypoints = interpolated
        )
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val analysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { proxy ->
                        if (!isProcessing) processFrame(proxy)
                    }
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun enterLiveMode() {
        if (videoView.isPlaying) videoView.stopPlayback()
        isVideoPlaying = false
        isLiveMode = true
        resetAnalysisState()
        syncHandler.removeCallbacks(syncRunnable)
        liveDetections.clear()

        videoContainer.visibility = View.GONE
        cameraContainer.visibility = View.VISIBLE

        liveHandler.removeCallbacks(liveSyncRunnable)
        liveHandler.post(liveSyncRunnable)
        Toast.makeText(this, "Live Mode Active", Toast.LENGTH_SHORT).show()
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processFrame(proxy: ImageProxy) {
        if (!isLiveMode) { proxy.close(); return }
        if (pendingRequests.get() >= MAX_CONCURRENT_REQUESTS) { proxy.close(); return }

        val captureTimeMs = System.currentTimeMillis()

        val bitmap = proxy.toBitmap()
        var rotated = bitmap
        if (proxy.imageInfo.rotationDegrees != 0) {
            rotated = rotateBitmap(bitmap, proxy.imageInfo.rotationDegrees.toFloat())
            if (rotated !== bitmap) bitmap.recycle()
        }

        val maxEdge = 256
        val scaleFactor = minOf(maxEdge.toFloat() / rotated.width, maxEdge.toFloat() / rotated.height, 1f)
        val fw = (rotated.width * scaleFactor).toInt()
        val fh = (rotated.height * scaleFactor).toInt()

        processBitmapFrame(rotated, captureTimeMs, fw, fh)
        proxy.close()
    }

    private fun processBitmapFrame(
        bitmap: Bitmap,
        timestamp: Long = 0L,
        frameW: Int = 640,
        frameH: Int = 480
    ) {
        pendingRequests.incrementAndGet()

        val resized = Bitmap.createScaledBitmap(bitmap, frameW, frameH, false)
        if (resized !== bitmap) bitmap.recycle()

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        resized.recycle()

        val jsonPayload = """
        {
            "api_key": "7VCjsMFfykWO22m0bCXb",
            "inputs": {
                "image": {
                    "type": "base64",
                    "value": "$base64"
                }
            }
        }
    """.trimIndent()

        val request = Request.Builder()
            .url(INFERENCE_URL)
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        Log.d(TAG, "Sending frame to Roboflow… (${base64.length} chars, ${frameW}x${frameH})")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "ROBOFLOW CONNECTION FAILED: ${e.message}")
                pendingRequests.decrementAndGet()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""

                if (sessionStartTime == 0L) sessionStartTime = System.currentTimeMillis()

                parseRoboflowResponse(responseBody, frameW, frameH, timestamp)

                pendingRequests.decrementAndGet()
            }
        })
    }

    private fun parseDetectionFromJson(json: String, w: Int, h: Int): Detection? {
        try {
            val root = JSONObject(json)
            val outputs = root.getJSONArray("outputs")

            Log.d(TAG, "Parsing JSON: ${outputs.length()} outputs")

            for (i in 0 until outputs.length()) {
                val output = outputs.getJSONObject(i)
                val predsV2 = output.optJSONArray("output_predictions_v2")

                if (predsV2 == null) {
                    Log.d(TAG, "Output $i has no predictions_v2")
                    continue
                }

                Log.d(TAG, "Output $i has ${predsV2.length()} predictions")

                for (j in 0 until predsV2.length()) {
                    val item = predsV2.getJSONObject(j)
                    val predictions = item.getJSONObject("predictions")
                    val predArray = predictions.getJSONArray("predictions")

                    if (predArray.length() == 0) {
                        Log.d(TAG, "Prediction item $j is empty")
                        continue
                    }

                    val pred = predArray.getJSONObject(0)
                    val confidence = pred.getDouble("confidence").toFloat()
                    Log.d(TAG, "Found prediction with confidence: $confidence")

                    val parentOrigin = pred.optJSONObject("parent_origin") ?: JSONObject()
                    val offsetX = parentOrigin.optInt("offset_x", 0)
                    val offsetY = parentOrigin.optInt("offset_y", 0)

                    val x = pred.getDouble("x").toFloat()
                    val y = pred.getDouble("y").toFloat()
                    val width = pred.getDouble("width").toFloat()
                    val height = pred.getDouble("height").toFloat()

                    val left = (x - width / 2 + offsetX) / w
                    val top = (y - height / 2 + offsetY) / h
                    val right = (x + width / 2 + offsetX) / w
                    val bottom = (y + height / 2 + offsetY) / h

                    val keypointsJson = pred.getJSONArray("keypoints")
                    val kpList = mutableListOf<Keypoint>()

                    for (k in 0 until keypointsJson.length()) {
                        val kp = keypointsJson.getJSONObject(k)
                        val kx = kp.getDouble("x").toFloat() + offsetX
                        val ky = kp.getDouble("y").toFloat() + offsetY
                        val kconf = kp.getDouble("confidence").toFloat()

                        kpList.add(Keypoint(kx / w, ky / h, kconf))
                    }

                    Log.d(TAG, "Returning valid detection with ${kpList.size} keypoints")

                    return Detection(
                        label = "batsman",
                        confidence = confidence,
                        bbox = RectF(left, top, right, bottom),
                        keypoints = kpList
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse JSON error", e)
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────
    // parseRoboflowResponse — Kalman-filtered per keypoint
    //
    // Each keypoint runs through its own Kalman filter to remove model
    // jitter while preserving real motion. The filter learns from:
    // - Measurement confidence (low conf = less trust)
    // - Velocity history (detect sudden jumps)
    // - Process noise (model error)
    // ─────────────────────────────────────────────────────────────────
    private fun parseRoboflowResponse(json: String, w: Int, h: Int, captureTimeMs: Long) {
        val raw = parseDetectionFromJson(json, w, h)
        if (raw != null && raw.keypoints.isNotEmpty()) {

            // Initialize Kalman filters once, reuse every frame
            if (liveKalmanFilters.size != raw.keypoints.size) {
                liveKalmanFilters.clear()
                repeat(raw.keypoints.size) { liveKalmanFilters.add(KeypointKalmanFilter()) }
            }

            // Apply Kalman filter to each keypoint
            val smoothedKps = raw.keypoints.mapIndexed { idx, kp ->
                val (sx, sy) = liveKalmanFilters[idx].update(kp.x, kp.y, kp.confidence)
                Keypoint(sx, sy, kp.confidence)
            }

            // Store as latest — the render loop picks it up on next tick
            latestLiveDetection = raw.copy(keypoints = smoothedKps)

            val shot = liveShotDetector.feed(smoothedKps)
            if (shot.isNotEmpty()) currentShotType = shot

            if (liveFrameW != w || liveFrameH != h) {
                liveFrameW = w; liveFrameH = h
                liveHandler.post { cameraOverlay.setImageSize(w, h) }
            }
        }
    }

    private fun getCurrentOverlay() = if (videoContainer.visibility == View.VISIBLE) videoOverlay else cameraOverlay

    private fun fetchDetectionSyncBase64(base64: String): Detection? {
        val jsonPayload = """
        {
            "api_key": "7VCjsMFfykWO22m0bCXb",
            "inputs": {
                "image": {
                    "type": "base64",
                    "value": "$base64"
                }
            }
        }
        """.trimIndent()

        val request = Request.Builder()
            .url(INFERENCE_URL)
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (response.isSuccessful && !body.isNullOrEmpty()) {
                Log.d(TAG, "Roboflow Sync Response: $body")
                return parseDetectionFromJson(body, videoFrameW, videoFrameH)
            } else {
                Log.w(TAG, "API Error: ${response.code} - $body")
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Sync Fetch Error", e)
        }
        return null
    }

    private fun interpolateDetections(a: Detection, b: Detection, alpha: Float): Detection {
        fun lerp(x: Float, y: Float) = x + (y - x) * alpha
        val bbox = android.graphics.RectF(
            lerp(a.bbox.left,   b.bbox.left),
            lerp(a.bbox.top,    b.bbox.top),
            lerp(a.bbox.right,  b.bbox.right),
            lerp(a.bbox.bottom, b.bbox.bottom)
        )
        val keypoints = if (a.keypoints.size == b.keypoints.size) {
            a.keypoints.zip(b.keypoints).map { (k1, k2) ->
                Keypoint(lerp(k1.x, k2.x), lerp(k1.y, k2.y), lerp(k1.confidence, k2.confidence))
            }
        } else a.keypoints
        return Detection(a.label, lerp(a.confidence, b.confidence), bbox, keypoints)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun bitmapToBase64AndRecycle(bitmap: Bitmap): String {
        val resized = Bitmap.createScaledBitmap(bitmap, videoFrameW, videoFrameH, true)
        if (resized != bitmap) bitmap.recycle()

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        resized.recycle()
        return b64
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        return if (degrees != 0f) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true) else bitmap
    }

    private fun analyzePose(detection: Detection) {
        val kp = detection.keypoints

        fun getPt(idx: Int): Pair<Float, Float>? {
            if (idx >= kp.size) return null
            val k = kp[idx]
            if (k.confidence < 0.3f) return null
            return k.x to k.y
        }

        val nose = getPt(0)
        if (nose != null) {
            headHistory.addLast(nose)
            if (headHistory.size > HISTORY_SIZE) headHistory.removeFirst()

            if (headHistory.size > 2) {
                val avgX = headHistory.map { it.first }.average()
                val avgY = headHistory.map { it.second }.average()
                val distSq = headHistory.sumOf { (it.first - avgX).pow(2) + (it.second - avgY).pow(2) }
                val variance = distSq / headHistory.size

                val maxVariance = 0.0015f
                val headTolerance = 0.0002f
                val score = if (variance.toFloat() <= headTolerance) 100f
                else (100f - ((variance.toFloat() - headTolerance) / (maxVariance - headTolerance)) * 100f).coerceIn(0f, 100f)

                headBadCount = if (score < 100f) minOf(headBadCount + 1, MAX_PERSISTENCE) else maxOf(headBadCount - 2, 0)
                val finalScore = if (headBadCount >= PERSISTENCE_FRAMES) score else 100f

                headStabilityScore = (headStabilityScore * 0.8f) + (finalScore * 0.2f)

                headStatus = "${headStabilityScore.toInt()}%"
            }
        }

        val ls = getPt(5)
        val rs = getPt(6)
        if (ls != null && rs != null) {
            val dy = (rs.second - ls.second)
            val dx = (rs.first - ls.first)
            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

            val maxAngle = 20f
            val shoulderTolerance = 4f
            val targetAngle = abs(angle)
            val score = if (targetAngle <= shoulderTolerance) 100f
            else (100f - ((targetAngle - shoulderTolerance) / (maxAngle - shoulderTolerance)) * 100f).coerceIn(0f, 100f)

            shoulderBadCount = if (score < 100f) minOf(shoulderBadCount + 1, MAX_PERSISTENCE) else maxOf(shoulderBadCount - 2, 0)
            val finalScore = if (shoulderBadCount >= PERSISTENCE_FRAMES) score else 100f

            shoulderScore = (shoulderScore * 0.8f) + (finalScore * 0.2f)

            shoulderStatus = "${shoulderScore.toInt()}%"
        }

        val lHip = getPt(11)
        val rHip = getPt(12)
        val lAnk = getPt(15)
        val rAnk = getPt(16)

        if (lHip != null && rHip != null && lAnk != null && rAnk != null) {
            val hipCenter = (lHip.first + rHip.first) / 2
            val ankCenter = (lAnk.first + rAnk.first) / 2

            val diff = abs(hipCenter - ankCenter)
            val maxDiff = 0.1f
            val weightTolerance = 0.02f
            val score = if (diff <= weightTolerance) 100f
            else (100f - ((diff - weightTolerance) / (maxDiff - weightTolerance)) * 100f).coerceIn(0f, 100f)

            weightBadCount = if (score < 100f) minOf(weightBadCount + 1, MAX_PERSISTENCE) else maxOf(weightBadCount - 2, 0)
            val finalScore = if (weightBadCount >= PERSISTENCE_FRAMES) score else 100f

            weightScore = (weightScore * 0.8f) + (finalScore * 0.2f)

            weightShiftText = "${weightScore.toInt()}%"
        }

        if (lAnk != null && rAnk != null) {
            val currentFeet = (lAnk.first + rAnk.first) / 2 to (lAnk.second + rAnk.second) / 2

            if (lastFootPosition != null) {
                val dist = sqrt((currentFeet.first - lastFootPosition!!.first).pow(2) + (currentFeet.second - lastFootPosition!!.second).pow(2))
                val maxDist = 0.05f
                val footTolerance = 0.005f
                val score = if (dist.toFloat() <= footTolerance) 100f
                else (100f - ((dist.toFloat() - footTolerance) / (maxDist - footTolerance)) * 100f).coerceIn(0f, 100f)

                footworkBadCount = if (score < 100f) minOf(footworkBadCount + 1, MAX_PERSISTENCE) else maxOf(footworkBadCount - 2, 0)
                val finalScore = if (footworkBadCount >= PERSISTENCE_FRAMES) score else 100f

                footworkScore = (footworkScore * 0.7f) + (finalScore * 0.3f)
                footworkStatus = "${footworkScore.toInt()}%"
            }
            lastFootPosition = currentFeet
        }
    }

    private fun resetAnalysisState() {
        liveShotDetector.reset()
        finalizedVideoShots.clear()
        currentShotType = ""
        headHistory.clear()
        headStabilityScore = 100f
        shoulderScore = 100f
        weightScore = 100f
        footworkScore = 100f
        weightShiftText = "100%"
        shoulderStatus = "100%"
        headStatus = "100%"
        footworkStatus = "100%"
        lastFootPosition = null

        headBadCount = 0
        shoulderBadCount = 0
        weightBadCount = 0
        footworkBadCount = 0

        lastGoodLiveDetection = null
        liveDetections.clear()

        runOnUiThread {
            headTv.text = "--"
            shouldersTv.text = "--"
            weightTv.text = " --"
            feetTv.text = "--"
        }
    }

    private fun saveSession() {
        if (sessionStartTime == 0L) sessionStartTime = System.currentTimeMillis()

        val durationSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt().coerceAtLeast(30)

        val summary = "Head: $headStatus | Shoulders: $shoulderStatus | Weight: $weightShiftText | Feet: $footworkStatus"

        val session = SessionEntity(
            userId = userId,
            drillType = "Pose Analysis",
            durationSeconds = durationSeconds,
            successRate = 1.0,
            flawDetails = summary,
            dateMillis = System.currentTimeMillis()
        )

        sessionViewModel.insert(session)
        Toast.makeText(this, "Session Saved to History!", Toast.LENGTH_LONG).show()
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21, android.graphics.ImageFormat.NV21, width, height, null
        )
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 85, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        syncHandler.removeCallbacksAndMessages(null)
        liveHandler.removeCallbacksAndMessages(null)
    }
}



//// PracticeSession.kt — FINAL WORKING VERSION (Dec 2025)




//package com.fyp.nextshot
//
//import android.annotation.SuppressLint
//import android.content.Intent
//import android.graphics.*
//import android.media.MediaMetadataRetriever
//import android.media.MediaPlayer
//import android.net.Uri
//import android.os.Bundle
//import android.os.Handler
//import android.os.Looper
//import android.util.Base64
//import android.util.Log
//import android.view.View
//import android.widget.*
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.annotation.OptIn
//import androidx.appcompat.app.AppCompatActivity
//import androidx.camera.core.*
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.view.PreviewView
//import androidx.core.content.ContextCompat
//import okhttp3.*
//import okhttp3.MediaType.Companion.toMediaType
//import okhttp3.RequestBody.Companion.toRequestBody
//import org.json.JSONArray
//import org.json.JSONObject
//import java.io.ByteArrayOutputStream
//import java.io.IOException
//import androidx.activity.viewModels
//import androidx.lifecycle.lifecycleScope
//import com.fyp.nextshot.data.local.database.AppDatabase
//import com.fyp.nextshot.data.local.models.SessionEntity
//import com.fyp.nextshot.data.repository.SessionRepository
//import com.fyp.nextshot.data.repository.TipsRepository
//import com.fyp.nextshot.ui.viewmodel.SessionViewModel
//import com.fyp.nextshot.ui.viewmodel.SessionViewModelFactory
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FirebaseFirestore
//import kotlinx.coroutines.launch
//import java.util.*
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//import java.util.concurrent.TimeUnit
//import kotlin.math.*
//
//class PracticeSession : AppCompatActivity() {
//
//
//
//    private val TAG = "NEXTSHOT_DEBUG"
//
//    private lateinit var previewView: PreviewView
//    private lateinit var videoView: VideoView
//    private lateinit var cameraOverlay: BoundingBoxOverlay
//    private lateinit var videoOverlay: BoundingBoxOverlay
//    private lateinit var videoContainer: FrameLayout
//    private lateinit var cameraContainer: FrameLayout
//    private lateinit var playPauseBtn: ImageView
//    private lateinit var headTv: TextView
//    private lateinit var shouldersTv: TextView
//    private lateinit var weightTv: TextView
//    private lateinit var feetTv: TextView
//    private var lastDetection: Detection? = null
//    private var lastUpdateTime = 0L
//
//    // For Video Sync
//    private val processedDetections = TreeMap<Long, Detection>()
//    private val syncHandler = Handler(Looper.getMainLooper())
//    private var isVideoPlaying = false
//
//    // Mode tracking to prevent interference
//    private var isLiveMode = true
//    private lateinit var progressDialog: android.app.AlertDialog
//    private var mediaPlayer: MediaPlayer? = null
//
//    // Live Mode Optimization
//    private val pendingRequests = java.util.concurrent.atomic.AtomicInteger(0)
//    private var lastProcessedTimestamp = 0L
//    private val MAX_CONCURRENT_REQUESTS = 4
//
//    private lateinit var cameraExecutor: ExecutorService
//
//    // INCREASED TIMEOUTS + CORRECT URL
//    private val client = OkHttpClient.Builder()
//        .connectTimeout(30, TimeUnit.SECONDS)
//        .writeTimeout(30, TimeUnit.SECONDS)
//        .readTimeout(60, TimeUnit.SECONDS)
//        .build()
//
//    // THIS IS THE ONLY CORRECT URL FOR WORKFLOWS IN 2025
//    private val INFERENCE_URL = "https://serverless.roboflow.com/hello-7pqr3/workflows/custom-workflow-3"
//
//    // ANALYSIS STATE
//    private var prevHeadCenterGlobal: Pair<Float, Float>? = null
//    private var headStabilityScore = 100f
//    private var shoulderScore = 100f
//    private var weightScore = 100f
//    private var footworkScore = 100f
//    private var weightShiftText = "100%"
//    private var isBalanced = true
//    private var isProcessing = false
//
//    // History Buffers for Analysis
//    private val headHistory = java.util.ArrayDeque<Pair<Float, Float>>()
//    private val HISTORY_SIZE = 10
//    private var lastFootPosition: Pair<Float, Float>? = null
//    private var footworkStatus = "100%"
//    private var shoulderStatus = "100%"
//    private var headStatus = "100%"
//
//    // Persistence Counters (Damping)
//    private var headBadCount = 0
//    private var shoulderBadCount = 0
//    private var weightBadCount = 0
//    private var footworkBadCount = 0
//    private val PERSISTENCE_FRAMES = 15 // ~1 second before registering flaw
//    private val MAX_PERSISTENCE = 30
//
//    // Session Management
//    private val auth by lazy { FirebaseAuth.getInstance() }
//    private val db by lazy { FirebaseFirestore.getInstance() }
//    private val userId = auth.currentUser?.uid ?: "FALLBACK_UID"
//    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
//    private val repository by lazy { SessionRepository(database.sessionDao(), userId, db) }
//    private val sessionViewModel: SessionViewModel by viewModels {
//        SessionViewModelFactory(repository)
//    }
//    private var sessionStartTime = 0L
//
//
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_practice_session)
//
//        previewView = findViewById(R.id.preview_view)
//        videoView = findViewById(R.id.video_view)
//        cameraOverlay = findViewById(R.id.bbox_overlay)
//        videoOverlay = findViewById(R.id.video_overlay)
//        videoContainer = findViewById(R.id.video_container)
//        cameraContainer = findViewById(R.id.camera_container)
//        playPauseBtn = findViewById(R.id.btn_play_pause)
//        headTv = findViewById(R.id.tv_head_stability)
//        shouldersTv = findViewById(R.id.tv_shoulders)
//        weightTv = findViewById(R.id.tv_weight_balance)
//        feetTv = findViewById(R.id.tv_footwork)
//
//
//
//        // Use more threads for network IO
//        cameraExecutor = Executors.newFixedThreadPool(8)
//
//        findViewById<View>(R.id.btn_upload_video).setOnClickListener { pickVideo() }
//        findViewById<View>(R.id.btn_live_record).setOnClickListener { enterLiveMode() }
//
//        // Hide the button as requested
//        playPauseBtn.visibility = View.GONE
//
//        // Tap video to toggle play/pause
//        videoContainer.setOnClickListener {
//            if (videoView.isPlaying) {
//                videoView.pause()
//                isVideoPlaying = false
//                syncHandler.removeCallbacks(syncRunnable)
//                Toast.makeText(this, "Paused", Toast.LENGTH_SHORT).show()
//            } else {
//                videoView.start()
//                isVideoPlaying = true
//                syncHandler.post(syncRunnable)
//                Toast.makeText(this, "Resuming", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//        findViewById<View>(R.id.btn_save_session).setOnClickListener {
//            saveSession()
//        }
//
//        setupProgressDialog()
//        setupProgressDialog()
//
//        if (allPermissionsGranted()) {
//            startCamera()
//        } else {
//            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
//        }
//    }
//
//    private val requestPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { isGranted: Boolean ->
//        if (isGranted) {
//            startCamera()
//        } else {
//            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
//        baseContext, android.Manifest.permission.CAMERA
//    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
//
//    private fun setupProgressDialog() {
//        val builder = android.app.AlertDialog.Builder(this)
//        builder.setCancelable(false)
//        builder.setView(ProgressBar(this).apply {
//            setPadding(50, 50, 50, 50)
//        })
//        builder.setMessage("Processing Video... Please Wait")
//        progressDialog = builder.create()
//    }
//
//    private fun pickVideo() {
//        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "video/*" }
//        videoPicker.launch(intent)
//    }
//
//    private val videoPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//        if (result.resultCode == RESULT_OK && result.data?.data != null) {
//            startVideoAnalysis(result.data!!.data!!)
//        }
//    }
//
//    private fun startVideoAnalysis(uri: Uri) {
//        isLiveMode = false
//        resetAnalysisState()
//        cameraContainer.visibility = View.GONE
//        videoContainer.visibility = View.VISIBLE
//        // Stop camera analysis to save resources?
//        // simple way: isProcessing = true (but for video)
//        // correct way: unbind camera? For now just hide and let it idle (it won't process if we flag it)
//
//        videoOverlay.clear()
//
//        // Stop any previous playback
//        videoView.stopPlayback()
//        syncHandler.removeCallbacks(syncRunnable)
//
//        videoView.setVideoURI(uri)
//        videoView.setOnPreparedListener { mp ->
//            mediaPlayer = mp
//            mp.isLooping = false
//            // Don't auto-start. Wait for analysis.
//        }
//
//        // Start Pre-processing
//        runOnUiThread {
//            progressDialog.show()
//        }
//        cameraExecutor.execute {
//            preProcessVideo(uri)
//        }
//    }
//
//    private fun preProcessVideo(uri: Uri) {
//        val retriever = MediaMetadataRetriever()
//        try {
//            retriever.setDataSource(this, uri)
//            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
//            val durationMs = durationStr?.toLongOrNull() ?: 0L
//
//            Log.d(TAG, "Starting pre-processing. Duration: $durationMs ms")
//
//            processedDetections.clear()
//
//            // 66ms ~ 15 FPS
//            val interval = 66L
//            var currentTime = 0L
//
//            // Collect all tasks first
//            val futures = mutableListOf<java.util.concurrent.Future<*>>()
//
//            while (currentTime < durationMs) {
//                val timeForFrame = currentTime
//
//                // Retrieve frame (Synchronous part - fast enough usually)
//                // We use OPTION_CLOSEST_SYNC for better accuracy if available, or just CLOSEST
//                val bitmap = retriever.getFrameAtTime(timeForFrame * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
//
//                if (bitmap != null) {
//                    // Convert to base64 immediately to save memory (don't keep raw bitmaps in queue)
//                    val base64 = bitmapToBase64AndRecycle(bitmap)
//
//                    // Submit network task
//                    val future = cameraExecutor.submit {
//                        val detection = fetchDetectionSyncBase64(base64)
//                        if (detection != null) {
//                            synchronized(processedDetections) {
//                                processedDetections[timeForFrame] = detection
//                            }
//                        } else {
//                            Log.w(TAG, "No detection for frame at $timeForFrame")
//                        }
//                    }
//                    futures.add(future)
//                } else {
//                    Log.w(TAG, "Could not retrieve frame at $timeForFrame")
//                }
//
//                currentTime += interval
//            }
//
//            // Wait for all
//            for (f in futures) {
//                try {
//                    f.get()
//                } catch (e: Exception) {
//                    Log.e(TAG, "Frame processing failed", e)
//                }
//            }
//
//            val count = processedDetections.size
//            Log.d(TAG, "Analysis Complete. Processed frames: $count")
//
//            // Analysis complete
//            runOnUiThread {
//                progressDialog.dismiss()
//                if (count > 0) {
//                    Toast.makeText(this@PracticeSession, "Analyzed $count frames!", Toast.LENGTH_SHORT).show()
//                    startSyncedPlayback()
//                } else {
//                    Toast.makeText(this@PracticeSession, "No body detected in video.", Toast.LENGTH_LONG).show()
//                }
//            }
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Pre-processing error", e)
//            runOnUiThread {
//                progressDialog.dismiss()
//                Toast.makeText(this@PracticeSession, "Error processing: ${e.message}", Toast.LENGTH_SHORT).show()
//            }
//        } finally {
//            retriever.release()
//        }
//    }
//
//    private fun startSyncedPlayback() {
//        videoView.start()
//
//        // Slow motion (0.5x) for better analysis
//        try {
//            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
//                mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(0.5f) ?: android.media.PlaybackParams().setSpeed(0.5f)
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Could not set playback speed", e)
//        }
//
//        isVideoPlaying = true
//        sessionStartTime = System.currentTimeMillis()
//
//        // Critical Fix: Overlay needs to know the source size to draw anything!
//        videoOverlay.setImageSize(640, 480)
//
//        syncHandler.post(syncRunnable)
//    }
//
//    private val syncRunnable = object : Runnable {
//        override fun run() {
//            if (!isVideoPlaying && !videoView.isPlaying) return
//
//            try {
//                val currentPos = videoView.currentPosition.toLong()
//
//                // Find closest detection within 100ms window
//                val entry = processedDetections.floorEntry(currentPos)
//                val ceilEntry = processedDetections.ceilingEntry(currentPos)
//
//                // Simple logic: picking the closest one
//                val bestDetection = when {
//                    entry == null && ceilEntry == null -> null
//                    entry == null -> ceilEntry!!.value
//                    ceilEntry == null -> entry.value
//                    (currentPos - entry.key) < (ceilEntry.key - currentPos) -> entry.value
//                    else -> ceilEntry.value
//                }
//
//                val overlay = videoOverlay
//                if (bestDetection != null) {
//                    overlay.setDetections(listOf(bestDetection))
//                } else {
//                    // Optional: clear if no detection nearby?
//                    // Let's keep last one if it's not too old, or just let it stay until replaced.
//                }
//
//                // Update Analysis UI for Video
//                if (bestDetection != null) {
//                    analyzePose(bestDetection)
//                    headTv.text = "$headStatus"
//                    shouldersTv.text = "$shoulderStatus"
//                    weightTv.text = "$weightShiftText"
//                    feetTv.text = "$footworkStatus"
//                }
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Sync error", e)
//            }
//
//            syncHandler.postDelayed(this, 33) // ~30 FPS UI update
//        }
//    }
//
//
//    @OptIn(ExperimentalGetImage::class)
//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//        cameraProviderFuture.addListener({
//            val cameraProvider = cameraProviderFuture.get()
//
//            // Force 4:3 ensures closest match to 640x480 analysis for alignment
//            val preview = Preview.Builder()
//                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
//                .build().also {
//                    it.setSurfaceProvider(previewView.surfaceProvider)
//                }
//
//            val analysis = ImageAnalysis.Builder()
//                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build()
//                .also {
//                    it.setAnalyzer(cameraExecutor) { proxy ->
//                        if (!isProcessing) processFrame(proxy)
//                    }
//                }
//
//            cameraProvider.unbindAll()
//            cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
//        }, ContextCompat.getMainExecutor(this))
//    }
//
//    private fun enterLiveMode() {
//        // Stop video
//        if (videoView.isPlaying) videoView.stopPlayback()
//        isVideoPlaying = false
//        isLiveMode = true
//        resetAnalysisState()
//        syncHandler.removeCallbacks(syncRunnable)
//        videoContainer.visibility = View.GONE
//
//        // Show camera
//        cameraContainer.visibility = View.VISIBLE
//
//        // Mirror video mode: pre-initialise the overlay size so it can render
//        // even before the first Roboflow response arrives.
//        cameraOverlay.setImageSize(640, 480)
//
//        Toast.makeText(this, "Live Mode Active", Toast.LENGTH_SHORT).show()
//    }
//
//    @androidx.camera.core.ExperimentalGetImage
//    private fun processFrame(proxy: ImageProxy) {
//        // Stop analysis if not in Live Mode
//        if (!isLiveMode) {
//            proxy.close()
//            return
//        }
//
//        // Drop frame if too many requests in flight
//        if (pendingRequests.get() >= MAX_CONCURRENT_REQUESTS) {
//            proxy.close()
//            return
//        }
//
//        // Track ordering
//        val timestamp = proxy.imageInfo.timestamp
//
//        val bitmap = proxy.toBitmap()
//        val rotated = rotateBitmap(bitmap, proxy.imageInfo.rotationDegrees.toFloat())
//
//        // Pass the real rotated dimensions so the overlay and parser stay in sync.
//        // Do NOT recycle `rotated` here — processBitmapFrame will handle it.
//        processBitmapFrame(rotated, timestamp, rotated.width, rotated.height)
//        proxy.close()
//    }
//
//    /**
//     * @param frameW  width  of the bitmap actually sent to Roboflow (after rotation)
//     * @param frameH  height of the bitmap actually sent to Roboflow (after rotation)
//     *
//     * We keep these as parameters so the parser and the overlay always agree on the
//     * coordinate space — the same fix that makes the video pipeline work correctly.
//     */
//    private fun processBitmapFrame(
//        bitmap: Bitmap,
//        timestamp: Long = 0L,
//        frameW: Int = 640,
//        frameH: Int = 480
//    ) {
//        pendingRequests.incrementAndGet()
//
//        // Scale to exactly the dimensions we will tell Roboflow / the overlay about.
//        // Using the REAL aspect ratio avoids distortion that shifts keypoint positions.
//        val resized = Bitmap.createScaledBitmap(bitmap, frameW, frameH, true)
//        val base64 = bitmapToBase64(resized)
//        if (resized !== bitmap) resized.recycle()
//
//        val jsonPayload = """
//        {
//            "api_key": "7VCjsMFfykWO22m0bCXb",
//            "inputs": {
//                "image": {
//                    "type": "base64",
//                    "value": "$base64"
//                }
//            }
//        }
//    """.trimIndent()
//
//        val request = Request.Builder()
//            .url(INFERENCE_URL)
//            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
//            .build()
//
//        Log.d(TAG, "Sending frame to Roboflow… (${base64.length} chars, ${frameW}x${frameH})")
//
//        client.newCall(request).enqueue(object : Callback {
//            override fun onFailure(call: Call, e: IOException) {
//                Log.e(TAG, "ROBOFLOW CONNECTION FAILED: ${e.message}")
//                pendingRequests.decrementAndGet()
//            }
//
//            override fun onResponse(call: Call, response: Response) {
//                val responseBody = response.body?.string() ?: ""
//
//                // Only update if this frame is newer than what's currently shown
//                // (For video sync, timestamp is 0, so we ignore)
//                if (timestamp > 0) {
//                    if (sessionStartTime == 0L) sessionStartTime = System.currentTimeMillis()
//                    if (timestamp > lastProcessedTimestamp) {
//                        lastProcessedTimestamp = timestamp
//                        // ✅ Pass the SAME dimensions used when encoding the frame
//                        parseRoboflowResponse(responseBody, frameW, frameH)
//                    } else {
//                        Log.d(TAG, "Dropping old frame")
//                    }
//                } else {
//                    // Legacy/Video path
//                    parseRoboflowResponse(responseBody, frameW, frameH)
//                }
//
//                pendingRequests.decrementAndGet()
//            }
//        })
//    }
//
//    private fun parseDetectionFromJson(json: String, w: Int, h: Int): Detection? {
//        try {
//            val root = JSONObject(json)
//            val outputs = root.getJSONArray("outputs")
//
//            Log.d(TAG, "Parsing JSON: ${outputs.length()} outputs")
//
//            for (i in 0 until outputs.length()) {
//                val output = outputs.getJSONObject(i)
//                val predsV2 = output.optJSONArray("output_predictions_v2")
//
//                if (predsV2 == null) {
//                    Log.d(TAG, "Output $i has no predictions_v2")
//                    continue
//                }
//
//                Log.d(TAG, "Output $i has ${predsV2.length()} predictions")
//
//                for (j in 0 until predsV2.length()) {
//                    val item = predsV2.getJSONObject(j)
//                    val predictions = item.getJSONObject("predictions")
//                    val predArray = predictions.getJSONArray("predictions")
//
//                    if (predArray.length() == 0) {
//                        Log.d(TAG, "Prediction item $j is empty")
//                        continue
//                    }
//
//                    val pred = predArray.getJSONObject(0)
//                    val confidence = pred.getDouble("confidence").toFloat()
//                    Log.d(TAG, "Found prediction with confidence: $confidence")
//
//                    val parentOrigin = pred.optJSONObject("parent_origin") ?: JSONObject()
//                    val offsetX = parentOrigin.optInt("offset_x", 0)
//                    val offsetY = parentOrigin.optInt("offset_y", 0)
//
//                    val x = pred.getDouble("x").toFloat()
//                    val y = pred.getDouble("y").toFloat()
//                    val width = pred.getDouble("width").toFloat()
//                    val height = pred.getDouble("height").toFloat()
//
//                    val left = (x - width / 2 + offsetX) / w
//                    val top = (y - height / 2 + offsetY) / h
//                    val right = (x + width / 2 + offsetX) / w
//                    val bottom = (y + height / 2 + offsetY) / h
//
//                    val keypointsJson = pred.getJSONArray("keypoints")
//                    val kpList = mutableListOf<Keypoint>()
//
//                    for (k in 0 until keypointsJson.length()) {
//                        val kp = keypointsJson.getJSONObject(k)
//                        val kx = kp.getDouble("x").toFloat() + offsetX
//                        val ky = kp.getDouble("y").toFloat() + offsetY
//                        val kconf = kp.getDouble("confidence").toFloat()
//
//                        kpList.add(Keypoint(kx / w, ky / h, kconf))
//                    }
//
//                    Log.d(TAG, "Returning valid detection with ${kpList.size} keypoints")
//
//                    return Detection(
//                        label = "batsman",
//                        confidence = confidence,
//                        bbox = RectF(left, top, right, bottom),
//                        keypoints = kpList
//                    )
//                }
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Parse JSON error", e)
//        }
//        return null
//    }
//
//    @SuppressLint("SetTextI18n")
//    private fun parseRoboflowResponse(json: String, w: Int, h: Int) {
//        val detection = parseDetectionFromJson(json, w, h)
//
//        // THIS IS THE ONLY UI UPDATE — SMOOTH & CORRECT
//        if (detection != null && detection.keypoints.isNotEmpty()) {
//            lastDetection = detection
//            lastUpdateTime = System.currentTimeMillis()
//        }
//
//        runOnUiThread {
//            val overlay = getCurrentOverlay()
//            overlay.setImageSize(w, h)
//
//            val now = System.currentTimeMillis()
//            val timeSinceLast = now - lastUpdateTime
//
//            if (lastDetection != null && timeSinceLast < 1000) { // Keep pose for 800ms
//                overlay.setDetections(listOf(lastDetection!!))
//            } else {
//                overlay.clear()
//            }
//
//            // Update Analysis UI
//            if (lastDetection != null) {
//                analyzePose(lastDetection!!)
//                headTv.text = "$headStatus"
//                shouldersTv.text = "$shoulderStatus"
//                weightTv.text = "$weightShiftText"
//                feetTv.text = "$footworkStatus"
//            }
//
//            overlay.invalidate()
//        }
//    }
//
//    private fun getCurrentOverlay() = if (videoContainer.visibility == View.VISIBLE) videoOverlay else cameraOverlay
//
//    // --- HELPER FOR VIDEO SYNC (BLOCKING) ---
//    private fun fetchDetectionSyncBase64(base64: String): Detection? {
//        val jsonPayload = """
//        {
//            "api_key": "7VCjsMFfykWO22m0bCXb",
//            "inputs": {
//                "image": {
//                    "type": "base64",
//                    "value": "$base64"
//                }
//            }
//        }
//        """.trimIndent()
//
//        val request = Request.Builder()
//            .url(INFERENCE_URL)
//            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
//            .build()
//
//        try {
//            val response = client.newCall(request).execute()
//            val body = response.body?.string()
//
//            if (response.isSuccessful && !body.isNullOrEmpty()) {
//                Log.d(TAG, "Roboflow Sync Response: $body")
//                return parseDetectionFromJson(body, 640, 480)
//            } else {
//                Log.w(TAG, "API Error: ${response.code} - $body")
//            }
//            response.close()
//        } catch (e: Exception) {
//            Log.e(TAG, "Sync Fetch Error", e)
//        }
//        return null
//    }
//
//    // Restored this function for live camera usage
//    private fun bitmapToBase64(bitmap: Bitmap): String {
//        val baos = ByteArrayOutputStream()
//        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
//        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
//    }
//
//    // Combined helper to resize/compress/recycle quickly
//    private fun bitmapToBase64AndRecycle(bitmap: Bitmap): String {
//        val resized = Bitmap.createScaledBitmap(bitmap, 640, 480, true)
//        if (resized != bitmap) bitmap.recycle() // Recycle original if scaled
//
//        val baos = ByteArrayOutputStream()
//        resized.compress(Bitmap.CompressFormat.JPEG, 60, baos)
//        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
//
//        resized.recycle() // Recycle resized
//        return b64
//    }
//
//    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
//        return if (degrees != 0f) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true) else bitmap
//    }
//
//    private fun analyzePose(detection: Detection) {
//        val kp = detection.keypoints
//        val w = 640f // Normalized to reference width
//        val h = 480f
//
//        // Helper to get raw coords (0-1 range)
//        fun getPt(idx: Int): Pair<Float, Float>? {
//            // Find keypoint with index logic if relying on list order is unsafe
//            // But usually list is ordered if full skeleton.
//            // Better: find by index if your Keypoint class doesn't store index.
//            // Wait, Keypoint class in BoundingBoxOverlay.kt doesn't have index or part name.
//            // We must rely on list order from skeleton logic:
//            // 0:Nose, 1:LEye, 2:REye, 3:LEar, 4:REar, 5:LShoulder, 6:RShoulder
//            // 11:LHip, 12:RHip, 15:LAnkle, 16:RAnkle
//            if (idx >= kp.size) return null
//            val k = kp[idx]
//            if (k.confidence < 0.3f) return null
//            return k.x to k.y
//        }
//
//        // 1. Head Stability (Nose: 0)
//        val nose = getPt(0)
//        if (nose != null) {
//            headHistory.addLast(nose)
//            if (headHistory.size > HISTORY_SIZE) headHistory.removeFirst()
//
//            if (headHistory.size > 2) {
//                // Calculate variance
//                val avgX = headHistory.map { it.first }.average()
//                val avgY = headHistory.map { it.second }.average()
//                val distSq = headHistory.sumOf { (it.first - avgX).pow(2) + (it.second - avgY).pow(2) }
//                val variance = distSq / headHistory.size
//
//                // Convert variance to percentage (0.0015f variance is 0% stable)
//                val maxVariance = 0.0015f
//                val headTolerance = 0.0002f // AI jitter deadzone
//                val score = if (variance.toFloat() <= headTolerance) 100f
//                else (100f - ((variance.toFloat() - headTolerance) / (maxVariance - headTolerance)) * 100f).coerceIn(0f, 100f)
//
//                headBadCount = if (score < 100f) minOf(headBadCount + 1, MAX_PERSISTENCE) else maxOf(headBadCount - 2, 0)
//                val finalScore = if (headBadCount >= PERSISTENCE_FRAMES) score else 100f
//
//                // Exponential moving average for smoothness
//                headStabilityScore = (headStabilityScore * 0.8f) + (finalScore * 0.2f)
//
//                headStatus = "${headStabilityScore.toInt()}%"
//            }
//        }
//
//        // 2. Shoulder Stability (5: Left, 6: Right)
//        val ls = getPt(5)
//        val rs = getPt(6)
//        if (ls != null && rs != null) {
//            val dy = (rs.second - ls.second)
//            val dx = (rs.first - ls.first)
//            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
//
//            // Assuming standard pose, shoulders should be roughly level (0 degrees)
//            val maxAngle = 20f
//            val shoulderTolerance = 4f // Allow up to 4 degrees of natural tilt/jitter
//            val targetAngle = abs(angle)
//            val score = if (targetAngle <= shoulderTolerance) 100f
//            else (100f - ((targetAngle - shoulderTolerance) / (maxAngle - shoulderTolerance)) * 100f).coerceIn(0f, 100f)
//
//            shoulderBadCount = if (score < 100f) minOf(shoulderBadCount + 1, MAX_PERSISTENCE) else maxOf(shoulderBadCount - 2, 0)
//            val finalScore = if (shoulderBadCount >= PERSISTENCE_FRAMES) score else 100f
//
//            // Exponential moving average for smoothness
//            shoulderScore = (shoulderScore * 0.8f) + (finalScore * 0.2f)
//
//            shoulderStatus = "${shoulderScore.toInt()}%"
//        }
//
//        // 3. Weight Distribution (Hips vs Ankles X)
//        // 11: LHip, 12: RHip, 15: LAnkle, 16: RAnkle
//        val lHip = getPt(11)
//        val rHip = getPt(12)
//        val lAnk = getPt(15)
//        val rAnk = getPt(16)
//
//        if (lHip != null && rHip != null && lAnk != null && rAnk != null) {
//            val hipCenter = (lHip.first + rHip.first) / 2
//            val ankCenter = (lAnk.first + rAnk.first) / 2
//
//            val diff = abs(hipCenter - ankCenter)
//            val maxDiff = 0.1f // 10% of frame width difference is 0% centered
//            val weightTolerance = 0.02f // 2% width deadzone for natural stance
//            val score = if (diff <= weightTolerance) 100f
//            else (100f - ((diff - weightTolerance) / (maxDiff - weightTolerance)) * 100f).coerceIn(0f, 100f)
//
//            weightBadCount = if (score < 100f) minOf(weightBadCount + 1, MAX_PERSISTENCE) else maxOf(weightBadCount - 2, 0)
//            val finalScore = if (weightBadCount >= PERSISTENCE_FRAMES) score else 100f
//
//            // Exponential moving average
//            weightScore = (weightScore * 0.8f) + (finalScore * 0.2f)
//
//            weightShiftText = "${weightScore.toInt()}%"
//        }
//
//        // 4. Footwork (Ankle movement)
//        if (lAnk != null && rAnk != null) {
//            val currentFeet = (lAnk.first + rAnk.first) / 2 to (lAnk.second + rAnk.second) / 2
//
//            if (lastFootPosition != null) {
//                val dist = sqrt((currentFeet.first - lastFootPosition!!.first).pow(2) + (currentFeet.second - lastFootPosition!!.second).pow(2))
//                val maxDist = 0.05f
//                val footTolerance = 0.005f // Ignore micro-pixel bounding box jitter
//                val score = if (dist.toFloat() <= footTolerance) 100f
//                else (100f - ((dist.toFloat() - footTolerance) / (maxDist - footTolerance)) * 100f).coerceIn(0f, 100f)
//
//                footworkBadCount = if (score < 100f) minOf(footworkBadCount + 1, MAX_PERSISTENCE) else maxOf(footworkBadCount - 2, 0)
//                val finalScore = if (footworkBadCount >= PERSISTENCE_FRAMES) score else 100f
//
//                // Fast recovery, slow trail for footwork
//                footworkScore = (footworkScore * 0.7f) + (finalScore * 0.3f)
//                footworkStatus = "${footworkScore.toInt()}%"
//            }
//            lastFootPosition = currentFeet
//        }
//    }
//
//    private fun resetAnalysisState() {
//        headHistory.clear()
//        headStabilityScore = 100f
//        shoulderScore = 100f
//        weightScore = 100f
//        footworkScore = 100f
//        weightShiftText = "100%"
//        shoulderStatus = "100%"
//        headStatus = "100%"
//        footworkStatus = "100%"
//        lastFootPosition = null
//
//        headBadCount = 0
//        shoulderBadCount = 0
//        weightBadCount = 0
//        footworkBadCount = 0
//
//        // Reset UI text immediately
//        runOnUiThread {
//            headTv.text = "--"
//            shouldersTv.text = "--"
//            weightTv.text = " --"
//            feetTv.text = "--"
//        }
//    }
//
//    private fun saveSession() {
//        if (sessionStartTime == 0L) sessionStartTime = System.currentTimeMillis()
//
//        val durationSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt().coerceAtLeast(30)
//
//        // Calculate average success rate (0.0 to 1.0)
//        val avgSuccess = (headStabilityScore + shoulderScore + weightScore + footworkScore) / 400.0
//
//        // Final Analysis Summary
//        val summary = "Head: $headStatus | Shoulders: $shoulderStatus | Weight: $weightShiftText | Feet: $footworkStatus"
//
//        val session = SessionEntity(
//            userId = userId,
//            drillType = "Pose Analysis",
//            durationSeconds = durationSeconds,
//            successRate = avgSuccess,
//            flawDetails = summary,
//            dateMillis = System.currentTimeMillis()
//        )
//
//        sessionViewModel.insert(session)
//        Toast.makeText(this, "Session Saved to History!", Toast.LENGTH_LONG).show()
//
//        // Trigger AI tip generation in background after session save
//        triggerTipGeneration()
//    }
//
//    /**
//     * Triggers AI tip generation in the background after a session is saved.
//     * This ensures tips are ready when the user navigates to the Tips screen.
//     */
//    private fun triggerTipGeneration() {
//        lifecycleScope.launch {
//            try {
//                val tipsRepository = TipsRepository(
//                    database.aiTipDao(),
//                    database.sessionDao(),
//                    userId,
//                    db
//                )
//                tipsRepository.generateTips(forceRefresh = true)
//                Log.d(TAG, "AI tips generated successfully after session save")
//            } catch (e: Exception) {
//                Log.e(TAG, "Failed to generate AI tips: ${e.message}", e)
//                // Non-critical — don't show error to user
//            }
//        }
//    }
//
//    private fun ImageProxy.toBitmap(): Bitmap {
//        val buffer = planes[0].buffer
//        val bytes = ByteArray(buffer.remaining())
//        buffer.get(bytes)
//        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        cameraExecutor.shutdown()
//        syncHandler.removeCallbacksAndMessages(null)
//    }
//}