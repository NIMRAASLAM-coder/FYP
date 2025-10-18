package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        var btn1=findViewById<Button>(R.id.btn1)

        btn1.setOnClickListener {
            val intent = Intent(this, SignIn::class.java)
            startActivity(intent)
        }
    }
}