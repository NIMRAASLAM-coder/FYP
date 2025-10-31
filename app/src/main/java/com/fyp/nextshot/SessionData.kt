package com.fyp.nextshot
data class SessionData(
    val id: Int,
    val title: String,
    val date: String,
    val score: Int,
    val accuracy: Int,
    val duration: Int,
    val shots: Int
)