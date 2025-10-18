package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SignIn : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_in)

        var signin=findViewById<Button>(R.id.signin)
        var signup=findViewById<TextView>(R.id.signup)
        var forgot_pass=findViewById<TextView>(R.id.forgot_pass)
        var email=findViewById<EditText>(R.id.email)
        var password=findViewById<EditText>(R.id.password)
        var google=findViewById<View>(R.id.google)

        signin.setOnClickListener {
            var intent= Intent(this, Dashboard::class.java)
            startActivity(intent);
        }

        signup.setOnClickListener {
            var intent= Intent(this, SignUp::class.java)
            startActivity(intent)
        }


    }
}