package com.example.educheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var roleSelection: RadioGroup
    private lateinit var studentRadio: RadioButton
    private lateinit var teacherRadio: RadioButton
    private lateinit var loginButton: Button
    private lateinit var createAccountButton: TextView
    private lateinit var backButton: ImageButton
    private lateinit var rememberMeCheckbox: CheckBox
    private lateinit var forgotPasswordText: TextView
    private var progressBar: ProgressBar? = null

    private lateinit var sessionManager: SessionManager

    companion object {
        private const val TAG = "LoginActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_login)
            enableEdgeToEdge()

            // Initialize Firebase
            try {
                auth = FirebaseAuth.getInstance()
                Log.d(TAG, "Firebase Auth initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Firebase Auth: ${e.message}")
                Toast.makeText(this, "Error connecting to the database: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }

            // יצירת SessionManager
            sessionManager = SessionManager(this)

            // בדיקה האם המשתמש בחר ב-Remember Me ואם הוא מחובר
            if (sessionManager.isRememberMeEnabled() && sessionManager.isLoggedIn()) {
                // אם המשתמש מחובר ובחר ב-Remember Me, ננווט ישירות למסך המתאים
                val userRole = sessionManager.getUserRole()
                navigateToMainScreen(userRole)
                return
            }

            // Initialize UI components
            try {
                emailInput = findViewById(R.id.emailInput)
                passwordInput = findViewById(R.id.passwordInput)
                roleSelection = findViewById(R.id.roleSelection)
                studentRadio = findViewById(R.id.studentRadio)
                teacherRadio = findViewById(R.id.teacherRadio)
                loginButton = findViewById(R.id.signInButton)
                createAccountButton = findViewById(R.id.signUpPrompt)
                backButton = findViewById(R.id.backButton)
                rememberMeCheckbox = findViewById(R.id.rememberMeCheckbox)

                // Try to find the ProgressBar (with handling for when it's not found)
                try {
                    progressBar = findViewById(R.id.progressBar)
                } catch (e: Exception) {
                    Log.w(TAG, "ProgressBar not found: ${e.message}")
                    // Continue even if there's no ProgressBar
                }

                Log.d(TAG, "UI components initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error finding UI components: ${e.message}")
                Toast.makeText(this, "Error loading UI: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }

            // טעינת מידע שמור אם יש והמשתמש בחר ב-Remember Me
            loadSavedLoginInfo()

            // Set click listeners
            try {
                loginButton.setOnClickListener { loginUser() }
                createAccountButton.setOnClickListener {
                    try {
                        startActivity(Intent(this, CreateUserActivity::class.java))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting CreateUserActivity: ${e.message}")
                        Toast.makeText(this, "Error navigating to registration screen: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                // Back button listener
                backButton.setOnClickListener {
                    onBackPressed()
                }

                // Forgot password listener
            } catch (e: Exception) {
                Log.e(TAG, "Error setting click listeners: ${e.message}")
                Toast.makeText(this, "Error setting up buttons: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing screen: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadSavedLoginInfo() {
        try {
            if (sessionManager.isRememberMeEnabled()) {
                // טען את האימייל השמור
                val savedEmail = sessionManager.getUserEmail()
                if (savedEmail.isNotEmpty()) {
                    emailInput.setText(savedEmail)
                    rememberMeCheckbox.isChecked = true
                    passwordInput.requestFocus()
                }

                // אם יש גם תפקיד שמור, סמן אותו
                val savedRole = sessionManager.getUserRole()
                when (savedRole) {
                    "student" -> studentRadio.isChecked = true
                    "teacher" -> teacherRadio.isChecked = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved login info: ${e.message}")
        }
    }

    private fun loginUser() {
        try {
            // Get form data
            val email = emailInput.text?.toString()?.trim() ?: ""
            val password = passwordInput.text?.toString()?.trim() ?: ""

            // Validate input
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return
            }

            // Check role selection
            val selectedRoleId = roleSelection.checkedRadioButtonId
            if (selectedRoleId == -1) {
                Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show()
                return
            }

            // Show progress if available
            progressBar?.visibility = View.VISIBLE
            loginButton.isEnabled = false

            Log.d(TAG, "Attempting login with email: $email")

            // Attempt to sign in
            try {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        // Always restore UI state
                        progressBar?.visibility = View.GONE
                        loginButton.isEnabled = true

                        if (task.isSuccessful) {
                            Log.d(TAG, "Login successful")

                            // Get currently authenticated user
                            val user = auth.currentUser
                            if (user == null) {
                                Log.e(TAG, "Authentication successful but currentUser is null")
                                Toast.makeText(this, "Login error: Unable to get user details", Toast.LENGTH_SHORT).show()
                                return@addOnCompleteListener
                            }

                            // Get user role from database
                            try {
                                val userRef = FirebaseDatabase.getInstance().reference
                                    .child("users").child(user.uid)

                                userRef.get().addOnSuccessListener { dataSnapshot ->
                                    try {
                                        if (dataSnapshot.exists()) {
                                            // Get the role from database
                                            val storedRole = dataSnapshot.child("role").getValue(String::class.java)

                                            // Determine selected role from UI
                                            val selectedRole = when (selectedRoleId) {
                                                R.id.studentRadio -> "student"
                                                R.id.teacherRadio -> "teacher"
                                                else -> null
                                            }

                                            Log.d(TAG, "Role check: Stored = $storedRole, Selected = $selectedRole")

                                            if (storedRole == selectedRole) {
                                                // בדיקה האם המשתמש בחר ב-Remember Me
                                                val isRememberMeChecked = rememberMeCheckbox.isChecked

                                                // נשמור את פרטי המשתמש ב-SessionManager רק אם המשתמש בחר ב-Remember Me
                                                sessionManager.createLoginSession(
                                                    userId = user.uid,
                                                    email = email,
                                                    role = storedRole ?: "",
                                                    rememberMe = isRememberMeChecked
                                                )

                                                // נעבור למסך המתאים
                                                navigateToMainScreen(storedRole)
                                            } else {
                                                // Role mismatch
                                                Log.d(TAG, "Role mismatch")
                                                Toast.makeText(this, "The selected role does not match your role", Toast.LENGTH_LONG).show()
                                                auth.signOut() // Sign out to prevent partial login
                                            }
                                        } else {
                                            // User data not found in database
                                            Log.e(TAG, "User authenticated but data not found in database")
                                            Toast.makeText(this, "User information not found in the system", Toast.LENGTH_SHORT).show()
                                            auth.signOut()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error processing user data: ${e.message}")
                                        Toast.makeText(this, "Error processing user data: ${e.message}", Toast.LENGTH_SHORT).show()
                                        auth.signOut()
                                    }
                                }.addOnFailureListener { e ->
                                    Log.e(TAG, "Error getting user data: ${e.message}")
                                    Toast.makeText(this, "Error retrieving user information: ${e.message}", Toast.LENGTH_SHORT).show()
                                    auth.signOut()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error accessing database: ${e.message}")
                                Toast.makeText(this, "Error accessing database: ${e.message}", Toast.LENGTH_SHORT).show()
                                auth.signOut()
                            }
                        } else {
                            // Authentication failed
                            val exception = task.exception
                            Log.e(TAG, "Authentication failed: ${exception?.message}")

                            // Various error options - I tried to think of all possibilities
                            val errorMessage = when {
                                exception?.message?.contains("password is invalid") == true ->
                                    "Incorrect password"
                                exception?.message?.contains("no user record") == true ->
                                    "User does not exist"
                                exception?.message?.contains("badly formatted") == true ->
                                    "Invalid email address"
                                exception?.message?.contains("network error") == true ->
                                    "Network error, check your internet connection"
                                exception?.message?.contains("too many unsuccessful login") == true ->
                                    "Too many failed login attempts, try again later"
                                else -> "Login failed: ${exception?.message}"
                            }
                            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        // This is a failsafe in case onComplete doesn't fire
                        progressBar?.visibility = View.GONE
                        loginButton.isEnabled = true
                        Log.e(TAG, "Login failure: ${e.message}")
                        Toast.makeText(this, "Login error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } catch (e: Exception) {
                // Make sure UI is restored if there's an exception
                progressBar?.visibility = View.GONE
                loginButton.isEnabled = true
                Log.e(TAG, "Exception in signInWithEmailAndPassword: ${e.message}")
                Toast.makeText(this, "Error in login attempt: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            // Make sure UI is restored if there's an exception
            progressBar?.visibility = View.GONE
            loginButton.isEnabled = true
            Log.e(TAG, "Exception in loginUser: ${e.message}")
            Toast.makeText(this, "General login error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToMainScreen(role: String?) {
        try {
            val intent = when (role) {
                "student" -> Intent(this, StudentMenuActivity::class.java)
                "teacher" -> Intent(this, TeacherMenuActivity::class.java)
                else -> {
                    Log.e(TAG, "Unknown role: $role")
                    Toast.makeText(this, "Unknown role: $role", Toast.LENGTH_SHORT).show()
                    auth.signOut()
                    return
                }
            }

            // Clear back stack
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to main screen: ${e.message}")
            Toast.makeText(this, "Error navigating to next screen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Optional: Add methods for additional functionality
    private fun handleRememberMe() {
        // Implement remember me logic if needed
        val isRememberMeChecked = rememberMeCheckbox.isChecked
        // Add shared preferences or other storage mechanism
    }
}