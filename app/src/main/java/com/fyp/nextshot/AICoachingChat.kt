package com.fyp.nextshot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fyp.nextshot.BuildConfig
import com.fyp.nextshot.databinding.ActivityAicoachingChatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class AICoachingChat : AppCompatActivity() {
    private lateinit var binding: ActivityAicoachingChatBinding

    // ------------------------------------------------------------------------
    // SETUP
    // ------------------------------------------------------------------------
    // FIXED: Load from BuildConfig (secure, no hardcode)
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
    // FIXED: Correct model name (hyphen, no space) + upgrade to 2.5 for better perf
    private val modelName = "gemini-2.5-flash"  // Or "gemini-1.5-flash" if quota limits
    // OkHttp Client
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Chat History to maintain context (A "Proper Chatbot")
    private val chatHistory = JSONArray()

    // ------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize view binding
        binding = ActivityAicoachingChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // FIXED: Validate API key early
        if (apiKey.isEmpty()) {
            Log.e("AICoachingChat", "API Key is empty! Check local.properties and build.gradle.")
            addBotResponse("Coach setup error: API key missing. Check app settings.", isInitial = true)
            return
        } else {
            Log.d("AICoachingChat", "API Key loaded successfully (length: ${apiKey.length})")
        }

        // FIXED: List available models on launch (one-time debug)
        listAvailableModels()

        // Setup UI components
        setupBottomNavigation()
        setupMenuButton()
        setupSendButton()

        // FIXED: Initialize history with system prompt for consistent coaching persona
        initializeChatHistory()

        // Add the initial welcome message
        addBotResponse("Hello! 👋 I'm your AI Cricket Coach. Ask me how to improve your batting, timing, or technique!", isInitial = true)
    }

    // FIXED: Add system prompt to history for all sessions
    private fun initializeChatHistory() {
        val systemPrompt = "You are an expert, friendly, and encouraging cricket batting coach named 'AI Cricket Coach'. Keep answers concise (under 150 words), actionable, and focused on technique improvement. Use emojis sparingly for engagement. End with a question to continue the conversation."
        val sysEntry = JSONObject().apply {
            put("role", "user")  // Gemini treats system as first "user" for context
            put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
        }
        chatHistory.put(sysEntry)
    }

    // FIXED: Debug: List available models on launch
    private fun listAvailableModels() {
        CoroutineScope(Dispatchers.IO).launch {
            val listUrl = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(listUrl).build()
            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        val modelsList = response.body?.string() ?: ""
                        Log.d("AICoachingChat", "Available Models: $modelsList")  // Check for "gemini-2.5-flash" or "gemini-1.5-flash"
                    } else {
                        Log.e("AICoachingChat", "List Models Failed: ${response.code} - ${response.body?.string()}")
                    }
                }
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("AICoachingChat", "List Models Network Error: ${e.message}")
                }
            })
        }
    }

    // --- Core Chat Logic ---
    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val message = binding.inputMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                // 1. Add user message to UI
                addUserMessage(message)
                // 2. Clear input
                binding.inputMessage.setText("")
                // 3. Show loading & get response
                showLoading()
                getAIResponseHTTP(message)
            }
        }
    }

    private fun getAIResponseHTTP(userMessage: String) {
        // FIXED: Quick key check before API call
        if (apiKey.isEmpty()) {
            // You are already on the Main thread here, so just call the UI methods directly
            hideLoading()
            addBotResponse("Coach offline: API key issue. Restart app?")
            return
        }

        // ... rest of the function ...



        // Run network request on background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Add User Message to History
                val userEntry = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                }
                chatHistory.put(userEntry)

                // 2. Make Request
                val responseText = makeGeminiRequest()

                // 3. Add Model Response to History (so it remembers for next time)
                val modelEntry = JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", responseText)))
                }
                chatHistory.put(modelEntry)

                // 4. Update UI
                withContext(Dispatchers.Main) {
                    hideLoading()
                    addBotResponse(responseText)
                }
            } catch (e: Exception) {
                Log.e("AICoachingChat", "HTTP Error", e)
                // Remove the last user message from history so we can try again cleanly
                if (chatHistory.length() > 0) {
                    chatHistory.remove(chatHistory.length() - 1)
                }
                withContext(Dispatchers.Main) {
                    hideLoading()
                    // FIXED: Better error with retry option
                    val errorMsg = "Oops! Couldn't connect to the coach. (${e.message})\nTap to retry?"
                    addBotResponse(errorMsg)
                }
            }
        }
    }

    // FIXED: Enhanced API Request with trim + logging to catch spaces
    private fun makeGeminiRequest(): String {
        // FIXED: Trim any spaces from modelName
        val cleanModelName = modelName.trim { it <= ' ' }  // Removes leading/trailing spaces
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModelName:generateContent?key=$apiKey"
        Log.d("AICoachingChat", "Full API URL: $url")  // FIXED: Log to verify no space (e.g., ...gemini-2.5-flash...)

        // FIXED: Add generation_config for max tokens & temperature (concise, focused coaching)
        val generationConfig = JSONObject().apply {
            put("temperature", 0.7)
            put("maxOutputTokens", 150)
            put("topP", 0.8)
            put("topK", 40)
        }

        // Construct Request Body
        val jsonBody = JSONObject()
        jsonBody.put("contents", chatHistory)

        // System Instruction
        val systemPrompt = "You are an expert, friendly, and encouraging cricket batting coach named 'AI Cricket Coach'. Keep answers concise and under 150 words."
        val sysInstruction = JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
        }
        jsonBody.put("systemInstruction", sysInstruction)
        jsonBody.put("generationConfig", generationConfig)

        Log.d("AICoachingChat", "Request Body: ${jsonBody.toString().take(500)}...")

        val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e("AICoachingChat", "API Error ${response.code}: $errorBody")
                val errorMsg = try {
                    val jsonError = JSONObject(errorBody).getJSONObject("error")
                    "${jsonError.getString("status")} (Code ${jsonError.getInt("code")}): ${jsonError.getString("message")}"
                } catch (e: Exception) {
                    "Server Error ${response.code}: $errorBody"
                }
                throw IOException(errorMsg)
            }

            val responseBody = response.body?.string() ?: throw IOException("Empty Response Body")
            Log.d("AICoachingChat", "Full Response: $responseBody")  // FIXED: Log full for debug

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val contentObj = firstCandidate.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    if (text.isNotEmpty()) return text
                }
            }
            return "I'm listening, but let's try that again! What cricket tip do you need?"
        }
    }

    // FIXED: Added loading state for real-time feel
    private fun showLoading() {
        val loadingView = createMessageView("Typing...", false, isInitial = true)
        binding.chatContainer.addView(loadingView)
        scrollToBottom()
    }

    private fun hideLoading() {
        // Remove last view if it's loading
        if (binding.chatContainer.childCount > 0) {
            val lastChild = binding.chatContainer.getChildAt(binding.chatContainer.childCount - 1)
            if (lastChild is LinearLayout && lastChild.findViewById<TextView>(R.id.text_message_body)?.text == "Typing...") {
                binding.chatContainer.removeViewAt(binding.chatContainer.childCount - 1)
            }
        }
    }

    // --- Dynamic Message UI Creation (Enhanced with Timestamps) ---
    private fun addUserMessage(message: String) {
        val userMessageView = createMessageView(message, true)
        binding.chatContainer.addView(userMessageView)
        scrollToBottom()
    }

    private fun addBotResponse(response: String, isInitial: Boolean = false) {
        val botMessageView = createMessageView(response, false, isInitial)
        binding.chatContainer.addView(botMessageView)
        scrollToBottom()
    }

    private fun createMessageView(message: String, isUser: Boolean, isInitial: Boolean = false): View {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val layoutRes = if (isUser) R.layout.message_user else R.layout.message_bot
        val messageLayout = inflater.inflate(layoutRes, binding.chatContainer, false) as LinearLayout
        val textBody = messageLayout.findViewById<TextView>(R.id.text_message_body)
        textBody.text = message

        // FIXED: Add timestamp (assume timestamp_view in layouts)
        val timestampView = messageLayout.findViewById<TextView>(R.id.timestamp)  // Add to XML if missing
        if (timestampView != null) {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            timestampView.text = timeFormat.format(Date())
            timestampView.visibility = View.VISIBLE
        }

        if (!isInitial && binding.chatContainer.childCount > 0) {
            val layoutParams = messageLayout.layoutParams as LinearLayout.LayoutParams
            layoutParams.topMargin = resources.getDimensionPixelSize(R.dimen.chat_message_margin)  // Define in dimens.xml if missing (e.g., 16dp)
            messageLayout.layoutParams = layoutParams
        }
        return messageLayout
    }

    private fun scrollToBottom() {
        binding.scrollView.post {
            binding.scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    // --- Navigation (Unchanged) ---
    private fun setupBottomNavigation() {
        binding.btnDashboard.setOnClickListener {
            startActivity(Intent(this, Dashboard::class.java))
            finish()
        }
        binding.btnPractice.setOnClickListener {
            startActivity(Intent(this, PracticeSession::class.java))
            finish()
        }
        binding.btnProgress.setOnClickListener {
            startActivity(Intent(this, Progress::class.java))
            finish()
        }
        binding.btnTips.setOnClickListener {
            startActivity(Intent(this, TipsForYou::class.java))
            finish()
        }
    }

    private fun setupMenuButton() {
        binding.btnMenu.setOnClickListener {
            // Menu Logic (e.g., open drawer)
        }
    }
}