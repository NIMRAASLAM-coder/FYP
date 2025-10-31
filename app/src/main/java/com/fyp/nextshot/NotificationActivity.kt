package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView

class NotificationActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar

    // Bottom navigation
    private lateinit var navDashboard: View
    private lateinit var navPractice: View
    private lateinit var navProgress: View
    private lateinit var navTips: View

    // Notification cards container
    private lateinit var notificationsContainer: LinearLayout
    private lateinit var emptyState: LinearLayout

    // Notification action buttons
    private lateinit var btnStartPractice: MaterialButton
    private lateinit var btnViewProgress: MaterialButton
    private lateinit var btnViewTips: MaterialButton

    // Notification close buttons
    private lateinit var closeDailyReminder: ImageView
    private lateinit var closeWeeklyReport: ImageView
    private lateinit var closeCoachingTip: ImageView

    // Check/Done button
    private lateinit var checkDailyReminder: ImageView

    // Track visible notifications
    private var visibleNotificationsCount = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        initializeViews()
        setupToolbarAndDrawer()
        setupBottomNavigation()
        setupNotificationActions()
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.menu)

        // Bottom navigation
        navDashboard = findViewById(R.id.nav_dashboard)
        navPractice = findViewById(R.id.nav_practice)
        navProgress = findViewById(R.id.nav_progress)
        navTips = findViewById(R.id.nav_tips)

        // Container and empty state
        notificationsContainer = findViewById(R.id.notifications_container)
        emptyState = findViewById(R.id.empty_state)

        // Action buttons
        btnStartPractice = findViewById(R.id.btn_start_practice)
        btnViewProgress = findViewById(R.id.btn_view_progress)
        btnViewTips = findViewById(R.id.btn_view_tips)

        // Close buttons
        closeDailyReminder = findViewById(R.id.close_daily_reminder)
        closeWeeklyReport = findViewById(R.id.close_weekly_report)
        closeCoachingTip = findViewById(R.id.close_coaching_tip)

        // Check button
        checkDailyReminder = findViewById(R.id.check_daily_reminder)
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
            handleDrawerNavigation(menuItem)
            true
        }
    }

    private fun setupBottomNavigation() {
        navDashboard.setOnClickListener {
            val intent = Intent(this, Dashboard::class.java)
            startActivity(intent)
        }

        navPractice.setOnClickListener {
            val intent = Intent(this, PracticeSession::class.java)
            startActivity(intent)
        }

        navProgress.setOnClickListener {
            val intent = Intent(this, Progress::class.java)
            startActivity(intent)
        }

        navTips.setOnClickListener {
            val intent = Intent(this, TipsForYou::class.java)
            startActivity(intent)
        }
    }

    private fun setupNotificationActions() {
        // Start Practice button
        btnStartPractice.setOnClickListener {
            val intent = Intent(this, PracticeSession::class.java)
            startActivity(intent)
        }

        // View Progress button
        btnViewProgress.setOnClickListener {
            val intent = Intent(this, Progress::class.java)
            startActivity(intent)
        }

        // View Tips button
        btnViewTips.setOnClickListener {
            val intent = Intent(this, TipsForYou::class.java)
            startActivity(intent)
        }

        // Daily Reminder - Close button
        closeDailyReminder.setOnClickListener {
            dismissNotification(0)
        }

        // Daily Reminder - Check button (mark as done)
        checkDailyReminder.setOnClickListener {
            Toast.makeText(this, "Marked as done!", Toast.LENGTH_SHORT).show()
            dismissNotification(0)
        }

        // Weekly Report - Close button
        closeWeeklyReport.setOnClickListener {
            dismissNotification(1)
        }

        // Coaching Tip - Close button
        closeCoachingTip.setOnClickListener {
            dismissNotification(2)
        }
    }

    private fun dismissNotification(cardIndex: Int) {
        val cardView = notificationsContainer.getChildAt(cardIndex)

        // Animate fade out
        cardView?.animate()
            ?.alpha(0f)
            ?.setDuration(300)
            ?.withEndAction {
                cardView.visibility = View.GONE
                visibleNotificationsCount--
                checkEmptyState()
            }
            ?.start()
    }

    private fun checkEmptyState() {
        if (visibleNotificationsCount == 0) {
            emptyState.visibility = View.VISIBLE
            emptyState.alpha = 0f
            emptyState.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
    }

    private fun handleDrawerNavigation(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.profile -> {
                val intent = Intent(this, EditProfileActivity::class.java)
                startActivity(intent)
            }
            R.id.notification -> {
                // Already on notification screen, do nothing
            }
            R.id.session_history -> {
                val intent = Intent(this, SessionHistory::class.java)
                startActivity(intent)
            }
            R.id.AI -> {
                val intent = Intent(this, AICoachingChat::class.java)
                startActivity(intent)
            }
            R.id.settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }
            R.id.signout -> {
                performSignOut()
            }
        }

        // Close the drawer after navigation
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun performSignOut() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Yes") { _, _ ->
                // Clear user session
                val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
                sharedPreferences.edit().clear().apply()

                // Navigate to SignIn
                val intent = Intent(this, SignIn::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


}