package com.example.educheck

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.educheck.utilities.Question
import com.example.educheck.utilities.TestResult
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.roundToInt

/**
 * Activity for displaying detailed statistics of a specific test
 */
class TestStatisticsDetailActivity : AppCompatActivity() {

    // UI components
    private lateinit var testTitleTextView: TextView
    private lateinit var participationCountTextView: TextView
    private lateinit var averageScoreTextView: TextView
    private lateinit var minScoreTextView: TextView
    private lateinit var maxScoreTextView: TextView
    private lateinit var scoresDistributionChart: BarChart
    private lateinit var questionsViewPager: ViewPager2
    private lateinit var questionsTabLayout: TabLayout
    private lateinit var currentQuestionTextView: TextView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var noDataTextView: TextView

    // Data
    private lateinit var testId: String
    private lateinit var testTitle: String
    private var testResultsList = mutableListOf<TestResult>()
    private var questionStatistics = mutableMapOf<String, QuestionStats>()
    private lateinit var test: com.example.educheck.utilities.Test
    private lateinit var pagerAdapter: QuestionStatisticsPagerAdapter

    // Firebase
    private lateinit var firestore: FirebaseFirestore

    companion object {
        private const val TAG = "TestStatisticsDetail"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_test_statistics_detail)

        // Set up insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get test ID from intent
        testId = intent.getStringExtra("TEST_ID") ?: ""
        testTitle = intent.getStringExtra("TEST_TITLE") ?: "Test Statistics"

