package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.fyp.nextshot.data.local.database.AppDatabase
import com.fyp.nextshot.data.local.models.SessionEntity
import com.fyp.nextshot.data.repository.SessionRepository
import com.fyp.nextshot.ui.viewmodel.SessionViewModel
import com.fyp.nextshot.ui.viewmodel.SessionViewModelFactory
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
    private val userId = auth.currentUser?.uid ?: "FALLBACK_UID"

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { SessionRepository(database.sessionDao(), userId, db) }
    private val sessionViewModel: SessionViewModel by viewModels {
        SessionViewModelFactory(repository)
    }

    private lateinit var progressHead: ProgressBar
    private lateinit var tvHeadPercentage: TextView
    private lateinit var progressShoulders: ProgressBar
    private lateinit var tvShouldersPercentage: TextView
    private lateinit var progressWeight: ProgressBar
    private lateinit var tvWeightPercentage: TextView
    private lateinit var progressFeet: ProgressBar
    private lateinit var tvFeetPercentage: TextView

    private lateinit var navDashboard: View
    private lateinit var navPractice: View
    private lateinit var navProgress: View
    private lateinit var navTips: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flaws_tracking)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        topProfileImage = findViewById(R.id.profile_image)
        toolbar = findViewById(R.id.menu)

        progressHead = findViewById(R.id.progress_head)
        tvHeadPercentage = findViewById(R.id.tv_head_percentage)
        progressShoulders = findViewById(R.id.progress_shoulders)
        tvShouldersPercentage = findViewById(R.id.tv_shoulders_percentage)
        progressWeight = findViewById(R.id.progress_weight)
        tvWeightPercentage = findViewById(R.id.tv_weight_percentage)
        progressFeet = findViewById(R.id.progress_feet)
        tvFeetPercentage = findViewById(R.id.tv_feet_percentage)

        setupToolbarAndDrawer()

        navDashboard = findViewById(R.id.nav_dashboard)
        navPractice = findViewById(R.id.nav_practice)
        navProgress = findViewById(R.id.nav_progress)
        navTips = findViewById(R.id.nav_tips)

        setupTabs()
        setupBottomNavigation()
        loadUserData()
        observeSessionData()

        highlightBottomNavItem(navProgress)
        
        topProfileImage.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    private fun observeSessionData() {
        sessionViewModel.allSessions.observe(this) { sessions ->
            if (!sessions.isNullOrEmpty()) {
                val lastSessions = sessions
                    .filter { it.drillType == "Pose Analysis" && !it.flawDetails.isNullOrEmpty() }
                    .sortedByDescending { it.dateMillis }
                    .take(5)
                
                if (lastSessions.isNotEmpty()) {
                    updateFlawsUI(lastSessions)
                }
            }
        }
    }

    private fun updateFlawsUI(sessions: List<SessionEntity>) {
        var totalHead = 0
        var totalShoulders = 0
        var totalWeight = 0
        var totalFeet = 0

        sessions.forEach { session ->
            val details = session.flawDetails ?: ""
            totalHead += extractValue(details, "Head:")
            totalShoulders += extractValue(details, "Shoulders:")
            totalWeight += extractValue(details, "Weight:")
            totalFeet += extractValue(details, "Feet:")
        }

        val count = sessions.size
        val avgHead = totalHead / count
        val avgShoulders = totalShoulders / count
        val avgWeight = totalWeight / count
        val avgFeet = totalFeet / count

        progressHead.progress = avgHead
        tvHeadPercentage.text = "$avgHead%"
        
        progressShoulders.progress = avgShoulders
        tvShouldersPercentage.text = "$avgShoulders%"
        
        progressWeight.progress = avgWeight
        tvWeightPercentage.text = "$avgWeight%"
        
        progressFeet.progress = avgFeet
        tvFeetPercentage.text = "$avgFeet%"
    }

    private fun extractValue(text: String, key: String): Int {
        return try {
            val part = text.split("|").find { it.trim().startsWith(key) }
            val valueStr = part?.substringAfter(":")?.trim()?.removeSuffix("%")
            valueStr?.toInt() ?: 0
        } catch (e: Exception) {
            0
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

    private fun setupTabs() {
        findViewById<MaterialButton>(R.id.tab_overview).setOnClickListener {
            startActivity(Intent(this, Progress::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        findViewById<MaterialButton>(R.id.tab_performance).setOnClickListener {
            startActivity(Intent(this, PerformanceTracking::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun setupBottomNavigation() {
        navDashboard.setOnClickListener { startActivity(Intent(this, Dashboard::class.java)); finish() }
        navPractice.setOnClickListener { startActivity(Intent(this, PracticeSession::class.java)); finish() }
        navProgress.setOnClickListener { startActivity(Intent(this, Progress::class.java)); finish() }
        navTips.setOnClickListener { startActivity(Intent(this, TipsForYou::class.java)); finish() }
    }

    private fun highlightBottomNavItem(selectedView: View) {
        listOf(navDashboard, navPractice, navProgress, navTips).forEach { view ->
            view.isActivated = (view == selectedView)
            val textView = (view as? android.view.ViewGroup)?.getChildAt(1) as? TextView
            val imageView = (view as? android.view.ViewGroup)?.getChildAt(0) as? ImageView
            
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
            R.id.nav_dashboard -> { startActivity(Intent(this, Dashboard::class.java)); finish() }
            R.id.nav_practice -> { startActivity(Intent(this, PracticeSession::class.java)); finish() }
            R.id.nav_progress -> { startActivity(Intent(this, Progress::class.java)); finish() }
            R.id.nav_tips -> { startActivity(Intent(this, TipsForYou::class.java)); finish() }
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