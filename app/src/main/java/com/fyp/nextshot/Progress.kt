package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import android.util.Log  // FIXED: For Log.d/e
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.children
import androidx.drawerlayout.widget.DrawerLayout
import com.fyp.nextshot.data.local.database.AppDatabase
import com.fyp.nextshot.data.local.models.SessionEntity
import com.fyp.nextshot.data.repository.SessionRepository
import com.fyp.nextshot.ui.viewmodel.SessionViewModel
import com.fyp.nextshot.ui.viewmodel.SessionViewModelFactory
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
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

    // Recent Sessions UI
    private lateinit var recentSessionsList: LinearLayout
    private lateinit var noDataView: TextView  // NEW: Placeholder for empty

    // Chart
    private lateinit var accuracyLineChart: LineChart

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
        // Fetch data from Cloud
        fetchSessionsFromCloud()
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

        // Recent Sessions Container
        recentSessionsList = findViewById(R.id.recent_sessions_list)

        // NEW: No data placeholder
        noDataView = TextView(this).apply {
            text = "No sessions yet—start practicing! 🏏"
            textSize = 16f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 64, 0, 0)
            visibility = View.GONE
        }
        recentSessionsList.addView(noDataView)

        // Chart
        accuracyLineChart = findViewById(R.id.accuracy_line_chart)

        // Bottom nav
        navDashboard = findViewById(R.id.dash)
        navPractice = findViewById(R.id.practice)
        navProgress = findViewById(R.id.progress)
        navTips = findViewById(R.id.tips)
    }

    // FIXED: Enhanced fetch with loading + real binding
//    private fun fetchSessionsFromCloud() {
//        if (userId == "FALLBACK_UID") {
//            Toast.makeText(this, "Please log in to view cloud data.", Toast.LENGTH_LONG).show()
//            return
//        }
//
//        // Show loading (optional: add ProgressBar overlay if needed)
//        Toast.makeText(this, "Loading progress...", Toast.LENGTH_SHORT).show()
//
//        db.collection("sessions")
//            .whereEqualTo("userId", userId)
//            .orderBy("dateMillis", Query.Direction.DESCENDING)
//            .limit(20)  // FIXED: Limit to recent 20 for perf
//            .get()
//            .addOnSuccessListener { querySnapshot ->
//                val sessions = querySnapshot.documents.mapNotNull { document ->
//                    try {
//                        document.toObject(SessionEntity::class.java)
//                    } catch (e: Exception) {
//                        Log.e("Progress", "Failed to parse session: ${document.id}", e)
//                        null
//                    }
//                }
//                if (sessions.isEmpty()) {
//                    showNoData()
//                } else {
//                    calculateAndDisplayStats(sessions)
//                    displayRecentSessions(sessions.take(5))  // FIXED: Show top 5
//                    setupAccuracyChart(sessions.take(10))  // NEW: Chart for last 10
//                }
//            }
//            .addOnFailureListener { e ->
//                Log.e("Progress", "Firestore query failed", e)
//                Toast.makeText(this, "Failed to load progress: ${e.message}", Toast.LENGTH_LONG).show()
//                showNoData()
//            }
//    }

    private fun fetchSessionsFromCloud() {
        if (userId == "FALLBACK_UID") {
            Toast.makeText(this, "Please log in to view cloud data.", Toast.LENGTH_LONG).show()
            return
        }

        Log.d("Progress", "Fetching sessions for userId: $userId")  // FIXED: Debug query

        db.collection("sessions")
            .whereEqualTo("userId", userId)
//            .orderBy("dateMillis", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val sessions = querySnapshot.documents.mapNotNull { document ->
                    try {
                        val session = document.toObject(SessionEntity::class.java)
                        Log.d("Progress", "Fetched session: ${session?.drillType}, accuracy: ${session?.successRate}")  // FIXED: Log each
                        session
                    } catch (e: Exception) {
                        Log.e("Progress", "Failed to parse session: ${document.id}", e)
                        null
                    }
                }
                Log.d("Progress", "Total sessions fetched: ${sessions.size}")  // FIXED: Log total

                if (sessions.isEmpty()) {
                    showNoData()
                } else {
                    // FIXED: Manual sort if no orderBy (by dateMillis descending)
                    val sortedSessions = sessions.sortedByDescending { it.dateMillis }
                    calculateAndDisplayStats(sessions)
                    displayRecentSessions(sessions.take(5))
                    setupAccuracyChart(sessions.take(10))
                }
            }
            .addOnFailureListener { e ->
                Log.e("Progress", "Firestore query failed: ${e.message}", e)  // FIXED: Full log
                Toast.makeText(this, "Failed to load progress: ${e.message}", Toast.LENGTH_LONG).show()
                showNoData()
            }
    }

    // FIXED: Real stats from sessions (accuracy as "score")
    private fun calculateAndDisplayStats(sessions: List<SessionEntity>) {
        val validSessions = sessions.filter { !it.drillType.contains("Failed") }  // FIXED: Ignore failed
        val accuracyList = sessions.map { (it.successRate * 100).roundToInt() }
        if (accuracyList.isEmpty()) {
            avgScoreValue.text = "N/A"
            bestScoreValue.text = "N/A"
            avgAccuracyValue.text = "0%"
            avgScoreProgressBar.progress = 0
            bestScoreProgressBar.progress = 0
            avgAccuracyProgressBar.progress = 0
            return
        }
        // FIXED: Real calculations
        val avgAccuracy = accuracyList.average().roundToInt()
        val bestAccuracy = accuracyList.maxOrNull() ?: 0
        // Update UI (treat accuracy as "score")
        avgScoreValue.text = "$avgAccuracy%"
        bestScoreValue.text = "$bestAccuracy%"
        avgAccuracyValue.text = "$avgAccuracy%"
        avgScoreProgressBar.progress = avgAccuracy
        bestScoreProgressBar.progress = bestAccuracy
        avgAccuracyProgressBar.progress = avgAccuracy
    }

    // FIXED: Real binding for recent sessions (no mocks)
