package com.fyp.nextshot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fyp.nextshot.data.local.database.AppDatabase
import com.fyp.nextshot.data.local.models.SessionEntity
import com.fyp.nextshot.data.repository.SessionRepository
import com.fyp.nextshot.ui.viewmodel.SessionViewModel
import com.fyp.nextshot.ui.viewmodel.SessionViewModelFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class VideoHistoryActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var videoAdapter: VideoSessionAdapter

    // --- Architecture Initialization ---
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val userId = auth.currentUser?.uid ?: "FALLBACK_UID"

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val repository by lazy { SessionRepository(database.sessionDao(), userId, db) }
    private val sessionViewModel: SessionViewModel by viewModels {
        SessionViewModelFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_history)

        toolbar = findViewById(R.id.menu)
        recyclerView = findViewById(R.id.video_recycler_view)
        emptyState = findViewById(R.id.empty_state_video)

        setupToolbar()
        setupRecyclerView()
        observeVideoSessions()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = "Video Analysis History"
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoSessionAdapter(
            sessions = mutableListOf(),
            onVideoClick = { session ->
                playVideo(session)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@VideoHistoryActivity)
            adapter = videoAdapter
        }
    }

    private fun observeVideoSessions() {
        sessionViewModel.allSessions.observe(this) { allSessions ->
            // Filter sessions that have a valid URI saved in the flawDetails field
            val videoSessions = allSessions.filter { session ->
                session.flawDetails?.contains("Video URL: content://") == true ||
                        session.flawDetails?.contains("Video URL: https://firebasestorage") == true
            }.sortedByDescending { it.dateMillis }

            videoAdapter.updateList(videoSessions)

            if (videoSessions.isEmpty()) {
                emptyState.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyState.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun playVideo(session: SessionEntity) {
        val videoUrlString = session.flawDetails
            ?.substringAfter("Video URL: ")
            ?.substringBefore(". Flaws:")
            ?.trim()

        if (videoUrlString != null && videoUrlString != "Upload Failed") {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(videoUrlString), "video/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot play video: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Video link is missing or upload failed.", Toast.LENGTH_SHORT).show()
        }
    }
}