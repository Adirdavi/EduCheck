package com.example.educheck

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.educheck.utilities.Teacher

class StudentMenuActivity : AppCompatActivity() {

    // Declaration of UI element variables
    private var settingsIcon: ImageView? = null
    private var logoutIcon: ImageView? = null  // Added logout icon
    private lateinit var appLogo: ImageView
    private lateinit var greetingText: TextView
    private lateinit var subGreetingText: TextView
    private lateinit var averageScore: TextView
    private var messageBadge: TextView? = null

    // Menu cards
    private lateinit var cardAvailableTests: CardView
    private lateinit var cardCompletedTests: CardView
    private lateinit var cardTeacherInfo: CardView
    private lateinit var cardSendNote: CardView
    private lateinit var cardAcademicProgress: CardView
    private lateinit var cardSchedule: CardView

    // Firebase references
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var firestore: FirebaseFirestore

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
        settingsIcon = findViewById(R.id.settingsIcon)
        logoutIcon = findViewById(R.id.logoutIcon)  // Initialize logout icon
        appLogo = findViewById(R.id.appLogo)
        greetingText = findViewById(R.id.greetingText)
        subGreetingText = findViewById(R.id.subGreetingText)
        averageScore = findViewById(R.id.averageScore)
        messageBadge = findViewById(R.id.requestsBadge)

        cardAvailableTests = findViewById(R.id.cardAvailableTests)
        cardCompletedTests = findViewById(R.id.cardCompletedTests)
        cardTeacherInfo = findViewById(R.id.cardTeacherInfo)
        cardSendNote = findViewById(R.id.cardSendNote)
        cardAcademicProgress = findViewById(R.id.cardAcademicProgress)
        cardSchedule = findViewById(R.id.cardSchedule)
    }

    private fun fetchTeachersList() {
        // Existing code, no changes
    }

    private fun setupStudentInfo() {
        // Existing code, no changes
    }

    private fun checkForUnreadMessages() {
        // Existing code, no changes
    }

    private fun setupClickListeners() {
        settingsIcon?.setOnClickListener {
            // Placeholder for settings functionality
        }

        // Add logout button click listener
        logoutIcon?.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        cardAvailableTests.setOnClickListener {
            val intent = Intent(this, TestsActivity::class.java)
            startActivity(intent)
        }

        cardCompletedTests.setOnClickListener {
            val intent = Intent(this, CompletedTestsActivity::class.java)
            startActivity(intent)
        }

        cardTeacherInfo.setOnClickListener {
            // Placeholder for teacher info functionality
        }

        cardSendNote.setOnClickListener {
            val intent = Intent(this, TeacherSelectionActivity::class.java)
            startActivity(intent)
        }

        cardAcademicProgress.setOnClickListener {

            val intent = Intent(this, StudentProgressActivity::class.java)
            startActivity(intent)
        }

        cardSchedule.setOnClickListener {
            // Placeholder for schedule functionality
        }
    }

    // Add new method for logout functionality
    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("התנתקות")
            .setMessage("האם אתה בטוח שברצונך להתנתק?")
            .setPositiveButton("כן") { _, _ ->
                logoutUser()
            }
            .setNegativeButton("לא", null)
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
                .setTitle("שגיאה")
                .setMessage("התרחשה שגיאה במהלך ההתנתקות: ${e.message}")
                .setPositiveButton("אישור", null)
                .show()
        }
    }
}

