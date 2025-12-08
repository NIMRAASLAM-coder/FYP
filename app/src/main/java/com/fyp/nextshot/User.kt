package com.fyp.nextshot
data class User(
    val uid: String = "", // The Firebase Authentication User ID
    val fullName: String? = null,
    val email: String? = null,
    val dob: String? = null,
    val experienceLevel: String? = null,
    val profileImageUrl: String? = null // To store the cloud URL of the image
)