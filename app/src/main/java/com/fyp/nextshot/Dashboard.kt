package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class Dashboard : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar

    // Bottom navigation views
    private lateinit var navDashboard: View
    private lateinit var navPractice: View
    private lateinit var navProgress: View
    private lateinit var navTips: View

    // Start button
    private lateinit var startButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Initialize views
        initializeViews()

        // Setup toolbar and drawer
        setupToolbarAndDrawer()

        // Setup click listeners
        setupClickListeners()

        // Highlight current page in bottom navigation
        highlightBottomNavItem(navDashboard)


        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnCompleteListener
                FirebaseFirestore.getInstance().collection("users").document(userId)
                    .update("fcmToken", token)
                Log.d("FCM_SAVE", "Manual token save: $token")
            }
        }

    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.menu)

        // Bottom navigation items
        navDashboard = findViewById(R.id.nav_dashboard)
        navPractice = findViewById(R.id.nav_practice)
        navProgress = findViewById(R.id.nav_progress)
        navTips = findViewById(R.id.nav_tips)

        // Start button
        startButton = findViewById(R.id.start)
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

        // Setup navigation drawer item click listener
        navigationView.setNavigationItemSelectedListener { menuItem ->
            handleDrawerNavigation(menuItem)
            true
        }
    }

    private fun setupClickListeners() {
        // Start practice button
        startButton.setOnClickListener {
            navigateToPractice()
        }

        // Bottom navigation click listeners
        navDashboard.setOnClickListener {
            // Already on dashboard, maybe refresh or scroll to top
            highlightBottomNavItem(navDashboard)
        }

        navPractice.setOnClickListener {
            navigateToPractice()
        }

        navProgress.setOnClickListener {
            navigateToProgress()
        }

        navTips.setOnClickListener {
            navigateToTips()
        }
    }

    private fun navigateToPractice() {
        val intent = Intent(this, PracticeSession::class.java)
        startActivity(intent)
    }

    private fun navigateToProgress() {
        val intent = Intent(this, Progress::class.java)
        startActivity(intent)
    }

    private fun navigateToTips() {
        val intent = Intent(this, TipsForYou::class.java)
        startActivity(intent)
    }

    private fun handleDrawerNavigation(menuItem: MenuItem) {
        when (menuItem.itemId) {
            // Side drawer menu items (from main_menu.xml)
            R.id.profile -> {
                val intent = Intent(this, EditProfileActivity::class.java)
                startActivity(intent)
            }
            R.id.notification -> {
                val intent = Intent(this, NotificationActivity::class.java)
                startActivity(intent)
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
                startActivity(Intent(this, SignOutConfirmationActivity::class.java))
                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                true
            }

            // Bottom navigation items
            R.id.nav_practice -> {
                navigateToPractice()
            }
            R.id.nav_progress -> {
                navigateToProgress()
            }
            R.id.nav_tips -> {
                navigateToTips()
            }
        }

        // Close the drawer after navigation
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun highlightBottomNavItem(selectedView: View) {
        // Reset all items to normal state
        listOf(navDashboard, navPractice, navProgress, navTips).forEach { view ->
            view.alpha = 0.6f
            // Find TextView in the layout and set normal style
            val textView = (view as? android.view.ViewGroup)?.getChildAt(1) as? android.widget.TextView
            textView?.textSize = 12f
            textView?.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        // Highlight selected item
        selectedView.alpha = 1f
        val selectedTextView = (selectedView as? android.view.ViewGroup)?.getChildAt(1) as? android.widget.TextView
        selectedTextView?.textSize = 12f
        selectedTextView?.setTypeface(null, android.graphics.Typeface.BOLD)
    }


}