package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.forEachIndexed
import kotlin.math.roundToInt
import com.fyp.nextshot.data.local.models.SessionEntity

class PerformanceTracking : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView

    // Firebase
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val userId = auth.currentUser?.uid ?: "FALLBACK_UID"

    // Charts
    private lateinit var scoreLineChart: LineChart
    private lateinit var accuracyBarChart: BarChart

    // Bottom navigation
    private lateinit var navDashboard: LinearLayout
    private lateinit var navPractice: LinearLayout
    private lateinit var navProgress: LinearLayout
    private lateinit var navTips: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_performance_tracking)

        // Initialize drawer and nav view
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        // Menu icon click opens drawer
        val menuToolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.menu)
        menuToolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // --- Tab Navigation (top section) ---
        val tabOverview = findViewById<MaterialButton>(R.id.tab_overview)
        val tabPerformance = findViewById<MaterialButton>(R.id.tab_performance)
        val tabFlaws = findViewById<MaterialButton>(R.id.tab_flaws)

        // Overview tab → Progress screen
        tabOverview.setOnClickListener {
            startActivity(Intent(this, Progress::class.java))
            finish()
        }

        // Performance tab → current screen (refresh data)
        tabPerformance.setOnClickListener {
            fetchPerformanceData()  // FIXED: Refresh on click
        }

        // Flaws tab → FlawsTracking screen
        tabFlaws.setOnClickListener {
            startActivity(Intent(this, FlawsTracking::class.java))
            finish()
        }

        // --- Charts ---
        scoreLineChart = findViewById(R.id.score_line_chart)
        accuracyBarChart = findViewById(R.id.accuracy_bar_chart)

        // --- Bottom Navigation (footer section) ---
        navDashboard = findViewById(R.id.dash)
        navPractice = findViewById(R.id.practice)
        navProgress = findViewById(R.id.progress)
        navTips = findViewById(R.id.tips)

        // Dashboard
        navDashboard.setOnClickListener {
            startActivity(Intent(this, Dashboard::class.java))
            finish()
        }

        // Practice Session
        navPractice.setOnClickListener {
            startActivity(Intent(this, PracticeSession::class.java))
            finish()
        }

        // Progress
        navProgress.setOnClickListener {
            startActivity(Intent(this, Progress::class.java))
            finish()
        }

        // Tips For You
        navTips.setOnClickListener {
            startActivity(Intent(this, TipsForYou::class.java))
            finish()
        }

        setupNavigationDrawer()
        // FIXED: Fetch real data on load
        fetchPerformanceData()
    }

    private fun fetchPerformanceData() {
        if (userId == "FALLBACK_UID") {
            Toast.makeText(this, "Please log in to view performance data.", Toast.LENGTH_LONG).show()
            return
        }

        Log.d("Performance", "Fetching sessions for userId: $userId")

        db.collection("sessions")
            .whereEqualTo("userId", userId)
            // FIXED: Temp comment orderBy (no index; re-add after billing)
            // .orderBy("dateMillis", Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .addOnSuccessListener { querySnapshot ->
                var sessions = querySnapshot.documents.mapNotNull { document ->
                    try {
                        val session = document.toObject(SessionEntity::class.java)
                        Log.d("Performance", "Fetched: ${session?.drillType}, accuracy: ${session?.successRate}")
                        session
                    } catch (e: Exception) {
                        Log.e("Performance", "Parse failed: ${document.id}", e)
                        null
                    }
                }
                Log.d("Performance", "Total sessions: ${sessions.size}")

                if (sessions.isEmpty()) {
                    showNoData()
                } else {
                    // FIXED: Manual sort for charts (by dateMillis desc)
                    sessions = sessions.sortedByDescending { it.dateMillis }
                    setupScoreProgressionChart(sessions)
                    setupAccuracyTrendsChart(sessions)
                }
            }
            .addOnFailureListener { e ->
                Log.e("Performance", "Query failed: ${e.message}", e)
                Toast.makeText(this, "Failed to load performance: ${e.message}", Toast.LENGTH_LONG).show()
                showNoData()
            }
    }

