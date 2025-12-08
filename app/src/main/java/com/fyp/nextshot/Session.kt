package com.fyp.nextshot

data class Session(
    val sessionId: String = "",
    val userId: String = "",
    val date: Long = System.currentTimeMillis(),
    val drillType: String? = null,
    val successRate: Double = 0.0,
    // Add fields related to BoundingBoxOverlay.kt analysis (e.g., List of flaw timestamps)
    val flawDetails: List<String>? = null
)