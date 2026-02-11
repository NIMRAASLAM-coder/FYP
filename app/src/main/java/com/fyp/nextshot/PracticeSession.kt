// PracticeSession.kt — FINAL WORKING VERSION (Dec 2025)
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

    // Mode tracking to prevent interference
    private var isLiveMode = true
    private lateinit var progressDialog: android.app.AlertDialog
    private var mediaPlayer: MediaPlayer? = null

    // Live Mode Optimization
    private val pendingRequests = java.util.concurrent.atomic.AtomicInteger(0)
    private var lastProcessedTimestamp = 0L
    private val MAX_CONCURRENT_REQUESTS = 4

    private lateinit var cameraExecutor: ExecutorService

    // INCREASED TIMEOUTS + CORRECT URL
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // THIS IS THE ONLY CORRECT URL FOR WORKFLOWS IN 2025
    private val INFERENCE_URL = "https://serverless.roboflow.com/hello-7pqr3/workflows/custom-workflow-3"

    // ANALYSIS STATE
    private var prevHeadCenterGlobal: Pair<Float, Float>? = null
    private var headStabilityScore = 100f
    private var weightShiftText = "neutral"
    private var isBalanced = true
    private var isProcessing = false

    // History Buffers for Analysis
    private val headHistory = java.util.ArrayDeque<Pair<Float, Float>>()
    private val HISTORY_SIZE = 10
    private var lastFootPosition: Pair<Float, Float>? = null
    private var footworkStatus = "Planted"
    private var shoulderStatus = "Stable"
    private var headStatus = "Stable"

    // Persistence Counters (Damping)
    private var headUnstableCount = 0
    private var shoulderLeftCount = 0
    private var shoulderRightCount = 0
    private val PERSISTENCE_THRESHOLD = 5 // Frames before reporting change

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



        // Use more threads for network IO
        cameraExecutor = Executors.newFixedThreadPool(8)

        findViewById<View>(R.id.btn_upload_video).setOnClickListener { pickVideo() }
        findViewById<View>(R.id.btn_live_record).setOnClickListener { enterLiveMode() }

        // Hide the button as requested
        playPauseBtn.visibility = View.GONE

        // Tap video to toggle play/pause
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
        resetAnalysisState()
        cameraContainer.visibility = View.GONE
        videoContainer.visibility = View.VISIBLE
        // Stop camera analysis to save resources?
        // simple way: isProcessing = true (but for video)
        // correct way: unbind camera? For now just hide and let it idle (it won't process if we flag it)

        videoOverlay.clear()

        // Stop any previous playback
        videoView.stopPlayback()
        syncHandler.removeCallbacks(syncRunnable)

        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mp ->
            mediaPlayer = mp
            mp.isLooping = false
            // Don't auto-start. Wait for analysis.
        }

        // Start Pre-processing
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

            Log.d(TAG, "Starting pre-processing. Duration: $durationMs ms")

            processedDetections.clear()

            // 66ms ~ 15 FPS
            val interval = 66L
            var currentTime = 0L

            // Collect all tasks first
            val futures = mutableListOf<java.util.concurrent.Future<*>>()

            while (currentTime < durationMs) {
                val timeForFrame = currentTime

                // Retrieve frame (Synchronous part - fast enough usually)
                // We use OPTION_CLOSEST_SYNC for better accuracy if available, or just CLOSEST
                val bitmap = retriever.getFrameAtTime(timeForFrame * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)

                if (bitmap != null) {
                    // Convert to base64 immediately to save memory (don't keep raw bitmaps in queue)
                    val base64 = bitmapToBase64AndRecycle(bitmap)

                    // Submit network task
                    val future = cameraExecutor.submit {
                        val detection = fetchDetectionSyncBase64(base64)
                        if (detection != null) {
                            synchronized(processedDetections) {
                                processedDetections[timeForFrame] = detection
                            }
                        } else {
                            Log.w(TAG, "No detection for frame at $timeForFrame")
                        }
                    }
                    futures.add(future)
                } else {
                    Log.w(TAG, "Could not retrieve frame at $timeForFrame")
                }

                currentTime += interval
            }

            // Wait for all
            for (f in futures) {
                try {
                    f.get()
                } catch (e: Exception) {
                    Log.e(TAG, "Frame processing failed", e)
                }
            }

            val count = processedDetections.size
            Log.d(TAG, "Analysis Complete. Processed frames: $count")

            // Analysis complete
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

        // Slow motion (0.5x) for better analysis
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(0.5f) ?: android.media.PlaybackParams().setSpeed(0.5f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not set playback speed", e)
        }

        isVideoPlaying = true
        sessionStartTime = System.currentTimeMillis()

        // Critical Fix: Overlay needs to know the source size to draw anything!
        videoOverlay.setImageSize(640, 480)

        syncHandler.post(syncRunnable)
    }

    private val syncRunnable = object : Runnable {
        override fun run() {
            if (!isVideoPlaying && !videoView.isPlaying) return

            try {
                val currentPos = videoView.currentPosition.toLong()

                // Find closest detection within 100ms window
                val entry = processedDetections.floorEntry(currentPos)
                val ceilEntry = processedDetections.ceilingEntry(currentPos)

                // Simple logic: picking the closest one
                val bestDetection = when {
                    entry == null && ceilEntry == null -> null
                    entry == null -> ceilEntry!!.value
                    ceilEntry == null -> entry.value
                    (currentPos - entry.key) < (ceilEntry.key - currentPos) -> entry.value
                    else -> ceilEntry.value
                }

                val overlay = videoOverlay
                if (bestDetection != null) {
                    overlay.setDetections(listOf(bestDetection))
                } else {
                    // Optional: clear if no detection nearby?
                    // Let's keep last one if it's not too old, or just let it stay until replaced.
                }

                // Update Analysis UI for Video
                if (bestDetection != null) {
                    analyzePose(bestDetection)
                    headTv.text = "Head: $headStatus"
                    shouldersTv.text = "Shoulders: $shoulderStatus"
                    weightTv.text = "Weight: $weightShiftText"
                    feetTv.text = "Feet: $footworkStatus"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Sync error", e)
            }

            syncHandler.postDelayed(this, 33) // ~30 FPS UI update
        }
    }


    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Force 4:3 ensures closest match to 640x480 analysis for alignment
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
        // Stop video
        if (videoView.isPlaying) videoView.stopPlayback()
        if (videoView.isPlaying) videoView.stopPlayback()
        isVideoPlaying = false
        isLiveMode = true
        resetAnalysisState()
        syncHandler.removeCallbacks(syncRunnable)
        videoContainer.visibility = View.GONE

        // Show camera
        cameraContainer.visibility = View.VISIBLE

        Toast.makeText(this, "Live Mode Active", Toast.LENGTH_SHORT).show()
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun processFrame(proxy: ImageProxy) {
        // Stop analysis if not in Live Mode
        if (!isLiveMode) {
            proxy.close()
            return
        }

        // Drop frame if too many requests in flight
        if (pendingRequests.get() >= MAX_CONCURRENT_REQUESTS) {
            proxy.close()
            return
        }

        // Track ordering
        val timestamp = proxy.imageInfo.timestamp

        val bitmap = proxy.toBitmap()
        val rotated = rotateBitmap(bitmap, proxy.imageInfo.rotationDegrees.toFloat())

        // Asynchronously process
        processBitmapFrame(rotated, timestamp)
        proxy.close()
    }

    private fun processBitmapFrame(bitmap: Bitmap, timestamp: Long = 0L) {
        pendingRequests.incrementAndGet()

        val resized = Bitmap.createScaledBitmap(bitmap, 640, 480, true)
        val base64 = bitmapToBase64(resized)
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

        Log.d(TAG, "Sending frame to Roboflow… (${base64.length} chars)")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "ROBOFLOW CONNECTION FAILED: ${e.message}")
                pendingRequests.decrementAndGet()
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""

                if (false) { // Disable verbose logs for live mode optimization
                    try {
                        // ... log parsing ...
                    } catch (e: Exception) {}
                }

                // Only update if this frame is newer than what's currently shown
                // (For video sync, timestamp is 0, so we ignore logic)
                if (timestamp > 0) {
                    if (sessionStartTime == 0L) sessionStartTime = System.currentTimeMillis()
                    if (timestamp > lastProcessedTimestamp) {
                        lastProcessedTimestamp = timestamp
                        parseRoboflowResponse(responseBody, 640, 480)
                    } else {
                        // Drop out-of-order frame
                        Log.d(TAG, "Dropping old frame")
                    }
                } else {
                    // Legacy/Video path (if used)
                    parseRoboflowResponse(responseBody, 640, 480)
                }

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

    @SuppressLint("SetTextI18n")
    private fun parseRoboflowResponse(json: String, w: Int, h: Int) {
        val detection = parseDetectionFromJson(json, w, h)

        // THIS IS THE ONLY UI UPDATE — SMOOTH & CORRECT
        if (detection != null && detection.keypoints.isNotEmpty()) {
            lastDetection = detection
            lastUpdateTime = System.currentTimeMillis()
        }

        runOnUiThread {
            val overlay = getCurrentOverlay()
            overlay.setImageSize(w, h)

            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastUpdateTime

            if (lastDetection != null && timeSinceLast < 1000) { // Keep pose for 800ms
                overlay.setDetections(listOf(lastDetection!!))
            } else {
                overlay.clear()
            }

            // Update Analysis UI
            if (lastDetection != null) {
                analyzePose(lastDetection!!)
                headTv.text = "Head: $headStatus"
                shouldersTv.text = "Shoulders: $shoulderStatus"
                weightTv.text = "Weight: $weightShiftText"
                feetTv.text = "Feet: $footworkStatus"
            }

            overlay.invalidate()
        }
    }

    private fun getCurrentOverlay() = if (videoContainer.visibility == View.VISIBLE) videoOverlay else cameraOverlay

    // --- HELPER FOR VIDEO SYNC (BLOCKING) ---
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
                return parseDetectionFromJson(body, 640, 480)
            } else {
                Log.w(TAG, "API Error: ${response.code} - $body")
            }
            response.close()
        } catch (e: Exception) {
            Log.e(TAG, "Sync Fetch Error", e)
        }
        return null
    }

    // Restored this function for live camera usage
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    // Combined helper to resize/compress/recycle quickly
    private fun bitmapToBase64AndRecycle(bitmap: Bitmap): String {
        val resized = Bitmap.createScaledBitmap(bitmap, 640, 480, true)
        if (resized != bitmap) bitmap.recycle() // Recycle original if scaled

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        resized.recycle() // Recycle resized
        return b64
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        return if (degrees != 0f) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true) else bitmap
    }

    private fun analyzePose(detection: Detection) {
        val kp = detection.keypoints
        val w = 640f // Normalized to reference width
        val h = 480f

        // Helper to get raw coords (0-1 range)
        fun getPt(idx: Int): Pair<Float, Float>? {
            // Find keypoint with index logic if relying on list order is unsafe
            // But usually list is ordered if full skeleton.
            // Better: find by index if your Keypoint class doesn't store index.
            // Wait, Keypoint class in BoundingBoxOverlay.kt doesn't have index or part name.
            // We must rely on list order from skeleton logic:
            // 0:Nose, 1:LEye, 2:REye, 3:LEar, 4:REar, 5:LShoulder, 6:RShoulder
            // 11:LHip, 12:RHip, 15:LAnkle, 16:RAnkle
            if (idx >= kp.size) return null
            val k = kp[idx]
            if (k.confidence < 0.3f) return null
            return k.x to k.y
        }

        // 1. Head Stability (Nose: 0)
        val nose = getPt(0)
        if (nose != null) {
            headHistory.addLast(nose)
            if (headHistory.size > HISTORY_SIZE) headHistory.removeFirst()

            if (headHistory.size > 2) {
                // Calculate variance
                val avgX = headHistory.map { it.first }.average()
                val avgY = headHistory.map { it.second }.average()
                val distSq = headHistory.sumOf { (it.first - avgX).pow(2) + (it.second - avgY).pow(2) }
                val variance = distSq / headHistory.size

                // Heuristic for instability
                val isInstabilityDetected = variance > 0.0005f // Rough threshold for normalized units

                if (isInstabilityDetected) {
                    headUnstableCount++
                } else {
                    headUnstableCount = 0
                }

                headStatus = if (headUnstableCount >= PERSISTENCE_THRESHOLD) "Not Stable" else "Stable"
            }
        }

        // 2. Shoulder Stability (5: Left, 6: Right)
        val ls = getPt(5)
        val rs = getPt(6)
        if (ls != null && rs != null) {
            val dy = (rs.second - ls.second)
            val dx = (rs.first - ls.first)
            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

            // Assuming standard pose, shoulders should be roughly level (0 degrees)
            // If angle > 10, tilted one way. If < -10, tilted the other.
            when {
                angle > 12 -> { // Right shoulder lower?
                    shoulderRightCount++
                    shoulderLeftCount = 0
                }
                angle < -12 -> { // Left shoulder lower?
                    shoulderLeftCount++
                    shoulderRightCount = 0
                }
                else -> {
                    shoulderLeftCount = 0
                    shoulderRightCount = 0
                }
            }

            shoulderStatus = when {
                shoulderLeftCount >= PERSISTENCE_THRESHOLD -> "Left"
                shoulderRightCount >= PERSISTENCE_THRESHOLD -> "Right"
                else -> "Stable"
            }
        }

        // 3. Weight Distribution (Hips vs Ankles X)
        // 11: LHip, 12: RHip, 15: LAnkle, 16: RAnkle
        val lHip = getPt(11)
        val rHip = getPt(12)
        val lAnk = getPt(15)
        val rAnk = getPt(16)

        if (lHip != null && rHip != null && lAnk != null && rAnk != null) {
            val hipCenter = (lHip.first + rHip.first) / 2
            val ankCenter = (lAnk.first + rAnk.first) / 2

            val diff = hipCenter - ankCenter
            weightShiftText = when {
                diff < -0.05f -> "Back"
                diff > 0.05f -> "Front"
                else -> "Centered"
            }
        }

        // 4. Footwork (Ankle movement)
        if (lAnk != null && rAnk != null) {
            val currentFeet = (lAnk.first + rAnk.first) / 2 to (lAnk.second + rAnk.second) / 2

            if (lastFootPosition != null) {
                val dist = sqrt((currentFeet.first - lastFootPosition!!.first).pow(2) + (currentFeet.second - lastFootPosition!!.second).pow(2))
                footworkStatus = if (dist > 0.015f) "Adjusting" else "Planted"
            }
            lastFootPosition = currentFeet
        }
    }

    private fun resetAnalysisState() {
        headHistory.clear()
        headStabilityScore = 100f
        weightShiftText = "neutral"
        shoulderStatus = "Stable"
        headStatus = "Stable"
        footworkStatus = "Planted"
        lastFootPosition = null

        headUnstableCount = 0
        shoulderLeftCount = 0
        shoulderRightCount = 0

        // Reset UI text immediately
        runOnUiThread {
            headTv.text = "Head: --"
            shouldersTv.text = "Shoulders: --"
            weightTv.text = "Weight: --"
            feetTv.text = "Feet: --"
        }
    }

    private fun saveSession() {
        if (sessionStartTime == 0L) sessionStartTime = System.currentTimeMillis()

        val durationSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt().coerceAtLeast(30)

        // Final Analysis Summary
        val summary = "Head: $headStatus | Shoulders: $shoulderStatus | Weight: $weightShiftText | Feet: $footworkStatus"

        val session = SessionEntity(
            userId = userId,
            drillType = "Pose Analysis",
            durationSeconds = durationSeconds,
            successRate = 1.0, // Default for now
            flawDetails = summary,
            dateMillis = System.currentTimeMillis()
        )

        sessionViewModel.insert(session)
        Toast.makeText(this, "Session Saved to History!", Toast.LENGTH_LONG).show()
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        syncHandler.removeCallbacksAndMessages(null)
    }
}