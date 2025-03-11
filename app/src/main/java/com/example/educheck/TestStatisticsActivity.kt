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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.educheck.utilities.Test
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

/**
 * Activity for displaying tests available for statistical analysis by the teacher
 */
class TestStatisticsActivity : AppCompatActivity() {

    // UI components
    private lateinit var testsRecyclerView: RecyclerView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var noTestsMessage: TextView

    // Data
    private val testsList = mutableListOf<Test>()
    private var testsParticipationMap = mutableMapOf<String, Int>()
    private lateinit var testsAdapter: TestsAdapter

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var teacherId: String = ""

    companion object {
        private const val TAG = "TestStatisticsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_test_statistics)

        // Set up insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Get teacher ID
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        teacherId = currentUser.uid

        // Initialize UI components
        initializeUI()

        // Load tests
        loadTeacherTests()
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI() {
        try {
            testsRecyclerView = findViewById(R.id.testsRecyclerView)
            progressIndicator = findViewById(R.id.progressIndicator)
            noTestsMessage = findViewById(R.id.noTestsMessage)

            // Hide the "No tests" message initially
            noTestsMessage.visibility = View.GONE

            // Set up the adapter for the recycler view
            testsAdapter = TestsAdapter()

            // Set up the recycler view
            testsRecyclerView.layoutManager = LinearLayoutManager(this)
            testsRecyclerView.adapter = testsAdapter

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UI: ${e.message}")
            Toast.makeText(this, "Error initializing UI: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Load tests created by the teacher
     */
    private fun loadTeacherTests() {
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE

            // Clear previous data
            testsList.clear()
            testsParticipationMap.clear()

            // Query tests created by the teacher
            firestore.collection("tests")
                .whereEqualTo("createdBy", teacherId)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        // No tests found
                        progressIndicator.visibility = View.GONE
                        noTestsMessage.visibility = View.VISIBLE
                        return@addOnSuccessListener
                    }

                    // Process test documents
                    processTestDocuments(documents)

                    // Check participation for each test
                    checkTestsParticipation()
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
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Process the test documents from Firestore
     */
    private fun processTestDocuments(documents: QuerySnapshot) {
        try {
            // Convert documents to Test objects
            val tempTests = mutableListOf<Test>()
            for (document in documents) {
                try {
                    val test = document.toObject(Test::class.java)
                    if (test.id.isNotEmpty()) {
                        tempTests.add(test)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error converting document to test: ${e.message}")
                }
            }

            // Sort tests by creation time (newest first)
            testsList.addAll(tempTests.sortedByDescending { it.createdAt })

            Log.d(TAG, "Loaded ${testsList.size} tests")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing test documents: ${e.message}")
        }
    }

    /**
     * Check participation for each test
     */
    private fun checkTestsParticipation() {
        if (testsList.isEmpty()) {
            progressIndicator.visibility = View.GONE
            noTestsMessage.visibility = View.VISIBLE
            return
        }

        // Counter for completed async operations
        var completedQueries = 0

        // For each test, get the number of student submissions
        testsList.forEach { test ->
            firestore.collection("test_results")
                .whereEqualTo("testId", test.id)
                .get()
                .addOnSuccessListener { resultsDocuments ->
                    // Save the count of submissions for this test
                    testsParticipationMap[test.id] = resultsDocuments.size()

                    // Increment counter
                    completedQueries++

                    // If all queries are done, update the UI
                    if (completedQueries == testsList.size) {
                        progressIndicator.visibility = View.GONE
                        testsAdapter.notifyDataSetChanged()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking participation for test ${test.id}: ${e.message}")

                    // Still increment counter to avoid deadlock
                    completedQueries++

                    // If all queries are done, update the UI
                    if (completedQueries == testsList.size) {
                        progressIndicator.visibility = View.GONE
                        testsAdapter.notifyDataSetChanged()
                    }
                }
        }
    }

    /**
     * Open the detailed statistics view for a specific test
     */
    private fun openTestStatisticsDetail(test: Test) {
        val intent = Intent(this, TestStatisticsDetailActivity::class.java).apply {
            putExtra("TEST_ID", test.id)
            putExtra("TEST_TITLE", test.title)
        }
        startActivity(intent)
    }

    /**
     * Adapter for the recycler view
     */
    inner class TestsAdapter : RecyclerView.Adapter<TestsAdapter.TestViewHolder>() {

        inner class TestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val testCard: MaterialCardView = itemView.findViewById(R.id.testCard)
            val testTitle: TextView = itemView.findViewById(R.id.testTitle)
            val testDetails: TextView = itemView.findViewById(R.id.testDetails)
            val participationCount: TextView = itemView.findViewById(R.id.participationCount)

            init {
                testCard.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        openTestStatisticsDetail(testsList[position])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.test_statistics_item, parent, false)
            return TestViewHolder(view)
        }

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
            val test = testsList[position]

            // Set test title
            holder.testTitle.text = test.title

            // Set test details
            holder.testDetails.text = "${test.questions.size} questions"

            // Set participation count
            val participationCount = testsParticipationMap[test.id] ?: 0
            holder.participationCount.text = "$participationCount submissions"
        }

        override fun getItemCount() = testsList.size
    }
}