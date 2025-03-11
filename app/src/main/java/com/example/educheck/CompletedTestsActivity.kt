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
import com.example.educheck.utilities.TestResult
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity that displays tests that the student has already solved
 */
class CompletedTestsActivity : AppCompatActivity() {

    private lateinit var testsRecyclerView: RecyclerView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var noTestsMessage: TextView

    private val testResultsList = mutableListOf<TestResult>()
    private lateinit var testsAdapter: CompletedTestsAdapter

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    companion object {
        private const val TAG = "CompletedTestsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_completed_tests)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Set up a listener for system insets updates
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        initializeUI()

        // Load completed tests
        loadCompletedTests()
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
            testsAdapter = CompletedTestsAdapter()

            // Set up the recycler view
            testsRecyclerView.layoutManager = LinearLayoutManager(this)
            testsRecyclerView.adapter = testsAdapter

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UI: ${e.message}")
            Toast.makeText(this, "Error initializing UI: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Load completed tests from Firestore
     */
    private fun loadCompletedTests() {
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE

            // Get the logged-in user ID
            val userId = auth.currentUser?.uid
            if (userId.isNullOrEmpty()) {
                progressIndicator.visibility = View.GONE
                Toast.makeText(this, "Error: User not logged in", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            Log.d(TAG, "Trying to load completed tests for user: $userId")

            // Fetch the student's test results
            firestore.collection("test_results")
                .whereEqualTo("studentId", userId)
                .get()
                .addOnSuccessListener { documents ->
                    progressIndicator.visibility = View.GONE
                    Log.d(TAG, "Found ${documents.size()} test results")

                    testResultsList.clear()

                    try {
                        // Convert documents to objects and sort on the client side
                        val results = documents.mapNotNull { document ->
                            try {
                                // Convert document to TestResult object with ID
                                val result = document.toObject(TestResult::class.java)
                                // Store document ID for later use
                                result.documentId = document.id
                                result
                            } catch (e: Exception) {
                                Log.e(TAG, "Error converting document to test result: ${e.message}")
                                null
                            }
                        }

                        // Sort results by submission time (newest first)
                        val sortedResults = results.sortedByDescending { it.submittedAt }

                        // Add the sorted results to the list
                        testResultsList.addAll(sortedResults)

                        Log.d(TAG, "Tests loaded and sorted, number of results: ${testResultsList.size}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing test results: ${e.message}")
                    }

                    // Update the adapter
                    testsAdapter.notifyDataSetChanged()

                    // Show message if there are no completed tests
                    if (testResultsList.isEmpty()) {
                        noTestsMessage.visibility = View.VISIBLE
                        Log.d(TAG, "No completed tests to display")
                    } else {
                        noTestsMessage.visibility = View.GONE
                    }
                }
                .addOnFailureListener { e ->
                    progressIndicator.visibility = View.GONE
                    noTestsMessage.visibility = View.VISIBLE

                    Log.e(TAG, "Error loading completed tests: ${e.message}")
                    Toast.makeText(this, "Error loading tests: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progressIndicator.visibility = View.GONE
            Log.e(TAG, "General error loading tests: ${e.message}")
            Toast.makeText(this, "Error loading tests: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Get test title dynamically
     */
    private fun getTestTitle(result: TestResult): String {
        // Check if there's a test title in the result
        if (result.testTitle.isNotEmpty()) {
            return result.testTitle
        }

        // If there's no title in the model, try to get it from Firebase
        if (result.testId.isNotEmpty()) {
            firestore.collection("tests").document(result.testId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val title = documentSnapshot.getString("title") ?: "Test"

                        // Update the view
                        testsAdapter.notifyDataSetChanged()
                    }
                }
        }

        // Return default
        return "Test"
    }

    /**
     * View test details with all questions and answers
     */
    private fun viewTestDetails(result: TestResult) {
        try {
            // הטען את המבחן המקורי ביחד עם התשובות של התלמיד
            firestore.collection("tests").document(result.testId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        try {
                            // המר את המסמך למבחן
                            val test = documentSnapshot.toObject(Test::class.java)

                            if (test != null && test.questions.isNotEmpty()) {
                                // פתח את ה-Activity של TakeTestActivity במצב צפייה בלבד
                                val intent = Intent(this, TakeTestActivity::class.java).apply {
                                    putExtra("TEST_ID", test.id)
                                    putExtra("VIEW_MODE", true)
                                    putExtra("TEST_RESULT_ID", result.documentId)
                                }
                                startActivity(intent)
                            } else {
                                Toast.makeText(this, "המבחן אינו מכיל שאלות", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing test data: ${e.message}")
                            Toast.makeText(this, "שגיאה בטעינת המבחן: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "המבחן לא נמצא", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error loading test: ${e.message}")
                    Toast.makeText(this, "שגיאה בטעינת המבחן: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error viewing test details: ${e.message}")
            Toast.makeText(this, "שגיאה בהצגת פרטי המבחן", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Adapter for the recycler view of completed tests
     */
    inner class CompletedTestsAdapter : RecyclerView.Adapter<CompletedTestsAdapter.TestViewHolder>() {

        inner class TestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val testCard: MaterialCardView = itemView.findViewById(R.id.testCard)
            val testTitle: TextView = itemView.findViewById(R.id.testTitle)
            val testScore: TextView = itemView.findViewById(R.id.testScore)
            val testDate: TextView = itemView.findViewById(R.id.testDate)

            init {
                // Set up click listener for the card
                testCard.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        viewTestDetails(testResultsList[position])
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TestViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.completed_test_item, parent, false)
            return TestViewHolder(view)
        }

        override fun onBindViewHolder(holder: TestViewHolder, position: Int) {
            val result = testResultsList[position]

            // Show test title (if there's a title use it, otherwise try to get it)
            holder.testTitle.text = if (result.testTitle.isNotEmpty()) {
                result.testTitle
            } else {
                getTestTitle(result)
            }

            // Set the score
            val score = String.format("%.1f", result.score)
            holder.testScore.text = "Score: $score"

            // Score color based on value
            val textColor = when {
                result.score >= 90 -> getColor(R.color.colorGreen)
                result.score >= 70 -> getColor(R.color.colorLightGreen)
                result.score >= 60 -> getColor(R.color.colorOrange)
                else -> getColor(R.color.colorRed)
            }
            holder.testScore.setTextColor(textColor)

            // Set test date
            try {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = Date(result.submittedAt)
                holder.testDate.text = dateFormat.format(date)
            } catch (e: Exception) {
                // In case of error processing the date
                holder.testDate.text = ""
                Log.e(TAG, "Error processing date: ${e.message}")
            }
        }

        override fun getItemCount() = testResultsList.size
    }
}