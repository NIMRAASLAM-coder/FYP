package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
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
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        findViewById<MaterialButton>(R.id.tab_flaws).setOnClickListener {
            startActivity(Intent(this, FlawsTracking::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        scoreLineChart = findViewById(R.id.score_line_chart)
        accuracyBarChart = findViewById(R.id.accuracy_bar_chart)

        navDashboard = findViewById(R.id.nav_dashboard)
        navPractice = findViewById(R.id.nav_practice)
        navProgress = findViewById(R.id.nav_progress)
        navTips = findViewById(R.id.nav_tips)

        setupBottomNavigation()
        fetchPerformanceData()
        loadUserData()
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

        navView.setNavigationItemSelectedListener { menuItem ->
            handleDrawerNavigation(menuItem)
            true
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

    private fun setupBottomNavigation() {
        navDashboard.setOnClickListener { startActivity(Intent(this, Dashboard::class.java)); finish() }
        navPractice.setOnClickListener { startActivity(Intent(this, PracticeSession::class.java)); finish() }
        navProgress.setOnClickListener { 
             startActivity(Intent(this, Progress::class.java))
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
                textView?.alpha = 1f
                imageView?.alpha = 1f
            } else {
                textView?.setTypeface(null, android.graphics.Typeface.NORMAL)
                textView?.alpha = 0.7f
                imageView?.alpha = 0.7f
            }
        }
    }

    private fun fetchPerformanceData() {
        if (userId == "FALLBACK_UID") return
        db.collection("sessions").whereEqualTo("userId", userId).limit(20).get()
            .addOnSuccessListener { querySnapshot ->
                val sessions = querySnapshot.documents.mapNotNull { it.toObject(SessionEntity::class.java) }
                if (sessions.isEmpty()) { showNoData() } 
                else {
                    val sorted = sessions.sortedBy { it.dateMillis }
                    setupScoreProgressionChart(sorted)
                    setupAccuracyTrendsChart(sorted)
                }
            }
    }

    private fun setupScoreProgressionChart(sessions: List<SessionEntity>) {
        val entries = sessions.mapIndexed { index, session -> 
            Entry(index.toFloat(), (session.successRate * 100).toFloat()) 
        }
        val dataSet = LineDataSet(entries, "Score").apply {
            color = getColor(R.color.words_blue)
            setCircleColor(getColor(R.color.words_blue))
            lineWidth = 2f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawValues(false)
        }
        scoreLineChart.data = LineData(dataSet)
        scoreLineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        scoreLineChart.xAxis.valueFormatter = IndexAxisValueFormatter(sessions.map { 
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it.dateMillis)) 
        })
        scoreLineChart.invalidate()
    }

    private fun setupAccuracyTrendsChart(sessions: List<SessionEntity>) {
        val drillTypes = sessions.groupBy { it.drillType ?: "General" }
        val entries = drillTypes.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.map { it.successRate * 100 }.average().toFloat())
        }
        val dataSet = BarDataSet(entries, "Accuracy").apply { color = getColor(R.color.light_blue) }
        accuracyBarChart.data = BarData(dataSet)
        accuracyBarChart.xAxis.valueFormatter = IndexAxisValueFormatter(drillTypes.keys.toList())
        accuracyBarChart.invalidate()
    }

    private fun showNoData() {
        scoreLineChart.data = null
        accuracyBarChart.data = null
        scoreLineChart.invalidate()
        accuracyBarChart.invalidate()
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { document ->
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

    private fun setupNavigationDrawer() {
        // This is now handled in setupToolbarAndDrawer()
    }
}