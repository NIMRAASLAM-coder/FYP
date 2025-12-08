//package com.fyp.nextshot
//
//import android.Manifest
//import android.content.ContentValues
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.graphics.RectF
//import android.net.Uri
//import android.os.Bundle
//import android.graphics.BitmapFactory
//import android.os.Handler
//import android.os.Looper
//import android.provider.MediaStore
//import android.util.Base64
//import android.util.Log
//import android.view.View
//import android.graphics.Matrix
//import android.widget.Button
//import android.widget.EditText
//import android.widget.TextView
//import android.widget.Toast
//import androidx.activity.result.ActivityResult
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.appcompat.app.AppCompatActivity
//import androidx.camera.core.CameraSelector
//import androidx.camera.core.ImageAnalysis
//import androidx.camera.core.ImageProxy
//import androidx.camera.core.Preview
//import androidx.camera.lifecycle.ProcessCameraProvider
//import androidx.camera.video.MediaStoreOutputOptions
//import androidx.camera.video.Quality
//import androidx.camera.video.QualitySelector
//import androidx.camera.video.Recorder
//import androidx.camera.video.Recording
//import androidx.camera.video.VideoCapture
//import androidx.camera.video.VideoRecordEvent
//import androidx.camera.view.PreviewView
//import androidx.core.content.ContextCompat
//import androidx.core.content.PermissionChecker
//import androidx.lifecycle.lifecycleScope
//import com.fyp.nextshot.data.local.database.AppDatabase
//import com.fyp.nextshot.data.local.models.SessionEntity
//import com.fyp.nextshot.data.repository.SessionRepository
//import com.google.android.material.button.MaterialButton
//import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.firestore.FirebaseFirestore
//import com.google.firebase.storage.FirebaseStorage
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import okhttp3.Call
//import okhttp3.Callback
//import okhttp3.MediaType.Companion.toMediaType
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.RequestBody.Companion.toRequestBody
//import okhttp3.Response
//import java.io.ByteArrayOutputStream
//import java.io.IOException
//import java.io.File
//import java.io.FileOutputStream // For file writing
//import java.text.SimpleDateFormat
//import java.util.Locale
//import java.util.concurrent.ExecutorService
//import java.util.concurrent.Executors
//import kotlin.random.Random
//
//// FIXED: Extension FUNCTION (top-level, after imports)
//private fun ImageProxy.toBitmap(): Bitmap? {
//    val buffer = planes[0].buffer
//    val bytes = ByteArray(buffer.remaining())
//    buffer.get(bytes)
//    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//}
//
//class PracticeSession : AppCompatActivity() {
//
//
//    private val tag = "PracticeSession"
//    // --- Data/Architecture Initialization ---
//    private lateinit var etCloudIdInput: EditText
//    private lateinit var btnTriggerUpdate: Button
//    private val auth by lazy { FirebaseAuth.getInstance() }
//    private val db by lazy { FirebaseFirestore.getInstance() }
//    private val storage by lazy { FirebaseStorage.getInstance() }
//    private val userId = auth.currentUser?.uid ?: "FALLBACK_UID"
//    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
//    private val repository by lazy { SessionRepository(database.sessionDao(), userId, db) }
//
//    // --- UI and Camera Components ---
//    private lateinit var workflowOutputImageView: android.widget.ImageView
//    private lateinit var cameraExecutor: ExecutorService
//    private lateinit var backgroundExecutor: ExecutorService
//    private lateinit var bboxOverlay: BoundingBoxOverlay
//    private lateinit var previewView: PreviewView
//    private lateinit var startStopButton: MaterialButton
//    private lateinit var uploadVideoButton: MaterialButton
//    private lateinit var instructionCard: androidx.cardview.widget.CardView
//
//    // --- State Management ---
//    private var isProcessing = false
//    private var isSessionActive = false
//    private var sessionStartTime: Long = 0
//
//    // --- CameraX Video ---
//    private var videoCapture: VideoCapture<Recorder>? = null
//    private var recording: Recording? = null
//
//    private var imageAnalyzer: ImageAnalysis? = null  // Track to pause/resume
//    // --- Timer Variables (FIXED) ---
//    private lateinit var recordingTimer: TextView
//    private val timerHandler = Handler(Looper.getMainLooper())
//    private val updateTimerRunnable = object : Runnable {
//        override fun run() {
//            val millis = System.currentTimeMillis() - sessionStartTime
//            val seconds = (millis / 1000) % 60
//            val minutes = (millis / (1000 * 60)) % 60
//
//            val time = String.format(Locale.getDefault(), "REC %02d:%02d", minutes, seconds)
//            recordingTimer.text = time
//
//            timerHandler.postDelayed(this, 1000)
//        }
//    }
//
//    private val requiredPermissions = mutableListOf(
//        Manifest.permission.CAMERA,
//        Manifest.permission.RECORD_AUDIO
//    ).apply {
//        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
//            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//        }
//    }.toTypedArray()
//
//    private val activityResultLauncher =
//        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
//            var permissionGranted = true
//            permissions.entries.forEach {
//                if (it.key in requiredPermissions && !it.value) {
//                    permissionGranted = false
//                }
//            }
//            if (!permissionGranted) {
//                Toast.makeText(
//                    this,
//                    "Camera and Audio permissions required.",
//                    Toast.LENGTH_LONG
//                ).show()
//            } else {
//                startLiveAnalysis()
//            }
//        }
//
//    // New: Manual launcher to handle the result
//    private val videoPickerLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result: ActivityResult ->
//        if (result.resultCode == RESULT_OK) {
//            val uri: Uri? = result.data?.data
//            uri?.let {
//                // IMPORTANT: Since the grant is only temporary, we move straight to uploading
//                // which relies on the upload function COPYING the file immediately.
//                uploadVideoToFirebase(it)
//            }
//        }
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_practice_session)
//
//        previewView = PreviewView(this)
//        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
//        findViewById<android.widget.FrameLayout>(R.id.camera_container).addView(previewView, 0)
//
//        startStopButton = findViewById(R.id.start_stop_button)
//        instructionCard = findViewById(R.id.instruction_card)
//        workflowOutputImageView = findViewById(R.id.workflow_output_image_view)
//        bboxOverlay = findViewById(R.id.bbox_overlay)
//        uploadVideoButton = findViewById(R.id.upload_video_button)
//
//        // Assuming your XML ID is 'recording_timer'
//        recordingTimer = findViewById(R.id.recording_timer)
//
//        recordingTimer.bringToFront()
//        // --- Demo/Update Tool Initialization ---
//        etCloudIdInput = findViewById(R.id.et_cloud_id_input)
//        btnTriggerUpdate = findViewById(R.id.btn_trigger_update)
//        // -------------------------------------
//
//        cameraExecutor = Executors.newSingleThreadExecutor()
//        backgroundExecutor = Executors.newCachedThreadPool()
//
//        instructionCard.visibility = View.VISIBLE
//        previewView.visibility = View.GONE
//        workflowOutputImageView.visibility = View.GONE
//        recordingTimer.visibility = View.GONE
//
//        // Ensure overlays are brought to front once views are initialized
//        recordingTimer.bringToFront()
//        bboxOverlay.bringToFront()
//
//        setupListeners()
//    }
//
//    private fun setupListeners() {
//        startStopButton.setOnClickListener {
//            if (!isSessionActive) {
//                if (allPermissionsGranted()) {
//                    startLiveAnalysis()
//                    startRecording()
//                    isSessionActive = true
//                    startStopButton.text = "Stop Session (Recording)"
//                } else {
//                    activityResultLauncher.launch(requiredPermissions)
//                }
//            } else {
//                stopRecording() // This triggers upload in finalize event
//                stopCamera()
//                isSessionActive = false
//                startStopButton.text = "Start Session"
//            }
//        }
//
//        uploadVideoButton.setOnClickListener {
//            if (allPermissionsGranted()) {
//                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
//                    addCategory(Intent.CATEGORY_OPENABLE)
//                    type = "video/*"
//                    // Request permission to grant persistent URI access (needed for read access)
//                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
//                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
//                }
//                videoPickerLauncher.launch(intent)
//            } else {
//                activityResultLauncher.launch(requiredPermissions)
//            }
//        }
//
//        // --- Demo Trigger Listener ---
//        btnTriggerUpdate.setOnClickListener {
//            val cloudId = etCloudIdInput.text.toString().trim()
//            if (cloudId.isNotEmpty()) {
//                demoAnalysisUpdate(cloudId)
//            } else {
//                Toast.makeText(this, "Please paste a Cloud Document ID.", Toast.LENGTH_SHORT).show()
//            }
//        }
//        // -----------------------------
//    }
//
//    // ----------------------------
//    // URI Copy Helper (DEFINITIVE FIX)
//    // ----------------------------
//    private suspend fun copyUriToCache(context: android.content.Context, uri: Uri, fileName: String): File? = withContext(Dispatchers.IO) {
//        try {
//            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
//            val cacheFile = File(context.cacheDir, fileName)
//            val outputStream = FileOutputStream(cacheFile)
//
//            inputStream.use { input ->
//                outputStream.use { output ->
//                    input.copyTo(output)
//                }
//            }
//            return@withContext cacheFile
//        } catch (e: Exception) {
//            Log.e(tag, "Failed to copy URI to cache: ${e.message}", e)
//            return@withContext null
//        }
//    }
//
//    private fun allPermissionsGranted() = requiredPermissions.all {
//        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
//    }
//
//    private fun startTimer() {
//        recordingTimer.visibility = View.VISIBLE
//        recordingTimer.bringToFront()  // ADDED: Ensure on top of preview
//        sessionStartTime = System.currentTimeMillis()
//        Log.d(tag, "Timer UI visible, startTime: $sessionStartTime")
//        timerHandler.post(updateTimerRunnable)
//    }
//
//    private fun stopTimer() {
//        recordingTimer.visibility = View.GONE
//        timerHandler.removeCallbacks(updateTimerRunnable)
//    }
//
//    private fun startLiveAnalysis() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//        instructionCard.visibility = View.GONE
//        previewView.visibility = View.VISIBLE
//        workflowOutputImageView.visibility = View.GONE
//
//        cameraProviderFuture.addListener({
//            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//            val preview = Preview.Builder().build().also {
//                it.setSurfaceProvider(previewView.surfaceProvider)
//            }
//            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//
//            val imageAnalyzer = ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .build()
//                .also {
//                    it.setAnalyzer(cameraExecutor) { imageProxy ->
//                        processCameraFrame(imageProxy)
//                    }
//                }
//            this.imageAnalyzer = imageAnalyzer
//
//            val recorder = Recorder.Builder()
//                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))  // SD for stability
//                .build()
//            videoCapture = VideoCapture.withOutput(recorder)
//
//            try {
//                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(
//                    this, cameraSelector, preview, imageAnalyzer, videoCapture
//                )
//                Log.d(tag, "Camera bound successfully")
//                runOnUiThread {
//                    recordingTimer.bringToFront()
//                    bboxOverlay.bringToFront()
//                    workflowOutputImageView.bringToFront()
//                    instructionCard.bringToFront()
//                }
//            } catch (exc: Exception) {
//                Log.e(tag, "Bind failed: ${exc.message}", exc)
//                val errorMsg = when {
//                    exc.message?.contains("No available camera") == true -> "No camera available—check hardware."  // FIXED: == true forces Boolean
//                    exc.message?.contains("Session") == true -> "Camera session conflict—clear cache."  // FIXED: == true
//                    else -> "Bind error: ${exc.message ?: "Unknown"}"
//                }
//                runOnUiThread {
//                    Toast.makeText(this, "Failed to start camera: $errorMsg", Toast.LENGTH_LONG).show()
//                    instructionCard.visibility = View.VISIBLE
//                    previewView.visibility = View.GONE
//                }
//                // FIXED: Non-recursive retry (call helper function)
//                retryStartLiveAnalysis()
//            }
//        }, ContextCompat.getMainExecutor(this))
//    }
//
//    // NEW: Helper for retry (avoids ambiguity)
//    private fun retryStartLiveAnalysis() {
//        Handler(Looper.getMainLooper()).postDelayed({ startLiveAnalysis() }, 2000)
//    }
//
//
//
//
//    // In PracticeSession.kt, UPDATE startRecording()
//
//    private fun startRecording() {
//        val videoCapture = this.videoCapture ?: return
//
//        startTimer()  // MOVED: Call here, before delay
//        Log.d(tag, "Timer started; pausing analysis for recording")
//
//        imageAnalyzer?.clearAnalyzer()  // Stop processing frames during record
//        Handler(Looper.getMainLooper()).postDelayed({
//
//            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
//                .format(System.currentTimeMillis())
//            val contentValues = ContentValues().apply {
//                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
//                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
//                if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
//                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/NextShot")
//                }
//            }
//
//            val mediaStoreOutputOptions = MediaStoreOutputOptions
//                .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
//                .setContentValues(contentValues)
//                .build()
//
//            recording = videoCapture.output
//                .prepareRecording(this, mediaStoreOutputOptions)
//                .apply {
//                    Log.d(tag, "Recording prepared: Video-only (no audio)")
//                }
//                .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
//                    when (recordEvent) {
//                        is VideoRecordEvent.Start -> {
//                            Toast.makeText(baseContext, "Recording Started", Toast.LENGTH_SHORT).show()
////                            startTimer()
//                            // Ensure button reflects recording status instantly
//                            startStopButton.text = "Stop Recording"
//                            Log.d(tag, "Recording event: Started")
//                        }
//                        is VideoRecordEvent.Finalize -> {
//                            stopTimer()
//                            val exactDurationMillis = recordEvent.recordingStats.recordedDurationNanos / 1_000_000L
//                            Log.d(tag, "Recording event: Finalize, Duration: $exactDurationMillis ms")
//
//
//                            // Copying the existing duration calculation for the Toast:
////                            val durationMillis = recordEvent.recordingStats.recordedDurationNanos / 1_000_000L
//                            val totalSeconds = (exactDurationMillis / 1000).toInt()
//                            val minutes = totalSeconds / 60
//                            val seconds = totalSeconds % 60
//                            val timeString = if (minutes > 0) { "$minutes minutes and $seconds seconds" } else { "$seconds seconds" }
//                            if (!recordEvent.hasError()) {
//                                Toast.makeText(baseContext, "Session complete. You practiced for $timeString. Keypoints ready for analysis!", Toast.LENGTH_LONG).show()
//                                uploadVideoToFirebase(recordEvent.outputResults.outputUri, totalSeconds)
//                            }else {
//                                val errorCode = recordEvent.error
//                                val errorMsg = when (errorCode) {
//                                    8 -> "Camera lost (Code 8)—try shorter sessions or check lighting"
//                                    else -> "Unknown error (Code $errorCode)"
//                                }
//                                Log.e(tag, "Video capture failed: $errorMsg. Full event: $recordEvent")
//                                Toast.makeText(baseContext, "Recording failed! $errorMsg. Check logs.", Toast.LENGTH_LONG).show()
//                                recording?.close()
//                                recording = null
//                                // NEW: Auto-rebind camera on Code 8 (resumes preview/keypoints)
//                                if (errorCode == 8) {
//                                    runOnUiThread {
//                                        Toast.makeText(baseContext, "Camera recovered—ready for next session.", Toast.LENGTH_SHORT).show()
//                                        startLiveAnalysis()  // Rebind to restart preview/analysis
//                                    }
//                                }
//                            }
//                        }
//                        else -> {}
//                    }
//                }
//        }, 1000) // Delay the actual start of recording by 500ms
//    }
//
//// In setupListeners(), ensure START button only calls startLiveAnalysis()
//// and then startRecording() is called inside that function or delayed,
//// but since your current code calls both, the 500ms delay above will stabilize it.
//
//    // Update stopRecording(): Re-enable analysis after stop
//    private fun stopRecording() {
//        val recording = this.recording
//        if (recording != null) {
//            Log.d(tag, "Initiating stop with buffer delay...")
//
//            // NEW: Resume analysis after a brief delay (let finalize complete)
//            Handler(Looper.getMainLooper()).postDelayed({
//                recording.stop()
//                this.recording = null
//                Log.d(tag, "stop() called—waiting for finalize")
//            }, 2000)  // 2s buffer: Lets frames flush before finalize
//        } else {
//            Toast.makeText(this, "No active recording to stop", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun stopCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//        cameraProviderFuture.addListener({
//            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//            cameraProvider.unbindAll()
//            instructionCard.visibility = View.VISIBLE
//            previewView.visibility = View.GONE
//            workflowOutputImageView.visibility = View.GONE
//            bboxOverlay.clear()
//        }, ContextCompat.getMainExecutor(this))
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        cameraExecutor.shutdown()
//        backgroundExecutor.shutdown()
//        timerHandler.removeCallbacks(updateTimerRunnable)
//    }
//
//    // Example function to call in PracticeSession (to demo UPDATE capability)
//    fun demoAnalysisUpdate(cloudIdToUpdate: String) {
//        // Simulate model output 10 minutes later
//        val improvedAccuracy = 0.95
//        val finalFlaws = "Minor head drop detected (FIXED)"
//
//        if (cloudIdToUpdate.isEmpty() || userId == "FALLBACK_UID") {
//            Toast.makeText(this, "Cannot update: No Cloud ID or User not logged in.", Toast.LENGTH_LONG).show()
//            return
//        }
//
//        lifecycleScope.launch {
//            try {
//                repository.updateAnalysisResult(
//                    cloudIdToUpdate,
//                    improvedAccuracy,
//                    finalFlaws
//                )
//                withContext(Dispatchers.Main) {
//                    Log.d(tag, "DEMO UPDATE SUCCESS: Cloud ID $cloudIdToUpdate updated.")
//                    Toast.makeText(this@PracticeSession, "Analysis UPDATE Demo: Success (Accuracy 95%)! Check Firestore.", Toast.LENGTH_LONG).show()
//                }
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@PracticeSession, "UPDATE Demo FAILED: ${e.message}", Toast.LENGTH_LONG).show()
//                }
//            }
//        }
//    }
//
//    // ----------------------------
//    // Video Upload and Data Save (DEFINITIVE FIX IMPLEMENTATION)
//    // ----------------------------
//
//    // In saveSessionRecord(): Explicit dateMillis, better error handling
//    private fun saveSessionRecord(videoUrl: String, durationSeconds: Int) {
//        val type = if (videoUrl.contains("Upload Failed")) "Failed Upload Session" else "Recorded Drill"
//        val mockAccuracy = Random.nextDouble(0.4, 0.95)
//        val mockFlaws = if (mockAccuracy < 0.7) "Head movement, low elbow" else "Excellent stance"
//
//        // UPDATED: Explicit dateMillis (uses current time)
//        val newSession = SessionEntity(
//            userId = userId,
//            dateMillis = System.currentTimeMillis(),  // ADDED: Explicit for consistency
//            cloudDocumentId = "",  // Defaults OK, set by repo
//            drillType = type,
//            durationSeconds = durationSeconds,
//            successRate = mockAccuracy,
//            flawDetails = "Video URL: $videoUrl. Flaws: $mockFlaws"
//        )
//
//        lifecycleScope.launch {
//            try {
//                repository.insert(newSession)  // Now schema-safe
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@PracticeSession, "Session saved to history! (Cloud synced)", Toast.LENGTH_SHORT).show()
//                    // Navigate
//                    val intent = Intent(this@PracticeSession, VideoHistoryActivity::class.java)
//                    intent.putExtra("userId", userId)
//                    startActivity(intent)
//                }
//            } catch (e: Exception) {
//                val errorMessage = e.message ?: "Unknown Error"
//                Log.e(tag, "Insert failed: $errorMessage", e)
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@PracticeSession, "Local save failed: $errorMessage. Check DB.", Toast.LENGTH_LONG).show()
//                }
//            }
//        }
//    }
//
//    // In uploadVideoToFirebase(): Enhanced auth + error logging (replace full function)
//    private fun uploadVideoToFirebase(videoUri: Uri, durationSeconds: Int = 0) {
//        if (auth.currentUser == null) {
//            Toast.makeText(this, "Please log in to upload.", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        Toast.makeText(this, "Uploading video...", Toast.LENGTH_SHORT).show()
//        Log.d(tag, "Upload start for URI: $videoUri")
//
//        // Force auth token refresh
//        auth.currentUser?.getIdToken(true)?.addOnCompleteListener { tokenTask ->
//            if (!tokenTask.isSuccessful) {
//                Toast.makeText(this, "Login expired—re-auth needed.", Toast.LENGTH_SHORT).show()
//                return@addOnCompleteListener
//            }
//            Log.d(tag, "Auth token refreshed; proceeding to upload")
//
//            lifecycleScope.launch {
//                val fileName = "${userId}_${System.currentTimeMillis()}.mp4"
//                var localFile: File? = null
//                var retries = 0
//                while (localFile == null && retries < 2) {
//                    localFile = copyUriToCache(this@PracticeSession, videoUri, fileName)
//                    if (localFile == null) {
//                        retries++
//                        kotlinx.coroutines.delay(500)
//                        Log.w(tag, "Copy retry $retries")
//                    }
//                }
//                if (localFile == null) {
//                    Toast.makeText(this@PracticeSession, "Video prep failed—try smaller file.", Toast.LENGTH_LONG).show()
//                    saveSessionRecord("Prep Failed", durationSeconds)
//                    return@launch
//                }
//
//                val videoRef = storage.reference.child("session_videos/$fileName")
//                videoRef.putFile(Uri.fromFile(localFile))
//                    .addOnSuccessListener {
//                        Log.d(tag, "putFile success!")
//                        videoRef.downloadUrl.addOnSuccessListener { uri ->
//                            val videoUrl = uri.toString()
//                            Log.d(tag, "Full upload success: $videoUrl")
//                            Toast.makeText(this@PracticeSession, "Upload done—analyzing keypoints...", Toast.LENGTH_SHORT).show()
//                            saveSessionRecord(videoUrl, durationSeconds)  // Triggers insert + nav
//                        }.addOnFailureListener { dlErr ->
//                            Log.e(tag, "Download URL failed: ${dlErr.message}", dlErr)
//                            saveSessionRecord("URL Fetch Failed: ${dlErr.message}", durationSeconds)
//                        }
//                    }
//                    .addOnFailureListener { e ->
//                        // ENHANCED: Log full details
//                        Log.e(tag, "Storage upload failed: ${e.message}", e)
//                        val fbError = when {
//                            e.message?.contains("does not exist") == true -> "Path/rules issue—check Firebase Storage rules."
//                            e.message?.contains("Permission denied") == true -> "Auth/rules denied write."
//                            else -> e.message ?: "Unknown"
//                        }
//                        Toast.makeText(this@PracticeSession, "Upload failed: $fbError", Toast.LENGTH_LONG).show()
//                        saveSessionRecord("Upload Failed: $fbError", durationSeconds)
//                    }
//                    .addOnCompleteListener {
//                        lifecycleScope.launch(Dispatchers.IO) {
//                            localFile.delete()
//                        }
//                    }
//            }
//        }
//    }
//
//    // ----------------------------
//    // Image Conversion Helpers (Kept as is)
//    // ----------------------------
//    private fun imageToBase64(imageProxy: ImageProxy): String {
//        val originalBitmap = imageProxy.toBitmap()
//        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
//
//        if (originalBitmap == null) return ""
//
//        val rotatedBitmap: Bitmap
//
//        if (rotationDegrees != 0) {
//            val matrix = android.graphics.Matrix()
//            matrix.postRotate(rotationDegrees.toFloat())
//            rotatedBitmap = Bitmap.createBitmap(
//                originalBitmap,
//                0,
//                0,
//                originalBitmap.width,
//                originalBitmap.height,
//                matrix,
//                true
//            )
//        } else {
//            rotatedBitmap = originalBitmap
//        }
//
//        val stream = ByteArrayOutputStream()
//        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
//        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
//    }
//    // ----------------------------
//    // Frame sending throttling & Processing
//    // ----------------------------
//
//
//    private fun processCameraFrame(imageProxy: ImageProxy) {
//        if (isProcessing) {
//            imageProxy.close()
//            return
//        }
//        isProcessing = true
//
//        val inputWidth = imageProxy.width
//        val inputHeight = imageProxy.height
//
//        Log.d("FRAME_SIZE", "Sending frame: ${imageProxy.width}x${imageProxy.height}")
//
//        backgroundExecutor.execute {
//            val WORKSPACE_NAME = "hello-7pqr3"
//            val WORKFLOW_ID = "custom-workflow-4"
//            val API_KEY = "7VCjsMFfykWO22m0bCXb"
//
//            val apiUrl = "https://serverless.roboflow.com/$WORKSPACE_NAME/workflows/$WORKFLOW_ID"
//
//            try {
//                val base64Image = imageToBase64(imageProxy)
//                imageProxy.close()
//
//                val json = """
//            {
//                "api_key": "$API_KEY",
//                "inputs": {
//                    "image": {
//                        "type": "base64",
//                        "value": "$base64Image"
//                    }
//                }
//            }
//            """.trimIndent()
//
//                val requestBody = json.toRequestBody("application/json".toMediaType())
//
//                val request = Request.Builder()
//                    .url(apiUrl)
//                    .post(requestBody)
//                    .build()
//
//                val client = OkHttpClient.Builder()
//                    .connectTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
//                    .readTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
//                    .writeTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
//                    .build()
//
//                client.newCall(request).enqueue(object : Callback {
//                    override fun onResponse(call: Call, response: Response) {
//                        val responseBody = response.body?.string()
//
//                        Log.d("ROBOFLOW_RESPONSE", responseBody ?: "null")
//                        if (!response.isSuccessful || responseBody?.contains("\"message\":") == true) {
//                            Log.e(tag, "API Error: ${response.code}-$responseBody")
//                            runOnUiThread {
//                                Toast.makeText(
//                                    this@PracticeSession,
//                                    "API Error: ${response.code}",
//                                    Toast.LENGTH_SHORT
//                                ).show()
//                            }
//                            isProcessing = false
//                            runOnUiThread { bboxOverlay.clear() }
//                            return
//                        }
//
//                        // --- START FIX ---
//                        // Ensure the image view is hidden at the start of response processing
//                        runOnUiThread {
//                            workflowOutputImageView.visibility = View.GONE
//                        }
//                        // --- END FIX ---
//
//
//                        if (responseBody != null) {
//                            try {
//                                val jsonObject = org.json.JSONObject(responseBody)
//
//                                if (!jsonObject.has("outputs")) {
//                                    Log.e(tag, "Roboflow response is missing the 'outputs' array.")
//                                    isProcessing = false
//                                    runOnUiThread { bboxOverlay.clear() }
//                                    return
//                                }
//                                val jsonArray = jsonObject.getJSONArray("outputs")
//
//                                if (jsonArray.length() == 0) {
//                                    Log.e(tag, "Workflow returned no outputs.")
//                                    isProcessing = false
//                                    runOnUiThread { bboxOverlay.clear() }
//                                    return
//                                }
//
//                                val mainObject = jsonArray.getJSONObject(0)
//
//                                if (mainObject.has("output_predictions_v4")) {
//                                    val predictionsObj = mainObject.getJSONObject("output_predictions_v4")
//                                    Log.d("RAW_PREDICTIONS", predictionsObj.toString(4))
//
//                                    val tracked = predictionsObj.optJSONObject("tracked_detections")
//                                        ?: predictionsObj.optJSONObject("predictions") ?: predictionsObj
//
//                                    val detections = mutableListOf<Detection>()
//
//                                    if (tracked.has("predictions")) {
//                                        val preds = tracked.getJSONArray("predictions")
//                                        val modelWidth = 320f
//                                        val modelHeight = 320f
//
//                                        for (i in 0 until preds.length()) {
//                                            val pred = preds.getJSONObject(i)
//                                            val x = pred.optDouble("x", 0.0).toFloat()
//                                            val y = pred.optDouble("y", 0.0).toFloat()
//                                            val w = pred.optDouble("width", 0.0).toFloat()
//                                            val h = pred.optDouble("height", 0.0).toFloat()
//                                            val label = pred.optString("class", "object")
//                                            val conf = pred.optDouble("confidence", 1.0).toFloat()
//
//                                            val left = (x - w / 2) / modelWidth
//                                            val top = (y - h / 2) / modelHeight
//                                            val right = (x + w / 2) / modelWidth
//                                            val bottom = (y + h / 2) / modelHeight
//
//                                            // Optional: Filter low confidence detections here (e.g., if conf > 0.5f)
//
//                                            detections.add(Detection(label, conf, RectF(left, top, right, bottom)))
//                                        }
//
//                                        runOnUiThread {
//                                            bboxOverlay.updateDetections(detections, inputWidth, inputHeight)
//                                            // We ensure the visualization image is GONE so the BBOX overlay is visible.
//                                            workflowOutputImageView.visibility = View.GONE
//                                        }
//                                    } else {
//                                        runOnUiThread {
//                                            bboxOverlay.clear()
//                                        }
//                                    }
//                                } else {
//                                    // Clear boxes if no predictions found in expected field
//                                    runOnUiThread { bboxOverlay.clear() }
//                                }
//
//                                // We intentionally DO NOT display output_visualization_2
//                                // if we want the real-time overlay visible.
//                                // Keeping this block only for reference or if you decided to switch visualizations later:
//                                if (mainObject.has("output_visualization_2")) {
//                                    val visualizationObject = mainObject.getJSONObject("output_visualization_2")
//                                    val base64ImageValue = visualizationObject.getString("value")
//                                    val annotatedBitmap = base64ToBitmap(base64ImageValue)
//
//                                    if (annotatedBitmap != null) {
//                                        // Set the image, but KEEP it hidden if you want the BBOX Overlay to be the primary output
//                                        runOnUiThread {
//                                            workflowOutputImageView.setImageBitmap(annotatedBitmap)
//                                            workflowOutputImageView.visibility = View.GONE
//                                        }
//                                    }
//                                }
//
//                            } catch (e: Exception) {
//                                Log.e(tag, "JSON Parsing or Bitmap decode failed: ${e.message}")
//                                isProcessing = false
//                                runOnUiThread { bboxOverlay.clear() }
//                            }
//                        }
//                        isProcessing = false  // Always reset here
//                    }
//
//                    override fun onFailure(call: Call, e: IOException) {
//                        Log.e("ROBOFLOW_ERROR", "Failed: ${e.message}")
//                        isProcessing = false
//                        runOnUiThread { bboxOverlay.clear() }
//                    }
//                })
//            } catch (e: Exception) {
//                Log.e(tag, "Image conversion failed on background thread: ${e.message}")
//                isProcessing = false
//                imageProxy.close()
//            }
//        }
//    }
//
//    // ----------------------------
//    // Base64 to Bitmap
//    // ----------------------------
//    private fun base64ToBitmap(base64Str: String): Bitmap? {
//        return try {
//            val cleanBase64 = base64Str.trim()
//            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
//            val bmp = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
//            bmp  // FIXED: Direct return (nullable Bitmap? matches function sig)
//        } catch (e: Exception) {
//            Log.e(tag, "Base64 to Bitmap failed: ${e.message}", e)
//            null
//        }
//    }
//
//}
package com.fyp.nextshot

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.graphics.Matrix
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import com.fyp.nextshot.data.local.database.AppDatabase
import com.fyp.nextshot.data.local.models.SessionEntity
import com.fyp.nextshot.data.repository.SessionRepository
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.File
import java.io.FileOutputStream // For file writing
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

