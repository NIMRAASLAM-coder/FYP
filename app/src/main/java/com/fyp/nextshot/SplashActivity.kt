package com.fyp.nextshot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make it fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_splash)

        val videoView = findViewById<VideoView>(R.id.videoView)
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.batsman_splash}")
        videoView.setVideoURI(videoUri)

        videoView.setOnCompletionListener {
            // When the video finishes, go to your main activity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        videoView.start()
    }
}