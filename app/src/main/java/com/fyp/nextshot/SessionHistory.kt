package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
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
    private val repository by lazy { SessionRepository(database.sessionDao(), userId,db) }
    private val db by lazy { FirebaseFirestore.getInstance() }
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

    // 3. Data Storage (Now using the Room Entity)
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

        // Start observing the local database data
        observeSessions()
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

        // Bottom navigation
        navDashboard = findViewById(R.id.nav_dashboard)
        navPractice = findViewById(R.id.nav_practice)
        navProgress = findViewById(R.id.nav_progress)
        navTips = findViewById(R.id.nav_tips)
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

    private fun setupSpinners() {
        // Date Filter
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

        // Session Type Filter
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
        // NOTE: SessionAdapter MUST be updated to accept List<SessionEntity>
        sessionAdapter = SessionAdapter(
            sessions = filteredSessions,
            onViewAnalysisClick = { session ->
                Toast.makeText(this, "Viewing analysis for ${session.drillType}", Toast.LENGTH_SHORT).show()
                // TODO: Navigate to Flaws Tracking analysis screen
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

    /**
     * Replaces loadSampleData. Observes data from the Room DB via ViewModel.
     * This handles loading the data list for the screen.
     */
    private fun observeSessions() {
        sessionViewModel.allSessions.observe(this) { sessions ->
            allSessions.clear()
            allSessions.addAll(sessions)
            // Call filterSessions to ensure the list is displayed with current filters
            filterSessions()
        }
    }

    /**
     * Implements the core Lists and Search functionality.
     * Filters the master list based on search bar text and spinner selections.
     */
    private fun filterSessions() {
        val searchQuery = searchBar.text.toString().lowercase().trim()
        val dateFilter = dateSpinner.selectedItem.toString()
        val typeFilter = sessionSpinner.selectedItem.toString()

        val currentTime = System.currentTimeMillis()
        val dayInMillis = TimeUnit.DAYS.toMillis(1)
        val weekInMillis = TimeUnit.DAYS.toMillis(7)
        val monthInMillis = TimeUnit.DAYS.toMillis(30)

        filteredSessions.clear()

        val results = allSessions.filter { session ->
            // --- 1. Search Filter ---
            // Matches search query against drill type or flaw details
            val matchesSearch = session.drillType.lowercase().contains(searchQuery) ||
                    (session.flawDetails?.lowercase()?.contains(searchQuery) == true)

            // --- 2. Session Type Filter ---
            val matchesType = when (typeFilter) {
                "All Sessions" -> true
                "Drills" -> session.drillType.contains("Drill")
                "Assessment" -> session.drillType.contains("Assessment")
                "High Score" -> session.successRate > 0.85 // Example criteria
                "Low Accuracy" -> session.successRate < 0.50 // Example criteria
                else -> true
            }

            // --- 3. Date Filter ---
            val timeElapsed = currentTime - session.dateMillis
            val matchesDate = when (dateFilter) {
                "All Dates" -> true
                "Today" -> timeElapsed < dayInMillis
                "This Week" -> timeElapsed < weekInMillis
                "This Month" -> timeElapsed < monthInMillis
                "Last Month" -> timeElapsed in monthInMillis..(2 * monthInMillis)
                else -> true
            }

            matchesSearch && matchesType && matchesDate
        }

        filteredSessions.addAll(results)
        sessionAdapter.notifyDataSetChanged()

        // Show/hide empty state
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
            
            Flaws Noted: ${session.flawDetails ?: "None"}
            
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
                // 1. Firebase Sign Out
                auth.signOut()

                // 2. Clear local session (if used for authentication state)
                val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
                sharedPreferences.edit().clear().apply()

                // 3. Navigate to SignIn and clear activity stack
                val intent = Intent(this, SignIn::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

//     You can keep this override for handling the back button if the drawer is open
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}