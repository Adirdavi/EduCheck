package com.example.educheck

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
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
import com.example.educheck.utilities.Teacher

class StudentMenuActivity : AppCompatActivity() {

    // Declaration of UI element variables
    private lateinit var appLogo: ImageView
    private lateinit var greetingText: TextView
    private lateinit var messageBadge: TextView
    private lateinit var logoutButton: MaterialButton

    // Menu cards
    private lateinit var cardAvailableTests: CardView
    private lateinit var cardSendNote: CardView
    private lateinit var cardAcademicProgress: CardView

    // Firebase references
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var firestore: FirebaseFirestore
    private var messagesListener: ListenerRegistration? = null

    // Session manager
    private lateinit var sessionManager: SessionManager

    // Student information
    private var studentId = ""
    private var studentName = ""
    private var teachersList = ArrayList<Teacher>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_student_menu)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Session Manager
        sessionManager = SessionManager(this)

        // Initialize Firebase
        initializeFirebase()

        // Initialize all elements
        initViews()

        // Fetch teachers list from Firebase
        fetchTeachersList()

        // Set up student info
        setupStudentInfo()

        // Check for unread messages
        checkForUnreadMessages()

        // Set up click listeners for all buttons
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        checkForUnreadMessages()
    }

    override fun onDestroy() {
        super.onDestroy()
        messagesListener?.remove()
    }

    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        firestore = FirebaseFirestore.getInstance()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            studentId = currentUser.uid
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun initViews() {
        try {
            // Updated to match the new layout
            appLogo = findViewById(R.id.appLogo)
            greetingText = findViewById(R.id.greetingText)
            messageBadge = findViewById(R.id.requestsBadge)
            logoutButton = findViewById(R.id.logoutButton)

            cardAvailableTests = findViewById(R.id.cardAvailableTests)
            cardSendNote = findViewById(R.id.cardSendNote)
            cardAcademicProgress = findViewById(R.id.cardAcademicProgress)

            // Set default greeting text
            greetingText.text = "Hello, Student!"
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing UI", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchTeachersList() {
        // Existing code, no changes needed
        // This would fetch the list of teachers from Firebase
    }

    private fun setupStudentInfo() {
        // Get the current user
        val currentUser = auth.currentUser ?: return

        // Basic fetching from Realtime Database - simple version
        try {
            // Default greeting while loading
            greetingText.text = "Hello, Student!"

            // Reference to the user's data in Realtime Database
            val userRef = database.getReference("users").child(currentUser.uid)

            // Add a listener for a single value event
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (snapshot.exists()) {
                            // Try several possible field names for first name
                            var firstName: String? = null

                            // Option 1: Check if there's a direct child node
                            val nameFields = listOf("firstName", "first_name", "name", "displayName", "Name")
                            for (field in nameFields) {
                                if (snapshot.hasChild(field)) {
                                    firstName = snapshot.child(field).getValue(String::class.java)
                                    if (!firstName.isNullOrEmpty()) {
                                        break
                                    }
                                }
                            }

                            // Option 2: Check if it's in the main object
                            if (firstName == null) {
                                val userData = snapshot.getValue(HashMap::class.java)
                                if (userData != null) {
                                    for (field in nameFields) {
                                        val value = userData[field]
                                        if (value != null && value is String) {
                                            firstName = value
                                            break
                                        }
                                    }
                                }
                            }

                            // Set the greeting text
                            if (!firstName.isNullOrEmpty()) {
                                greetingText.text = "Hello, $firstName!"
                            } else {
                                // Fallback to display name from Auth
                                val displayName = currentUser.displayName
                                if (!displayName.isNullOrEmpty()) {
                                    // Get first name if there's a space
                                    val firstNameFromDisplayName = displayName.split(" ").first()
                                    greetingText.text = "Hello, $firstNameFromDisplayName!"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Silent catch - keep default greeting
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Keep default greeting
                }
            })
        } catch (e: Exception) {
            // Silent catch - keep default greeting
        }
    }

    private fun checkForUnreadMessages() {
        try {
            messagesListener?.remove()

            messagesListener = firestore.collection("chats")
                .whereArrayContains("participants", studentId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        return@addSnapshotListener
                    }

                    if (snapshot == null || snapshot.isEmpty) {
                        messageBadge.visibility = View.GONE
                        return@addSnapshotListener
                    }

                    var totalUnreadMessages = 0

                    for (document in snapshot.documents) {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val messages = document.get("messages") as? ArrayList<HashMap<String, Any>> ?: ArrayList()

                            val unreadCount = messages.count { message ->
                                val receiverId = message["receiverId"] as? String ?: ""
                                val isRead = message["isRead"] as? Boolean ?: true
                                receiverId == studentId && !isRead
                            }

                            totalUnreadMessages += unreadCount
                        } catch (e: Exception) {
                            // Silent catch
                        }
                    }

                    messageBadge.visibility = if (totalUnreadMessages > 0) View.VISIBLE else View.GONE

                    if (totalUnreadMessages > 0) {
                        messageBadge.text = if (totalUnreadMessages > 9) "9+" else totalUnreadMessages.toString()
                    }
                }
        } catch (e: Exception) {
            // Silent catch
        }
    }

    private fun setupClickListeners() {
        // Set up logout button click listener
        logoutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        cardAvailableTests.setOnClickListener {
            val intent = Intent(this, TestsActivity::class.java)
            startActivity(intent)
        }

        cardSendNote.setOnClickListener {
            val intent = Intent(this, TeacherSelectionActivity::class.java)
            startActivity(intent)
        }

        cardAcademicProgress.setOnClickListener {
            val intent = Intent(this, StudentProgressActivity::class.java)
            startActivity(intent)
        }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                logoutUser()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun logoutUser() {
        try {
            // Clear session
            sessionManager.logoutUser()

            // Sign out from Firebase
            auth.signOut()

            // Navigate to login screen
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            // Handle any errors during logout
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("An error occurred during logout: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }
}