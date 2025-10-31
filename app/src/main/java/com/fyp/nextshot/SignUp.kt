package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SignUp : AppCompatActivity() {

    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)

        // Apply window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize views with updated IDs
        val signInTab = findViewById<TextView>(R.id.signInTab)
        val signInLink = findViewById<TextView>(R.id.signInLink)
        val createAccountButton = findViewById<Button>(R.id.createAccountButton)
        val nameInput = findViewById<EditText>(R.id.nameInput)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val passwordToggle = findViewById<ImageView>(R.id.passwordToggle)

        // Sign In Tab Click (navigate to Sign In page)
        signInTab.setOnClickListener {
            navigateToSignIn()
        }

        // Sign In Link Click (at bottom)
        signInLink.setOnClickListener {
            navigateToSignIn()
        }

        // Create Account Button Click
        createAccountButton.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            // Validation
            if (validateInputs(name, email, password)) {
                // TODO: Add your signup logic here (e.g., Firebase Authentication)
                // For now, navigate to Dashboard
                val intent = Intent(this, Dashboard::class.java)
                // Optional: Pass user data
                intent.putExtra("USER_NAME", name)
                intent.putExtra("USER_EMAIL", email)
                startActivity(intent)
                // Optional: finish() to prevent going back to sign up
                // finish()
            }
        }

        // Password Visibility Toggle
        passwordToggle.setOnClickListener {
            togglePasswordVisibility(passwordInput, passwordToggle)
        }
    }

    /**
     * Navigates to Sign In activity
     */
    private fun navigateToSignIn() {
        val intent = Intent(this, SignIn::class.java)
        startActivity(intent)
        // Optional: Add animation
        // overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    /**
     * Validates all input fields
     */
    private fun validateInputs(name: String, email: String, password: String): Boolean {
        var isValid = true

        val nameInput = findViewById<EditText>(R.id.nameInput)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)

        // Validate Full Name
        if (name.isEmpty()) {
            nameInput.error = "Full name is required"
            isValid = false
        } else if (name.length < 2) {
            nameInput.error = "Name must be at least 2 characters"
            isValid = false
        } else if (!name.matches(Regex("^[a-zA-Z\\s]+$"))) {
            nameInput.error = "Name should only contain letters"
            isValid = false
        }

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
        } else if (password.length < 8) {
            passwordInput.error = "Password must be at least 8 characters"
            isValid = false
        } else if (!password.matches(Regex(".*[A-Z].*"))) {
            passwordInput.error = "Password must contain at least one uppercase letter"
            isValid = false
        } else if (!password.matches(Regex(".*[a-z].*"))) {
            passwordInput.error = "Password must contain at least one lowercase letter"
            isValid = false
        } else if (!password.matches(Regex(".*\\d.*"))) {
            passwordInput.error = "Password must contain at least one number"
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

    override fun onBackPressed() {
        super.onBackPressed()
        // Navigate back to Sign In
        navigateToSignIn()
    }
}