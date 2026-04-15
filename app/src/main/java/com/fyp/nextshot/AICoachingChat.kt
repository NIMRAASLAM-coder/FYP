package com.fyp.nextshot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import java.util.Locale as JavaLocale
import java.util.concurrent.TimeUnit
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.view.GravityCompat
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class AICoachingChat : AppCompatActivity() {
    private lateinit var binding: ActivityAicoachingChatBinding
    
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // ------------------------------------------------------------------------
    // SETUP
    // ------------------------------------------------------------------------
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
    private val modelName = "gemini-2.5-flash"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val chatHistory = JSONArray()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isListening = false
    private val REQUEST_RECORD_AUDIO = 1001

    // ------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAicoachingChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (apiKey.isEmpty()) {
            Log.e("AICoachingChat", "API Key is empty!")
            addBotResponse("Coach setup error: API key missing.", isInitial = true)
        }

        setupToolbarAndDrawer()
        setupBottomNavigation()
        setupSendButton()
        setupVoiceInput()

        initializeChatHistory()
        loadUserData()

        addBotResponse("Hello! 👋 I'm your AI Cricket Coach. Ask me how to improve your batting, timing, or technique!", isInitial = true)
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            handleDrawerNavigation(menuItem)
            true
        }
    }

    private fun handleDrawerNavigation(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.nav_dashboard -> {
                startActivity(Intent(this, Dashboard::class.java))
                finish()
            }
            R.id.nav_practice -> {
                startActivity(Intent(this, PracticeSession::class.java))
                finish()
            }
            R.id.nav_progress -> {
                startActivity(Intent(this, Progress::class.java))
                finish()
            }
            R.id.nav_tips -> {
                startActivity(Intent(this, TipsForYou::class.java))
                finish()
            }
            R.id.AI -> {
                // Already here
            }
            R.id.profile -> startActivity(Intent(this, EditProfileActivity::class.java))
            R.id.notification -> startActivity(Intent(this, NotificationActivity::class.java))
            R.id.session_history -> startActivity(Intent(this, SessionHistory::class.java))
            R.id.settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.signout -> startActivity(Intent(this, SignOutConfirmationActivity::class.java))
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = document.toObject(User::class.java)
                    user?.let {
                        val headerView = binding.navView.getHeaderView(0)
                        val headerProfileImage = headerView.findViewById<android.widget.ImageView>(R.id.profile_image)
                        val headerUserName = headerView.findViewById<TextView>(R.id.user_name)
                        val headerUserEmail = headerView.findViewById<TextView>(R.id.user_email)
                        val headerUserAge = headerView.findViewById<TextView>(R.id.user_age)
                        val headerUserExperience = headerView.findViewById<TextView>(R.id.user_experience)
                        
                        headerUserName.text = it.fullName ?: "Player"
                        headerUserEmail.text = it.email ?: auth.currentUser?.email
                        headerUserAge.text = ProfileUtils.calculateAge(it.dob)
                        headerUserExperience.text = it.experienceLevel ?: "Experience: N/A"

                        ProfileUtils.loadProfileImage(this, it.profileImageUrl, headerProfileImage, R.drawable.img_21)
                    }
                }
            }
    }

    private fun initializeChatHistory() {
        val systemPrompt = "You are an expert, friendly, and encouraging cricket batting coach named 'AI Cricket Coach'. Keep answers concise (under 150 words), actionable, and focused on technique improvement."
        val sysEntry = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
        }
        chatHistory.put(sysEntry)
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            val message = binding.inputMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                addUserMessage(message)
                binding.inputMessage.setText("")
                showLoading()
                getAIResponseHTTP(message)
            }
        }
    }

    private fun setupVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        } else {
            initSpeechRecognizer()
            initTextToSpeech()
        }

        binding.btnMic.setOnClickListener {
            if (!isListening) startListening() else stopListening()
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                Toast.makeText(this@AICoachingChat, "Listening...", Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isListening = false
                binding.btnMic.setImageResource(R.drawable.mic)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { transcript ->
                    binding.inputMessage.setText(transcript)
                    binding.btnSend.performClick()
                }
                isListening = false
                binding.btnMic.setImageResource(R.drawable.mic)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                partial?.firstOrNull()?.let { binding.inputMessage.setText(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.setLanguage(JavaLocale.getDefault())
            }
        }
    }

    private fun startListening() {
        isListening = true
        binding.btnMic.setImageResource(R.drawable.stop)
        speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, JavaLocale.getDefault())
        })
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        binding.btnMic.setImageResource(R.drawable.mic)
    }

    private fun speakResponse(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach_${System.currentTimeMillis()}")
    }

    private fun getAIResponseHTTP(userMessage: String) {
        if (apiKey.isEmpty()) {
            hideLoading()
            addBotResponse("Coach offline: API key issue.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userEntry = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                }
                chatHistory.put(userEntry)

                val responseText = makeGeminiRequest()

                val modelEntry = JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().put(JSONObject().put("text", responseText)))
                }
                chatHistory.put(modelEntry)

                withContext(Dispatchers.Main) {
                    hideLoading()
                    addBotResponse(responseText)
                }
            } catch (e: Exception) {
                if (chatHistory.length() > 0) chatHistory.remove(chatHistory.length() - 1)
                withContext(Dispatchers.Main) {
                    hideLoading()
                    addBotResponse("Oops! Couldn't connect to the coach.")
                }
            }
        }
    }

    private fun makeGeminiRequest(): String {
        val cleanModelName = modelName.trim()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModelName:generateContent?key=$apiKey"
        
        val generationConfig = JSONObject().apply {
            put("temperature", 0.7)
            put("maxOutputTokens", 150)
        }

        val jsonBody = JSONObject()
        jsonBody.put("contents", chatHistory)

        val systemPrompt = "You are an expert, friendly, and encouraging cricket batting coach. Keep answers concise."
        val sysInstruction = JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
        }
        jsonBody.put("systemInstruction", sysInstruction)
        jsonBody.put("generationConfig", generationConfig)

        val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("API Error ${response.code}")
            val responseBody = response.body?.string() ?: throw IOException("Empty Response")
            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text", "")
                }
            }
            return "I'm listening, but let's try that again!"
        }
    }

    private fun showLoading() {
        val loadingView = createMessageView("Typing...", false, isInitial = true)
        binding.chatContainer.addView(loadingView)
        scrollToBottom()
    }

    private fun hideLoading() {
        if (binding.chatContainer.childCount > 0) {
            val lastChild = binding.chatContainer.getChildAt(binding.chatContainer.childCount - 1)
            if (lastChild is LinearLayout && lastChild.findViewById<TextView>(R.id.text_message_body)?.text == "Typing...") {
                binding.chatContainer.removeViewAt(binding.chatContainer.childCount - 1)
            }
        }
    }

    private fun addUserMessage(message: String) {
        val userMessageView = createMessageView(message, true)
        binding.chatContainer.addView(userMessageView)
        scrollToBottom()
    }

    private fun addBotResponse(response: String, isInitial: Boolean = false) {
        val botMessageView = createMessageView(response, false, isInitial)
        binding.chatContainer.addView(botMessageView)
        scrollToBottom()
        if (!isInitial) speakResponse(response)
    }

    private fun createMessageView(message: String, isUser: Boolean, isInitial: Boolean = false): View {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val layoutRes = if (isUser) R.layout.message_user else R.layout.message_bot
        val messageLayout = inflater.inflate(layoutRes, binding.chatContainer, false) as LinearLayout
        val textBody = messageLayout.findViewById<TextView>(R.id.text_message_body)
        textBody.text = message

        val timestampView = messageLayout.findViewById<TextView>(R.id.timestamp)
        if (timestampView != null) {
            timestampView.text = SimpleDateFormat("HH:mm", JavaLocale.getDefault()).format(Date())
            timestampView.visibility = View.VISIBLE
        }

        return messageLayout
    }

    private fun scrollToBottom() {
        binding.scrollView.post {
            binding.scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initSpeechRecognizer()
            initTextToSpeech()
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}