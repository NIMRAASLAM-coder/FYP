package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView

class FlawsTracking : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flaws_tracking)

        // Initialize drawer and nav view
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        // Menu icon click opens drawer
        val menuToolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.menu)
        menuToolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Setup tabs and bottom navigation
        setupTabs()
        setupBottomNavigation()
        setupNavigationDrawer()
    }

    // ----- TOP TAB NAVIGATION -----
    private fun setupTabs() {
        val tabOverview = findViewById<MaterialButton>(R.id.tab_overview)
        val tabPerformance = findViewById<MaterialButton>(R.id.tab_performance)
        val tabFlaws = findViewById<MaterialButton>(R.id.tab_flaws)

        // Overview tab → Progress
        tabOverview.setOnClickListener {
            startActivity(Intent(this, Progress::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Performance tab → PerformanceTracking
        tabPerformance.setOnClickListener {
            startActivity(Intent(this, PerformanceTracking::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Flaws tab → current screen
        tabFlaws.setOnClickListener {
            // Already here, do nothing
        }
    }

    // ----- BOTTOM NAVIGATION -----
    private fun setupBottomNavigation() {
        val navDashboard = findViewById<LinearLayout>(R.id.dash)
        val navPractice = findViewById<LinearLayout>(R.id.practice)
        val navProgress = findViewById<LinearLayout>(R.id.progress)
        val navTips = findViewById<LinearLayout>(R.id.tips)

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

        // Progress
        navProgress.setOnClickListener {
            startActivity(Intent(this, Progress::class.java))
            finish()
        }

        // Tips
        navTips.setOnClickListener {
            startActivity(Intent(this, TipsForYou::class.java))
            finish()
        }
    }

    // ----- DRAWER MENU HANDLING -----
    private fun setupNavigationDrawer() {
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                // Add menu actions if needed
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }
}
