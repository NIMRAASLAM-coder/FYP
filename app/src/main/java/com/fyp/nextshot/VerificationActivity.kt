package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class VerificationActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var otpField1: EditText
    private lateinit var otpField2: EditText
    private lateinit var otpField3: EditText
    private lateinit var otpField4: EditText
    private lateinit var tvResend: TextView
    private lateinit var btnVerify: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification)

        // Initialize views
        btnBack = findViewById(R.id.btnBack)
        otpField1 = findViewById(R.id.otpField1)
        otpField2 = findViewById(R.id.otpField2)
        otpField3 = findViewById(R.id.otpField3)
        otpField4 = findViewById(R.id.otpField4)
        tvResend = findViewById(R.id.tvResend)
        btnVerify = findViewById(R.id.btnVerify)

        // Setup OTP field listeners
        setupOtpFields()

        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Resend code
        tvResend.setOnClickListener {
            Toast.makeText(this, "Code resent to your email", Toast.LENGTH_SHORT).show()
        }

        // Verify button
        btnVerify.setOnClickListener {
            val otp = "${otpField1.text}${otpField2.text}${otpField3.text}${otpField4.text}"
            if (otp.length == 4) {
                // Navigate to New Password activity
                val intent = Intent(this, NewPasswordActivity::class.java)
//                intent.putExtra("EMAIL", email) // Pass email if needed
                startActivity(intent)


            } else {
                Toast.makeText(this, "Please enter complete OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupOtpFields() {
        otpField1.addTextChangedListener(OtpTextWatcher(otpField1, otpField2))
        otpField2.addTextChangedListener(OtpTextWatcher(otpField2, otpField3))
        otpField3.addTextChangedListener(OtpTextWatcher(otpField3, otpField4))
        otpField4.addTextChangedListener(OtpTextWatcher(otpField4, null))
    }

    private inner class OtpTextWatcher(
        private val currentField: EditText,
        private val nextField: EditText?
    ) : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (s?.length == 1 && nextField != null) {
                nextField.requestFocus()
            }
        }

        override fun afterTextChanged(s: Editable?) {}
    }
}