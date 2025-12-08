package com.fyp.nextshot.data.repository

import android.util.Log
import com.fyp.nextshot.data.local.dao.SessionDao
import com.fyp.nextshot.data.local.models.SessionEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.firebase.firestore.DocumentReference

class SessionRepository(
    private val sessionDao: SessionDao,
    private val currentUserId: String,
    private val firestore: FirebaseFirestore
) {

    // 1. GET: Flow of local data (fast loading for history/progress)
    val allSessions: Flow<List<SessionEntity>> = sessionDao.getAllSessionsForUser(currentUserId)

    /**
     * Inserts a new session, saves it locally (Room), and synchronizes it to the cloud (Firestore).
     * It then updates the local record with the Firestore-generated Document ID.
     */
    suspend fun insert(session: SessionEntity) {
        // 1. Save Locally (INSERT) - Room generates the local primary key (id)
        val localId = sessionDao.insertSession(session)

        // 2. Prepare the entity with the newly generated local ID
        val sessionWithLocalId = session.copy(id = localId.toInt())

        try {
            // 3. Insert to Cloud (Firestore) and wait for result using suspendCancellableCoroutine
            val documentReference = suspendCancellableCoroutine<DocumentReference> { continuation ->
                firestore.collection("sessions")
                    .add(sessionWithLocalId)
                    .addOnSuccessListener { ref -> continuation.resume(ref) }
                    .addOnFailureListener { e -> continuation.resumeWithException(e) }
            }

            val cloudId = documentReference.id

            // 4. Update the local Room record with the Cloud ID
            val updatedSession = sessionWithLocalId.copy(cloudDocumentId = cloudId)
            sessionDao.updateSession(updatedSession)
            // In insert() function, after updateSession:
            Log.d("SYNC", "Session $localId synced. Cloud ID: $cloudId. Flaws: ${sessionWithLocalId.flawDetails?.take(50)}...")  // ADDED: Log snippet for keypoints

            Log.d("SYNC", "Session $localId synced. Cloud ID: $cloudId")
        } catch (e: Exception) {
            Log.e("SYNC", "Failed to sync session $localId to cloud: ${e.message}")
        }
    }

    /**
     * Implements the critical UPDATE functionality for post-model analysis.
     * Updates the session record in both the Cloud and the Local database.
     */
    suspend fun updateAnalysisResult(
        cloudDocumentId: String,
        newAccuracy: Double,
        newFlaws: String
    ) {
        if (cloudDocumentId.isEmpty()) {
            Log.e("UPDATE", "Cannot update session: missing Cloud Document ID.")
            return
        }

        // 1. Update Cloud Record (Firestore)
        val updates = mapOf(
            "successRate" to newAccuracy,
            "flawDetails" to newFlaws,
            "analysisComplete" to true
        )

        try {
            suspendCancellableCoroutine<Void?> { continuation ->
                firestore.collection("sessions").document(cloudDocumentId).update(updates)
                    .addOnSuccessListener { continuation.resume(null) }
                    .addOnFailureListener { e -> continuation.resumeWithException(e) }
            }
            Log.d("UPDATE", "Analysis results updated on cloud successfully.")
        } catch (e: Exception) {
            Log.e("UPDATE", "Failed to update analysis on cloud: ${e.message}")
        }

        // 2. Update Local Record (Room)
        val localSession = sessionDao.getSessionByCloudId(cloudDocumentId)

        localSession?.let {
            val updatedLocalSession = it.copy(
                successRate = newAccuracy,
                flawDetails = newFlaws
            )
            sessionDao.updateSession(updatedLocalSession)
            Log.d("UPDATE", "Local session updated with analysis.")
        }
    }

    // --- Standard CRUD Functions ---

    suspend fun update(session: SessionEntity) {
        sessionDao.updateSession(session)
    }

    suspend fun delete(session: SessionEntity) {
        sessionDao.deleteSession(session)

        // Delete from Firestore
        if (session.cloudDocumentId.isNotEmpty()) {
            try {
                suspendCancellableCoroutine<Void?> { continuation ->
                    firestore.collection("sessions").document(session.cloudDocumentId)
                        .delete()
                        .addOnSuccessListener { continuation.resume(null) }
                        .addOnFailureListener { e -> continuation.resumeWithException(e) }
                }
                Log.d("DELETE", "Cloud session deleted.")
            } catch (e: Exception) {
                Log.e("DELETE", "Failed to delete cloud session: $e")
            }
        }
    }
}