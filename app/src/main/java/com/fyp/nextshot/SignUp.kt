package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignUp : AppCompatActivity() {

    private var isPasswordVisible = false
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        // Apply window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase instances
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

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
                registerUser(name, email, password)
            }
            // IMPORTANT: If you had any manual navigation to SignIn here, it's removed.
        }

        // Password Visibility Toggle
        passwordToggle.setOnClickListener {
            togglePasswordVisibility(passwordInput, passwordToggle)
        }
    }

    /**
     * Handles user registration using Firebase Auth and stores data in Firestore.
     */
    private fun registerUser(name: String, email: String, password: String) {
        // Step 1: Create user with Email and Password
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    val userId = firebaseUser?.uid

                    if (userId != null) {
                        // Step 2: Store additional user data (Name, Email) in Firestore
                        val user = hashMapOf(
                            "uid" to userId,
                            "fullName" to name,
                            "email" to email,
                            "createdAt" to com.google.firebase.Timestamp.now()
                        )

                        // Create a collection named "users" and add a document with the UID
                        db.collection("users").document(userId)
                            .set(user)
                            .addOnSuccessListener {
                                // Both Auth and Firestore successful
                                Toast.makeText(this, "Account Created! Welcome to NextShot.", Toast.LENGTH_LONG).show()
                                navigateToDashboard()
                            }
                            .addOnFailureListener { e ->
                                // Firestore failed, but user is created in Auth. Handle cleanup:
                                Toast.makeText(this, "Failed to save user data. Please contact support. Error: ${e.message}", Toast.LENGTH_LONG).show()
                                firebaseUser.delete()
                                // The user stays on the sign-up screen to see the error.
                            }
                    } else {
                        Toast.makeText(this, "Registration successful, but user ID is null.", Toast.LENGTH_LONG).show()
                    }

                } else {
                    // Registration failed (e.g., email already in use, weak password, etc.)
                    val errorMessage = task.exception?.message ?: "Registration failed."
//                    Toast.makeText(this, "Registration Failed: $errorMessage", Toast.LENGTH_LONG).show()
                    // The user stays on the SignUp screen to see the error and try again.
                }
            }
    }

    /**
     * Navigates to Sign In activity and finishes the current one.
     */
    private fun navigateToSignIn() {
        val intent = Intent(this, SignIn::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Navigates to Dashboard activity and clears the back stack.
     */
    private fun navigateToDashboard() {
        val intent = Intent(this, Dashboard::class.java)
        // Clear all previous activities and start a new task
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Validates all input fields
     */
    private fun validateInputs(name: String, email: String, password: String): Boolean {
        // ... (Keep your existing validation logic)
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
        } else if (password.length < 4) {
            passwordInput.error = "Password must be at least 4 characters"
            isValid = false
        } else if (!password.matches(Regex(".*[a-z].*"))) {
            passwordInput.error = "Password must contain at least one lowercase letter"
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
            isPasswordVisible = true
        }
        // Move cursor to end of text
        passwordInput.setSelection(passwordInput.text.length)
    }

    override fun onBackPressed() {
        navigateToSignIn()
    }
}