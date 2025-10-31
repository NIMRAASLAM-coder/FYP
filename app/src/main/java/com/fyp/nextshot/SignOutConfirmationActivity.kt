package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SignOutConfirmationActivity : AppCompatActivity() {

    private lateinit var btnCancel: Button
    private lateinit var btnConfirmLogout: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_out_confirmation)

        // Initialize views
        btnCancel = findViewById(R.id.btnCancel)
        btnConfirmLogout = findViewById(R.id.btnConfirmLogout)

        // Cancel button
        btnCancel.setOnClickListener {
            finish()
        }

        // Confirm logout button
        btnConfirmLogout.setOnClickListener {
            // TODO: Clear session/tokens
            startActivity(Intent(this, SuccessfulLogoutActivity::class.java))
            finish()
        }
    }
}