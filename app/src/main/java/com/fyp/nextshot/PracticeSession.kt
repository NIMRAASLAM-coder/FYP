// PracticeSession.kt — FINAL WORKING VERSION (Dec 2025)
package com.fyp.nextshot

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
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
    private lateinit var weightTv: TextView

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
        weightTv = findViewById(R.id.tv_weight_balance)

        cameraExecutor = Executors.newFixedThreadPool(2)

        findViewById<View>(R.id.btn_upload_video).setOnClickListener { pickVideo() }

        playPauseBtn.setOnClickListener {
            if (videoView.isPlaying) {
                videoView.pause()
                playPauseBtn.setImageResource(android.R.drawable.ic_media_play)
            } else {
                videoView.start()
                playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        startCamera()
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
        cameraContainer.visibility = View.GONE
        videoContainer.visibility = View.VISIBLE
        videoOverlay.clear()

        videoView.setVideoURI(uri)
        videoView.start()
        playPauseBtn.setImageResource(android.R.drawable.ic_media_pause)

        cameraExecutor.execute {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: return@execute

                var timeUs = 0L
                val stepUs = 1000000L  // 1 FPS only — prevents timeout!

                while (timeUs < duration * 1000) {
                    val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                    processBitmapFrame(bitmap)
                    timeUs += stepUs
                    Thread.sleep(1000) // 1 second delay
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video processing error", e)
            } finally {
                retriever.release()
                runOnUiThread { Toast.makeText(this, "Analysis complete!", Toast.LENGTH_LONG).show() }
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
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

    @androidx.camera.core.ExperimentalGetImage
    private fun processFrame(proxy: ImageProxy) {
        if (isProcessing) {
            proxy.close()
            return
        }
        isProcessing = true

        val bitmap = proxy.toBitmap()
        val rotated = rotateBitmap(bitmap, proxy.imageInfo.rotationDegrees.toFloat())
        processBitmapFrame(rotated)
        proxy.close()
        isProcessing = false
    }

    private fun processBitmapFrame(bitmap: Bitmap) {
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
                isProcessing = false
            }

            override fun onResponse(call: Call, response: Response) {
                isProcessing = false

                // 1. Connection success
                if (response.isSuccessful) {
                    Log.i(TAG, "ROBOFLOW CONNECTED – ${response.code} ${response.message}")
                } else {
                    Log.w(TAG, "ROBOFLOW RETURNED ERROR – ${response.code} ${response.message}")
                }

                val responseBody = response.body?.string() ?: ""

                // 2. Full raw response (very useful!)
                Log.d(TAG, "RAW ROBOFLOW RESPONSE:\n$responseBody")

                if (!response.isSuccessful || responseBody.isEmpty()) {
                    runOnUiThread {
                        Toast.makeText(this@PracticeSession, "Server error ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                    return
                }

                // 3. Parsed predictions (human-readable)
                try {
                    val root = JSONObject(responseBody)
                    val outputs = root.optJSONArray("outputs") ?: JSONArray()

                    var foundPredictions = false
                    for (i in 0 until outputs.length()) {
                        val out = outputs.getJSONObject(i)
                        val preds = out.optJSONArray("predictions") ?: continue
                        if (preds.length() > 0) {
                            foundPredictions = true
                            Log.i(TAG, "PREDICTIONS FOUND (output $i):")
                            for (j in 0 until preds.length()) {
                                val p = preds.getJSONObject(j)
                                val cls = p.optString("class", "unknown")
                                val conf = p.optDouble("confidence", 0.0)
                                val x = p.optDouble("x", 0.0)
                                val y = p.optDouble("y", 0.0)
                                Log.i(TAG, "→ $cls | confidence=%.2f | center=(%.1f,%.1f)".format(conf, x, y))
                            }
                        }
                    }

                    if (!foundPredictions) {
                        Log.w(TAG, "No predictions in any output")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to pretty-print predictions", e)
                }

                // Continue with your existing parsing (unchanged)
                parseRoboflowResponse(responseBody, 640, 480)
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun parseRoboflowResponse(json: String, w: Int, h: Int) {
        try {
            val root = JSONObject(json)
            val outputs = root.getJSONArray("outputs")

            var detection: Detection? = null

            for (i in 0 until outputs.length()) {
                val output = outputs.getJSONObject(i)
                val predsV2 = output.optJSONArray("output_predictions_v2") ?: continue

                for (j in 0 until predsV2.length()) {
                    val item = predsV2.getJSONObject(j)
                    val predictions = item.getJSONObject("predictions")
                    val imageInfo = predictions.getJSONObject("image")
                    val predArray = predictions.getJSONArray("predictions")

                    if (predArray.length() == 0) continue

                    val pred = predArray.getJSONObject(0)

                    val parentOrigin = pred.optJSONObject("parent_origin") ?: JSONObject()
                    val offsetX = parentOrigin.optInt("offset_x", 0)
                    val offsetY = parentOrigin.optInt("offset_y", 0)

                    val x = pred.getDouble("x").toFloat()
                    val y = pred.getDouble("y").toFloat()
                    val width = pred.getDouble("width").toFloat()
                    val height = pred.getDouble("height").toFloat()
                    val confidence = pred.getDouble("confidence").toFloat()

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

                        // Only show keypoints with decent confidence
                        if (kconf > 0.3f) {
                            kpList.add(Keypoint(kx / w, ky / h, kconf))
                        }
                    }

                    detection = Detection(
                        label = "batsman",
                        confidence = confidence,
                        bbox = RectF(left, top, right, bottom),
                        keypoints = kpList
                    )

                    break
                }
                if (detection != null) break
            }

            // Update UI on main thread
            runOnUiThread {
                val overlay = if (videoContainer.visibility == View.VISIBLE) videoOverlay else cameraOverlay
                overlay.setImageSize(w, h)
                overlay.setDetections(if (detection != null) listOf(detection) else emptyList())
                overlay.invalidate()

                // Optional: Show confidence or status
                // headTv.text = "Keypoints: ${detection?.keypoints?.size ?: 0}"
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Roboflow response", e)
            runOnUiThread { getCurrentOverlay().clear() }
        }
    }

    private fun getCurrentOverlay() = if (videoContainer.visibility == View.VISIBLE) videoOverlay else cameraOverlay

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        return if (degrees != 0f) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true) else bitmap
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
    }
}