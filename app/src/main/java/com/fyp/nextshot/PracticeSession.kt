package com.fyp.nextshot

import android.util.Size
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PracticeSession : AppCompatActivity() {

    private val TAG = "PracticeSession"
    private lateinit var workflowOutputImageView: android.widget.ImageView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var bboxOverlay: BoundingBoxOverlay
    private lateinit var previewView: PreviewView
    private lateinit var startStopButton: MaterialButton
    private lateinit var instructionCard: androidx.cardview.widget.CardView

    private val REQUIRED_PERMISSIONS = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO
    )

    // Activity Result Launcher for permission request
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) {
                    permissionGranted = false
                }
            }
            if (!permissionGranted) {
                Toast.makeText(
                    this,
                    "Camera and Audio permissions required to run session.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice_session)

        previewView = PreviewView(this)
        findViewById<android.widget.FrameLayout>(R.id.camera_container).addView(previewView, 0) // Add PreviewView at index 0 (bottom layer)

        startStopButton = findViewById(R.id.start_stop_button)
        instructionCard = findViewById(R.id.instruction_card)
        workflowOutputImageView = findViewById(R.id.workflow_output_image_view)
        bboxOverlay = findViewById(R.id.bbox_overlay)

        cameraExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor = Executors.newCachedThreadPool()

        instructionCard.visibility = View.VISIBLE
        previewView.visibility = View.GONE
        // Ensure the output image view is initially hidden
        workflowOutputImageView.visibility = View.GONE

        startStopButton.setOnClickListener {
            if (startStopButton.text.toString() == "Start Session") {
                if (allPermissionsGranted()) {
                    startCamera()
                } else {
                    activityResultLauncher.launch(REQUIRED_PERMISSIONS)
                }
                startStopButton.text = "Stop Recording"
            } else {
                Toast.makeText(this, "Stopping session...", Toast.LENGTH_SHORT).show()
                stopCamera()
                startStopButton.text = "Start Session"
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        instructionCard.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        workflowOutputImageView.visibility = View.GONE // Ensure image view is hidden when starting

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Image Analysis Use Case
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(320, 320))           // ← MATCH YOUR WORKFLOW
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processCameraFrame(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera preview.", Toast.LENGTH_SHORT).show()
                instructionCard.visibility = View.VISIBLE
                previewView.visibility = View.GONE
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            instructionCard.visibility = View.VISIBLE
            previewView.visibility = View.GONE
            workflowOutputImageView.visibility = View.GONE // Hide output on stop
            bboxOverlay.clear() // Clear any lingering boxes
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        backgroundExecutor.shutdown()
    }

    // ----------------------------
    // Image Conversion Helpers
    // ----------------------------
    // ----------------------------
// Image Conversion Helpers
// ----------------------------
    private fun imageToBase64(imageProxy: ImageProxy): String {
        // 1. Get the original Bitmap from the ImageProxy (assuming RGBA_8888 format)
        val originalBitmap = imageProxy.toBitmap()!!

        // 2. Get the rotation degrees required for the image to be upright
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val rotatedBitmap: Bitmap

        if (rotationDegrees != 0) {
            // 3. Create a Matrix object and apply the rotation
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())

            // 4. Create a new rotated Bitmap
            rotatedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
            // Recycle the original bitmap to free memory
            originalBitmap.recycle()
        } else {
            rotatedBitmap = originalBitmap
        }

        // 5. Compress the corrected Bitmap to a Base64 string
        val stream = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)

        // 6. Recycle the rotated bitmap if it's not the original
        if (rotationDegrees != 0) {
            rotatedBitmap.recycle()
        }

        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    // ----------------------------
    // Frame sending throttling & Processing
    // ----------------------------
    private var isProcessing = false

    private fun processCameraFrame(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }
        isProcessing = true

        val inputWidth = imageProxy.width
        val inputHeight = imageProxy.height

        Log.d("FRAME_SIZE", "Sending frame: ${imageProxy.width}x${imageProxy.height}")

        backgroundExecutor.execute {
            val WORKSPACE_NAME = "hello-7pqr3"
            val WORKFLOW_ID = "custom-workflow-4"
            val API_KEY = "7VCjsMFfykWO22m0bCXb"

            val apiUrl = "https://serverless.roboflow.com/$WORKSPACE_NAME/workflows/$WORKFLOW_ID"

            try {
                val base64Image = imageToBase64(imageProxy)
                imageProxy.close()

                val json = """
            {
                "api_key": "$API_KEY",
                "inputs": {
                    "image": {
                        "type": "base64",
                        "value": "$base64Image"
                    }
                }
            }
            """.trimIndent()

                val requestBody = json.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url(apiUrl)
                    .post(requestBody)
                    .build()

                val client = OkHttpClient.Builder()
                    .connectTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()

                        Log.d("ROBOFLOW_RESPONSE", responseBody ?: "null")
                        if (!response.isSuccessful || responseBody?.contains("\"message\":") == true) {
                            Log.e(TAG, "API Error: ${response.code}-$responseBody")
                            runOnUiThread {
                                Toast.makeText(
                                    this@PracticeSession,
                                    "API Error: ${response.code}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            isProcessing = false
                            runOnUiThread { bboxOverlay.clear() }
                            return
                        }

                        // --- START FIX ---
                        // Ensure the image view is hidden at the start of response processing
                        runOnUiThread {
                            workflowOutputImageView.visibility = View.GONE
                        }
                        // --- END FIX ---


                        if (responseBody != null) {
                            try {
                                val jsonObject = org.json.JSONObject(responseBody)

                                if (!jsonObject.has("outputs")) {
                                    Log.e(TAG, "Roboflow response is missing the 'outputs' array.")
                                    isProcessing = false
                                    runOnUiThread { bboxOverlay.clear() }
                                    return
                                }
                                val jsonArray = jsonObject.getJSONArray("outputs")

                                if (jsonArray.length() == 0) {
                                    Log.e(TAG, "Workflow returned no outputs.")
                                    isProcessing = false
                                    runOnUiThread { bboxOverlay.clear() }
                                    return
                                }

                                val mainObject = jsonArray.getJSONObject(0)

                                if (mainObject.has("output_predictions_v4")) {
                                    val predictionsObj = mainObject.getJSONObject("output_predictions_v4")
                                    Log.d("RAW_PREDICTIONS", predictionsObj.toString(4))

                                    val tracked = predictionsObj.optJSONObject("tracked_detections")
                                        ?: predictionsObj.optJSONObject("predictions") ?: predictionsObj

                                    val detections = mutableListOf<Detection>()

                                    if (tracked.has("predictions")) {
                                        val preds = tracked.getJSONArray("predictions")
                                        val modelWidth = 320f
                                        val modelHeight = 320f

                                        for (i in 0 until preds.length()) {
                                            val pred = preds.getJSONObject(i)
                                            val x = pred.optDouble("x", 0.0).toFloat()
                                            val y = pred.optDouble("y", 0.0).toFloat()
                                            val w = pred.optDouble("width", 0.0).toFloat()
                                            val h = pred.optDouble("height", 0.0).toFloat()
                                            val label = pred.optString("class", "object")
                                            val conf = pred.optDouble("confidence", 1.0).toFloat()

                                            val left = (x - w / 2) / modelWidth
                                            val top = (y - h / 2) / modelHeight
                                            val right = (x + w / 2) / modelWidth
                                            val bottom = (y + h / 2) / modelHeight

                                            // Optional: Filter low confidence detections here (e.g., if conf > 0.5f)

                                            detections.add(Detection(label, conf, RectF(left, top, right, bottom)))
                                        }

                                        runOnUiThread {
                                            bboxOverlay.updateDetections(detections, inputWidth, inputHeight)
                                            // We ensure the visualization image is GONE so the BBOX overlay is visible.
                                            workflowOutputImageView.visibility = View.GONE
                                        }
                                    } else {
                                        runOnUiThread {
                                            bboxOverlay.clear()
                                        }
                                    }
                                } else {
                                    // Clear boxes if no predictions found in expected field
                                    runOnUiThread { bboxOverlay.clear() }
                                }

                                // We intentionally DO NOT display output_visualization_2
                                // if we want the real-time overlay visible.
                                // Keeping this block only for reference or if you decided to switch visualizations later:
                                if (mainObject.has("output_visualization_2")) {
                                    val visualizationObject = mainObject.getJSONObject("output_visualization_2")
                                    val base64ImageValue = visualizationObject.getString("value")
                                    val annotatedBitmap = base64ToBitmap(base64ImageValue)

                                    if (annotatedBitmap != null) {
                                        // Set the image, but KEEP it hidden if you want the BBOX Overlay to be the primary output
                                        runOnUiThread {
                                            workflowOutputImageView.setImageBitmap(annotatedBitmap)
                                            workflowOutputImageView.visibility = View.GONE
                                        }
                                    }
                                }

                            } catch (e: Exception) {
                                Log.e(TAG, "JSON Parsing or Bitmap decode failed: ${e.message}")
                                isProcessing = false
                                runOnUiThread { bboxOverlay.clear() }
                            }
                        }
                        isProcessing = false  // Always reset here
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("ROBOFLOW_ERROR", "Failed: ${e.message}")
                        isProcessing = false
                        runOnUiThread { bboxOverlay.clear() }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Image conversion failed on background thread: ${e.message}")
                isProcessing = false
                imageProxy.close()
            }
        }
    }

    // ----------------------------
    // Base64 to Bitmap
    // ----------------------------
    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val cleanBase64 = base64Str.trim()
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Base64 to Bitmap failed: ${e.message}", e)
            null
        }
    }
}