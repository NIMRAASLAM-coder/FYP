package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fyp.nextshot.databinding.ActivityAicoachingChatBinding

class AICoachingChat : AppCompatActivity() {

    private lateinit var binding: ActivityAicoachingChatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize view binding
        binding = ActivityAicoachingChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup bottom navigation
        setupBottomNavigation()

        // Setup menu button
        setupMenuButton()

        // Setup send button
        setupSendButton()
    }

    private fun setupBottomNavigation() {
        binding.btnDashboard.setOnClickListener {
            // Navigate to Dashboard
            startActivity(Intent(this, Dashboard::class.java))
            finish()
        }

        binding.btnPractice.setOnClickListener {
            // Navigate to Practice
            startActivity(Intent(this, PracticeSession::class.java))
            finish()
        }

        binding.btnProgress.setOnClickListener {
            // Navigate to Progress
            startActivity(Intent(this, Progress::class.java))
            finish()
        }

        binding.btnTips.setOnClickListener {
            // Navigate to Tips
            startActivity(Intent(this, TipsForYou::class.java))
            finish()
        }
    }

    private fun setupMenuButton() {
        binding.btnMenu.setOnClickListener {
            // Open navigation drawer or side menu
            // This depends on your dashboard implementation
            // If using DrawerLayout, call openDrawer
        }
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val message = binding.inputMessage.text.toString().trim()

            if (message.isNotEmpty()) {
                // Add user message to chat container
                addUserMessage(message)

                // Clear input field
                binding.inputMessage.setText("")

                // TODO: Send message to AI backend and get response
                // For now, add a dummy bot response
                addBotResponse("Thanks for your question! I'm processing your request...")
            }
        }
    }

    private fun addUserMessage(message: String) {
        // You can add dynamic message UI here
        // This is a placeholder - implement based on your chat UI requirements
    }

    private fun addBotResponse(response: String) {
        // You can add dynamic bot response UI here
        // This is a placeholder - implement based on your chat UI requirements
    }
}