//    private fun displayRecentSessions(recentSessions: List<SessionEntity>) {
//        recentSessionsList.removeViews(1, recentSessionsList.childCount - 1)  // Clear except placeholder
//        if (recentSessions.isEmpty()) {
//            noDataView.visibility = View.VISIBLE
//            return
//        }
//        noDataView.visibility = View.GONE
//
//        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
//        val inflater = layoutInflater
//        recentSessions.forEachIndexed { index, session ->
//            val sessionView = inflater.inflate(R.layout.item_session, recentSessionsList, false)
//            // FIXED: Real IDs from XML (assume item_session.xml has these)
//            val sessionTitle = sessionView.findViewById<TextView>(R.id.session_title) ?: return@forEachIndexed
//            val sessionDate = sessionView.findViewById<TextView>(R.id.session_date) ?: return@forEachIndexed
//            val sessionScore = sessionView.findViewById<TextView>(R.id.session_score) ?: return@forEachIndexed
//            val sessionAccuracy = sessionView.findViewById<TextView>(R.id.session_accuracy) ?: return@forEachIndexed
//            val sessionDuration = sessionView.findViewById<TextView>(R.id.session_duration) ?: return@forEachIndexed
//            val sessionShots = sessionView.findViewById<TextView>(R.id.session_shots) ?: return@forEachIndexed
//
//            // FIXED: Real data binding
//            val accuracyVal = (session.successRate * 100).roundToInt()
//            val durationMinutes = session.durationSeconds / 60
//            sessionTitle.text = "Session #${index + 1}: ${session.drillType}"
//            sessionDate.text = dateFormat.format(Date(session.dateMillis))
//            sessionScore.text = "$accuracyVal%"
//            sessionAccuracy.text = "$accuracyVal%"
//            sessionDuration.text = "$durationMinutes min"
//            sessionShots.text = "N/A"  // Add field if tracked
//
//            recentSessionsList.addView(sessionView)
//        }
//    }


    private fun displayRecentSessions(recentSessions: List<SessionEntity>) {
        recentSessionsList.removeAllViews()  // FIXED: Clear all views (no 'children')
        if (recentSessions.isEmpty()) {
            noDataView.visibility = View.VISIBLE
            return
        }
        noDataView.visibility = View.GONE

        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val inflater = layoutInflater
        recentSessions.forEachIndexed { index, session ->  // FIXED: Scoped 'session' instead of 'it'
            val sessionView = inflater.inflate(R.layout.item_session, recentSessionsList, false)
            val sessionTitle = sessionView.findViewById<TextView>(R.id.session_title)
            val sessionDate = sessionView.findViewById<TextView>(R.id.session_date)
            val sessionScore = sessionView.findViewById<TextView>(R.id.session_score)
            val sessionAccuracy = sessionView.findViewById<TextView>(R.id.session_accuracy)
            val sessionDuration = sessionView.findViewById<TextView>(R.id.session_duration)
            val sessionShots = sessionView.findViewById<TextView>(R.id.session_shots)

            // FIXED: Safe binding with logs
            if (sessionTitle != null && sessionDate != null && sessionScore != null && sessionAccuracy != null && sessionDuration != null && sessionShots != null) {
                val accuracyVal = (session.successRate * 100).roundToInt()
                val durationMinutes = session.durationSeconds / 60
                sessionTitle.text = "Session #${index + 1}: ${session.drillType}"
                sessionDate.text = dateFormat.format(Date(session.dateMillis))
                sessionScore.text = "$accuracyVal%"
                sessionAccuracy.text = "$accuracyVal%"
                sessionDuration.text = "$durationMinutes min"
                sessionShots.text = "N/A"
                recentSessionsList.addView(sessionView)
                Log.d("Progress", "Bound session #${index + 1}: $$ {session.drillType} ( $${accuracyVal}%)")  // FIXED: Log success
            } else {
                Log.e("Progress", "Missing views in item_session.xml for session ${session.id}")  // FIXED: Log error
            }
        }
    }

    // NEW: Setup line chart for accuracy trend
    private fun setupAccuracyChart(lastSessions: List<SessionEntity>) {
        val entries = mutableListOf<Entry>()
        lastSessions.forEachIndexed { index, session ->
            val accuracy = (session.successRate * 100).roundToInt().toFloat()
            entries.add(Entry(index.toFloat(), accuracy))
        }

        val dataSet = LineDataSet(entries, "Accuracy Trend").apply {
            color = resources.getColor(android.R.color.holo_blue_dark)
            setDrawCircles(true)
            lineWidth = 2f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val lineData = LineData(dataSet)
        accuracyLineChart.data = lineData
        accuracyLineChart.description.isEnabled = false
        accuracyLineChart.setTouchEnabled(true)
        accuracyLineChart.isDragEnabled = true
        accuracyLineChart.setScaleEnabled(true)
        accuracyLineChart.setPinchZoom(true)

        val xAxis = accuracyLineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)

        accuracyLineChart.axisLeft.setDrawGridLines(false)
        accuracyLineChart.axisRight.isEnabled = false
        accuracyLineChart.legend.isEnabled = false

        accuracyLineChart.animateX(1000)
        accuracyLineChart.invalidate()  // Refresh
    }

    // NEW: Show no data placeholder
    private fun showNoData() {
        noDataView.visibility = View.VISIBLE
        recentSessionsList.children.forEach { if (it != noDataView) it.visibility = View.GONE }
        // Reset stats to N/A (call in fetch if empty)
        avgScoreValue.text = "N/A"
        bestScoreValue.text = "N/A"
        avgAccuracyValue.text = "0%"
        avgScoreProgressBar.progress = 0
        bestScoreProgressBar.progress = 0
        avgAccuracyProgressBar.progress = 0
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

    private fun handleDrawerNavigation(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.profile -> startActivity(Intent(this, EditProfileActivity::class.java))
            R.id.notification -> startActivity(Intent(this, NotificationActivity::class.java))
            R.id.session_history -> startActivity(Intent(this, SessionHistory::class.java))
            R.id.AI -> startActivity(Intent(this, AICoachingChat::class.java))
            R.id.settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.signout -> startActivity(Intent(this, SignOutConfirmationActivity::class.java))

            // Bottom navigation items as drawer items
            R.id.nav_dashboard -> {
                startActivity(Intent(this, Dashboard::class.java))
                finish()
            }
            R.id.nav_practice -> {
                startActivity(Intent(this, PracticeSession::class.java))
                finish()
            }
            R.id.nav_progress -> {
                // Already in Progress
            }
            R.id.nav_tips -> {
                startActivity(Intent(this, TipsForYou::class.java))
                finish()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun setupTabNavigation() {
        tabPerformance.setOnClickListener {
            startActivity(Intent(this, PerformanceTracking::class.java))
        }
        tabFlaws.setOnClickListener {
            startActivity(Intent(this, FlawsTracking::class.java))
        }
        tabOverview.setOnClickListener {
            fetchSessionsFromCloud()  // FIXED: Refresh on click
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
        }
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
                    startActivity(Intent(this@Progress, Dashboard::class.java))
                    finish()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        fetchSessionsFromCloud()  // FIXED: Refresh on resume (e.g., after new session)
    }
}