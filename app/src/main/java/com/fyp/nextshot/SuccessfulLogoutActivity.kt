package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class SuccessfulLogoutActivity : AppCompatActivity() {

    private lateinit var btnBackToLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_successful_logout)

        // Initialize views
        btnBackToLogin = findViewById(R.id.btnBackToLogin)

        // Back to login button
        btnBackToLogin.setOnClickListener {
            startActivity(Intent(this, SignIn::class.java))
            finishAffinity() // Close all activities
        }
    }
}