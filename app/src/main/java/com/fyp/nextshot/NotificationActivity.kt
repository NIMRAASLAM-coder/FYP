package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NotificationActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var topProfileImage: ImageView

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

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
        loadUserData()
        
        topProfileImage.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.menu)
        topProfileImage = findViewById(R.id.profile_image)

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

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = document.toObject(User::class.java)
                    user?.let {
                        ProfileUtils.loadProfileImage(this, it.profileImageUrl, topProfileImage, R.drawable.img_7)
                        
                        // Load into Drawer Header
                        val headerView = navigationView.getHeaderView(0)
                        val headerProfileImage = headerView.findViewById<ImageView>(R.id.profile_image)
                        val headerUserName = headerView.findViewById<TextView>(R.id.user_name)
                        val headerUserEmail = headerView.findViewById<TextView>(R.id.user_email)
                        
                        headerUserName.text = it.fullName ?: "Player"
                        headerUserEmail.text = it.email ?: auth.currentUser?.email
                        ProfileUtils.loadProfileImage(this, it.profileImageUrl, headerProfileImage, R.drawable.img_21)
                    }
                }
            }
    }

    private fun setupBottomNavigation() {
        navDashboard.setOnClickListener {
            startActivity(Intent(this, Dashboard::class.java))
            finish()
        }

        navPractice.setOnClickListener {
            startActivity(Intent(this, PracticeSession::class.java))
            finish()
        }

        navProgress.setOnClickListener {
            startActivity(Intent(this, Progress::class.java))
            finish()
        }

        navTips.setOnClickListener {
            startActivity(Intent(this, TipsForYou::class.java))
            finish()
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
        if (visibleNotificationsCount <= 0) {
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
            R.id.nav_dashboard -> {
                startActivity(Intent(this, Dashboard::class.java))
                finish()
            }
            R.id.nav_practice -> {
                startActivity(Intent(this, PracticeSession::class.java))
                finish()
            }
            R.id.nav_progress -> {
                startActivity(Intent(this, Progress::class.java))
                finish()
            }
            R.id.nav_tips -> {
                startActivity(Intent(this, TipsForYou::class.java))
                finish()
            }
            R.id.profile -> {
                startActivity(Intent(this, EditProfileActivity::class.java))
            }
            R.id.notification -> {
                // Already here
            }
            R.id.session_history -> {
                startActivity(Intent(this, SessionHistory::class.java))
            }
            R.id.AI -> {
                startActivity(Intent(this, AICoachingChat::class.java))
            }
            R.id.settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.signout -> {
                startActivity(Intent(this, SignOutConfirmationActivity::class.java))
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}