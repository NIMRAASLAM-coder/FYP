package com.fyp.nextshot

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*
import kotlin.collections.HashMap

// NOTE: You must ensure you have a data class named 'User' defined elsewhere
// (e.g., in data/local/models/) matching your Firestore document structure:
/*
data class User(
    val uid: String = "",
    val fullName: String? = null,
    val email: String? = null,
    val dob: String? = null,
    val experienceLevel: String? = null,
    val profileImageUrl: String? = null
)
*/

class EditProfileActivity : AppCompatActivity() {

    private val TAG = "EditProfileActivity"

    // Firebase instances
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val currentUserUid: String? = auth.currentUser?.uid


    // View initialization properties
    private lateinit var profileImage: ImageView
    private lateinit var changePhoto: TextView
    private lateinit var closeBtn: ImageView
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etDob: EditText
    private lateinit var spinnerExperience: Spinner
    private lateinit var btnSave: Button

    // State for image handling
    private var selectedImageUri: Uri? = null // Holds the URI if a NEW image is selected
    private var currentProfileImageUrl: String? = null // Holds the existing image URL from Firestore

    // Launcher for image picker
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedImageUri = it
                // Preview the new image immediately (GET operation for local preview)
                Glide.with(this)
                    .load(it)
                    .circleCrop()
                    .into(profileImage)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        // Initialize views
        profileImage = findViewById(R.id.profile_image)
        changePhoto = findViewById(R.id.change_photo)
        closeBtn = findViewById(R.id.btn_close)
        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email)
        etDob = findViewById(R.id.et_dob)
        spinnerExperience = findViewById(R.id.spinner_experience)
        btnSave = findViewById(R.id.btn_save)

        // 1. Setup Spinner and Load Data
        setupSpinner()
        loadUserProfile()

        // 2. Set Listeners
        etDob.setOnClickListener { showDatePickerDialog() }

        // Clicking the text or image launches the picker
        changePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        profileImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        closeBtn.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            // CRITICAL: Call the function that handles image upload FIRST
            uploadImageAndSaveProfile()
        }
    }

    private fun setupSpinner() {
        val levels = listOf(
            "Beginner level",
            "Intermediate level",
            "Advanced level",
            "Professional level"
        )
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            levels
        )
        spinnerExperience.adapter = spinnerAdapter
    }

    private fun showDatePickerDialog() {
        val c = Calendar.getInstance()
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        DatePickerDialog(
            this,
            { _, y, m, d ->
                etDob.setText(String.format("%02d/%02d/%04d", d, m + 1, y))
            },
            year,
            month,
            day
        ).show()
    }

    /**
     * Handles the required Data Sync (GET) for persistence and Image GET from URL.
     */
    private fun loadUserProfile() {
        etEmail.isEnabled = false

        currentUserUid?.let { uid ->
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val user = document.toObject(User::class.java)
                        user?.let {
                            etName.setText(it.fullName)
                            etEmail.setText(it.email ?: auth.currentUser?.email)
                            etDob.setText(it.dob)

                            val levels = listOf(
                                "Beginner level",
                                "Intermediate level",
                                "Advanced level",
                                "Professional level"
                            )
                            val index = levels.indexOf(it.experienceLevel)
                            if (index >= 0) {
                                spinnerExperience.setSelection(index)
                            }

                            // Image GET (Retrieve URL and load via Glide)
                            it.profileImageUrl?.let { imageUrl ->
                                currentProfileImageUrl = imageUrl // Store existing URL
                                Glide.with(this)
                                    .load(imageUrl)
                                    .circleCrop()
                                    .placeholder(R.drawable.user) // Use a default icon
                                    .into(profileImage)
                            }
                        }
                    } else {
                        etEmail.setText(auth.currentUser?.email)
                        Toast.makeText(this, "Profile not found. Creating new profile.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    val specificError = e.message ?: "Unknown Network Failure"
                    Log.e(TAG, "FAILED TO LOAD PROFILE: $specificError", e)

                    Toast.makeText(this, "Failed to load profile data: $specificError", Toast.LENGTH_LONG).show()
                }
        } ?: run {
            Toast.makeText(this, "Authentication error. Please log in.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Step 1: Checks if a new image was selected.
     * Step 2: Uploads the new image (POST) to Firebase Storage using continueWithTask for robust URL retrieval.
     * Step 3: Saves the profile data and the new URL to Firestore (UPDATE).
     */
    private fun uploadImageAndSaveProfile() {
        val uid = currentUserUid ?: return
        val imageUri = selectedImageUri

        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        if (imageUri != null) {
            // Case 1: NEW image selected -> UPLOAD (POST) required
            val profilePicRef = storage.reference.child("profile_images/$uid.jpg")
            
            Log.d(TAG, "Starting upload to: ${profilePicRef.path}")
            
            val uploadTask = profilePicRef.putFile(imageUri)

            // Chain the upload and getDownloadUrl tasks. 
            // This prevents race conditions where downloadUrl is called before upload completes.
            uploadTask.continueWithTask { task ->
                if (!task.isSuccessful) {
                    // This handles errors during the upload itself (putFile)
                    task.exception?.let {
                        throw it
                    }
                }
                // Upload success, now request download URL
                profilePicRef.downloadUrl
            }.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val downloadUri = task.result
                    val newImageUrl = downloadUri.toString()
                    Log.d(TAG, "Image upload and URL retrieval success: $newImageUrl")
                    saveProfileData(newImageUrl)
                } else {
                    // This handles errors from either upload or downloadUrl
                    val e = task.exception
                    Log.e(TAG, "Upload/Url failed: ${e?.message}", e)
                    
                    // Specific feedback for the user
                    Toast.makeText(this, "Upload failed: ${e?.message}", Toast.LENGTH_LONG).show()
                    
                    btnSave.isEnabled = true
                    btnSave.text = "Save"
                }
            }
        } else {
            // Case 2: NO new image selected -> No POST required, just UPDATE profile data
            saveProfileData(currentProfileImageUrl) 
        }
    }

    /**
     * Final step: Saves text data and the finalized image URL (UPDATE).
     */
    private fun saveProfileData(imageUrl: String?) {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val dob = etDob.text.toString().trim()
        val experience = spinnerExperience.selectedItem.toString()
        val uid = currentUserUid ?: return

        if (name.isEmpty() || dob.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            btnSave.isEnabled = true
            btnSave.text = "Save"
            return
        }

        val userUpdates = User(
            uid = uid,
            fullName = name,
            email = email,
            dob = dob,
            experienceLevel = experience,
            profileImageUrl = imageUrl // Final image URL (new or existing)
        )

        // UPDATE operation on Firestore
        db.collection("users").document(uid).set(userUpdates) // Use set() to create/update
            .addOnSuccessListener {
                Toast.makeText(this, "Profile and image saved successfully!", Toast.LENGTH_SHORT).show()
                finish() // Close activity on success
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving profile: ${e.message}")
                Toast.makeText(this, "Error saving profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
            .addOnCompleteListener {
                btnSave.isEnabled = true
                btnSave.text = "Save"
            }
    }
}