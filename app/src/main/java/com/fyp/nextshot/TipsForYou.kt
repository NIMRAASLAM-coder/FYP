package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class TipsForYou : AppCompatActivity() {

    private lateinit var navDashboard: LinearLayout
    private lateinit var navPractice: LinearLayout
    private lateinit var navProgress: LinearLayout
    private lateinit var navTips: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tips_for_you)

        // Button to go to TipsForAll
        val nextButton = findViewById<Button>(R.id.tab_all_tips)
        nextButton.setOnClickListener {
            val intent = Intent(this, TipsForAll::class.java)
            startActivity(intent)
            finish()
        }

        // Initialize bottom navigation views
        navDashboard = findViewById(R.id.dash)
        navPractice = findViewById(R.id.practice)
        navProgress = findViewById(R.id.progress)
        navTips = findViewById(R.id.tips)

        setupBottomNavigation()
        setupBackPressHandler()
    }

    private fun setupBottomNavigation() {
        // Dashboard
        navDashboard.setOnClickListener {
            val intent = Intent(this, Dashboard::class.java)
            startActivity(intent)
            finish()
        }

        // Practice
        navPractice.setOnClickListener {
            val intent = Intent(this, PracticeSession::class.java)
            startActivity(intent)
            finish()
        }

        // Progress
        navProgress.setOnClickListener {
            val intent = Intent(this, Progress::class.java)
            startActivity(intent)
            finish()
        }

        // Tips (navigate back to TipsForYou)
        navTips.setOnClickListener {
            val intent = Intent(this, TipsForAll::class.java)
            startActivity(intent)
            finish()
        }
        // Tips (current screen)
        navTips.setOnClickListener {
            // Do nothing or refresh the screen
        }
    }

    private fun setupBackPressHandler() {
        // Back button returns to Progress
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val intent = Intent(this@TipsForYou, Progress::class.java)
                startActivity(intent)
                finish()
            }
        })
    }
}
