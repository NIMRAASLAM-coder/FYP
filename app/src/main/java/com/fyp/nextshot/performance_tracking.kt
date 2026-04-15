package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PerformanceTracking : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var topProfileImage: ImageView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val userId = auth.currentUser?.uid ?: "FALLBACK_UID"

    private lateinit var scoreLineChart: LineChart
    private lateinit var accuracyBarChart: BarChart

    private lateinit var navDashboard: View
    private lateinit var navPractice: View
    private lateinit var navProgress: View
    private lateinit var navTips: View

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { SessionRepository(database.sessionDao(), userId, db) }
    private val sessionViewModel: SessionViewModel by viewModels {
        SessionViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_performance_tracking)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        topProfileImage = findViewById(R.id.profile_image)
        toolbar = findViewById(R.id.menu)

        setupToolbarAndDrawer()

        findViewById<MaterialButton>(R.id.tab_overview).setOnClickListener {
            startActivity(Intent(this, Progress::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
        findViewById<MaterialButton>(R.id.tab_flaws).setOnClickListener {
            startActivity(Intent(this, FlawsTracking::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        scoreLineChart = findViewById(R.id.score_line_chart)
        accuracyBarChart = findViewById(R.id.accuracy_bar_chart)

        navDashboard = findViewById(R.id.nav_dashboard)
        navPractice = findViewById(R.id.nav_practice)
        navProgress = findViewById(R.id.nav_progress)
        navTips = findViewById(R.id.nav_tips)

        setupBottomNavigation()
        observeSessionData()
        loadUserData()
        highlightBottomNavItem(navProgress)
        
        topProfileImage.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    private fun observeSessionData() {
        sessionViewModel.allSessions.observe(this) { sessions ->
            if (sessions.isNullOrEmpty()) {
                showNoData()
            } else {
                val analyzedSessions = sessions.filter { it.drillType == "Pose Analysis" }
                    .sortedBy { it.dateMillis }
                
                if (analyzedSessions.isNotEmpty()) {
                    setupScoreProgressionChart(analyzedSessions.takeLast(15))
                    setupAccuracyTrendsChart(analyzedSessions)
                } else {
                    showNoData()
                }
            }
        }
    }

    private fun setupScoreProgressionChart(sessions: List<SessionEntity>) {
        val entries = sessions.mapIndexed { index, session -> 
            Entry(index.toFloat(), (session.successRate * 100).toFloat()) 
        }
        val dataSet = LineDataSet(entries, "Overall Stability %").apply {
            color = getColor(R.color.words_blue)
            setCircleColor(getColor(R.color.words_blue))
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawCircleHole(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(true)
            valueTextSize = 8f
        }
        
        scoreLineChart.data = LineData(dataSet)
        scoreLineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        scoreLineChart.xAxis.setDrawGridLines(false)
        scoreLineChart.xAxis.valueFormatter = IndexAxisValueFormatter(sessions.map { 
            SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(it.dateMillis)) 
        })
        scoreLineChart.axisRight.isEnabled = false
        scoreLineChart.description.isEnabled = false
        scoreLineChart.animateX(1000)
        scoreLineChart.invalidate()
    }

    private fun setupAccuracyTrendsChart(sessions: List<SessionEntity>) {
        var headSum = 0f
        var shoulderSum = 0f
        var weightSum = 0f
        var feetSum = 0f
        var count = 0

        sessions.forEach { session ->
            val details = session.flawDetails
            if (!details.isNullOrEmpty()) {
                headSum += extractValue(details, "Head:")
                shoulderSum += extractValue(details, "Shoulders:")
                weightSum += extractValue(details, "Weight:")
                feetSum += extractValue(details, "Feet:")
                count++
            }
        }

        if (count == 0) return

        val labels = listOf("Head", "Shoulders", "Weight", "Feet")
        val averages = listOf(headSum / count, shoulderSum / count, weightSum / count, feetSum / count)

        val entries = averages.mapIndexed { index, value -> BarEntry(index.toFloat(), value) }
        val dataSet = BarDataSet(entries, "Metric Performance %").apply {
            colors = listOf(
                getColor(R.color.words_blue),
                getColor(R.color.light_blue),
                getColor(R.color.words_blue),
                getColor(R.color.light_blue)
            )
            setDrawValues(true)
            valueTextSize = 10f
        }

        accuracyBarChart.data = BarData(dataSet)
        accuracyBarChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        accuracyBarChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        accuracyBarChart.xAxis.granularity = 1f
        accuracyBarChart.xAxis.setDrawGridLines(false)
        accuracyBarChart.axisRight.isEnabled = false
        accuracyBarChart.axisLeft.axisMinimum = 0f
        accuracyBarChart.axisLeft.axisMaximum = 105f
        accuracyBarChart.description.isEnabled = false
        accuracyBarChart.legend.isEnabled = false
        accuracyBarChart.animateY(1200)
        accuracyBarChart.invalidate()
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
        val toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener { handleDrawerNavigation(it); true }
    }

    private fun handleDrawerNavigation(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.nav_dashboard -> { startActivity(Intent(this, Dashboard::class.java)); finish() }
            R.id.nav_practice -> { startActivity(Intent(this, PracticeSession::class.java)); finish() }
            R.id.nav_progress -> { 
                startActivity(Intent(this, Progress::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish() 
            }
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

    private fun setupBottomNavigation() {
        navDashboard.setOnClickListener { startActivity(Intent(this, Dashboard::class.java)); finish() }
        navPractice.setOnClickListener { startActivity(Intent(this, PracticeSession::class.java)); finish() }
        navProgress.setOnClickListener { 
            startActivity(Intent(this, Progress::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish() 
        }
        navTips.setOnClickListener { startActivity(Intent(this, TipsForYou::class.java)); finish() }
    }

    private fun highlightBottomNavItem(selectedView: View) {
        listOf(navDashboard, navPractice, navProgress, navTips).forEach { view ->
            view.isActivated = (view == selectedView)
            val textView = (view as? android.view.ViewGroup)?.getChildAt(1) as? TextView
            val imageView = (view as? android.view.ViewGroup)?.getChildAt(0) as? ImageView
            if (view == selectedView) {
                textView?.setTypeface(null, android.graphics.Typeface.BOLD)
                textView?.alpha = 1f; imageView?.alpha = 1f
            } else {
                textView?.setTypeface(null, android.graphics.Typeface.NORMAL)
                textView?.alpha = 0.7f; imageView?.alpha = 0.7f
            }
        }
    }

    private fun showNoData() {
        scoreLineChart.data = null; accuracyBarChart.data = null
        scoreLineChart.invalidate(); accuracyBarChart.invalidate()
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val user = document.toObject(User::class.java)
                user?.let {
                    ProfileUtils.loadProfileImage(this, it.profileImageUrl, topProfileImage, R.drawable.img_7)
                    val headerView = navView.getHeaderView(0)
                    headerView.findViewById<TextView>(R.id.user_name).text = it.fullName ?: "Player"
                    headerView.findViewById<TextView>(R.id.user_email).text = it.email ?: auth.currentUser?.email
                    ProfileUtils.loadProfileImage(this, it.profileImageUrl, headerView.findViewById(R.id.profile_image), R.drawable.img_21)
                }
            }
        }
    }
}