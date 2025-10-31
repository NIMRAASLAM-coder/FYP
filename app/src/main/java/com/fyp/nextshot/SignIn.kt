package com.fyp.nextshot

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class SignIn : AppCompatActivity() {

    private val TAG = "SignInActivity"
    private var isPasswordVisible = false
    private lateinit var auth: FirebaseAuth

    // Google Sign-In
    private lateinit var googleSignInClient: GoogleSignInClient
    // Use the Activity Result API for handling the sign-in intent result
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        // Apply window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        // Check if user is already signed in
        if (auth.currentUser != null) {
            navigateToDashboard()
            return
        }

        // --- NEW: Configure Google Sign In ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Requires string resource
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        // 3. Initialize ActivityResultLauncher for Google Sign-in
        setupGoogleSignInLauncher()

        // Initialize views with updated IDs
        val signInButton = findViewById<Button>(R.id.signInButton)
        val signUpTab = findViewById<TextView>(R.id.signUpTab)
        val forgotPassword = findViewById<TextView>(R.id.forgotPassword)
        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val passwordToggle = findViewById<ImageView>(R.id.passwordToggle)
        val googleSignIn = findViewById<View>(R.id.googleSignIn)



        // Sign In Button Click
        signInButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            // Validation
            if (validateInputs(email, password)) {
                signInUser(email, password) // Use Firebase sign-in
            }
        }

        // Sign Up Tab Click
        signUpTab.setOnClickListener {
            val intent = Intent(this, SignUp::class.java)
            startActivity(intent)
        }

        // Forgot Password Click
        forgotPassword.setOnClickListener {
            val intent = Intent(this, ForgetPasswordActivity::class.java)
            startActivity(intent)
        }

        // Password Visibility Toggle
        passwordToggle.setOnClickListener {
            togglePasswordVisibility(passwordInput, passwordToggle)
        }

        // Google Sign In Click
        googleSignIn.setOnClickListener {
            handleGoogleSignIn()
        }
    }

    /**
     * Firebase function to sign in user with email and password.
     * ONLY SUCCEEDS IF USER IS REGISTERED.
     */
    private fun signInUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, navigate to dashboard
                    Toast.makeText(this, "Sign In Successful!", Toast.LENGTH_SHORT).show()
                    navigateToDashboard()
                } else {
                    // Sign in failed (wrong email/password, or user does not exist)
                    val errorMessage = task.exception?.message ?: "Authentication failed."
                    Toast.makeText(this, "Sign In Failed: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }
    }

    /**
     * Navigates to the Dashboard and clears the back stack.
     */
    private fun navigateToDashboard() {
        val intent = Intent(this, Dashboard::class.java)
        // Clear all previous activities and start a new task
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish() // Prevents going back to sign in
    }

    // --- Google Sign-In Implementation ---

    private fun setupGoogleSignInLauncher() {
        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data
                val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
                try {
                    val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                    Log.d(TAG, "Google Sign In: ID Token acquired.")
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: com.google.android.gms.common.api.ApiException) {
                    Log.w(TAG, "Google sign in failed", e)
                    Toast.makeText(this, "Google Sign In failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    /**
     * Handles Google Sign In: Launches the Google account selection prompt.
     */
    private fun handleGoogleSignIn() {
        Log.d(TAG, "Launching Google Sign In intent")
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    /**
     * Authenticates with Firebase using the Google ID Token.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Check if the user is new and save their data to Firestore
                        checkAndSaveGoogleUser(user.uid, user.displayName, user.email)
                        // Note: If saving to Firestore is successful, we navigate to Dashboard there.
                    }
                } else {
                    // Sign in failed
                    Toast.makeText(this, "Firebase Google Auth failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    /**
     * Checks if a user is new (only created via Google) and saves basic data to Firestore.
     */
    private fun checkAndSaveGoogleUser(uid: String, name: String?, email: String?) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    if (!task.result.exists()) {
                        // User is new to Firestore (first Google sign-in)
                        val user = hashMapOf(
                            "uid" to uid,
                            "fullName" to (name ?: "User"), // Use provided name or default
                            "email" to (email ?: "N/A"),
                            "createdAt" to com.google.firebase.Timestamp.now(),
                            "signInMethod" to "google"
                        )

                        db.collection("users").document(uid).set(user)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Welcome via Google Sign In!", Toast.LENGTH_SHORT).show()
                                navigateToDashboard()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Google Sign In successful, but failed to save user data.", Toast.LENGTH_LONG).show()
                                navigateToDashboard() // Still navigate, but log the error
                            }
                    } else {
                        // User already exists in Firestore
                        Toast.makeText(this, "Welcome Back!", Toast.LENGTH_SHORT).show()
                        navigateToDashboard()
                    }
                } else {
                    // Failed to check Firestore (possibly connectivity issue)
                    Toast.makeText(this, "Google Sign In successful, but failed to verify data status.", Toast.LENGTH_LONG).show()
                    navigateToDashboard() // Still navigate if Auth succeeded
                }
            }
    }







    /**
     * Validates email and password inputs
     */
    private fun validateInputs(email: String, password: String): Boolean {
        var isValid = true

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)

        // Validate Email
        if (email.isEmpty()) {
            emailInput.error = "Email is required"
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Please enter a valid email"
            isValid = false
        }

        // Validate Password
        if (password.isEmpty()) {
            passwordInput.error = "Password is required"
            isValid = false
        } else if (password.length < 4) {
            passwordInput.error = "Password must be at least 4 characters"
            isValid = false
        }

        return isValid
    }

    /**
     * Toggles password visibility
     */
    private fun togglePasswordVisibility(passwordInput: EditText, passwordToggle: ImageView) {
        if (isPasswordVisible) {
            passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordToggle.setImageResource(R.drawable.img_3) // Eye closed icon
            isPasswordVisible = false
        } else {
            passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            isPasswordVisible = true
        }
        passwordInput.setSelection(passwordInput.text.length)
    }



}