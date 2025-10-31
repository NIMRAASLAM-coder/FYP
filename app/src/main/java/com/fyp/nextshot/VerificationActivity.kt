package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VerificationActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    // Re-purpose the OTP fields to show instructions/email address if needed
    private lateinit var tvTitle: TextView
    private lateinit var tvMessage: TextView
    private lateinit var btnProceedToSignIn: Button // Renamed from btnVerify

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        // Initialize views (using old IDs for now if XML is not yet updated)
        btnBack = findViewById(R.id.btnBack)
        tvTitle = findViewById(R.id.verificationTitle) // Assuming you'll add this ID to the title TextView
        tvMessage = findViewById(R.id.verificationMessage) // Assuming you'll add this ID to the description TextView
        btnProceedToSignIn = findViewById(R.id.btnVerify) // Using btnVerify ID from original XML

        // 1. Get the custom message passed from ForgetPasswordActivity
        val message = intent.getStringExtra("ACTION_MESSAGE")
            ?: "Please check your email inbox for a secure link to reset your password."

        // Optional: Update Title and Message
        // tvTitle.text = "Email Sent!"
        // tvMessage.text = message

        // 2. Setup button listeners
        btnBack.setOnClickListener {
            finish()
        }

        // 3. Proceed to Sign In (the action after reset link is sent)
        btnProceedToSignIn.setOnClickListener {
            val intent = Intent(this, SignIn::class.java)
            // Clear back stack to prevent going back to ForgetPassword
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // --- NOTE ---
        // OTP fields (otpField1, etc.) and Resend button (tvResend) are no longer used
        // in this adapted flow and should be removed from the UI (XML).
    }
}