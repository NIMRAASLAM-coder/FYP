package com.fyp.nextshot

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class DashboardActivity : AppCompatActivity() {

    private lateinit var chatContainer: LinearLayout
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dashboard_activity)

        chatContainer = findViewById(R.id.chatContainer)
        //  etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        // Preload greeting message
        addBotMessage("Hello! I'm your AI Cricket Coach. I'm here to help you improve your batting technique, answer questions about cricket, and provide personalized coaching advice. What would you like to know?")

        // Suggested question buttons
        //findViewById<Button>(R.id.btnQuestion1).setOnClickListener { sendMessage(it as Button) }
        //  findViewById<Button>(R.id.btnQuestion2).setOnClickListener { sendMessage(it as Button) }
        // findViewById<Button>(R.id.btnQuestion3).setOnClickListener { sendMessage(it as Button) }

        // Send custom message
        btnSend.setOnClickListener {
            val userMsg = etMessage.text.toString().trim()
            if (userMsg.isNotEmpty()) {
                addUserMessage(userMsg)
                etMessage.text.clear()
                addBotMessage("That's a great question! Let me give you some advice about \"$userMsg\".")
            }
        }
    }

    private fun sendMessage(button: Button) {
        val question = button.text.toString()
        addUserMessage(question)
        addBotMessage("Good question! Here are some tips related to \"$question\".")
    }

    private fun addUserMessage(message: String) {
        val tv = TextView(this)
        tv.text = message
        tv.setBackgroundResource(R.drawable.bg_input)
        tv.setPadding(20, 12, 20, 12)
        tv.textSize = 15f
        tv.setTextColor(resources.getColor(android.R.color.black))
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(100, 8, 0, 8)
        params.gravity = android.view.Gravity.END
        tv.layoutParams = params
        chatContainer.addView(tv)
    }

    private fun addBotMessage(message: String) {
        val tv = TextView(this)
        tv.text = message
        tv.setBackgroundResource(R.drawable.bg_input)
        tv.setPadding(20, 12, 20, 12)
        tv.textSize = 15f
        tv.setTextColor(resources.getColor(android.R.color.black))
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 8, 100, 8)
        params.gravity = android.view.Gravity.START
        tv.layoutParams = params
        chatContainer.addView(tv)
    }
}
