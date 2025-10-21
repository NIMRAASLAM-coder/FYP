package com.fyp.nextshot

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.util.*

class EditProfileActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var changePhoto: TextView
    private lateinit var closeBtn: ImageView
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etDob: EditText
    private lateinit var spinnerExperience: Spinner
    private lateinit var btnSave: Button

    private var selectedImageUri: Uri? = null

    // Launcher for image picker
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedImageUri = it
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

        // Spinner setup
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

        // Date picker for DOB
        etDob.setOnClickListener {
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

        // Open gallery to select profile photo
        changePhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Close button
        closeBtn.setOnClickListener {
            finish() // simply closes the screen
        }

        // Save button
        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val dob = etDob.text.toString().trim()
            val experience = spinnerExperience.selectedItem.toString()

            if (name.isEmpty() || email.isEmpty() || dob.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // You can replace this with actual Firebase/Room DB save logic
            Toast.makeText(
                this,
                "Profile Saved:\n$name\n$email\n$dob\n$experience",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
