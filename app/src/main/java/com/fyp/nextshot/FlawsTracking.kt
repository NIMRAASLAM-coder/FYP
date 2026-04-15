package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FlawsTracking : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var topProfileImage: ImageView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // Bottom navigation
    private lateinit var navDashboard: View
    private lateinit var navPractice: View
    private lateinit var navProgress: View
    private lateinit var navTips: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flaws_tracking)

        // Initialize drawer and nav view
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        topProfileImage = findViewById(R.id.profile_image)
        toolbar = findViewById(R.id.menu)

        setupToolbarAndDrawer()

        // Initialize bottom navigation views
        navDashboard = findViewById(R.id.nav_dashboard)
        navPractice = findViewById(R.id.nav_practice)
        navProgress = findViewById(R.id.nav_progress)
        navTips = findViewById(R.id.nav_tips)

        // Setup tabs and bottom navigation
        setupTabs()
        setupBottomNavigation()
        loadUserData()

        // Highlight current page
        highlightBottomNavItem(navProgress)
        
        topProfileImage.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
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
        navView.setNavigationItemSelectedListener { menuItem ->
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
                        val headerView = navView.getHeaderView(0)
                        val headerProfileImage = headerView.findViewById<ImageView>(R.id.profile_image)
                        val headerUserName = headerView.findViewById<TextView>(R.id.user_name)
                        val headerUserEmail = headerView.findViewById<TextView>(R.id.user_email)
                        val headerUserAge = headerView.findViewById<TextView>(R.id.user_age)
                        val headerUserExperience = headerView.findViewById<TextView>(R.id.user_experience)
                        
                        headerUserName.text = it.fullName ?: "Player"
                        headerUserEmail.text = it.email ?: auth.currentUser?.email
                        headerUserAge.text = ProfileUtils.calculateAge(it.dob)
                        headerUserExperience.text = it.experienceLevel ?: "Experience: N/A"

                        ProfileUtils.loadProfileImage(this, it.profileImageUrl, headerProfileImage, R.drawable.img_21)
                    }
                }
            }
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

    private fun highlightBottomNavItem(selectedView: View) {
        listOf(navDashboard, navPractice, navProgress, navTips).forEach { view ->
            view.isActivated = (view == selectedView)
            val textView = (view as? android.view.ViewGroup)?.getChildAt(1) as? android.widget.TextView
            val imageView = (view as? android.view.ViewGroup)?.getChildAt(0) as? android.widget.ImageView
            
            if (view == selectedView) {
                textView?.setTypeface(null, android.graphics.Typeface.BOLD)
                textView?.setTextColor(getColor(R.color.white))
                imageView?.setColorFilter(getColor(R.color.white))
                textView?.alpha = 1f
                imageView?.alpha = 1f
            } else {
                textView?.setTypeface(null, android.graphics.Typeface.NORMAL)
                textView?.setTextColor(getColor(R.color.white))
                textView?.alpha = 0.7f
                imageView?.setColorFilter(getColor(R.color.white))
                imageView?.alpha = 0.7f
            }
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
            R.id.profile -> startActivity(Intent(this, EditProfileActivity::class.java))
            R.id.notification -> startActivity(Intent(this, NotificationActivity::class.java))
            R.id.session_history -> startActivity(Intent(this, SessionHistory::class.java))
            R.id.AI -> startActivity(Intent(this, AICoachingChat::class.java))
            R.id.settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.signout -> startActivity(Intent(this, SignOutConfirmationActivity::class.java))
        }
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }
}