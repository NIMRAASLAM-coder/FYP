package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException

class NewPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth // Declare FirebaseAuth
    private lateinit var btnBack: ImageView
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnToggleNewPassword: ImageView
    private lateinit var btnToggleConfirmPassword: ImageView
    private lateinit var btnSend: Button

    private var isNewPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_password)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize views
        btnBack = findViewById(R.id.btnBack)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnToggleNewPassword = findViewById(R.id.btnToggleNewPassword)
        btnToggleConfirmPassword = findViewById(R.id.btnToggleConfirmPassword)
        btnSend = findViewById(R.id.btnSend)

        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Toggle new password visibility
        btnToggleNewPassword.setOnClickListener {
            isNewPasswordVisible = !isNewPasswordVisible
            togglePasswordVisibility(etNewPassword, btnToggleNewPassword, isNewPasswordVisible)
        }

        // Toggle confirm password visibility
        btnToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            togglePasswordVisibility(etConfirmPassword, btnToggleConfirmPassword, isConfirmPasswordVisible)
        }

        // Send button - UPDATED WITH FIREBASE LOGIC
        btnSend.setOnClickListener {
            val newPassword = etNewPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            // 1. Initial Client-Side Validation
            when {
                newPassword.isEmpty() || confirmPassword.isEmpty() -> {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
                newPassword.length < 6 -> {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                }
                newPassword != confirmPassword -> {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // 2. Call Firebase Update Function
                    updateUserPassword(newPassword)
                }
            }
        }
    }

    /**
     * Updates the password for the currently authenticated user.
     */
    private fun updateUserPassword(newPassword: String) {
        val user = auth.currentUser

        if (user != null) {
            user.updatePassword(newPassword)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Password updated successfully! Please sign in.", Toast.LENGTH_LONG).show()

                        // Sign out the user for a clean transition and require re-login with the new password
                        auth.signOut()

                        // Navigate to Sign In
                        val intent = Intent(this, SignIn::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // Handle failure case
                        val exception = task.exception
                        val message = when(exception) {
                            is FirebaseAuthRecentLoginRequiredException -> "Update failed: Please re-authenticate."
                            is FirebaseAuthInvalidCredentialsException -> "Update failed: Invalid credentials provided."
                            else -> "Update failed: ${exception?.message}"
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            // Should not happen if the flow is correct (user clicks link and lands here)
            Toast.makeText(this, "Error: No authenticated user found. Please try the reset process again.", Toast.LENGTH_LONG).show()
            // Navigate back to Sign In if no user is found
            startActivity(Intent(this, SignIn::class.java))
            finish()
        }
    }


    private fun togglePasswordVisibility(editText: EditText, imageView: ImageView, isVisible: Boolean) {
        if (isVisible) {
            // Show password (InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD is the correct constant)
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            // Hide password (InputType.TYPE_TEXT_VARIATION_PASSWORD is the correct constant)
            editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        // Use your eye icon resource ID
        imageView.setImageResource(R.drawable.img_3)
        editText.setSelection(editText.text.length)
    }
}