        if (testId.isEmpty()) {
            Toast.makeText(this, "Error: Invalid test ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()

        // Initialize UI components
        initializeUI()

        // Load test data and results
        loadTestData()
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI() {
        try {
            testTitleTextView = findViewById(R.id.testTitleTextView)
            participationCountTextView = findViewById(R.id.participationCountTextView)
            averageScoreTextView = findViewById(R.id.averageScoreTextView)
            minScoreTextView = findViewById(R.id.minScoreTextView)
            maxScoreTextView = findViewById(R.id.maxScoreTextView)
            scoresDistributionChart = findViewById(R.id.scoresDistributionChart)
            questionsViewPager = findViewById(R.id.questionsViewPager)
            questionsTabLayout = findViewById(R.id.questionsTabLayout)
            currentQuestionTextView = findViewById(R.id.currentQuestionTextView)
            progressIndicator = findViewById(R.id.progressIndicator)
            noDataTextView = findViewById(R.id.noDataTextView)

            val backButton: ImageButton = findViewById(R.id.backButton)
            backButton.setOnClickListener {
                // חזרה למסך הקודם
                onBackPressed()
            }

            // Set test title
            testTitleTextView.text = testTitle

            // Hide no data message initially
            noDataTextView.visibility = View.GONE

            // Setup scores distribution chart
            setupScoresDistributionChart()

            // Set up ViewPager page change listener
            questionsViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    // Update current question indicator
                    updateCurrentQuestionText(position)
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UI: ${e.message}")
            Toast.makeText(this, "Error initializing UI: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Update the current question text indicator
     */
    private fun updateCurrentQuestionText(position: Int) {
        if (::test.isInitialized && test.questions.isNotEmpty()) {
            currentQuestionTextView.text = "Question ${position + 1} of ${test.questions.size}"
        }
    }

    /**
     * Setup the scores distribution chart
     */
    private fun setupScoresDistributionChart() {
        try {
            // Configure chart appearance
            scoresDistributionChart.description.isEnabled = false
            scoresDistributionChart.setDrawGridBackground(false)
            scoresDistributionChart.setDrawBarShadow(false)
            scoresDistributionChart.setDrawValueAboveBar(true)

            // Disable all interactions
            scoresDistributionChart.setTouchEnabled(false)  // Disable all touch interactions
            scoresDistributionChart.setPinchZoom(false)
            scoresDistributionChart.isDoubleTapToZoomEnabled = false
            scoresDistributionChart.isClickable = false
            scoresDistributionChart.isLongClickable = false

            // Configure X axis
            val xAxis = scoresDistributionChart.xAxis
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.isGranularityEnabled = true

            // Configure left Y axis
            val leftAxis = scoresDistributionChart.axisLeft
            leftAxis.setDrawGridLines(true)
            leftAxis.axisMinimum = 0f

            // Disable right Y axis
            scoresDistributionChart.axisRight.isEnabled = false

            // Set "no data" text
            scoresDistributionChart.setNoDataText("No student submissions")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up scores distribution chart: ${e.message}")
        }
    }

    /**
     * Load test data and results
     */
    private fun loadTestData() {
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE

            // Load the test data first
            firestore.collection("tests").document(testId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // Convert to Test object
                        test = document.toObject(com.example.educheck.utilities.Test::class.java) ?: com.example.educheck.utilities.Test()

                        // If test has no ID, use document ID
                        if (test.id.isEmpty()) {
                            test = test.copy(id = document.id)
                        }

                        // Now load test results
                        loadTestResults()
                    } else {
                        // Test not found
                        progressIndicator.visibility = View.GONE
                        noDataTextView.visibility = View.VISIBLE
                        noDataTextView.text = "Test not found"
                    }
                }
                .addOnFailureListener { e ->
                    progressIndicator.visibility = View.GONE
                    noDataTextView.visibility = View.VISIBLE
                    Log.e(TAG, "Error loading test: ${e.message}")
                    Toast.makeText(this, "Error loading test: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progressIndicator.visibility = View.GONE
            Log.e(TAG, "General error loading test: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Load test results from Firestore
     */
    private fun loadTestResults() {
        try {
            firestore.collection("test_results")
                .whereEqualTo("testId", testId)
                .get()
                .addOnSuccessListener { documents ->
                    progressIndicator.visibility = View.GONE

                    if (documents.isEmpty) {
                        // No results found
                        noDataTextView.visibility = View.VISIBLE
                        noDataTextView.text = "No student submissions"
                        return@addOnSuccessListener
                    }

                    // Process results
                    testResultsList.clear()
                    for (document in documents) {
                        try {
                            val result = document.toObject(TestResult::class.java)
                            result.documentId = document.id
                            testResultsList.add(result)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document to TestResult: ${e.message}")
                        }
                    }

                    if (testResultsList.isNotEmpty()) {
                        // Process the statistics
                        processTestStatistics()

                        // Update UI with data
                        updateUI()
                    } else {
                        noDataTextView.visibility = View.VISIBLE
                        noDataTextView.text = "No valid student submissions"
                    }
                }
                .addOnFailureListener { e ->
                    progressIndicator.visibility = View.GONE
                    noDataTextView.visibility = View.VISIBLE
                    Log.e(TAG, "Error loading test results: ${e.message}")
                    Toast.makeText(this, "Error loading results: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progressIndicator.visibility = View.GONE
            Log.e(TAG, "General error loading results: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Process test statistics from the results
     */
    private fun processTestStatistics() {
        try {
            // Clear previous statistics
            questionStatistics.clear()

            // Initialize question statistics for each question in the test
            test.questions.forEach { question ->
                questionStatistics[question.id] = QuestionStats(question)
            }

            // Process each test result
            testResultsList.forEach { result ->
                // Process each answer in the result
                result.answers.forEach { answer ->
                    // Only process answers for known questions
                    val stats = questionStatistics[answer.questionId]
                    if (stats != null) {
                        // Increment the count for the selected option
                        if (answer.selectedOptionIndex >= 0 && answer.selectedOptionIndex < stats.optionCounts.size) {
                            stats.optionCounts[answer.selectedOptionIndex]++
                            stats.totalAnswers++
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing test statistics: ${e.message}")
        }
    }

    /**
     * Update UI with test statistics
     */
    private fun updateUI() {
        try {
            // Update participation count
            participationCountTextView.text = "${testResultsList.size} submissions"

            // Calculate score statistics
            val scores = testResultsList.map { it.score }
            val averageScore = scores.average()
            val minScore = scores.minOrNull() ?: 0.0
            val maxScore = scores.maxOrNull() ?: 0.0

            // Update score statistics text views
            averageScoreTextView.text = String.format("%.1f", averageScore)
            minScoreTextView.text = String.format("%.1f", minScore)
            maxScoreTextView.text = String.format("%.1f", maxScore)

            // Update scores distribution chart
            updateScoresDistributionChart(scores)

            // Setup ViewPager with questions
            setupQuestionsPager()

        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI: ${e.message}")
        }
    }

    /**
     * Update the scores distribution chart
     */
    private fun updateScoresDistributionChart(scores: List<Double>) {
        try {
            // Define score ranges using whole numbers
            val ranges = listOf(
                "0-59", "60-69", "70-79", "80-89", "90-100"
            )

            // Count scores in each range
            val rangeCounts = IntArray(5)

            for (score in scores) {
                val rangeIndex = when {
                    score < 60 -> 0
                    score < 70 -> 1
                    score < 80 -> 2
                    score < 90 -> 3
                    else -> 4
                }
                rangeCounts[rangeIndex]++
            }

            // Create entries for the chart
            val entries = ArrayList<BarEntry>()
            for (i in rangeCounts.indices) {
                // Use whole number index
                entries.add(BarEntry(i.toFloat(), rangeCounts[i].toFloat()))
            }

            // Set colors based on score ranges
            val colors = listOf(
                ContextCompat.getColor(this, R.color.colorFail),
                ContextCompat.getColor(this, R.color.colorAverage),
                ContextCompat.getColor(this, R.color.colorGood),
                ContextCompat.getColor(this, R.color.colorVeryGood),
                ContextCompat.getColor(this, R.color.colorExcellent)
            )

            // Create dataset
            val dataSet = BarDataSet(entries, "Score Distribution")
            dataSet.colors = colors
            dataSet.valueTextSize = 12f

            // Create bar data
            val barData = BarData(dataSet)

            // Configure X axis labels
            scoresDistributionChart.xAxis.valueFormatter = IndexAxisValueFormatter(ranges)

            // Adjust chart properties to remove half-steps
            scoresDistributionChart.xAxis.granularity = 1f  // Ensure whole number steps
            scoresDistributionChart.xAxis.isGranularityEnabled = true

            // Set data to chart
            scoresDistributionChart.data = barData

            // Refresh the chart
            scoresDistributionChart.invalidate()

        } catch (e: Exception) {
            Log.e(TAG, "Error updating scores distribution chart: ${e.message}")
        }
    }

    /**
     * Setup ViewPager for questions
     */
    private fun setupQuestionsPager() {
        try {
            // Create the pager adapter
            pagerAdapter = QuestionStatisticsPagerAdapter(
                this,
                test.questions,
                questionStatistics
            )

            // Set adapter to ViewPager
            questionsViewPager.adapter = pagerAdapter

            // Connect TabLayout with ViewPager
            TabLayoutMediator(questionsTabLayout, questionsViewPager) { tab, position ->
                tab.text = "Q${position + 1}"
            }.attach()

            // Update initial question text
            updateCurrentQuestionText(0)

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up questions pager: ${e.message}")
        }
    }

    /**
     * Data class for tracking question statistics
     */
    inner class QuestionStats(val question: Question) {
        var totalAnswers: Int = 0
        val optionCounts: IntArray = IntArray(4) // Assumes max 4 options per question
    }
}