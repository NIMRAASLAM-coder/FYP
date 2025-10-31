package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth // Import Firebase Auth

class SignOutConfirmationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth // Declare FirebaseAuth
    private lateinit var btnCancel: Button
    private lateinit var btnConfirmLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_out_confirmation)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize views
        btnCancel = findViewById(R.id.btnCancel)
        btnConfirmLogout = findViewById(R.id.btnConfirmLogout)

        // Cancel button: Simply close this activity
        btnCancel.setOnClickListener {
            finish()
        }

        // Confirm logout button: Perform Firebase logout and navigate
        btnConfirmLogout.setOnClickListener {
            performLogout()
        }
    }

    /**
     * Performs Firebase sign out and navigates to the successful logout screen.
     */
    private fun performLogout() {
        // 1. Clear session/tokens using Firebase signOut
        auth.signOut()

        // 2. Navigate to the Successful Logout screen
        val intent = Intent(this, SuccessfulLogoutActivity::class.java)
        // Use FLAG_ACTIVITY_CLEAR_TASK and FLAG_ACTIVITY_NEW_TASK
        // to clear the entire activity stack (including Dashboard)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        // 3. Finish the confirmation activity
        finish()
    }
}