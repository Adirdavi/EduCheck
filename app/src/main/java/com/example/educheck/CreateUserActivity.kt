package com.example.educheck

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class CreateUserActivity : AppCompatActivity() {
    // Firebase and UI component declarations
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var roleGroup: RadioGroup
    private lateinit var registerButton: Button
    private lateinit var loginPrompt: TextView
    private var progressBar: ProgressBar? = null

    // Companion object for logging and constants
    companion object {
        private const val TAG = "CreateUserActivity"
        private const val MIN_PASSWORD_LENGTH = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Attempt to set content view with error handling
        try {
            setContentView(R.layout.activity_create_user)

            // Initialize Firebase components
            initializeFirebase()

            // Initialize UI components
            initializeUIComponents()

            // Set up click listeners
            setupClickListeners()

            // Set up input validation
            setupInputValidation()

        } catch (e: Exception) {
            handleInitializationError(e)
        }
    }

    // Initialize Firebase components
    private fun initializeFirebase() {
        try {
            auth = FirebaseAuth.getInstance()
            database = FirebaseDatabase.getInstance().reference
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase: ${e.message}")
            Toast.makeText(this, "Error connecting to database: ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        }
    }

    // Initialize UI components
    private fun initializeUIComponents() {
        try {
            firstNameInput = findViewById(R.id.firstNameInput)
            lastNameInput = findViewById(R.id.lastNameInput)
            emailInput = findViewById(R.id.emailInput)
            passwordInput = findViewById(R.id.passwordInput)
            roleGroup = findViewById(R.id.roleGroup)
            registerButton = findViewById(R.id.registerButton)
            loginPrompt = findViewById(R.id.loginPrompt)
            progressBar = findViewById(R.id.progressBar)

            Log.d(TAG, "UI components initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error finding UI components: ${e.message}")
            Toast.makeText(this, "Error loading UI: ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        }
    }

    // Set up click listeners
    private fun setupClickListeners() {
        try {
            registerButton.setOnClickListener { registerUser() }

            // Navigate to login screen
            loginPrompt.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
                // Optional: finish this activity if you don't want to keep it in the back stack
                // finish()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting click listeners: ${e.message}")
        }
    }

    // Set up input validation
    private fun setupInputValidation() {
        // Real-time email validation
        emailInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateEmail()
            }
        })

        // Real-time password validation
        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePassword()
            }
        })
    }

    // Validate email format
    private fun validateEmail(): Boolean {
        val email = emailInput.text.toString().trim()
        return if (email.isEmpty()) {
            emailInput.error = "Email cannot be empty"
            false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Invalid email format"
            false
        } else {
            emailInput.error = null
            true
        }
    }

    // Validate password strength
    private fun validatePassword(): Boolean {
        val password = passwordInput.text.toString().trim()
        return if (password.isEmpty()) {
            passwordInput.error = "Password cannot be empty"
            false
        } else if (password.length < MIN_PASSWORD_LENGTH) {
            passwordInput.error = "Password must be at least $MIN_PASSWORD_LENGTH characters"
            false
        } else {
            passwordInput.error = null
            true
        }
    }

    // Handle registration process
    private fun registerUser() {
        // Validate all inputs before registration
        if (!validateInputs()) return

        // Prepare user data
        val firstName = firstNameInput.text.toString().trim()
        val lastName = lastNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        // Check role selection
        val selectedRoleId = roleGroup.checkedRadioButtonId
        if (selectedRoleId == -1) {
            Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show()
            return
        }
        val isStudent = selectedRoleId == R.id.studentRadio

        // Prepare UI for registration process
        prepareUIForRegistration()

        // Attempt user creation
        performFirebaseRegistration(firstName, lastName, email, password, isStudent)
    }

    // Validate all input fields
    private fun validateInputs(): Boolean {
        var isValid = true

        // Validate first name
        if (firstNameInput.text.toString().trim().isEmpty()) {
            firstNameInput.error = "First name cannot be empty"
            isValid = false
        }

        // Validate last name
        if (lastNameInput.text.toString().trim().isEmpty()) {
            lastNameInput.error = "Last name cannot be empty"
            isValid = false
        }

        // Validate email
        isValid = validateEmail() && isValid

        // Validate password
        isValid = validatePassword() && isValid

        return isValid
    }

    // Prepare UI for registration process
    private fun prepareUIForRegistration() {
        progressBar?.visibility = View.VISIBLE
        registerButton.isEnabled = false
    }

    // Perform Firebase user registration
    private fun performFirebaseRegistration(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        isStudent: Boolean
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                // Always reset UI
                progressBar?.visibility = View.GONE
                registerButton.isEnabled = true

                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        saveUserData(it.uid, firstName, lastName, email, isStudent)
                    } ?: run {
                        handleRegistrationError("User creation failed")
                    }
                } else {
                    handleRegistrationError(task.exception?.message ?: "Registration failed")
                }
            }
    }

    // Save additional user data to database
    private fun saveUserData(
        userId: String,
        firstName: String,
        lastName: String,
        email: String,
        isStudent: Boolean
    ) {
        val role = if (isStudent) "student" else "teacher"
        val userMap = mapOf(
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to email,
            "role" to role
        )

        database.child("users").child(userId).setValue(userMap)
            .addOnSuccessListener {
                Log.d(TAG, "User data saved successfully")
                Toast.makeText(this, "Registration completed successfully", Toast.LENGTH_SHORT).show()
                navigateToAppropriateScreen(isStudent)
            }
            .addOnFailureListener { e ->
                handleDataSaveError(e, userId)
            }
    }

    // Navigate to appropriate screen based on user role
    private fun navigateToAppropriateScreen(isStudent: Boolean) {
        try {
            val intent = if (isStudent) {
                Intent(this, StudentMenuActivity::class.java)
            } else {
                Intent(this, TeacherMenuActivity::class.java)
            }

            // Prevent returning to registration screen
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Navigation error: ${e.message}")
            Toast.makeText(this, "Registration succeeded, but navigation failed", Toast.LENGTH_LONG).show()
        }
    }

    // Handle registration errors
    private fun handleRegistrationError(errorMessage: String) {
        val formattedMessage = when {
            errorMessage.contains("email address is already in use") ->
                "Email address already exists in the system"
            errorMessage.contains("password is invalid") ->
                "Password must contain at least $MIN_PASSWORD_LENGTH characters"
            errorMessage.contains("badly formatted") ->
                "Invalid email address"
            errorMessage.contains("network error") ->
                "Network error, check your internet connection"
            errorMessage.contains("API key") ->
                "Firebase configuration error"
            else -> "Registration failed: $errorMessage"
        }

        Log.e(TAG, "Registration error: $errorMessage")
        Toast.makeText(this, formattedMessage, Toast.LENGTH_LONG).show()
    }

    // Handle data save errors
    private fun handleDataSaveError(e: Exception, userId: String) {
        Log.e(TAG, "Error saving user data: ${e.message}")
        Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_LONG).show()

        // Sign out user if data saving fails
        try {
            auth.signOut()
        } catch (ex: Exception) {
            Log.e(TAG, "Error signing out after data save failure: ${ex.message}")
        }
    }

    // Handle initialization errors
    private fun handleInitializationError(e: Exception) {
        Log.e(TAG, "Fatal error in onCreate: ${e.message}")
        Toast.makeText(this, "Error initializing screen: ${e.message}", Toast.LENGTH_LONG).show()
        finish()
    }
}