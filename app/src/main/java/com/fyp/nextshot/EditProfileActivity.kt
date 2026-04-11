package com.fyp.nextshot

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.util.*

class EditProfileActivity : AppCompatActivity() {

    private val TAG = "EditProfileActivity"

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val currentUserUid: String? = auth.currentUser?.uid

    private lateinit var profileImage: ImageView
    private lateinit var changePhoto: TextView
    private lateinit var closeBtn: ImageView
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etDob: EditText
    private lateinit var spinnerExperience: Spinner
    private lateinit var btnSave: Button

    private var selectedImageUri: Uri? = null 
    private var currentProfileImageUrl: String? = null 

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedImageUri = it
                Glide.with(this).load(it).circleCrop().into(profileImage)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        profileImage = findViewById(R.id.profile_image)
        changePhoto = findViewById(R.id.change_photo)
        closeBtn = findViewById(R.id.btn_close)
        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email)
        etDob = findViewById(R.id.et_dob)
        spinnerExperience = findViewById(R.id.spinner_experience)
        btnSave = findViewById(R.id.btn_save)

        setupSpinner()
        loadUserProfile()

        etDob.setOnClickListener { showDatePickerDialog() }
        changePhoto.setOnClickListener { pickImageLauncher.launch("image/*") }
        profileImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        closeBtn.setOnClickListener { finish() }

        btnSave.setOnClickListener {
            handleSave()
        }
    }

    private fun setupSpinner() {
        val levels = listOf("Beginner level", "Intermediate level", "Advanced level", "Professional level")
        spinnerExperience.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, levels)
    }

    private fun showDatePickerDialog() {
        val c = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            etDob.setText(String.format("%02d/%02d/%04d", d, m + 1, y))
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

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
                            val levels = listOf("Beginner level", "Intermediate level", "Advanced level", "Professional level")
                            val index = levels.indexOf(it.experienceLevel)
                            if (index >= 0) spinnerExperience.setSelection(index)

                            it.profileImageUrl?.let { imageData ->
                                currentProfileImageUrl = imageData
                                if (imageData.isNotEmpty()) {
                                    // Glide can load Base64 strings if formatted as Data URI
                                    // or we can decode it. Glide automatically handles many formats.
                                    val imageBytes = if (imageData.startsWith("data:image")) {
                                        Base64.decode(imageData.substringAfter(","), Base64.DEFAULT)
                                    } else {
                                        Base64.decode(imageData, Base64.DEFAULT)
                                    }
                                    Glide.with(this).load(imageBytes).circleCrop().placeholder(R.drawable.user).into(profileImage)
                                }
                            }
                        }
                    } else {
                        etEmail.setText(auth.currentUser?.email)
                    }
                }
        }
    }

    private fun handleSave() {
        val uid = auth.currentUser?.uid ?: return
        
        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        if (selectedImageUri != null) {
            // Convert and Compress Image to Base64
            val base64String = encodeImageToBase64(selectedImageUri!!)
            if (base64String != null) {
                saveProfileData(base64String)
            } else {
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "Save And Continue"
            }
        } else {
            saveProfileData(currentProfileImageUrl)
        }
    }

    private fun encodeImageToBase64(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Resize if too large (Max width/height 400px for profile pics)
            val scaledBitmap = if (bitmap.width > 400 || bitmap.height > 400) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                if (ratio > 1) Bitmap.createScaledBitmap(bitmap, 400, (400 / ratio).toInt(), true)
                else Bitmap.createScaledBitmap(bitmap, (400 * ratio).toInt(), 400, true)
            } else bitmap

            val outputStream = ByteArrayOutputStream()
            // Compress to JPEG with 70% quality to keep Base64 string small
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val byteArray = outputStream.toByteArray()
            
            // Return as Data URI string
            "data:image/jpeg;base64," + Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding image: ${e.message}")
            null
        }
    }

    private fun saveProfileData(imageData: String?) {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val dob = etDob.text.toString().trim()
        val experience = spinnerExperience.selectedItem.toString()
        val uid = auth.currentUser?.uid ?: return

        if (name.isEmpty() || dob.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            btnSave.isEnabled = true
            btnSave.text = "Save And Continue"
            return
        }

        val userUpdates = User(uid, name, email, dob, experience, imageData)

        db.collection("users").document(uid).set(userUpdates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "Save And Continue"
            }
    }
}