// FIXED: Extension FUNCTION (top-level, after imports)
private fun ImageProxy.toBitmap(): Bitmap? {
    // Check for null planes (common cause of camera crashes)
    if (planes.isEmpty() || planes[0].buffer == null) return null
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

class PracticeSession : AppCompatActivity() {


    private val tag = "PracticeSession"
    // --- Data/Architecture Initialization ---
    private lateinit var etCloudIdInput: EditText
    private lateinit var btnTriggerUpdate: Button
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val userId = auth.currentUser?.uid ?: "FALLBACK_UID"
    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { SessionRepository(database.sessionDao(), userId, db) }

    // --- UI and Camera Components ---
    private lateinit var workflowOutputImageView: android.widget.ImageView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var bboxOverlay: BoundingBoxOverlay
    private lateinit var previewView: PreviewView
    private lateinit var startStopButton: MaterialButton
    private lateinit var uploadVideoButton: MaterialButton
    private lateinit var instructionCard: androidx.cardview.widget.CardView

    // --- State Management ---
    private var isProcessing = false
    private var isSessionActive = false
    private var sessionStartTime: Long = 0

    // --- CameraX Video ---
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var imageAnalyzer: ImageAnalysis? = null  // Track to pause/resume
    // --- Timer Variables (FIXED) ---
    private lateinit var recordingTimer: TextView
    private val timerHandler = Handler(Looper.getMainLooper())
    private val updateTimerRunnable = object : Runnable {
        override fun run() {
            val millis = System.currentTimeMillis() - sessionStartTime
            val seconds = (millis / 1000) % 60
            val minutes = (millis / (1000 * 60)) % 60

            val time = String.format(Locale.getDefault(), "REC %02d:%02d", minutes, seconds)
            recordingTimer.text = time

            timerHandler.postDelayed(this, 1000)
        }
    }

    private val requiredPermissions = mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).apply {
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in requiredPermissions && !it.value) {
                    permissionGranted = false
                }
            }
            if (!permissionGranted) {
                Toast.makeText(
                    this,
                    "Camera and Audio permissions required.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                startLiveAnalysis()
            }
        }

    // New: Manual launcher to handle the result
    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let {
                uploadVideoToFirebase(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice_session)

        previewView = PreviewView(this)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        findViewById<android.widget.FrameLayout>(R.id.camera_container).addView(previewView, 0)

        startStopButton = findViewById(R.id.start_stop_button)
        instructionCard = findViewById(R.id.instruction_card)
        workflowOutputImageView = findViewById(R.id.workflow_output_image_view)
        bboxOverlay = findViewById(R.id.bbox_overlay)
        uploadVideoButton = findViewById(R.id.upload_video_button)

        // Assuming your XML ID is 'recording_timer'
        recordingTimer = findViewById(R.id.recording_timer)

        recordingTimer.bringToFront()
        // --- Demo/Update Tool Initialization ---
        etCloudIdInput = findViewById(R.id.et_cloud_id_input)
        btnTriggerUpdate = findViewById(R.id.btn_trigger_update)
        // -------------------------------------

        cameraExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor = Executors.newCachedThreadPool()

        instructionCard.visibility = View.VISIBLE
        previewView.visibility = View.GONE
        workflowOutputImageView.visibility = View.GONE
        recordingTimer.visibility = View.GONE

        // Ensure overlays are brought to front once views are initialized
        recordingTimer.bringToFront()
        bboxOverlay.bringToFront()

        setupListeners()
        // INITIAL CALL: Request permissions on create
        if (!allPermissionsGranted()) {
            activityResultLauncher.launch(requiredPermissions)
        }
    }

    private fun setupListeners() {
        startStopButton.setOnClickListener {
            if (!isSessionActive) {
                if (allPermissionsGranted()) {
                    // Start camera binding logic, which includes startRecording call
                    startLiveAnalysis()
                    startRecording()
                    isSessionActive = true
                    startStopButton.text = "Stop Session (Recording)"
                } else {
                    activityResultLauncher.launch(requiredPermissions)
                }
            } else {
                stopRecording() // This triggers upload in finalize event
                stopCamera()
                isSessionActive = false
                startStopButton.text = "Start Session"
            }
        }

        uploadVideoButton.setOnClickListener {
            if (allPermissionsGranted()) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "video/*"
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                }
                videoPickerLauncher.launch(intent)
            } else {
                activityResultLauncher.launch(requiredPermissions)
            }
        }

        // --- Demo Trigger Listener ---
        btnTriggerUpdate.setOnClickListener {
            val cloudId = etCloudIdInput.text.toString().trim()
            if (cloudId.isNotEmpty()) {
                demoAnalysisUpdate(cloudId)
            } else {
                Toast.makeText(this, "Please paste a Cloud Document ID.", Toast.LENGTH_SHORT).show()
            }
        }
        // -----------------------------
    }

    // ----------------------------
    // URI Copy Helper (DEFINITIVE FIX)
    // ----------------------------
    private suspend fun copyUriToCache(context: android.content.Context, uri: Uri, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val cacheFile = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(cacheFile)

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext cacheFile
        } catch (e: Exception) {
            Log.e(tag, "Failed to copy URI to cache: ${e.message}", e)
            return@withContext null
        }
    }

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startTimer() {
        recordingTimer.visibility = View.VISIBLE
        recordingTimer.bringToFront()
        sessionStartTime = System.currentTimeMillis()
        Log.d(tag, "Timer UI visible, startTime: $sessionStartTime")
        timerHandler.post(updateTimerRunnable)
    }

    private fun stopTimer() {
        recordingTimer.visibility = View.GONE
        timerHandler.removeCallbacks(updateTimerRunnable)
    }

    // CRITICAL FIX: Ensure camera binds reliably
    private fun startLiveAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        instructionCard.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        workflowOutputImageView.visibility = View.GONE

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processCameraFrame(imageProxy)
                    }
                }
            this.imageAnalyzer = imageAnalyzer

            // Video Capture setup (using a lower quality for stability if needed)
            // Find these lines:
            val recorder = androidx.camera.video.Recorder.Builder()
                .setQualitySelector(
                    androidx.camera.video.QualitySelector.fromOrderedList(
                        // Order the qualities you prefer for recording stability/quality
                        listOf(androidx.camera.video.Quality.SD, androidx.camera.video.Quality.FHD),

                        // CRITICAL FIX: Use the correct static constructor for FallbackStrategy.
                        // This tells CameraX: "Try SD or FHD first, otherwise fall back to the LOWEST available quality."
                        androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(androidx.camera.video.Quality.LOWEST)
                    )
                )
                .build()

            videoCapture = androidx.camera.video.VideoCapture.withOutput(recorder)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer, videoCapture
                )
                Log.d(tag, "Camera bound successfully")
                runOnUiThread {
                    recordingTimer.bringToFront()
                    bboxOverlay.bringToFront()
                    workflowOutputImageView.bringToFront()
                    instructionCard.bringToFront()
                }
            } catch (exc: Exception) {
                // ENHANCED: Handle common exceptions
                Log.e(tag, "Bind failed: ${exc.message}", exc)
                val errorMsg = when {
                    exc.message?.contains("No available camera") == true -> "No camera available—check hardware."
                    exc.message?.contains("Session") == true -> "Camera session conflict—try clearing cache."
                    else -> "Bind error: ${exc.message ?: "Unknown"}"
                }
                runOnUiThread {
                    Toast.makeText(this, "Failed to start camera: $errorMsg", Toast.LENGTH_LONG).show()
                    instructionCard.visibility = View.VISIBLE
                    previewView.visibility = View.GONE
                }
                // Attempt a quick retry if camera fails to bind on first try
                Handler(Looper.getMainLooper()).postDelayed({ startLiveAnalysis() }, 2000)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // CRITICAL FIX: Stabilizing the recording start
    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return

        // Timer starts immediately
        startTimer()
        Log.d(tag, "Timer started; pausing analysis for recording")

        // Stop Image Analysis processing during recording to free up buffer space
        imageAnalyzer?.clearAnalyzer()

        // Use a short delay to ensure the camera surface fully settles before starting the encoder
        Handler(Looper.getMainLooper()).postDelayed({

            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/NextShot")
                }
            }

            val mediaStoreOutputOptions = MediaStoreOutputOptions
                .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build()

            recording = videoCapture.output
                .prepareRecording(this, mediaStoreOutputOptions)
                .apply {
                    val hasAudioPermission = PermissionChecker.checkSelfPermission(this@PracticeSession, Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED
                    if (hasAudioPermission) {
                        withAudioEnabled()
                    }
                    Log.d(tag, "Recording prepared: Audio enabled: $hasAudioPermission")
                }
                .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            Toast.makeText(baseContext, "Recording Started", Toast.LENGTH_SHORT).show()
                            startStopButton.text = "Stop Recording"
                        }
                        is VideoRecordEvent.Finalize -> {
                            stopTimer()

                            val durationMillis = recordEvent.recordingStats.recordedDurationNanos / 1_000_000L
                            val totalSeconds = (durationMillis / 1000).toInt()
                            val minutes = totalSeconds / 60
                            val seconds = totalSeconds % 60
                            val timeString = if (minutes > 0) { "$minutes minutes and $seconds seconds" } else { "$seconds seconds" }

                            if (!recordEvent.hasError()) {
                                Toast.makeText(baseContext, "Session complete. You practiced for $timeString. Keypoints ready for analysis!", Toast.LENGTH_LONG).show()
                                uploadVideoToFirebase(recordEvent.outputResults.outputUri, totalSeconds)
                            } else {
                                val errorCode = recordEvent.error
                                Log.e(tag, "Video capture failed with error: ${recordEvent.error}")
                                Toast.makeText(baseContext, "Recording failed! (Code $errorCode). Check logcat for details.", Toast.LENGTH_LONG).show()
                                recording?.close()
                                recording = null
                            }
                            // Re-enable Image Analysis after recording stops (only if camera is still bound)
                            imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy -> processCameraFrame(imageProxy) }
                        }
                        else -> {}
                    }
                }
        }, 500) // Delay the actual start of recording by 500ms
    }

    private fun stopRecording() {
        val recording = this.recording
        if (recording != null) {
            Log.d(tag, "Initiating stop with buffer delay...")
            // NEW: Delay stop() call slightly to let buffers settle (prevents Code 8)
            Handler(Looper.getMainLooper()).postDelayed({
                recording.stop()
                this.recording = null
                Log.d(tag, "stop() called—waiting for finalize")
            }, 2000)  // 2s buffer: Lets frames flush before finalize

            // NEW: On error (from Finalize handler), auto-rebind camera
            // (Add this inside the Finalize when() block, after existing if (!hasError()))
        } else {
            Toast.makeText(this, "No active recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            instructionCard.visibility = View.VISIBLE
            previewView.visibility = View.GONE
            workflowOutputImageView.visibility = View.GONE
            bboxOverlay.clear()
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        backgroundExecutor.shutdown()
        timerHandler.removeCallbacks(updateTimerRunnable)
    }

    // Example function to call in PracticeSession (to demo UPDATE capability)
    fun demoAnalysisUpdate(cloudIdToUpdate: String) {
        // Simulate model output 10 minutes later
        val improvedAccuracy = 0.95
        val finalFlaws = "Minor head drop detected (FIXED)"

        if (cloudIdToUpdate.isEmpty() || userId == "FALLBACK_UID") {
            Toast.makeText(this, "Cannot update: No Cloud ID or User not logged in.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            try {
                repository.updateAnalysisResult(
                    cloudIdToUpdate,
                    improvedAccuracy,
                    finalFlaws
                )
                withContext(Dispatchers.Main) {
                    Log.d(tag, "DEMO UPDATE SUCCESS: Cloud ID $cloudIdToUpdate updated.")
                    Toast.makeText(this@PracticeSession, "Analysis UPDATE Demo: Success (Accuracy 95%)! Check Firestore.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PracticeSession, "UPDATE Demo FAILED: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ----------------------------
    // Video Upload and Data Save (DEFINITIVE FIX IMPLEMENTATION)
    // ----------------------------

    private fun uploadVideoToFirebase(videoUri: Uri, durationSeconds: Int = 0) {
        if (userId == "FALLBACK_UID") {
            Toast.makeText(this, "Please log in to upload videos.", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Preparing video for upload...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val fileName = "${userId}_${System.currentTimeMillis()}.mp4"

            // FIX: Copy the file to the app's cache storage first (runs on IO Dispatcher)
            val localFile: File? = copyUriToCache(this@PracticeSession, videoUri, fileName)

            if (localFile == null) {
                Toast.makeText(this@PracticeSession, "Video preparation failed.", Toast.LENGTH_LONG).show()
                saveSessionRecord("Upload Failed: File preparation", durationSeconds)
                return@launch
            }

            val videoRef = storage.reference.child("session_videos/$fileName")

            // Use the secure local File object for upload
            videoRef.putFile(Uri.fromFile(localFile))
                .addOnSuccessListener {
                    // Get URL and save
                    videoRef.downloadUrl.addOnSuccessListener { uri ->
                        val videoUrl = uri.toString()
                        Toast.makeText(this@PracticeSession, "Video upload success! Simulating analysis...", Toast.LENGTH_SHORT).show()

                        saveSessionRecord(videoUrl, durationSeconds)
                    }
                }
                .addOnFailureListener { e ->
                    // ENHANCED: Log full details
                    Log.e(tag, "Storage upload failed: ${e.message}", e)
                    val fbError = when {
                        e.message?.contains("does not exist") == true -> "Path/rules issue—check Firebase Storage rules."
                        e.message?.contains("Permission denied") == true -> "Auth/rules denied write."
                        else -> e.message ?: "Unknown"
                    }
                    Toast.makeText(this@PracticeSession, "Video upload failed: $fbError", Toast.LENGTH_LONG).show()
                    saveSessionRecord("Upload Failed: $fbError", durationSeconds)
                }
                .addOnCompleteListener {
                    // Clean up the temporary cached file regardless of success/failure
                    lifecycleScope.launch(Dispatchers.IO) {
                        localFile.delete()
                    }
                }
        }
    }

    private fun saveSessionRecord(videoUrl: String, durationSeconds: Int) {
        val type = if (videoUrl.contains("Upload Failed")) "Failed Upload Session" else "Recorded Drill"

        val mockAccuracy = Random.nextDouble(0.4, 0.95)
        val mockFlaws = if (mockAccuracy < 0.7) "Head movement, low elbow" else "Excellent stance"

        val newSession = SessionEntity(
            userId = userId,
            drillType = type,
            durationSeconds = durationSeconds,
            successRate = mockAccuracy,
            flawDetails = "Video URL: $videoUrl. Flaws: $mockFlaws",
            dateMillis = System.currentTimeMillis() // Added for consistency
        )

        lifecycleScope.launch {
            try {
                repository.insert(newSession)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PracticeSession, "CLOUD SYNC SUCCESS! Check History.", Toast.LENGTH_LONG).show()
                    // NEW: Navigate to Video History after successful save
                    val intent = Intent(this@PracticeSession, VideoHistoryActivity::class.java)
                    startActivity(intent)
                }

            } catch (e: Exception) {
                // This block catches the failure exception (e.g., PERMISSION_DENIED)
                val errorMessage = e.message ?: "Unknown Firestore Error"
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PracticeSession, "CLOUD SYNC FAILED: $errorMessage", Toast.LENGTH_LONG).show()
                }
                Log.e(tag, "Firestore Insert Failed: $errorMessage", e)
            }
        }
    }

    // ----------------------------
    // Image Conversion Helpers (Kept as is)
    // ----------------------------
    private fun imageToBase64(imageProxy: ImageProxy): String {
        val originalBitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        if (originalBitmap == null) return ""

        val rotatedBitmap: Bitmap

        if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            rotatedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
        } else {
            rotatedBitmap = originalBitmap
        }

        val stream = ByteArrayOutputStream()
        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    // ----------------------------
    // Frame sending throttling & Processing
    // ----------------------------

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
                            Log.e(tag, "API Error: ${response.code}-$responseBody")
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
                                    Log.e(tag, "Roboflow response is missing the 'outputs' array.")
                                    isProcessing = false
                                    runOnUiThread { bboxOverlay.clear() }
                                    return
                                }
                                val jsonArray = jsonObject.getJSONArray("outputs")

                                if (jsonArray.length() == 0) {
                                    Log.e(tag, "Workflow returned no outputs.")
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
                                Log.e(tag, "JSON Parsing or Bitmap decode failed: ${e.message}")
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
                Log.e(tag, "Image conversion failed on background thread: ${e.message}")
                isProcessing = false
                imageProxy.close()
            }
        }
    }
//    private fun processCameraFrame(imageProxy: ImageProxy) {
//        if (isProcessing) {
//            imageProxy.close()
//            return
//        }
//        isProcessing = true
//
//        val inputWidth = imageProxy.width
//        val inputHeight = imageProxy.height
//
//        backgroundExecutor.execute {
//            val WORKSPACE_NAME = "hello-7pqr3"
//            val WORKFLOW_ID = "custom-workflow-4"
//            val API_KEY = "7VCjsMFfykWO22m0bCXb"
//
//            val apiUrl = "https://serverless.roboflow.com/$WORKSPACE_NAME/workflows/$WORKFLOW_ID"
//
//            try {
//                val base64Image = imageToBase64(imageProxy)
//                imageProxy.close()
//
//                val json = """
//            {
//                "api_key": "$API_KEY",
//                "inputs": {
//                    "image": {
//                        "type": "base64",
//                        "value": "$base64Image"
//                    }
//                }
//            }
//            """.trimIndent()
//
//                val requestBody = json.toRequestBody("application/json".toMediaType())
//
//                val request = Request.Builder()
//                    .url(apiUrl)
//                    .post(requestBody)
//                    .build()
//
//                val client = OkHttpClient.Builder()
//                    .connectTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
//                    .readTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
//                    .writeTimeout(50, java.util.concurrent.TimeUnit.SECONDS)
//                    .build()
//
//                client.newCall(request).enqueue(object : Callback {
//                    override fun onResponse(call: Call, response: Response) {
//                        // ... (Roboflow response handling logic) ...
//                        isProcessing = false
//                    }
//
//                    override fun onFailure(call: Call, e: IOException) {
//                        Log.e("ROBOFLOW_ERROR", "Failed: ${e.message}")
//                        isProcessing = false
//                        runOnUiThread { bboxOverlay.clear() }
//                    }
//                })
//            } catch (e: Exception) {
//                Log.e(tag, "Image conversion failed on background thread: ${e.message}")
//                isProcessing = false
//                imageProxy.close()
//            }
//        }
//    }

    // ----------------------------
    // Base64 to Bitmap
    // ----------------------------
    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val cleanBase64 = base64Str.trim()
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(tag, "Base64 to Bitmap failed: ${e.message}", e)
            null
        }
    }
}