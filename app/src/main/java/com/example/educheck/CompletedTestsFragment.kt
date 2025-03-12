package com.example.educheck.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.educheck.R
import com.example.educheck.TakeTestActivity
import com.example.educheck.utilities.Test
import com.example.educheck.utilities.TestResult
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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

                                // Make sure we have the total questions count for the UI
                                if (result.totalQuestions <= 0) {
                                    // If total questions is not available, fetch it from the test document
                                    fetchTestDetails(result)
                                }

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
     * Fetch additional test details if needed
     */
    private fun fetchTestDetails(result: TestResult) {
        if (result.testId.isNotEmpty()) {
            firestore.collection("tests").document(result.testId)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        try {
                            // Get test title if missing
                            if (result.testTitle.isEmpty()) {
                                val title = documentSnapshot.getString("title") ?: "Test"
                                result.testTitle = title
                            }

                            // Get total questions count if missing
                            if (result.totalQuestions <= 0) {
                                val test = documentSnapshot.toObject(Test::class.java)
                                if (test != null) {
                                    result.totalQuestions = test.questions.size
                                    Log.d(TAG, "Updated total questions for test ${result.testId}: ${result.totalQuestions}")
                                }
                            }

                            // Notify adapter of changes
                            testsAdapter.notifyDataSetChanged()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error fetching test details: ${e.message}")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching test: ${e.message}")
                }
        }
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
     * Adapter for the recycler view of completed tests with enhanced UI
     */
    inner class CompletedTestsAdapter : RecyclerView.Adapter<CompletedTestsAdapter.TestViewHolder>() {

        inner class TestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            // Card and basic info
            val testCard: MaterialCardView = itemView.findViewById(R.id.testCard)
            val testTitle: TextView = itemView.findViewById(R.id.testTitle)
            val testDate: TextView = itemView.findViewById(R.id.testDate)

            // Progress circle and score
            val scoreProgress: CircularProgressIndicator = itemView.findViewById(R.id.scoreProgress)
            val testScore: TextView = itemView.findViewById(R.id.testScore)
            val scoreDescription: TextView = itemView.findViewById(R.id.scoreDescription)

            // Question counters
            val correctAnswers: TextView = itemView.findViewById(R.id.correctAnswers)
            val totalQuestions: TextView = itemView.findViewById(R.id.totalQuestions)

            // Optional course label
            val courseLabel: TextView? = itemView.findViewById(R.id.courseLabel)

            // Icons for correct/total questions cards (might be null if not in layout)
            val correctIcon: ImageView? = itemView.findViewWithTag("correctIcon")
            val questionsIcon: ImageView? = itemView.findViewWithTag("questionsIcon")

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

            // Show test title
            holder.testTitle.text = if (result.testTitle.isNotEmpty()) {
                result.testTitle
            } else {
                "Test ${position + 1}"
            }

            // Set the score (now Double type)
            val scoreValue = result.score
            holder.testScore.text = String.format("%.1f", scoreValue)

            // Set the progress indicator to match the score
            holder.scoreProgress.progress = scoreValue.toInt()

            // Set score color and description based on value
            val (textColor, description) = when {
                scoreValue >= 90 -> Pair(
                    ContextCompat.getColor(requireContext(), R.color.colorGreen),
                    "Excellent!"
                )
                scoreValue >= 80 -> Pair(
                    ContextCompat.getColor(requireContext(), R.color.colorLightGreen),
                    "Very Good!"
                )
                scoreValue >= 70 -> Pair(
                    ContextCompat.getColor(requireContext(), R.color.colorLightGreen),
                    "Good"
                )
                scoreValue >= 60 -> Pair(
                    ContextCompat.getColor(requireContext(), R.color.colorOrange),
                    "Satisfactory"
                )
                else -> Pair(
                    ContextCompat.getColor(requireContext(), R.color.colorRed),
                    "Needs Improvement"
                )
            }

            // Apply colors to UI elements
            holder.testScore.setTextColor(textColor)
            holder.scoreProgress.setIndicatorColor(textColor)
            holder.scoreDescription.text = description

            // Set correct answers and total questions counts
            val totalQuestionsCount = result.totalQuestions

            if (totalQuestionsCount > 0) {
                // Calculate correct answers based on score percentage
                val correctAnswersCount = ((scoreValue * totalQuestionsCount) / 100.0).roundToInt()

                holder.correctAnswers.text = correctAnswersCount.toString()
                holder.totalQuestions.text = totalQuestionsCount.toString()

                // Set colors for the icons if they exist
                holder.correctIcon?.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.colorGreen)
                )
                holder.questionsIcon?.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.colorBlue)
                )
            } else {
                // If total questions is not available yet
                holder.correctAnswers.text = "-"
                holder.totalQuestions.text = "-"
            }

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