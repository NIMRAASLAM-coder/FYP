package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SessionHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_history) // <-- Match XML filename

        // Search bar
        val searchBar = findViewById<EditText>(R.id.search_bar)

        // Spinners
        val dateSpinner = findViewById<Spinner>(R.id.datespinner)
        val sessionSpinner = findViewById<Spinner>(R.id.sessionspinner)

        // Session list layout
        val sessionList = findViewById<LinearLayout>(R.id.sessionlist)

        // --- Sample Session Cards (dynamic UI injection) ---
        sessionList.addView(createSessionCard("Session #3", "Wednesday, October 1, 2025", 85, 76, 45, 120))
        sessionList.addView(createSessionCard("Session #2", "Wednesday, October 1, 2025", 80, 78, 40, 110))

        // --- Spinner Setup ---
        ArrayAdapter.createFromResource(
            this,
            R.array.date_filter,
            android.R.layout.simple_spinner_dropdown_item
        ).also { adapter -> dateSpinner.adapter = adapter }

        ArrayAdapter.createFromResource(
            this,
            R.array.session_filter,
            android.R.layout.simple_spinner_dropdown_item
        ).also { adapter -> sessionSpinner.adapter = adapter }

        // --- Bottom Navigation Setup ---
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
            //  startActivity(Intent(this, PracticeActivity::class.java))
            overridePendingTransition(0, 0)
        }

        progressTab.setOnClickListener {
            //   startActivity(Intent(this, ProgressActivity::class.java))
            overridePendingTransition(0, 0)
        }

        tipsTab.setOnClickListener {
            //  startActivity(Intent(this, TipsActivity::class.java))
            overridePendingTransition(0, 0)
        }
    }

    /**
     * Dynamically creates a session card with given session details.
     */
    private fun createSessionCard(
        sessionTitle: String,
        date: String,
        score: Int,
        accuracy: Int,
        minutes: Int,
        shots: Int
    ): LinearLayout {
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(24, 24, 24, 24)
        card.background = resources.getDrawable(R.drawable.bg_card_light, theme)
        var layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.setMargins(0, 16, 0, 0)
        card.layoutParams = layoutParams

        val title = TextView(this).apply {
            text = sessionTitle
            textSize = 16f
            setTextColor(resources.getColor(R.color.black, theme))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val dateView = TextView(this).apply {
            text = date
            setTextColor(resources.getColor(R.color.light_blue, theme))
            textSize = 13f
        }

        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
            weightSum = 3f
        }

        metricsRow.addView(createMetricItem("🏏", "$score\nScore"))
        metricsRow.addView(createMetricItem("🎯", "$accuracy%\nAccuracy"))
        metricsRow.addView(createMetricItem("⏱", "$minutes min\n$shots shots"))

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            gravity = android.view.Gravity.CENTER
        }

        val btnAnalysis = Button(this).apply {
            text = "View Analysis"
            setTextColor(resources.getColor(R.color.white, theme))
            background = resources.getDrawable(R.drawable.bg_primary_button, theme)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
        }

        val btnShare = Button(this).apply {
            text = "Share"
            background = resources.getDrawable(R.drawable.bg_primary_button, theme)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8
            }
        }

        btnLayout.addView(btnAnalysis)
        btnLayout.addView(btnShare)

        card.addView(title)
        card.addView(dateView)
        card.addView(metricsRow)
        card.addView(btnLayout)

        return card
    }

    private fun createMetricItem(icon: String, text: String): LinearLayout {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        item.gravity = android.view.Gravity.CENTER

        val txt = TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setTextColor(resources.getColor(R.color.black, theme))
        }

        item.addView(txt)
        return item
    }
}
