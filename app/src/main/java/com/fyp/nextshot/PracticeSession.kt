package com.fyp.nextshot

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View // ADDED: To resolve 'View' reference error
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PracticeSession : AppCompatActivity() {

    private val TAG = "PracticeSession"
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var startStopButton: MaterialButton
    private lateinit var instructionCard: androidx.cardview.widget.CardView // To manage visibility

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
                Toast.makeText(this, "Camera and Audio permissions required to run session.", Toast.LENGTH_LONG).show()
                // Do not finish, allow the user to see the screen and try again
            } else {
                // Permissions granted, start camera immediately
                startCamera()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_practice_session)

        // Initialize views
        previewView = PreviewView(this)
        findViewById<android.widget.FrameLayout>(R.id.camera_container).addView(previewView)

        startStopButton = findViewById(R.id.start_stop_button)
        instructionCard = findViewById(R.id.instruction_card)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // CAMERA IS INITIALLY HIDDEN/STOPPED
        instructionCard.visibility = View.VISIBLE
        previewView.visibility = View.GONE

        // Set the Start button listener
        startStopButton.setOnClickListener {
            // Check if button text is 'Start Session' (meaning camera is off)
            if (startStopButton.text.toString() == "Start Session") {
                // 1. Request permissions (if not already granted)
                if (allPermissionsGranted()) {
                    startCamera()
                } else {
                    activityResultLauncher.launch(REQUIRED_PERMISSIONS)
                }

                // 2. TEMPORARY: Change button text to indicate the next state (Recording)
                startStopButton.text = "Stop Recording"

            } else {
                // TODO: Implement video recording stop logic here
                Toast.makeText(this, "Stopping session...", Toast.LENGTH_SHORT).show()

                // Stop camera preview
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

        // Hide instructions, show camera container
        instructionCard.visibility = View.GONE
        previewView.visibility = View.VISIBLE

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview setup
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera preview.", Toast.LENGTH_SHORT).show()
                instructionCard.visibility = View.VISIBLE
                previewView.visibility = View.GONE
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        // Unbind all use cases to stop the camera
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            // Show instructions again
            instructionCard.visibility = View.VISIBLE
            previewView.visibility = View.GONE
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}