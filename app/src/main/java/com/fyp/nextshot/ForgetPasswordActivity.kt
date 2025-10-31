package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ForgetPasswordActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var etEmail: EditText
    private lateinit var btnSend: Button
    private lateinit var btnBackToSignIn: Button
    private lateinit var tvSignUp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forget_password)

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

        // Send button - navigate to verification
        btnSend.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
            } else {
                // TODO: Send password reset email
                startActivity(Intent(this, VerificationActivity::class.java))
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
}