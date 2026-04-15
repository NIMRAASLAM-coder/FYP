package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import com.google.firebase.firestore.FirebaseFirestore
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyp.nextshot.data.local.database.AppDatabase
import com.fyp.nextshot.data.local.models.SessionEntity
import com.fyp.nextshot.data.repository.SessionRepository
import com.fyp.nextshot.ui.viewmodel.SessionViewModel
import com.fyp.nextshot.ui.viewmodel.SessionViewModelFactory
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SessionHistory : AppCompatActivity() {

    // 1. Core Architecture Components
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val userId = auth.currentUser?.uid ?: "FALLBACK_UID"

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val repository by lazy { SessionRepository(database.sessionDao(), userId, db) }
    private val sessionViewModel: SessionViewModel by viewModels {
        SessionViewModelFactory(repository)
    }

    // 2. View Initialization Properties
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var searchBar: EditText
    private lateinit var dateSpinner: Spinner
    private lateinit var sessionSpinner: Spinner
    private lateinit var sessionsRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var navDashboard: View
    private lateinit var navPractice: View
    private lateinit var navProgress: View
    private lateinit var navTips: View
    private lateinit var profileIcon: ImageView

    // 3. Data Storage
    private lateinit var sessionAdapter: SessionAdapter
    private var allSessions = mutableListOf<SessionEntity>()
    private var filteredSessions = mutableListOf<SessionEntity>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_history)

        initializeViews()
        setupToolbarAndDrawer()
        setupSpinners()
        setupRecyclerView()
        setupSearchBar()
        setupBottomNavigation()

        // Load data
        observeSessions()
        loadUserData()
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.menu)

        searchBar = findViewById(R.id.search_bar)
        dateSpinner = findViewById(R.id.date_spinner)
        sessionSpinner = findViewById(R.id.session_spinner)
        sessionsRecyclerView = findViewById(R.id.sessions_recycler_view)
        emptyState = findViewById(R.id.empty_state)
        profileIcon = findViewById(R.id.profile_icon)

        // Bottom navigation
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
                        // Load into Top Bar Icon
                        ProfileUtils.loadProfileImage(this, it.profileImageUrl, profileIcon, R.drawable.ic_person)
                        
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

        profileIcon.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
    }

    private fun setupSpinners() {
        val dateFilters = arrayOf("All Dates", "Today", "This Week", "This Month", "Last Month")
        val dateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, dateFilters)
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dateSpinner.adapter = dateAdapter

        dateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterSessions()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val sessionFilters = arrayOf("All Sessions", "Drills", "Assessment", "High Score", "Low Accuracy")
        val sessionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sessionFilters)
        sessionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sessionSpinner.adapter = sessionAdapter

        sessionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterSessions()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        sessionAdapter = SessionAdapter(
            sessions = filteredSessions,
            onViewAnalysisClick = { session ->
                Toast.makeText(this, "Viewing analysis for ${session.drillType}", Toast.LENGTH_SHORT).show()
            },
            onShareClick = { session ->
                shareSession(session)
            }
        )

        sessionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@SessionHistory)
            adapter = sessionAdapter
        }
    }

    private fun setupSearchBar() {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterSessions()
            }
        })
    }

    private fun setupBottomNavigation() {
        navDashboard.setOnClickListener { startActivity(Intent(this, Dashboard::class.java)) }
        navPractice.setOnClickListener { startActivity(Intent(this, PracticeSession::class.java)) }
        navProgress.setOnClickListener { startActivity(Intent(this, Progress::class.java)) }
        navTips.setOnClickListener { startActivity(Intent(this, TipsForYou::class.java)) }
    }

    private fun observeSessions() {
        sessionViewModel.allSessions.observe(this) { sessions ->
            allSessions.clear()
            allSessions.addAll(sessions)
            filterSessions()
        }
    }

    private fun filterSessions() {
        val searchQuery = searchBar.text.toString().lowercase().trim()
        val dateFilter = dateSpinner.selectedItem?.toString() ?: "All Dates"
        val typeFilter = sessionSpinner.selectedItem?.toString() ?: "All Sessions"

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startOfThisMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, -1)
        val startOfLastMonth = calendar.timeInMillis

        filteredSessions.clear()

        val results = allSessions.filter { session ->
            val matchesSearch = session.drillType.lowercase().contains(searchQuery) ||
                    (session.flawDetails?.lowercase()?.contains(searchQuery) == true)

            val matchesType = when (typeFilter) {
                "All Sessions" -> true
                "Drills" -> session.drillType.contains("Drill", ignoreCase = true)
                "Assessment" -> session.drillType.contains("Assessment", ignoreCase = true)
                "High Score" -> session.successRate > 0.85
                "Low Accuracy" -> session.successRate < 0.50
                else -> true
            }

            val matchesDate = when (dateFilter) {
                "All Dates" -> true
                "Today" -> session.dateMillis >= startOfToday
                "This Week" -> session.dateMillis >= (System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7))
                "This Month" -> session.dateMillis >= startOfThisMonth
                "Last Month" -> session.dateMillis >= startOfLastMonth && session.dateMillis < startOfThisMonth
                else -> true
            }

            matchesSearch && matchesType && matchesDate
        }

        filteredSessions.addAll(results)
        sessionAdapter.notifyDataSetChanged()

        if (filteredSessions.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            sessionsRecyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            sessionsRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun shareSession(session: SessionEntity) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(session.dateMillis))

        val shareText = """
            Check out my cricket session!
            
            Drill: ${session.drillType}
            Date: $formattedDate
            Accuracy: ${(session.successRate * 100).toInt()}%
            Duration: ${session.durationSeconds / 60} minutes
            
            #NextShot #CricketTraining
        """.trimIndent()

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Session"))
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
            R.id.session_history -> { /* Already here */ }
            R.id.AI -> startActivity(Intent(this, AICoachingChat::class.java))
            R.id.settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.signout -> performSignOut()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun performSignOut() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Yes") { _, _ ->
                auth.signOut()
                val intent = Intent(this, SignIn::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}