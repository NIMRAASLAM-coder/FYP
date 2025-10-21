package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings) // Make sure XML name matches

        // Bottom navigation buttons
        val btnDashboard = findViewById<LinearLayout>(R.id.bottom_nav_bar).findViewById<LinearLayout>(0)
        val btnPractice = findViewById<LinearLayout>(R.id.bottom_nav_bar).getChildAt(1) as LinearLayout
        val btnProgress = findViewById<LinearLayout>(R.id.bottom_nav_bar).getChildAt(2) as LinearLayout
        val btnTips = findViewById<LinearLayout>(R.id.bottom_nav_bar).getChildAt(3) as LinearLayout

        // Dashboard Click
        btnDashboard.setOnClickListener {
            Toast.makeText(this, "Navigating to Dashboard...", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        // Practice Click
        btnPractice.setOnClickListener {
            Toast.makeText(this, "Navigating to Practice...", Toast.LENGTH_SHORT).show()
         //   val intent = Intent(this, PracticeActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        // Progress Click
        btnProgress.setOnClickListener {
            Toast.makeText(this, "Navigating to Progress...", Toast.LENGTH_SHORT).show()
        //    val intent = Intent(this, ProgressActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }

        // Tips Click
        btnTips.setOnClickListener {
            Toast.makeText(this, "Navigating to Tips...", Toast.LENGTH_SHORT).show()
           // val intent = Intent(this, TipsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }
}
