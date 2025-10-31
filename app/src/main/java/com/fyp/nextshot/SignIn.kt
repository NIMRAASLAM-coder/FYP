package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SignIn : AppCompatActivity() {

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_in)

        // Apply window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views with updated IDs
        val signInButton = findViewById<Button>(R.id.signInButton)
        val signUpTab = findViewById<TextView>(R.id.signUpTab)
        val forgotPassword = findViewById<TextView>(R.id.forgotPassword)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val passwordToggle = findViewById<ImageView>(R.id.passwordToggle)
        val googleSignIn = findViewById<View>(R.id.googleSignIn)

        // Sign In Button Click
        signInButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            // Validation
            if (validateInputs(email, password)) {
                // Navigate to Dashboard
                val intent = Intent(this, Dashboard::class.java)
                startActivity(intent)
                // Optional: finish() to prevent going back to sign in
                // finish()
            }
        }

        // Sign Up Tab Click
        signUpTab.setOnClickListener {
            val intent = Intent(this, SignUp::class.java)
            startActivity(intent)
        }

        // Forgot Password Click
        forgotPassword.setOnClickListener {
            val intent = Intent(this, ForgetPasswordActivity::class.java)
            startActivity(intent)
        }

        // Password Visibility Toggle
        passwordToggle.setOnClickListener {
            togglePasswordVisibility(passwordInput, passwordToggle)
        }

        // Google Sign In Click
        googleSignIn.setOnClickListener {
            // TODO: Implement Google Sign In
            handleGoogleSignIn()
        }
    }

    /**
     * Validates email and password inputs
     */
    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)

        // Validate Email
        if (email.isEmpty()) {
            emailInput.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Please enter a valid email"
            isValid = false
        }

        // Validate Password
        if (password.isEmpty()) {
            passwordInput.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordInput.error = "Password must be at least 6 characters"
            isValid = false
        }

        return isValid
    }

    /**
     * Toggles password visibility
     */
    private fun togglePasswordVisibility(passwordInput: EditText, passwordToggle: ImageView) {
        if (isPasswordVisible) {
            // Hide password
            passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordToggle.setImageResource(R.drawable.img_3) // Eye closed icon
            isPasswordVisible = false
        } else {
            // Show password
            passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            // passwordToggle.setImageResource(R.drawable.ic_eye_open) // Eye open icon (if you have it)
            isPasswordVisible = true
        }

        // Move cursor to end of text
        passwordInput.setSelection(passwordInput.text.length)
    }

    /**
     * Handles Google Sign In
     */
    private fun handleGoogleSignIn() {
        // TODO: Implement Google Sign In authentication
        // This is a placeholder for Google Sign In implementation
        // You'll need to set up Firebase or Google Sign In SDK

        // Example:
        // 1. Initialize Google Sign In client
        // 2. Launch sign in intent
        // 3. Handle result in onActivityResult

        // For now, just show a message or navigate
        // Toast.makeText(this, "Google Sign In coming soon", Toast.LENGTH_SHORT).show()
    }


}