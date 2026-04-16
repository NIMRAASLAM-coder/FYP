package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyp.nextshot.data.local.database.AppDatabase
import com.fyp.nextshot.data.repository.TipsRepository
import com.fyp.nextshot.ui.viewmodel.TipsViewModel
import com.fyp.nextshot.ui.viewmodel.TipsViewModelFactory
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TipsForYou : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var topProfileImage: ImageView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var navDashboard: View
    private lateinit var navPractice: View
    private lateinit var navProgress: View
    private lateinit var navTips: View

    // Dynamic Tips components
    private lateinit var tipsRecyclerView: RecyclerView
    private lateinit var loadingState: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var fabRefresh: FloatingActionButton
    private lateinit var tipAdapter: DynamicTipAdapter

    // ViewModel setup (same pattern as SessionHistory)
    private val userId by lazy { auth.currentUser?.uid ?: "FALLBACK_UID" }
    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val tipsRepository by lazy {
        TipsRepository(database.aiTipDao(), database.sessionDao(), userId, db)
    }
    private val tipsViewModel: TipsViewModel by viewModels {
        TipsViewModelFactory(tipsRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tips_for_you)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        topProfileImage = findViewById(R.id.profile_image)
        toolbar = findViewById(R.id.menu)
        
        // Dynamic tips views
        tipsRecyclerView = findViewById(R.id.tips_recycler_view)
        loadingState = findViewById(R.id.loading_state)
        emptyState = findViewById(R.id.empty_state)
        fabRefresh = findViewById(R.id.fab_refresh_tips)

        setupToolbarAndDrawer()
        setupRecyclerView()
        setupObservers()

        // Button to go to TipsForAll
        val nextButton = findViewById<Button>(R.id.tab_all_tips)
        nextButton.setOnClickListener {
            val intent = Intent(this, TipsForAll::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Initialize bottom navigation views
        navDashboard = findViewById(R.id.dash)
        navPractice = findViewById(R.id.practice)
        navProgress = findViewById(R.id.progress)
        navTips = findViewById(R.id.tips)

        setupBottomNavigation()
        setupBackPressHandler()
        loadUserData()

        // Highlight current page
        highlightBottomNavItem(navTips)
        
        topProfileImage.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }

        // Refresh button
        fabRefresh.setOnClickListener {
            Toast.makeText(this, "Refreshing tips...", Toast.LENGTH_SHORT).show()
            tipsViewModel.refreshTips()
        }

        // Auto-generate tips on open
        tipsViewModel.generateTips()
    }

    private fun setupRecyclerView() {
        tipAdapter = DynamicTipAdapter()
        tipsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TipsForYou)
            adapter = tipAdapter
        }
    }

    private fun setupObservers() {
        // Observe tips data
        tipsViewModel.tips.observe(this) { tips ->
            if (tips.isNullOrEmpty()) {
                // Only show empty state if NOT loading
                if (tipsViewModel.isLoading.value != true) {
                    showEmptyState()
                }
            } else {
                showTips(tips)
            }
        }

        // Observe loading state
        tipsViewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                showLoadingState()
            } else {
                // Let the tips observer handle showing content or empty state
                val currentTips = tipsViewModel.tips.value
                if (currentTips.isNullOrEmpty()) {
                    showEmptyState()
                }
                // If tips exist, tips observer already handled it
            }
        }

        // Observe errors
        tipsViewModel.error.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                Log.e("TipsForYou", "Error: $error")
            }
        }
    }

    private fun showLoadingState() {
        loadingState.visibility = View.VISIBLE
        tipsRecyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE
        fabRefresh.visibility = View.GONE
    }

    private fun showTips(tips: List<com.fyp.nextshot.data.local.models.AiTipEntity>) {
        loadingState.visibility = View.GONE
        emptyState.visibility = View.GONE
        tipsRecyclerView.visibility = View.VISIBLE
        fabRefresh.visibility = View.VISIBLE
        tipAdapter.submitList(tips)
    }

    private fun showEmptyState() {
        loadingState.visibility = View.GONE
        tipsRecyclerView.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        fabRefresh.visibility = View.VISIBLE
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
            startActivity(Intent(this, Progress::class.java))
            finish()
        }
        navTips.setOnClickListener {
            // Already here
            highlightBottomNavItem(navTips)
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
                    startActivity(Intent(this@TipsForYou, Dashboard::class.java))
                    finish()
                }
            }
        })
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
                // Already here
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