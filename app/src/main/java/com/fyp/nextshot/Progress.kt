package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView

class Progress : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar

    // Tabs
    private lateinit var tabOverview: MaterialButton
    private lateinit var tabPerformance: MaterialButton
    private lateinit var tabFlaws: MaterialButton

    // Stats
    private lateinit var avgScoreValue: TextView
    private lateinit var bestScoreValue: TextView
    private lateinit var avgAccuracyValue: TextView

    // Bottom navigation
    private lateinit var navDashboard: LinearLayout
    private lateinit var navPractice: LinearLayout
    private lateinit var navProgress: LinearLayout
    private lateinit var navTips: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_progress)

        initializeViews()
        setupToolbarAndDrawer()
        setupTabNavigation()
        setupBottomNavigation()
        setupBackPressHandler()
        loadProgressData()
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.menu)

        // Tabs
        tabOverview = findViewById(R.id.tab_overview)
        tabPerformance = findViewById(R.id.tab_performance)
        tabFlaws = findViewById(R.id.tab_flaws)

        // Stats
        avgScoreValue = findViewById(R.id.avg_score_value)
        bestScoreValue = findViewById(R.id.best_score_value)
        avgAccuracyValue = findViewById(R.id.avg_accuracy_value)

        // Bottom nav
        navDashboard = findViewById(R.id.dash)
        navPractice = findViewById(R.id.practice)
        navProgress = findViewById(R.id.progress)
        navTips = findViewById(R.id.tips)
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupTabNavigation() {
        // Tab navigation (inside Progress)
        tabPerformance.setOnClickListener {
            startActivity(Intent(this, PerformanceTracking::class.java))
        }

        tabFlaws.setOnClickListener {
            startActivity(Intent(this, FlawsTracking::class.java))
        }

        tabOverview.setOnClickListener {
            // Stay on the same screen (optional refresh or reload data)
            loadProgressData()
        }
    }

    private fun setupBottomNavigation() {
        // Dashboard
        navDashboard.setOnClickListener {
            startActivity(Intent(this, Dashboard::class.java))
            finish()
        }

        // Practice
        navPractice.setOnClickListener {
            startActivity(Intent(this, PracticeSession::class.java))
            finish()
        }

        // Progress (current)
        navProgress.setOnClickListener {
            // Stay here (or refresh)
        }

        // Tips
        navTips.setOnClickListener {
            startActivity(Intent(this, TipsForYou::class.java))
            finish()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // Back to Dashboard by default
                    startActivity(Intent(this@Progress, Dashboard::class.java))
                    finish()
                }
            }
        })
    }

    private fun loadProgressData() {
        avgScoreValue.text = "83"
        bestScoreValue.text = "91"
        avgAccuracyValue.text = "75%"
    }

    // Optional: Dynamic stats update
    fun updateStats(avgScore: Int, bestScore: Int, avgAccuracy: Int) {
        avgScoreValue.text = avgScore.toString()
        bestScoreValue.text = bestScore.toString()
        avgAccuracyValue.text = "$avgAccuracy%"
    }

    data class SessionData(
        val sessionNumber: Int,
        val date: String,
        val duration: String,
        val shots: Int,
        val accuracy: Int
    )

    override fun onResume() {
        super.onResume()
        loadProgressData()
    }
}