//    // FIXED: Fetch real sessions from Firestore
//    private fun fetchPerformanceData() {
//        if (userId == "FALLBACK_UID") {
//            Toast.makeText(this, "Please log in to view performance data.", Toast.LENGTH_LONG).show()
//            return
//        }
//
//        Log.d("Performance", "Fetching sessions for userId: $userId")
//
//        db.collection("sessions")
//            .whereEqualTo("userId", userId)
////            .orderBy("dateMillis", Query.Direction.DESCENDING)
//            .limit(20)  // Recent 20 for perf
//            .get()
//            .addOnSuccessListener { querySnapshot ->
//                val sessions = querySnapshot.documents.mapNotNull { document ->
//                    try {
//                        val session = document.toObject(SessionEntity::class.java)
//                        Log.d("Performance", "Fetched: ${session?.drillType}, accuracy: ${session?.successRate}")
//                        session
//                    } catch (e: Exception) {
//                        Log.e("Performance", "Parse failed: ${document.id}", e)
//                        null
//                    }
//                }
//                Log.d("Performance", "Total sessions: ${sessions.size}")
//
//                if (sessions.isEmpty()) {
//                    showNoData()
//                } else {
//                    // FIXED: Manual sort for charts (by dateMillis desc)
//                    sessions = sessions.sortedByDescending { it.dateMillis }
//                    setupScoreProgressionChart(sessions)
//                    setupAccuracyTrendsChart(sessions)
//                }
//            }
//            .addOnFailureListener { e ->
//                Log.e("Performance", "Query failed: ${e.message}", e)
//                Toast.makeText(this, "Failed to load performance: ${e.message}", Toast.LENGTH_LONG).show()
//                showNoData()
//            }
//    }

    // FIXED: Line chart for score progression (successRate over time)
    private fun setupScoreProgressionChart(sessions: List<SessionEntity>) {
        val entries = mutableListOf<Entry>()
        sessions.forEachIndexed { index, session ->
            val accuracy = (session.successRate * 100).roundToInt().toFloat()
            entries.add(Entry(index.toFloat(), accuracy))
        }

        val dataSet = LineDataSet(entries, "Score Progression").apply {
            color = resources.getColor(android.R.color.holo_blue_dark)
            setDrawCircles(true)
            lineWidth = 2f
            circleRadius = 4f
            setDrawCircleHole(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillColor = resources.getColor(android.R.color.holo_blue_light)
            fillAlpha = 50
        }

        val lineData = LineData(dataSet)
        scoreLineChart.data = lineData
        scoreLineChart.description.isEnabled = false
        scoreLineChart.setTouchEnabled(true)
        scoreLineChart.isDragEnabled = true
        scoreLineChart.setScaleEnabled(true)
        scoreLineChart.setPinchZoom(true)

        val xAxis = scoreLineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = IndexAxisValueFormatter(sessions.map { SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it.dateMillis)) })

        scoreLineChart.axisLeft.axisMinimum = 0f
        scoreLineChart.axisLeft.axisMaximum = 100f
        scoreLineChart.axisLeft.setDrawGridLines(true)
        scoreLineChart.axisRight.isEnabled = false
        scoreLineChart.legend.isEnabled = false

        scoreLineChart.animateX(1000)
        scoreLineChart.invalidate()

        if (entries.isEmpty()) {
            Log.w("Performance", "No entries for chart—check sessions")
            return
        }

    }

    // FIXED: Bar chart for accuracy by drillType (avg per type)
    private fun setupAccuracyTrendsChart(sessions: List<SessionEntity>) {
        val drillTypes = sessions.groupBy { it.drillType }.mapKeys { it.key.take(20) }  // Shorten labels
        val entries = mutableListOf<BarEntry>()
        drillTypes.entries.forEachIndexed { index, (type, typeSessions) ->
            val avgAccuracy = typeSessions.map { it.successRate * 100 }.average().roundToInt().toFloat()
            entries.add(BarEntry(index.toFloat(), avgAccuracy))
        }

        val dataSet = BarDataSet(entries, "Accuracy by Drill Type").apply {
            color = resources.getColor(android.R.color.holo_green_dark)
            barBorderWidth = 0f
            setDrawValues(false)
        }

        val barData = BarData(dataSet)
        accuracyBarChart.data = barData
        accuracyBarChart.description.isEnabled = false
        accuracyBarChart.setFitBars(true)
        accuracyBarChart.axisLeft.axisMinimum = 0f
        accuracyBarChart.axisLeft.axisMaximum = 100f
        accuracyBarChart.axisRight.isEnabled = false
        accuracyBarChart.xAxis.valueFormatter = IndexAxisValueFormatter(drillTypes.keys.toList())
        accuracyBarChart.xAxis.setDrawGridLines(false)
        accuracyBarChart.legend.isEnabled = false

        accuracyBarChart.animateY(1000)
        accuracyBarChart.invalidate()
    }

    // FIXED: Show no data state
    private fun showNoData() {
        // Hide charts or set empty data
        scoreLineChart.data = LineData()  // Empty line
        accuracyBarChart.data = BarData()  // Empty bar
        scoreLineChart.invalidate()
        accuracyBarChart.invalidate()

        Toast.makeText(this, "No performance data yet—start practicing! 🏏", Toast.LENGTH_LONG).show()
    }

    // ----- DRAWER MENU HANDLING -----
    private fun setupNavigationDrawer() {
        navView.setNavigationItemSelectedListener { menuItem ->
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
        }
        drawerLayout.closeDrawer(GravityCompat.START)
    }
}