package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AppCompatActivity

class PerformanceTracking : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_performance_tracking)

        // --- Tab Navigation (top section) ---
        val tabOverview = findViewById<MaterialButton>(R.id.tab_overview)
        val tabPerformance = findViewById<MaterialButton>(R.id.tab_performance)
        val tabFlaws = findViewById<MaterialButton>(R.id.tab_flaws)

        // Overview tab → Progress screen
        tabOverview.setOnClickListener {
            startActivity(Intent(this, Progress::class.java))
            finish()
        }

        // Performance tab → current screen
        tabPerformance.setOnClickListener {
            // Already here, do nothing
        }

        // Flaws tab → FlawsTracking screen
        tabFlaws.setOnClickListener {
            startActivity(Intent(this, FlawsTracking::class.java))
            finish()
        }

        // --- Bottom Navigation (footer section) ---
        val navDashboard = findViewById<LinearLayout>(R.id.dash)
        val navPractice = findViewById<LinearLayout>(R.id.practice)
        val navProgress = findViewById<LinearLayout>(R.id.progress)
        val navTips = findViewById<LinearLayout>(R.id.tips)

        // Dashboard
        navDashboard.setOnClickListener {
            startActivity(Intent(this, Dashboard::class.java))
            finish()
        }

        // Practice Session
        navPractice.setOnClickListener {
            startActivity(Intent(this, PracticeSession::class.java))
            finish()
        }

        // Progress
        navProgress.setOnClickListener {
            startActivity(Intent(this, Progress::class.java))
            finish()
        }

        // Tips For You
        navTips.setOnClickListener {
            startActivity(Intent(this, TipsForYou::class.java))
            finish()
        }
    }
}
