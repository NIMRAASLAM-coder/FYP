package com.fyp.nextshot

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*


// ------------------------------------------------------------------------------------

class EditProfileActivity : AppCompatActivity() {

    // Firebase instances
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
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
    private var selectedImageUri: Uri? = null
    private var currentProfileImageUrl: String? = null // To hold the existing image URL

    // Launcher for image picker
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedImageUri = it
                // Preview the image using Glide
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

        changePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        closeBtn.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveUserProfile()
        }
    }

    private fun setupSpinner() {
        // Experience levels (Make sure these match any array resource you might be using later)
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
                // Format the date (dd/mm/yyyy)
                etDob.setText(String.format("%02d/%02d/%04d", d, m + 1, y))
            },
            year,
            month,
            day
        ).show()
    }

    /**
     * Loads the existing profile data for the current user from Firebase Firestore.
     * This handles the required Data Sync (GET) for persistence.
     */
    private fun loadUserProfile() {
        // Disable email editing if logged in via email/password, as it's the primary key.
        etEmail.isEnabled = false

        currentUserUid?.let { uid ->
            // Fetch document from 'users' collection with the UID as the document ID
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val user = document.toObject(User::class.java)
                        user?.let {
                            // Populate EditText fields
                            etName.setText(it.fullName)
                            etEmail.setText(it.email ?: auth.currentUser?.email)
                            etDob.setText(it.dob)

                            // Populate Spinner
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

                            // Load profile image from URL using Glide
                            it.profileImageUrl?.let { imageUrl ->
                                currentProfileImageUrl = imageUrl // Store for potential re-use
                                Glide.with(this)
                                    .load(imageUrl)
                                    .circleCrop()
                                    .into(profileImage)
                            }
                        }
                    } else {
                        // Document doesn't exist (first login), populate with available auth data
                        etEmail.setText(auth.currentUser?.email)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to load profile data. Check network.", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            // Handle case where user is somehow not authenticated
            Toast.makeText(this, "Authentication error. Please log in.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Saves the updated profile data to Firebase Firestore.
     * This handles the required Data Update/Insert (POST/PUT).
     */
    private fun saveUserProfile() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val dob = etDob.text.toString().trim()
        val experience = spinnerExperience.selectedItem.toString()
        val uid = currentUserUid ?: return

        if (name.isEmpty() || dob.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }


        // IMPORTANT: In the next step (Step 3), we will wrap this saving logic
        // with the image upload process. For now, we save only the text fields.

        val userUpdates = User(
            uid = currentUserUid!!,
            fullName = name,
            email = email,
            dob = dob,
            experienceLevel = experience,
            profileImageUrl = currentProfileImageUrl // Keep existing image URL for now
        )

        currentUserUid?.let { uid ->
            db.collection("users").document(uid).set(userUpdates) // Use set() to create/update
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving profile: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}