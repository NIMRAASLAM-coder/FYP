package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class SignUp : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)

        var signin=findViewById<TextView>(R.id.signin)
        var create_acc=findViewById<TextView>(R.id.create_acc)

        signin.setOnClickListener {
            var intent= Intent(this, SignIn::class.java)
            startActivity(intent)
        }

        create_acc.setOnClickListener {
            var intent= Intent(this, Dashboard::class.java)
            startActivity(intent)
        }
    }
}