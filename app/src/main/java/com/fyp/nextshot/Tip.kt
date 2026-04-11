package com.fyp.nextshot

data class Tip(
    val title: String,
    val description: String,
    val category: String,
    val videoUrl: String,
    val thumbnailUrl: String? = null,
    val thumbnailResId: Int = R.drawable.img_7
)