package com.fyp.nextshot.data.repository

import android.util.Log
import com.fyp.nextshot.BuildConfig
import com.fyp.nextshot.data.local.dao.AiTipDao
import com.fyp.nextshot.data.local.dao.SessionDao
import com.fyp.nextshot.data.local.models.AiTipEntity
import com.fyp.nextshot.data.local.models.SessionEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class TipsRepository(
    private val aiTipDao: AiTipDao,
    private val sessionDao: SessionDao,
    private val currentUserId: String,
    private val firestore: FirebaseFirestore
) {
    companion object {
        private const val TAG = "TipsRepository"
        private const val CACHE_DURATION_MS = 30 * 60 * 1000L  // 30 minutes cache
    }

    // Groq API configuration
    private val groqApiKey: String = BuildConfig.GROQ_API_KEY
    private val groqModel = "llama-3.3-70b-versatile"
    private val groqUrl = "https://api.groq.com/openai/v1/chat/completions"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // --- PUBLIC API ---

    /**
     * Observable flow of the latest AI tips for the current user.
     */
    val latestTips: Flow<List<AiTipEntity>> = aiTipDao.getLatestTipsForUser(currentUserId)

    /**
     * Generates AI tips based on recent session data.
     * Skips generation if cache is still fresh (within 30 min).
     * @param forceRefresh If true, bypasses cache check.
     */
    suspend fun generateTips(forceRefresh: Boolean = false) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Cache check
                if (!forceRefresh) {
                    val lastTimestamp = aiTipDao.getLatestTipTimestamp(currentUserId)
                    if (lastTimestamp != null) {
                        val elapsed = System.currentTimeMillis() - lastTimestamp
                        if (elapsed < CACHE_DURATION_MS) {
                            Log.d(TAG, "Tips cache still fresh (${elapsed / 1000}s old). Skipping API call.")
                            return@withContext
                        }
                    }
                }

                // 2. Fetch recent sessions from Firestore
                val recentSessions = fetchRecentSessions()
                if (recentSessions.isEmpty()) {
                    Log.d(TAG, "No sessions found. Cannot generate tips.")
                    return@withContext
                }

                Log.d(TAG, "Found ${recentSessions.size} sessions to analyze")

                // 3. Extract flaw scores from sessions
                val flawData = extractFlawScores(recentSessions)
                Log.d(TAG, "Extracted flaw data: $flawData")

                if (flawData.isEmpty()) {
                    Log.d(TAG, "No flaw data found in sessions. Sessions might not be Pose Analysis type.")
                    return@withContext
                }

                // 4. Build the prompt with real scores
                val prompt = buildCoachingPrompt(flawData, recentSessions)
                Log.d(TAG, "Prompt built:\n$prompt")

                // 5. Call Groq API to generate feedback
                val aiResponse = callGroqApi(prompt)
                Log.d(TAG, "Groq response received (${aiResponse.length} chars):\n$aiResponse")

                // 6. Save as a single comprehensive tip
                val tip = AiTipEntity(
                    userId = currentUserId,
                    sessionCloudId = recentSessions.first().cloudDocumentId,
                    title = "Your Performance Review",
                    description = aiResponse,
                    tag = "Performance Review",
                    generatedAtMillis = System.currentTimeMillis()
                )

                aiTipDao.deleteAllForUser(currentUserId)
                aiTipDao.insertAll(listOf(tip))
                Log.d(TAG, "Saved AI performance review to database")

                // 7. Sync to Firestore
                syncTipsToFirestore(listOf(tip))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate tips: ${e.message}", e)
                throw e
            }
        }
    }

    // --- PRIVATE HELPERS ---

    /**
     * Fetches the last 5 Pose Analysis sessions from Firestore.
     */
    private suspend fun fetchRecentSessions(): List<SessionEntity> {
        return suspendCancellableCoroutine { continuation ->
            firestore.collection("sessions")
                .whereEqualTo("userId", currentUserId)
                .orderBy("dateMillis", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)  // Fetch more, then filter for Pose Analysis
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val sessions = querySnapshot.documents.mapNotNull { doc ->
                        try {
                            SessionEntity(
                                id = doc.getLong("id")?.toInt() ?: 0,
                                userId = doc.getString("userId") ?: "",
                                cloudDocumentId = doc.id,
                                dateMillis = doc.getLong("dateMillis") ?: 0L,
                                drillType = doc.getString("drillType") ?: "",
                                durationSeconds = doc.getLong("durationSeconds")?.toInt() ?: 0,
                                successRate = doc.getDouble("successRate") ?: 0.0,
                                flawDetails = doc.getString("flawDetails")
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing session doc: ${e.message}")
                            null
                        }
                    }.filter { session ->
                        // Only include Pose Analysis sessions with flaw data
                        session.drillType == "Pose Analysis" && !session.flawDetails.isNullOrEmpty()
                                && session.flawDetails.contains("Head:")
                    }.take(5)

                    Log.d(TAG, "Fetched ${sessions.size} Pose Analysis sessions from Firestore")
                    sessions.forEach { s ->
                        Log.d(TAG, "  Session: drill=${s.drillType}, flaws=${s.flawDetails}")
                    }

                    continuation.resume(sessions)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to fetch sessions from Firestore: ${e.message}")
                    continuation.resume(emptyList())
                }
        }
    }

    /**
     * Extracts Head, Shoulders, Weight, Feet scores from session flawDetails.
     * Format: "Head: 85% | Shoulders: 92% | Weight: 78% | Feet: 65%"
     */
    private fun extractFlawScores(sessions: List<SessionEntity>): Map<String, List<Int>> {
        val allScores = mutableMapOf<String, MutableList<Int>>()

        sessions.forEach { session ->
            val flaws = session.flawDetails ?: return@forEach
            Log.d(TAG, "Parsing flaws: $flaws")

            flaws.split("|").forEach { part ->
                val trimmed = part.trim()
                val colonIndex = trimmed.indexOf(":")
                if (colonIndex > 0) {
                    val area = trimmed.substring(0, colonIndex).trim()
                    val scoreStr = trimmed.substring(colonIndex + 1).trim()
                        .replace("%", "").replace(" ", "")
                    scoreStr.toIntOrNull()?.let { score ->
                        allScores.getOrPut(area) { mutableListOf() }.add(score)
                        Log.d(TAG, "  Parsed: $area = $score%")
                    }
                }
            }
        }

        return allScores
    }

    /**
     * Builds a coaching prompt using real extracted flaw scores.
     */
    private fun buildCoachingPrompt(
        flawData: Map<String, List<Int>>,
        sessions: List<SessionEntity>
    ): String {
        val sb = StringBuilder()

        sb.appendLine("You are an expert cricket batting coach. A batsman has completed ${sessions.size} practice session(s).")
        sb.appendLine("Here are their average scores from pose analysis:\n")

        // Calculate averages for each area
        flawData.forEach { (area, scores) ->
            val avg = scores.average().toInt()
            val trend = when {
                scores.size >= 2 && scores.last() > scores.first() -> "improving"
                scores.size >= 2 && scores.last() < scores.first() -> "declining"
                else -> "stable"
            }
            sb.appendLine("• $area: ${avg}% average (trend: $trend, scores: ${scores.joinToString(", ")}%)")
        }

        sb.appendLine()
        sb.appendLine("Generate a comprehensive performance review with bullet points for EACH area above.")
        sb.appendLine()
        sb.appendLine("Rules:")
        sb.appendLine("- Use • bullet for each area")
        sb.appendLine("- If score is 85%+: Praise the batsman enthusiastically and suggest advanced refinements")
        sb.appendLine("- If score is 60-84%: Appreciate the effort and give a specific drill to improve")
        sb.appendLine("- If score is below 60%: Encourage the batsman ('This is your biggest growth area!') and give a step-by-step drill")
        sb.appendLine("- Always mention the actual percentage score")
        sb.appendLine("- End with an overall summary line starting with 🏏")
        sb.appendLine("- Use warm, motivational coaching tone")
        sb.appendLine("- Keep each bullet to 2-3 sentences")
        sb.appendLine("- Do NOT use any JSON formatting, markdown, or code blocks")
        sb.appendLine("- Return ONLY the plain text review with bullet points")

        return sb.toString()
    }

    /**
     * Calls Groq API (OpenAI-compatible) to generate coaching feedback.
     */
    private fun callGroqApi(prompt: String): String {
        if (groqApiKey.isEmpty()) {
            throw IllegalStateException("Groq API key is not configured. Add GROQ_API_KEY to local.properties")
        }

        Log.d(TAG, "Calling Groq API with model: $groqModel")

        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", "You are an expert cricket batting coach AI. Generate personalized performance reviews based on session data. Respond with plain text only — no JSON, no markdown, no code blocks.")
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val jsonBody = JSONObject().apply {
            put("model", groqModel)
            put("messages", messagesArray)
            put("temperature", 0.7)
            put("max_tokens", 1024)
        }

        val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(groqUrl)
            .addHeader("Authorization", "Bearer $groqApiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Groq API Error ${response.code}: $responseBody")
                throw java.io.IOException("Groq API Error ${response.code}: $responseBody")
            }

            Log.d(TAG, "Groq API Response: $responseBody")

            // Parse OpenAI-compatible response
            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                val content = message?.optString("content", "") ?: ""
                if (content.isNotBlank()) {
                    return content.trim()
                }
            }

            throw java.io.IOException("Unexpected Groq API response format")
        }
    }

    /**
     * Syncs tips to Firestore for cross-device access.
     */
    private fun syncTipsToFirestore(tips: List<AiTipEntity>) {
        val batch = firestore.batch()
        val collection = firestore.collection("ai_tips")

        // Delete existing tips for user first
        firestore.collection("ai_tips")
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { snapshot ->
                val deleteBatch = firestore.batch()
                snapshot.documents.forEach { doc ->
                    deleteBatch.delete(doc.reference)
                }
                deleteBatch.commit().addOnSuccessListener {
                    // Now insert new tips
                    tips.forEach { tip ->
                        val docRef = collection.document()
                        val data = mapOf(
                            "userId" to tip.userId,
                            "sessionCloudId" to tip.sessionCloudId,
                            "title" to tip.title,
                            "description" to tip.description,
                            "tag" to tip.tag,
                            "generatedAtMillis" to tip.generatedAtMillis
                        )
                        batch.set(docRef, data)
                    }
                    batch.commit()
                        .addOnSuccessListener { Log.d(TAG, "Tips synced to Firestore") }
                        .addOnFailureListener { Log.e(TAG, "Firestore sync failed: ${it.message}") }
                }
            }
            .addOnFailureListener { Log.e(TAG, "Failed to clean old tips: ${it.message}") }
    }
}
