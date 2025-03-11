package com.example.educheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class CreateUserActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var roleGroup: RadioGroup
    private lateinit var registerButton: Button
    private var progressBar: ProgressBar? = null

    companion object {
        private const val TAG = "CreateUserActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_create_user)

            // Initialize Firebase
            try {
                auth = FirebaseAuth.getInstance()
                database = FirebaseDatabase.getInstance().reference
                Log.d(TAG, "Firebase initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Firebase: ${e.message}")
                Toast.makeText(this, "Error connecting to database: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }

            // Initialize UI components - Removing risk of app crash
            try {
                firstNameInput = findViewById(R.id.firstNameInput)
                lastNameInput = findViewById(R.id.lastNameInput)
                emailInput = findViewById(R.id.emailInput)
                passwordInput = findViewById(R.id.passwordInput)
                roleGroup = findViewById(R.id.roleGroup)
                registerButton = findViewById(R.id.registerButton)

                // Try to find the ProgressBar (handling case where it's not found)
                try {
                    progressBar = findViewById(R.id.progressBar)
                } catch (e: Exception) {
                    Log.w(TAG, "ProgressBar not found: ${e.message}")
                    // Continue even without ProgressBar
                }

                Log.d(TAG, "UI components initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error finding UI components: ${e.message}")
                Toast.makeText(this, "Error loading UI: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }

            // Set click listener
            try {
                registerButton.setOnClickListener { registerUser() }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting click listener: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing screen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerUser() {
        try {
            // Get and validate form data
            val firstName = firstNameInput.text?.toString()?.trim() ?: ""
            val lastName = lastNameInput.text?.toString()?.trim() ?: ""
            val email = emailInput.text?.toString()?.trim() ?: ""
            val password = passwordInput.text?.toString()?.trim() ?: ""

            // Basic validation
            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return
            }

            // Check role selection
            val selectedRoleId = roleGroup.checkedRadioButtonId
            if (selectedRoleId == -1) {
                Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show()
                return
            }

            val isStudent = selectedRoleId == R.id.studentRadio

            // Check password strength
            if (password.length < 6) {
                Toast.makeText(this, "Password must contain at least 6 characters", Toast.LENGTH_SHORT).show()
                return
            }

            // Show progress bar if available
            progressBar?.visibility = View.VISIBLE
            registerButton.isEnabled = false

            Log.d(TAG, "Attempting to create user with email: $email")

            // Try to create the user
            try {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        // Always hide progress and enable button regardless of result
                        progressBar?.visibility = View.GONE
                        registerButton.isEnabled = true

                        if (task.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null) {
                                Log.d(TAG, "User created successfully with ID: ${user.uid}")

                                // Try to save additional user data
                                saveUserData(user.uid, firstName, lastName, email, isStudent)
                            } else {
                                Log.e(TAG, "User created but currentUser is null")
                                Toast.makeText(this, "Error creating user", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val exception = task.exception
                            Log.e(TAG, "User creation failed: ${exception?.message}")

                            // Different error options - tried to think of all possibilities
                            val errorMessage = when {
                                exception?.message?.contains("email address is already in use") == true ->
                                    "Email address already exists in the system"
                                exception?.message?.contains("password is invalid") == true ->
                                    "Password must contain at least 6 characters"
                                exception?.message?.contains("badly formatted") == true ->
                                    "Invalid email address"
                                exception?.message?.contains("network error") == true ->
                                    "Network error, check your internet connection"
                                exception?.message?.contains("API key") == true ->
                                    "Firebase configuration error"
                                else -> "Registration failed: ${exception?.message}"
                            }
                            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        // This is a failsafe in case onComplete doesn't fire for some reason
                        progressBar?.visibility = View.GONE
                        registerButton.isEnabled = true
                        Log.e(TAG, "Registration failure: ${e.message}")
                        Toast.makeText(this, "Registration error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } catch (e: Exception) {
                // Make sure UI is restored if there's an exception
                progressBar?.visibility = View.GONE
                registerButton.isEnabled = true
                Log.e(TAG, "Exception in createUserWithEmailAndPassword: ${e.message}")
                Toast.makeText(this, "Error creating account: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // Make sure UI is restored if there's an exception
            progressBar?.visibility = View.GONE
            registerButton.isEnabled = true
            Log.e(TAG, "Exception in registerUser: ${e.message}")
            Toast.makeText(this, "General registration error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveUserData(userId: String, firstName: String, lastName: String, email: String, isStudent: Boolean) {
        try {
            val role = if (isStudent) "student" else "teacher"

            val userMap = mapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "email" to email,
                "role" to role
            )

            Log.d(TAG, "Saving user data for userId: $userId with role: $role")

            // Save data in database
            database.child("users").child(userId).setValue(userMap)
                .addOnSuccessListener {
                    Log.d(TAG, "User data saved successfully")
                    Toast.makeText(this, "Registration completed successfully", Toast.LENGTH_SHORT).show()

                    try {
                        // Navigate to appropriate screen based on role
                        val intent = if (isStudent) {
                            Intent(this, StudentMenuActivity::class.java)
                        } else {
                            Intent(this, TeacherMenuActivity::class.java)
                        }

                        // Ensure we can't return to registration screen by pressing Back
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error navigating after successful registration: ${e.message}")
                        Toast.makeText(this, "Registration succeeded, but there was an error navigating to the next screen: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error saving user data: ${e.message}")
                    Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_LONG).show()

                    // If data saving fails, log out of the user to avoid a state where user is logged in but without data
                    try {
                        auth.signOut()
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error signing out after data save failure: ${ex.message}")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in saveUserData: ${e.message}")
            Toast.makeText(this, "Error saving user data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}