package com.example.educheck

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.educheck.utilities.Test
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

/**
 * Activity that displays tests - for both students and teachers.
 * Students can take tests, teachers can view tests.
 */
class TestsActivity : AppCompatActivity() {

    // List of available tests
    private val testsList = mutableListOf<Test>()

    // Adapter for the tests recycler view
    private lateinit var testsAdapter: TestsAdapter

    // UI component references
    private lateinit var testsRecyclerView: RecyclerView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var noTestsMessage: TextView
    private var createTestButton: MaterialButton? = null
    private lateinit var titleTextView: TextView

    // Firestore and Auth objects
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // User role
    private var isTeacher = false
    private var userId = ""

    companion object {
        private const val TAG = "TestsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge support
        enableEdgeToEdge()

        // Load the layout file for the screen
        setContentView(R.layout.activity_tests)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Get current user ID
        userId = auth.currentUser?.uid ?: ""
        if (userId.isEmpty()) {
            Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Determine if the user is a teacher
        isTeacher = intent.getBooleanExtra("IS_TEACHER", false)
        Log.d(TAG, "IS_TEACHER flag from intent: $isTeacher")

        // Set listener for system insets updates
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        initializeUI()

        // Load tests from Firebase
        loadTestsFromFirebase()
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI() {
        try {
            testsRecyclerView = findViewById(R.id.testsRecyclerView)
            progressIndicator = findViewById(R.id.progressIndicator)
            noTestsMessage = findViewById(R.id.noTestsMessage)
            titleTextView = findViewById(R.id.titleTextView)

            // Set the appropriate title based on user role
            titleTextView.text = if (isTeacher) "Manage Tests" else "Available Tests"
            Log.d(TAG, "Setting title for isTeacher=$isTeacher")

            // Try to find the create test button - make this optional
            if (isTeacher) {
                try {
                    createTestButton = findViewById(R.id.createTestButton)
                    createTestButton?.visibility = View.VISIBLE
                    createTestButton?.setOnClickListener {
                        val intent = Intent(this, CreateTestActivity::class.java)
                        startActivity(intent)
                    }
                    Log.d(TAG, "Create button initialized for teacher")
                } catch (e: Exception) {
                    Log.e(TAG, "Create test button not found: ${e.message}")
                }
            }

            // Hide the "No tests" message initially
            noTestsMessage.visibility = View.GONE

            // Set up the recycler view with the appropriate adapter based on user role
            testsRecyclerView.layoutManager = LinearLayoutManager(this)

            if (isTeacher) {
                testsAdapter = TeacherTestsAdapter()
            } else {
                testsAdapter = StudentTestsAdapter()
            }

            testsRecyclerView.adapter = testsAdapter

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UI: ${e.message}")
            Toast.makeText(this, "Error initializing UI: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Load the list of tests from Firebase
     */
    private fun loadTestsFromFirebase() {
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE

            // Query based on user role
            val query = if (isTeacher) {
                // For teachers, only show their own tests
                // Simplified query to avoid index requirement
                Log.d(TAG, "Loading teacher tests for user ID: $userId")
                firestore.collection("tests")
                    .whereEqualTo("createdBy", userId)
                // Removed orderBy to avoid index requirement
            } else {
                // For students, show all tests
                Log.d(TAG, "Loading all tests for student")
                firestore.collection("tests")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
            }

            // Execute the query
            query.get()
                .addOnSuccessListener { documents ->
                    progressIndicator.visibility = View.GONE
                    Log.d(TAG, "Found ${documents.size()} tests")

                    testsList.clear()
                    val tempList = mutableListOf<Test>()

                    for (document in documents) {
                        try {
                            val test = document.toObject(Test::class.java)
                            if (test.id.isNotEmpty()) {  // Ensure test is valid
                                tempList.add(test)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document to test: ${e.message}")
                            // Continue to next item
                        }
                    }

                    // Sort in memory if we're in teacher mode (since we removed orderBy from query)
                    if (isTeacher) {
                        testsList.addAll(tempList.sortedByDescending { it.createdAt })
                    } else {
                        testsList.addAll(tempList)
                    }

                    // Update the adapter
                    testsAdapter.notifyDataSetChanged()

                    // Show message if there are no available tests
                    if (testsList.isEmpty()) {
                        val message = if (isTeacher) "You haven't created any tests yet" else "No tests available"
                        noTestsMessage.text = message
                        noTestsMessage.visibility = View.VISIBLE
                    } else {
                        noTestsMessage.visibility = View.GONE
                    }
                }
                .addOnFailureListener { e ->
                    progressIndicator.visibility = View.GONE
                    noTestsMessage.visibility = View.VISIBLE

                    Log.e(TAG, "Error loading tests: ${e.message}")
                    Toast.makeText(this, "Error loading tests: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progressIndicator.visibility = View.GONE
            Log.e(TAG, "General error loading tests: ${e.message}")
            Toast.makeText(this, "Error loading tests: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list when returning to this screen
        if (::firestore.isInitialized) {
            loadTestsFromFirebase()
        }
    }

    /**
     * Open screen to take the test (for students)
     */
    private fun openTestActivity(test: Test) {
        try {
            val intent = Intent(this, TakeTestActivity::class.java).apply {
                putExtra("TEST_ID", test.id)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening test screen: ${e.message}")
            Toast.makeText(this, "Error opening test: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open screen to view the test (for teachers)
     */
    private fun openTestPreview(test: Test) {
        try {
            val intent = Intent(this, TakeTestActivity::class.java).apply {
                putExtra("TEST_ID", test.id)
                putExtra("PREVIEW_MODE", true) // Set preview mode flag
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening test preview: ${e.message}")
            Toast.makeText(this, "Error opening test preview: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open screen to edit the test (for teachers)
     */
    private fun editTest(test: Test) {
        try {
            val intent = Intent(this, CreateTestActivity::class.java).apply {
                putExtra("EDIT_MODE", true)
                putExtra("TEST_ID", test.id)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening edit screen: ${e.message}")
            Toast.makeText(this, "Error opening edit screen: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Confirm and delete a test (for teachers)
     */
    private fun confirmAndDeleteTest(test: Test, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Test")
            .setMessage("Are you sure you want to delete the test \"${test.title}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteTest(test, position)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Delete a test from Firebase
     */
    private fun deleteTest(test: Test, position: Int) {
        progressIndicator.visibility = View.VISIBLE

        firestore.collection("tests").document(test.id)
            .delete()
            .addOnSuccessListener {
                progressIndicator.visibility = View.GONE
                // Remove from local list
                testsList.removeAt(position)
                testsAdapter.notifyItemRemoved(position)

                // Show empty message if no tests left
                if (testsList.isEmpty()) {
                    noTestsMessage.visibility = View.VISIBLE
                }

                Toast.makeText(this, "Test deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressIndicator.visibility = View.GONE
                Log.e(TAG, "Error deleting test: ${e.message}")
                Toast.makeText(this, "Error deleting test: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Base adapter for RecyclerView
     */
    abstract inner class TestsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = testsList.size
    }

    /**
     * Adapter for student view
     */
    inner class StudentTestsAdapter : TestsAdapter() {

        inner class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val testNumberText: TextView = itemView.findViewById(R.id.testNumberText)
            val testTitle: TextView = itemView.findViewById(R.id.testTitle)
            val testDetails: TextView = itemView.findViewById(R.id.testDetails)
            val startTestButton: MaterialButton = itemView.findViewById(R.id.startTestButton)

            init {
                startTestButton.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        openTestActivity(testsList[position])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.test_item, parent, false)
            return StudentViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val studentHolder = holder as StudentViewHolder
            val test = testsList[position]

            // Set test number
            studentHolder.testNumberText.text = (position + 1).toString()

            studentHolder.testTitle.text = test.title
            studentHolder.testDetails.text = "${test.questions.size} questions"
        }
    }

    /**
     * Adapter for teacher view - without view button
     */
    inner class TeacherTestsAdapter : TestsAdapter() {

        inner class TeacherViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val testNumberText: TextView = itemView.findViewById(R.id.testNumberText)
            val testTitle: TextView = itemView.findViewById(R.id.testTitle)
            val testDetails: TextView = itemView.findViewById(R.id.testDetails)
            val editButton: MaterialButton = itemView.findViewById(R.id.editButton)
            val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)

            init {
                editButton.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        editTest(testsList[position])
                    }
                }

                deleteButton.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        confirmAndDeleteTest(testsList[position], position)
                    }
                }

                // Enable clicking on the whole item to preview the test
                itemView.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        openTestPreview(testsList[position])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.test_item_teacher, parent, false)
            return TeacherViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val teacherHolder = holder as TeacherViewHolder
            val test = testsList[position]

            // Set test number
            teacherHolder.testNumberText.text = (position + 1).toString()

            teacherHolder.testTitle.text = test.title
            teacherHolder.testDetails.text = "${test.questions.size} questions"
        }
    }
}