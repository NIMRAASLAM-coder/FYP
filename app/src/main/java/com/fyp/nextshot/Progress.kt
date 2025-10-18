package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class Progress : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_progress)

        var dash = findViewById<View>(R.id.dash)
        var practice=findViewById<View>(R.id.practice)

        dash.setOnClickListener{
            val intent = Intent(this, Dashboard::class.java)
            startActivity(intent)
        }

        practice.setOnClickListener{
            val intent = Intent(this, PracticeSession::class.java)
            startActivity(intent)
        }
    }
}