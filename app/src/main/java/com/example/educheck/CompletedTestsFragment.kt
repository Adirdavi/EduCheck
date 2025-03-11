package com.example.educheck.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.educheck.R
import com.example.educheck.TakeTestActivity
import com.example.educheck.utilities.Test
import com.example.educheck.utilities.TestResult
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fragment that displays tests that the student has already completed
 */
class CompletedTestsFragment : Fragment() {

    private lateinit var testsRecyclerView: RecyclerView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var noTestsMessage: TextView

    private val testResultsList = mutableListOf<TestResult>()
    private lateinit var testsAdapter: CompletedTestsAdapter

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    companion object {
        private const val TAG = "CompletedTestsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_completed_tests, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize UI components
        initializeUI(view)

        // Load completed tests
        loadCompletedTests()
    }

    override fun onResume() {
        super.onResume()
        if (::firestore.isInitialized) {
            loadCompletedTests()
        }
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI(view: View) {
        try {
            testsRecyclerView = view.findViewById(R.id.testsRecyclerView)
            progressIndicator = view.findViewById(R.id.progressIndicator)
            noTestsMessage = view.findViewById(R.id.noTestsMessage)

            // Hide the "No tests" message initially
            noTestsMessage.visibility = View.GONE

            // Set up the adapter for the recycler view
            testsAdapter = CompletedTestsAdapter()

            // Set up the recycler view
            testsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            testsRecyclerView.adapter = testsAdapter

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UI: ${e.message}")
            Toast.makeText(requireContext(), "Error initializing UI: ${e.message}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), "Error: User not logged in", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "Error loading tests: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progressIndicator.visibility = View.GONE
            Log.e(TAG, "General error loading tests: ${e.message}")
            Toast.makeText(requireContext(), "Error loading tests: ${e.message}", Toast.LENGTH_LONG).show()
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
            // Load the original test along with student's answers
            firestore.collection("tests").document(result.testId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        try {
                            // Convert document to test
                            val test = documentSnapshot.toObject(Test::class.java)

                            if (test != null && test.questions.isNotEmpty()) {
                                // Open the TakeTestActivity in view-only mode
                                val intent = Intent(requireActivity(), TakeTestActivity::class.java).apply {
                                    putExtra("TEST_ID", test.id)
                                    putExtra("VIEW_MODE", true)
                                    putExtra("TEST_RESULT_ID", result.documentId)
                                }
                                startActivity(intent)
                            } else {
                                Toast.makeText(requireContext(), "The test does not contain questions", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing test data: ${e.message}")
                            Toast.makeText(requireContext(), "Error loading test: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Test not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error loading test: ${e.message}")
                    Toast.makeText(requireContext(), "Error loading test: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error viewing test details: ${e.message}")
            Toast.makeText(requireContext(), "Error viewing test details", Toast.LENGTH_SHORT).show()
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
                result.score >= 90 -> requireContext().getColor(R.color.colorGreen)
                result.score >= 70 -> requireContext().getColor(R.color.colorLightGreen)
                result.score >= 60 -> requireContext().getColor(R.color.colorOrange)
                else -> requireContext().getColor(R.color.colorRed)
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