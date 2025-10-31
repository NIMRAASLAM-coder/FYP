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
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView

class SessionHistory : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar

    // UI elements
    private lateinit var searchBar: EditText
    private lateinit var dateSpinner: Spinner
    private lateinit var sessionSpinner: Spinner
    private lateinit var sessionsRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout

    // Bottom navigation
    private lateinit var navDashboard: View
    private lateinit var navPractice: View
    private lateinit var navProgress: View
    private lateinit var navTips: View

    // Data
    private lateinit var sessionAdapter: SessionAdapter
    private var allSessions = mutableListOf<SessionData>()
    private var filteredSessions = mutableListOf<SessionData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_history)

        initializeViews()
        setupToolbarAndDrawer()
        setupSpinners()
        setupRecyclerView()
        setupSearchBar()
        setupBottomNavigation()
        loadSampleData()
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
        val sessionFilters = arrayOf("All Sessions", "High Score", "Recent", "Practice", "Assessment")
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
                Toast.makeText(this, "Viewing analysis for ${session.title}", Toast.LENGTH_SHORT).show()
                // TODO: Navigate to analysis screen
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
        navDashboard.setOnClickListener {
            val intent = Intent(this, Dashboard::class.java)
            startActivity(intent)
        }

        navPractice.setOnClickListener {
            val intent = Intent(this, PracticeSession::class.java)
            startActivity(intent)
        }

        navProgress.setOnClickListener {
            val intent = Intent(this, Progress::class.java)
            startActivity(intent)
        }

        navTips.setOnClickListener {
            val intent = Intent(this, TipsForYou::class.java)
            startActivity(intent)
        }
    }

    private fun loadSampleData() {
        // Sample data - replace with actual data from database
        allSessions.addAll(listOf(
            SessionData(
                id = 3,
                title = "Session #3",
                date = "Wednesday, October 1, 2025",
                score = 85,
                accuracy = 78,
                duration = 45,
                shots = 120
            ),
            SessionData(
                id = 2,
                title = "Session #2",
                date = "Tuesday, September 30, 2025",
                score = 82,
                accuracy = 75,
                duration = 40,
                shots = 115
            ),
            SessionData(
                id = 1,
                title = "Session #1",
                date = "Monday, September 29, 2025",
                score = 78,
                accuracy = 72,
                duration = 35,
                shots = 100
            )
        ))

        filterSessions()
    }

    private fun filterSessions() {
        val searchQuery = searchBar.text.toString().lowercase()

        filteredSessions.clear()
        filteredSessions.addAll(
            allSessions.filter { session ->
                session.title.lowercase().contains(searchQuery) ||
                        session.date.lowercase().contains(searchQuery)
            }
        )

        // Apply additional filters based on spinners
        // TODO: Implement date and session type filtering

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

    private fun shareSession(session: SessionData) {
        val shareText = """
            Check out my cricket session!
            
            ${session.title}
            Date: ${session.date}
            Score: ${session.score}
            Accuracy: ${session.accuracy}%
            Duration: ${session.duration} minutes
            Shots: ${session.shots}
            
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
                performSignOut()
            }
        }

        // Close the drawer after navigation
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun performSignOut() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Yes") { _, _ ->
                // Clear user session
                val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
                sharedPreferences.edit().clear().apply()

                // Navigate to SignIn
                val intent = Intent(this, SignIn::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


}

