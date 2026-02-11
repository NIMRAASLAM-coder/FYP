package com.fyp.nextshot.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "session_table")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String = "", // Default value for Firestore
    val cloudDocumentId: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val drillType: String = "",
    val durationSeconds: Int = 0,
    val successRate: Double = 0.0,
    // Store flaws as a single String for simplicity now
    val flawDetails: String? = null
)