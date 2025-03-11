package com.example.educheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class TeacherMenuActivity : AppCompatActivity(), View.OnClickListener {

    // Private variables
    private var auth: FirebaseAuth? = null
    private lateinit var firestore: FirebaseFirestore
    private lateinit var database: FirebaseDatabase
    private var messagesListener: ListenerRegistration? = null
    private var teacherId: String = ""
    private var unreadMessagesBadge: TextView? = null
    private lateinit var cardStudentChat: CardView
    private lateinit var greetingText: TextView // Added greeting text variable

    companion object {
        private const val TAG = "TeacherMenuActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            setContentView(R.layout.activity_teacher_menu)

            // Set up insets
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            // Initialize greeting text
            try {
                greetingText = findViewById(R.id.greetingText)
            } catch (e: Exception) {
                Log.e(TAG, "Error finding greetingText: ${e.message}")
            }

            // Initialize Firebase Auth, Firestore, and Realtime Database
            try {
                auth = FirebaseAuth.getInstance()
                firestore = FirebaseFirestore.getInstance()
                database = FirebaseDatabase.getInstance()

                // Get teacher ID
                val currentUser = auth?.currentUser
                if (currentUser != null) {
                    teacherId = currentUser.uid
                    // Fetch teacher's first name from Realtime Database
                    fetchTeacherFirstNameFromRTDB(teacherId)
                } else {
                    // Use sample value for development
                    teacherId = "teacher123"
                    greetingText.text = "Hello, Teacher!"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Firebase: ${e.message}")
                // Use sample value for development
                teacherId = "teacher123"
                greetingText.text = "Hello, Teacher!"
            }

            // Try to find the notification badge
            try {
                // Repurpose the requests badge for unread messages
                unreadMessagesBadge = findViewById(R.id.requestsBadge)
            } catch (e: Exception) {
                Log.e(TAG, "Error finding unreadMessagesBadge: ${e.message}")
            }

            // Check for unread messages
            checkForUnreadMessages()

            // Initialize all buttons and set click listeners with error handling
            initClickListeners()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
            Toast.makeText(this, "Error initializing screen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Fetch teacher's first name from Firebase Realtime Database
     */
    private fun fetchTeacherFirstNameFromRTDB(teacherId: String) {
        try {
            // Try the teachers path
            val teacherRef = database.getReference("teachers").child(teacherId)

            teacherRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Try different field names for first name
                        val possibleFirstNameFields = listOf(
                            "firstName", "firstname", "first_name", "FirstName", "name", "Name"
                        )

                        // Try each possible field name
                        for (fieldName in possibleFirstNameFields) {
                            val firstName = snapshot.child(fieldName).getValue(String::class.java)
                            if (!firstName.isNullOrEmpty()) {
                                greetingText.text = "Hello, $firstName!"
                                return
                            }
                        }

                        // If no specific field found, try checking the root level
                        val completeSnapshot = snapshot.getValue(HashMap::class.java)
                        if (completeSnapshot != null) {
                            for (fieldName in possibleFirstNameFields) {
                                val value = completeSnapshot[fieldName]
                                if (value != null && value is String && value.isNotEmpty()) {
                                    greetingText.text = "Hello, $value!"
                                    return
                                }
                            }
                        }

                        // No name found, try users path
                        tryUsersPath()
                    } else {
                        // No data, try users path
                        tryUsersPath()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Error with teachers path, try users path
                    tryUsersPath()
                }

                // Try the users path as a fallback
                private fun tryUsersPath() {
                    database.getReference("users").child(teacherId)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(usersSnapshot: DataSnapshot) {
                                if (usersSnapshot.exists()) {
                                    // Try different field names for first name
                                    val possibleFirstNameFields = listOf(
                                        "firstName", "firstname", "first_name", "FirstName", "name", "Name"
                                    )

                                    // Try each possible field name
                                    for (fieldName in possibleFirstNameFields) {
                                        val firstName = usersSnapshot.child(fieldName).getValue(String::class.java)
                                        if (!firstName.isNullOrEmpty()) {
                                            greetingText.text = "Hello, $firstName!"
                                            return
                                        }
                                    }

                                    // Try checking root level
                                    val completeSnapshot = usersSnapshot.getValue(HashMap::class.java)
                                    if (completeSnapshot != null) {
                                        for (fieldName in possibleFirstNameFields) {
                                            val value = completeSnapshot[fieldName]
                                            if (value != null && value is String && value.isNotEmpty()) {
                                                greetingText.text = "Hello, $value!"
                                                return
                                            }
                                        }
                                    }

                                    // Default fallback
                                    greetingText.text = "Hello, Teacher!"
                                } else {
                                    // Default fallback
                                    greetingText.text = "Hello, Teacher!"
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                // Default fallback
                                greetingText.text = "Hello, Teacher!"
                            }
                        })
                }
            })
        } catch (e: Exception) {
            greetingText.text = "Hello, Teacher!"
        }
    }

    override fun onResume() {
        super.onResume()
        // Check for unread messages every time the screen is resumed
        checkForUnreadMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
    }

    /**
     * Helper function to initialize all click listeners
     */
    private fun initClickListeners() {
        try {
            // List of buttons we want to initialize
            val buttonIds = listOf(
                R.id.cardUploadTest,
                R.id.cardExistingTests,
                R.id.cardStatisticalAnalysis,
                R.id.cardStudentTracking,
                R.id.cardStudentChat,
                R.id.cardErrorReporting,
                R.id.logoutButton
            )

            // Go through each ID and try to find it and set a click listener
            for (id in buttonIds) {
                try {
                    val view = findViewById<View>(id)
                    view.setOnClickListener(this)
                } catch (e: Exception) {
                    Log.e(TAG, "Error finding or setting listener for view with ID $id: ${e.message}")
                }
            }

            // Initialize the student chat card
            try {
                cardStudentChat = findViewById(R.id.cardStudentChat)
            } catch (e: Exception) {
                Log.e(TAG, "Error finding cardStudentChat: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in initClickListeners: ${e.message}")
        }
    }

    /**
     * Check for unread messages and display notification accordingly
     */
    private fun checkForUnreadMessages() {
        try {
            // Remove previous listener if it exists
            messagesListener?.remove()

            messagesListener = firestore.collection("chat_messages")
                .whereEqualTo("receiverId", teacherId)
                .whereEqualTo("isRead", false)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e(TAG, "Listen failed: ${e.message}")
                        return@addSnapshotListener
                    }

                    // Check if there are unread messages
                    val hasUnreadMessages = snapshot != null && !snapshot.isEmpty

                    // Update UI - show/hide badge
                    unreadMessagesBadge?.visibility = if (hasUnreadMessages) View.VISIBLE else View.GONE

                    // Can also update badge text with count
                    if (hasUnreadMessages) {
                        val unreadCount = snapshot?.size() ?: 0
                        unreadMessagesBadge?.text = if (unreadCount > 9) "9+" else unreadCount.toString()
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkForUnreadMessages: ${e.message}")
        }
    }

    override fun onClick(view: View) {
        try {
            when(view.id) {
                R.id.cardUploadTest -> {
                    // Navigate to create new test screen
                    try {
                        val intent = Intent(this, CreateTestActivity::class.java)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error navigating to CreateTestActivity: ${e.message}")
                        Toast.makeText(this, "Error navigating to create test screen: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.cardExistingTests -> {

                    try {
                        val intent = Intent(this, TestsActivity::class.java)
                        intent.putExtra("IS_TEACHER", true)
                        intent.putExtra("CALLING_ACTIVITY", "TeacherMenuActivity")
                        Log.d(TAG, "Opening TestsActivity as teacher")
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error navigating to TestsActivity: ${e.message}")
                        Toast.makeText(this, "Error showing existing tests: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.cardStatisticalAnalysis -> {
                    // Open the Test Statistics Activity
                    try {
                        val intent = Intent(this, TestStatisticsActivity::class.java)
                        Log.d(TAG, "Opening TestStatisticsActivity")
                        startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error navigating to TestStatisticsActivity: ${e.message}")
                        Toast.makeText(this, "Error opening statistics: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.cardStudentTracking -> {
                    Toast.makeText(this, "Student Tracking", Toast.LENGTH_SHORT).show()
                }
                R.id.cardStudentChat -> {
                    // Open student selection for chat
                    openStudentChatSelection()
                }
                R.id.cardErrorReporting -> {
                    Toast.makeText(this, "Error Reporting", Toast.LENGTH_SHORT).show()
                }
                R.id.logoutButton -> {
                    performLogout()
                }
                else -> {
                    Log.d(TAG, "Unknown view clicked with ID: ${view.id}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling click: ${e.message}")
            Toast.makeText(this, "Error handling click: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open student selection for chat
     */
    private fun openStudentChatSelection() {
        try {
            val intent = Intent(this, StudentSelectionActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening student selection: ${e.message}")
            Toast.makeText(this, "Error opening student selection", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Function that handles logging out of the account
     */
    private fun performLogout() {
        try {
            Toast.makeText(this, "Logging out", Toast.LENGTH_SHORT).show()

            // נשתמש ב-SessionManager כדי לבצע התנתקות
            val sessionManager = SessionManager(this)
            sessionManager.logoutUser()

            // Log out from Firebase
            auth?.signOut()

            // Return to login screen
            val intent = Intent(this, LoginActivity::class.java)
            // Clear the stack so user can't return to this screen using Back button
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error during logout: ${e.message}")
            Toast.makeText(this, "Error logging out: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}