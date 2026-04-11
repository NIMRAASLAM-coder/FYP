package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class TipsForAll : AppCompatActivity() {

    private lateinit var navDashboard: LinearLayout
    private lateinit var navPractice: LinearLayout
    private lateinit var navProgress: LinearLayout
    private lateinit var navTips: LinearLayout

    private lateinit var tabForYou: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tips_for_all)

        navDashboard = findViewById(R.id.dash)
        navPractice = findViewById(R.id.practice)
        navProgress = findViewById(R.id.progress)
        navTips = findViewById(R.id.tips)
        tabForYou = findViewById(R.id.tab_for_you)

        // --- RecyclerView Setup ---
        val tipsList = mutableListOf<Tip>()

        // --- Data Population ---
        // Cover Drive
        tipsList.add(Tip("Mastering the Cover Drive - Part 1", "Learn the basics of the cover drive shot.", "Cover Drive", "https://youtu.be/TSxJVw57jqs?si=QEBLEXA-mXhsgobp"))
        tipsList.add(Tip("Mastering the Cover Drive - Part 2", "Advanced techniques for the cover drive.", "Cover Drive", "https://youtu.be/nPBgrDjRCcg?si=O3X7NulnYARIvior"))
        tipsList.add(Tip("Cover Drive Footwork", "Improving your foot movement for the cover drive.", "Cover Drive", "https://youtu.be/2x48B3rppP4?si=Ucypt0P5C-hUfiJp"))
        tipsList.add(Tip("Quick Tip: Cover Drive", "Short guidance on perfect timing.", "Cover Drive", "https://youtube.com/shorts/hdtNGJX5Eo0?si=_EusMbs1QNX05orK"))
        tipsList.add(Tip("Cover Drive Mastery", "Full guide to mastering the drive.", "Cover Drive", "https://www.youtube.com/watch?v=h3N-BRQXTS4"))

        // Straight Shot
        tipsList.add(Tip("The Perfect Straight Drive", "Technique for hitting it straight down the ground.", "Straight Shot", "https://youtu.be/cKB8qrRrSJQ?si=VTgatogZRZUxk4xq"))
        tipsList.add(Tip("Straight Shot Precision", "Focus on head position for straight shots.", "Straight Shot", "https://youtu.be/ASskVkjHsAU?si=oh4kC9cJGd8Aa7vK"))
        tipsList.add(Tip("Straight Drive Drills", "Effective drills for your straight drive.", "Straight Shot", "https://youtu.be/IRVcM9XdTXY?si=-PFXcB3vqC2b_qic"))
        tipsList.add(Tip("Power in Straight Shots", "How to generate more power in your straight drives.", "Straight Shot", "https://youtu.be/eOqhRgUPLcg?si=UW7vX_K7HRjjBJ_V"))
        tipsList.add(Tip("Straight Shot Basics", "Simple steps for beginners.", "Straight Shot", "https://youtu.be/Fpjp2o2arVs?si=LoBvXzqk1SFDOJbO"))

        // Forward Defensive
        tipsList.add(Tip("Forward Defensive Technique", "The ultimate guide to the forward defensive stroke.", "Forward Defensive", "https://youtu.be/CdlYCoqUVEQ?si=tcczdt9RXyv5ftDH"))
        tipsList.add(Tip("Solid Defense", "Building a rock-solid forward defense.", "Forward Defensive", "https://youtu.be/pEy8-o7nwek?si=RiE5Kh14JXbwujnv"))
        tipsList.add(Tip("Defensive Footwork", "Short clip on defensive foot movement.", "Forward Defensive", "https://youtube.com/shorts/Xm-LMxACZMQ?si=c9o_pSuEvkmUzQFo"))
        tipsList.add(Tip("Defense Against Spin", "Playing the forward defensive against spinners.", "Forward Defensive", "https://youtu.be/0ZhXvTzVr0s?si=KskvREX6IkVI35nj"))
        tipsList.add(Tip("Defensive Drills", "Practice these drills to improve your defense.", "Forward Defensive", "https://youtu.be/mULeuQ6XgUA?si=FvWC2U1D47ndGkhU"))
        tipsList.add(Tip("Soft Hands in Defense", "Learn how to play with soft hands.", "Forward Defensive", "https://youtu.be/sKIwkvdAyJU?si=68-RNygfJStFFzfW"))
        tipsList.add(Tip("Advanced Defense", "Taking your forward defensive to the next level.", "Forward Defensive", "https://youtu.be/Bxq1ZyjwBh4?si=1wBhBE32PxJCPIg_"))

        // Mixture of shots
        tipsList.add(Tip("Cricket Batting Masterclass", "A variety of shots and when to play them.", "Mixed Shots", "https://youtu.be/Gy_MjikAnhw?si=hR_Ea0QkmHbrx-gl"))
        tipsList.add(Tip("All-round Batting Drills", "Drills covering multiple shots.", "Mixed Shots", "https://youtu.be/KY8gsVeKn0w?si=nBpihlcpdmyrw24Z"))

        val recyclerView = findViewById<RecyclerView>(R.id.tips_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = TipsAdapter(tipsList) { url ->
            val videoId = TipsAdapter.extractVideoId(url)
            if (videoId.isNotEmpty()) {
                VideoPlayerDialogFragment.newInstance(videoId)
                    .show(supportFragmentManager, "video_player")
            }
        }

        tabForYou.setOnClickListener {
            startActivity(Intent(this, TipsForYou::class.java))
            finish()
        }

        setupBottomNavigation()
        setupBackPressHandler()
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
             // Already on Tips screen
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@TipsForAll, Dashboard::class.java))
                finish()
            }
        })
    }
}
