package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class NewPasswordActivity : AppCompatActivity() {

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

        // Send button
        btnSend.setOnClickListener {
            val newPassword = etNewPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

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
                    // TODO: Update password with backend
                    Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, SignIn::class.java))
                    finish()
                }
            }
        }
    }

    private fun togglePasswordVisibility(editText: EditText, imageView: ImageView, isVisible: Boolean) {
        if (isVisible) {
            editText.inputType = 1 // TEXT
            imageView.setImageResource(R.drawable.img_3)
        } else {
            editText.inputType = 129 // PASSWORD
            imageView.setImageResource(R.drawable.img_3)
        }
        editText.setSelection(editText.text.length)
    }
}
