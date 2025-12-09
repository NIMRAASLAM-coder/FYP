package com.fyp.nextshot

data class User(
    var uid: String = "", // The Firebase Authentication User ID
    var fullName: String? = null,
    var email: String? = null,
    var dob: String? = null,
    var experienceLevel: String? = null,
    var profileImageUrl: String? = null // To store the cloud URL of the image
)