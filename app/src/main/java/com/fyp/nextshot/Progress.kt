package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class Progress : AppCompatActivity() {

    // --- Architecture Initialization ---
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val userId = auth.currentUser?.uid ?: "FALLBACK_UID"

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { SessionRepository(database.sessionDao(), userId, db) }
    private val sessionViewModel: SessionViewModel by viewModels {
        SessionViewModelFactory(repository)
    }

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
    private lateinit var avgScoreProgressBar: ProgressBar
    private lateinit var bestScoreProgressBar: ProgressBar
    private lateinit var avgAccuracyProgressBar: ProgressBar
    private lateinit var topProfileImage: ImageView

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

        // Highlight current page
        highlightBottomNavItem(navProgress)

        // Fetch data
        fetchSessionsFromCloud()
        loadUserData()
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

        // Progress Bars
        avgScoreProgressBar = findViewById(R.id.avg_score_progress_bar)
        bestScoreProgressBar = findViewById(R.id.best_score_progress_bar)
        avgAccuracyProgressBar = findViewById(R.id.avg_accuracy_progress_bar)
        topProfileImage = findViewById(R.id.profile_image)

        // Bottom nav
        navDashboard = findViewById(R.id.nav_dashboard)
        navPractice = findViewById(R.id.nav_practice)
        navProgress = findViewById(R.id.nav_progress)
        navTips = findViewById(R.id.nav_tips)
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

    private fun fetchSessionsFromCloud() {
        if (userId == "FALLBACK_UID") {
            Toast.makeText(this, "Please log in to view cloud data.", Toast.LENGTH_LONG).show()
            return
        }

        db.collection("sessions")
            .whereEqualTo("userId", userId)
            .orderBy("dateMillis", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val sessions = querySnapshot.documents.mapNotNull { document ->
                    document.toObject(SessionEntity::class.java)
                }
                calculateAndDisplayStats(sessions)
                displayRecentSessions(sessions.take(3))
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load progress: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun calculateAndDisplayStats(sessions: List<SessionEntity>) {
        if (sessions.isEmpty()) {
            avgScoreValue.text = "N/A"
            bestScoreValue.text = "N/A"
            avgAccuracyValue.text = "0%"
            avgScoreProgressBar.progress = 0
            bestScoreProgressBar.progress = 0
            avgAccuracyProgressBar.progress = 0
            return
        }

        val accuracyList = sessions.map { (it.successRate * 100) }

        // Calculate Stats
        val avgAccuracy = accuracyList.average().roundToInt()
        val bestAccuracy = accuracyList.maxOrNull()?.roundToInt() ?: 0

        // Update UI
        avgScoreValue.text = avgAccuracy.toString()
        bestScoreValue.text = bestAccuracy.toString()
        avgAccuracyValue.text = "$avgAccuracy%"

        avgScoreProgressBar.progress = avgAccuracy
        bestScoreProgressBar.progress = bestAccuracy
        avgAccuracyProgressBar.progress = avgAccuracy
    }

    private fun displayRecentSessions(recentSessions: List<SessionEntity>) {
        val recentSessionsList = findViewById<LinearLayout>(R.id.recent_sessions_list) ?: return
        recentSessionsList.removeAllViews()

        val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        val inflater = layoutInflater

        recentSessions.forEachIndexed { index, session ->
            val sessionView = inflater.inflate(R.layout.item_session, recentSessionsList, false)

            val sessionTitle = sessionView.findViewById<TextView>(R.id.session_title)
            val sessionDate = sessionView.findViewById<TextView>(R.id.session_date)
            val sessionScore = sessionView.findViewById<TextView>(R.id.session_score)
            val sessionAccuracy = sessionView.findViewById<TextView>(R.id.session_accuracy)
            val sessionDuration = sessionView.findViewById<TextView>(R.id.session_duration)
            val sessionShots = sessionView.findViewById<TextView>(R.id.session_shots)
            val analysisSummaryLayout = sessionView.findViewById<LinearLayout>(R.id.analysis_summary_layout)
            val tvAnalysisDetails = sessionView.findViewById<TextView>(R.id.tv_analysis_details)

            val accuracyVal = (session.successRate * 100).roundToInt()
            val durationMinutes = session.durationSeconds / 60

            sessionTitle.text = "Session #${index + 1}: ${session.drillType}"
            sessionDate.text = dateFormat.format(Date(session.dateMillis))
            sessionScore.text = accuracyVal.toString()
            sessionAccuracy.text = "$accuracyVal%"
            sessionDuration.text = durationMinutes.toString()
            sessionShots.text = "N/A"

            if (session.drillType == "Pose Analysis" && session.flawDetails != null) {
                analysisSummaryLayout.visibility = View.VISIBLE
                tvAnalysisDetails.text = session.flawDetails
            } else {
                analysisSummaryLayout.visibility = View.GONE
            }

            recentSessionsList.addView(sessionView)
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

        navigationView.setNavigationItemSelectedListener { menuItem ->
            handleDrawerNavigation(menuItem)
            true
        }
        
        topProfileImage.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
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
                // Already here
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

    private fun setupTabNavigation() {
        tabPerformance.setOnClickListener {
            startActivity(Intent(this, PerformanceTracking::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        tabFlaws.setOnClickListener {
            startActivity(Intent(this, FlawsTracking::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        tabOverview.setOnClickListener {
            // Already here
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
            // Already here
            highlightBottomNavItem(navProgress)
        }
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

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    startActivity(Intent(this@Progress, Dashboard::class.java))
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        fetchSessionsFromCloud()
        loadUserData()
    }
}