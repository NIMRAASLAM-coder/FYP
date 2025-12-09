// File: src/main/java/com/fyp/nextshot/Keypoint.kt
package com.fyp.nextshot

data class Keypoint(
    val x: Float,
    val y: Float,
    val confidence: Float = 0f
)