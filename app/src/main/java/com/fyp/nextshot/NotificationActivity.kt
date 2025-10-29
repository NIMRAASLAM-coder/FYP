package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

class NotificationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification) // <-- match your XML filename

        // ===== Buttons inside the cards =====
        val btnPractice = findViewById<LinearLayout>(R.id.btnPractice)
        val btnProgress = findViewById<LinearLayout>(R.id.btnProgress)
        val btnTips = findViewById<LinearLayout>(R.id.btnTips)

        // You can show feedback or navigate to other screens
        btnPractice?.setOnClickListener {
            Snackbar.make(it, "Starting Daily Practice...", Snackbar.LENGTH_SHORT).show()
            // startActivity(Intent(this, PracticeActivity::class.java))
        }

        btnProgress?.setOnClickListener {
            Snackbar.make(it, "Opening Weekly Progress...", Snackbar.LENGTH_SHORT).show()
            // startActivity(Intent(this, ProgressActivity::class.java))
        }

        btnTips?.setOnClickListener {
            Snackbar.make(it, "Viewing New Tips...", Snackbar.LENGTH_SHORT).show()
            //startActivity(Intent(this, TipsActivity::class.java))
        }

        // ===== BOTTOM NAVIGATION =====
        val bottomNav = findViewById<LinearLayout>(R.id.bottom_nav_bar)

        val dashboardTab = bottomNav.getChildAt(0) as LinearLayout
        val practiceTab = bottomNav.getChildAt(1) as LinearLayout
        val progressTab = bottomNav.getChildAt(2) as LinearLayout
        val tipsTab = bottomNav.getChildAt(3) as LinearLayout

        dashboardTab.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            overridePendingTransition(0, 0)
        }

        practiceTab.setOnClickListener {
            //startActivity(Intent(this, PracticeActivity::class.java))
            overridePendingTransition(0, 0)
        }

        progressTab.setOnClickListener {
            // startActivity(Intent(this, ProgressActivity::class.java))
            overridePendingTransition(0, 0)
        }

        tipsTab.setOnClickListener {
            // startActivity(Intent(this, TipsActivity::class.java))
            overridePendingTransition(0, 0)
        }
    }
}
