package com.fyp.nextshot.data.local.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_tips_table")
data class AiTipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: String = "",
    val sessionCloudId: String = "",  // Links to the session that triggered generation
    val title: String = "",
    val description: String = "",
    val tag: String = "",             // e.g., "Head Stability", "Footwork", "Weight Balance"
    val generatedAtMillis: Long = System.currentTimeMillis()
)
