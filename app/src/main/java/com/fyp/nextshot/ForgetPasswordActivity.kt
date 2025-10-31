package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth // Import Firebase Auth

class ForgetPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth // Declare FirebaseAuth
    private lateinit var btnBack: ImageView
    private lateinit var etEmail: EditText
    private lateinit var btnSend: Button
    private lateinit var btnBackToSignIn: Button
    private lateinit var tvSignUp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forget_password)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize views
        btnBack = findViewById(R.id.btnBack)
        etEmail = findViewById(R.id.etEmail)
        btnSend = findViewById(R.id.btnSend)
        btnBackToSignIn = findViewById(R.id.btnBackToSignIn)
        tvSignUp = findViewById(R.id.tvSignUp)

        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Send button - Implement Firebase logic here
        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_LONG).show()
                etEmail.error = "Invalid Email"
            } else {
                // Call the function to send the reset email
                sendResetEmail(email)
            }
        }

        // Back to sign in
        btnBackToSignIn.setOnClickListener {
            startActivity(Intent(this, SignIn::class.java))
            finish()
        }

        // Sign up
        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUp::class.java))
            finish()
        }
    }

    /**
     * Sends a password reset email to the provided email address.
     */
    private fun sendResetEmail(email: String) {
        // Disable button to prevent multiple clicks while processing
        btnSend.isEnabled = false
        btnSend.text = "Sending..."

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                btnSend.isEnabled = true
                btnSend.text = "Send" // Restore button text

                if (task.isSuccessful) {
                    // Success: Email sent.
                    Toast.makeText(this, "Password reset email sent to $email.", Toast.LENGTH_LONG).show()

                    // Navigate to VerificationActivity to instruct the user to check their inbox.
                    val intent = Intent(this, VerificationActivity::class.java).apply {
                        putExtra("ACTION_MESSAGE", "A password reset link has been sent to your email. Please click the link to set a new password.")
                    }
                    startActivity(intent)
                    finish() // Close this activity
                } else {
                    // Failure: Could be because the user doesn't exist, or a network error.
                    val errorMessage = task.exception?.message ?: "Failed to send reset email. Please try again."
                    Toast.makeText(this, "Error: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }
    }
}