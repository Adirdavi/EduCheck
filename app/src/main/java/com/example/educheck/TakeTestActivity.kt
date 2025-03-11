package com.example.educheck

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.educheck.utilities.Question
import com.example.educheck.utilities.StudentAnswer
import com.example.educheck.utilities.Test
import com.example.educheck.utilities.TestResult
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

/**
 * Activity allowing a student to take a test.
 * Can also be used to view completed tests with correct answers
 */
class TakeTestActivity : AppCompatActivity() {

    // UI component references
    private lateinit var testTitleTextView: TextView
    private lateinit var questionNumberTextView: TextView
    private lateinit var questionTextView: TextView
    private lateinit var optionsRadioGroup: RadioGroup
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var progressIndicator: LinearProgressIndicator

    // Test information
    private var testId: String = ""
    private lateinit var currentTest: Test
    private var currentQuestionIndex = 0

    // View mode (for reviewing completed tests)
    private var viewMode = false
    private var testResultId: String = ""
    private lateinit var testResult: TestResult

    // Snapshot of test questions at time of submission (for review mode)
    private var testQuestionSnapshots = mutableListOf<Question>()

    // List of student answers
    private val studentAnswers = mutableMapOf<String, Int>()

    // Firebase objects
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    companion object {
        private const val TAG = "TakeTestActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Load the layout file for the screen
        setContentView(R.layout.activity_take_test)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Set listener for system insets updates
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Check if we're in view mode
        viewMode = intent.getBooleanExtra("VIEW_MODE", false)

        // Get test ID from intent
        testId = intent.getStringExtra("TEST_ID") ?: ""
        if (testId.isEmpty()) {
            Toast.makeText(this, "Error: No test ID received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // If in view mode, get the test result ID
        if (viewMode) {
            testResultId = intent.getStringExtra("TEST_RESULT_ID") ?: ""
            if (testResultId.isEmpty()) {
                Toast.makeText(this, "Error: No test result ID received", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        // Initialize UI components
        initializeUI()

        // Load the test data
        if (viewMode) {
            loadTestResultFromFirebase()
        } else {
            loadTestFromFirebase()
        }
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI() {
        try {
            testTitleTextView = findViewById(R.id.testTitleTextView)
            questionNumberTextView = findViewById(R.id.questionNumberTextView)
            questionTextView = findViewById(R.id.questionTextView)
            optionsRadioGroup = findViewById(R.id.optionsRadioGroup)
            previousButton = findViewById(R.id.previousButton)
            nextButton = findViewById(R.id.nextButton)
            progressIndicator = findViewById(R.id.progressIndicator)

            // Set button text according to mode
            if (viewMode) {
                nextButton.text = if (currentQuestionIndex == 0) "Next" else "Finish"
            }

            // Set button listeners
            previousButton.setOnClickListener {
                if (!viewMode) saveCurrrentAnswer()
                if (currentQuestionIndex > 0) {
                    currentQuestionIndex--
                    displayCurrentQuestion()
                }
            }

            nextButton.setOnClickListener {
                if (!viewMode) saveCurrrentAnswer()
                if (currentQuestionIndex < getQuestionsToDisplay().size - 1) {
                    currentQuestionIndex++
                    displayCurrentQuestion()
                } else {
                    if (viewMode) {
                        // Just finish the activity in view mode
                        finish()
                    } else {
                        // Show confirmation dialog in test mode
                        showSubmitConfirmationDialog()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UI: ${e.message}")
            Toast.makeText(this, "Error initializing UI: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Load the test from Firebase
     */
    private fun loadTestFromFirebase() {
        try {
            // Show loading indicator (if exists)
            val loadingIndicator = findViewById<ProgressBar>(R.id.loadingIndicator)
            loadingIndicator?.visibility = View.VISIBLE

            firestore.collection("tests").document(testId)
                .get()
                .addOnSuccessListener { document ->
                    loadingIndicator?.visibility = View.GONE

                    if (document != null && document.exists()) {
                        try {
                            // Convert document to test
                            currentTest = document.toObject(Test::class.java) ?: Test()

                            // Ensure valid ID (if missing, use document ID)
                            if (currentTest.id.isEmpty()) {
                                currentTest = currentTest.copy(id = document.id)
                            }

                            // Verify test has questions
                            if (currentTest.questions.isEmpty()) {
                                Toast.makeText(this, "The test contains no questions", Toast.LENGTH_SHORT).show()
                                finish()
                                return@addOnSuccessListener
                            }

                            // Display test title and first question
                            testTitleTextView.text = currentTest.title
                            displayCurrentQuestion()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting test: ${e.message}")
                            Toast.makeText(this, "Error loading test: ${e.message}", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        Toast.makeText(this, "Test not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    loadingIndicator?.visibility = View.GONE
                    Log.e(TAG, "Error loading test: ${e.message}")
                    Toast.makeText(this, "Error loading test: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
        } catch (e: Exception) {
            Log.e(TAG, "General error loading test: ${e.message}")
            Toast.makeText(this, "Error loading test: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Load test result and original test for view mode
     */
    private fun loadTestResultFromFirebase() {
        try {
            // Show loading indicator
            val loadingIndicator = findViewById<ProgressBar>(R.id.loadingIndicator)
            loadingIndicator?.visibility = View.VISIBLE

            // First load the test result
            firestore.collection("test_results").document(testResultId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        try {
                            // Convert document to TestResult
                            testResult = document.toObject(TestResult::class.java) ?: TestResult()

                            // Save the result document ID for further operations
                            testResult.documentId = document.id

                            // Check if the test was deleted
                            val testDeleted = document.getBoolean("testDeleted") ?: false

                            // Save the student's answers to our map
                            testResult.answers.forEach { answer ->
                                studentAnswers[answer.questionId] = answer.selectedOptionIndex
                            }

                            // Load the question snapshots if available
                            val snapshotsList = document.get("questionSnapshots") as? List<Map<String, Any>>
                            if (!snapshotsList.isNullOrEmpty()) {
                                // Convert snapshots to Question objects
                                testQuestionSnapshots = snapshotsList.mapNotNull { snapshot ->
                                    try {
                                        val id = snapshot["id"] as? String ?: ""
                                        val text = snapshot["text"] as? String ?: ""
                                        val options = (snapshot["options"] as? List<*>)?.map { it.toString() } ?: listOf()
                                        val correctOptionIndex = (snapshot["correctOptionIndex"] as? Long)?.toInt() ?: -1

                                        Question(id, text, options, correctOptionIndex)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error converting question snapshot: ${e.message}")
                                        null
                                    }
                                }.toMutableList()

                                Log.d(TAG, "Loaded ${testQuestionSnapshots.size} question snapshots")
                            }

                            // Now handle the appropriate action
                            if (testDeleted) {
                                // Test was deleted by teacher - handle it gracefully
                                handleDeletedTest(testResult)
                            } else if (testQuestionSnapshots.isNotEmpty()) {
                                // We have question snapshots, use them
                                testTitleTextView.text = testResult.testTitle
                                displayCurrentQuestion()
                            } else {
                                // No snapshots, try to load the original test
                                loadOriginalTestForReview()
                            }
                        } catch (e: Exception) {
                            loadingIndicator?.visibility = View.GONE
                            Log.e(TAG, "Error converting test result: ${e.message}")
                            Toast.makeText(this, "Error loading test result: ${e.message}", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    } else {
                        loadingIndicator?.visibility = View.GONE
                        Toast.makeText(this, "Test result not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    loadingIndicator?.visibility = View.GONE
                    Log.e(TAG, "Error loading test result: ${e.message}")
                    Toast.makeText(this, "Error loading test result: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
        } catch (e: Exception) {
            Log.e(TAG, "General error loading test result: ${e.message}")
            Toast.makeText(this, "Error loading test result: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Handle the case where a test was deleted but student wants to view results
     */
    private fun handleDeletedTest(result: TestResult) {
        try {
            // הסתר מחוון טעינה
            val loadingIndicator = findViewById<ProgressBar>(R.id.loadingIndicator)
            loadingIndicator?.visibility = View.GONE

            // הצג התרעה לסטודנט
            AlertDialog.Builder(this)
                .setTitle("Test Not Available")
                .setMessage("This test has been deleted by the teacher. You can still see your score, but the details of the questions and answers are no longer available.")
                .setPositiveButton("OK") { _, _ ->
                    // חזור למסך הקודם
                    finish()
                }
                .setCancelable(false)
                .show()

        } catch (e: Exception) {
            Log.e(TAG, "Error handling deleted test: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Load the original test from Firestore for review
     */
    private fun loadOriginalTestForReview() {
        val loadingIndicator = findViewById<ProgressBar>(R.id.loadingIndicator)

        firestore.collection("tests").document(testResult.testId)
            .get()
            .addOnSuccessListener { document ->
                loadingIndicator?.visibility = View.GONE

                if (document != null && document.exists()) {
                    try {
                        // Convert document to test
                        currentTest = document.toObject(Test::class.java) ?: Test()

                        // Ensure valid ID
                        if (currentTest.id.isEmpty()) {
                            currentTest = currentTest.copy(id = document.id)
                        }

                        // The test may have been updated, but we still need to display it
                        if (currentTest.questions.isEmpty()) {
                            Toast.makeText(this, "The test contains no questions", Toast.LENGTH_SHORT).show()
                            finish()
                            return@addOnSuccessListener
                        }

                        // Store a flag that the test might have been updated
                        val testUpdated = !verifyAnswerIdsMatch()

                        if (testUpdated) {
                            // Alert the student that the test has been updated
                            AlertDialog.Builder(this)
                                .setTitle("Test Updated")
                                .setMessage("This test has been updated by the teacher since you took it. The questions you see may be different from what you answered.")
                                .setPositiveButton("OK", null)
                                .show()
                        }

                        // Display test title and first question
                        testTitleTextView.text = currentTest.title
                        displayCurrentQuestion()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error converting test: ${e.message}")
                        Toast.makeText(this, "Error loading test: ${e.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    // The test has been deleted or not found
                    // Update the test result to mark it as deleted
                    firestore.collection("test_results").document(testResult.documentId)
                        .update("testDeleted", true)
                        .addOnSuccessListener {
                            handleDeletedTest(testResult)
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Error updating test result: ${e.message}")
                            Toast.makeText(this, "Test not found", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                }
            }
            .addOnFailureListener { e ->
                loadingIndicator?.visibility = View.GONE
                Log.e(TAG, "Error loading test: ${e.message}")
                Toast.makeText(this, "Error loading test: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    /**
     * Verify that the answered question IDs match the current test questions
     */
    private fun verifyAnswerIdsMatch(): Boolean {
        val answeredQuestionIds = testResult.answers.map { it.questionId }.toSet()
        val currentQuestionIds = currentTest.questions.map { it.id }.toSet()

        // Consider it a match if all answered questions exist in the current test
        return answeredQuestionIds.all { it in currentQuestionIds }
    }

    /**
     * Get the appropriate questions to display based on the mode
     */
    private fun getQuestionsToDisplay(): List<Question> {
        return when {
            !viewMode -> currentTest.questions
            testQuestionSnapshots.isNotEmpty() -> testQuestionSnapshots
            else -> {
                // Filter current test questions to only those that were answered
                val answeredQuestionIds = testResult.answers.map { it.questionId }.toSet()
                currentTest.questions.filter { it.id in answeredQuestionIds }
            }
        }
    }

    /**
     * Display the current question
     */
    private fun displayCurrentQuestion() {
        val questions = getQuestionsToDisplay()
        if (questions.isEmpty()) {
            Toast.makeText(this, "No questions to display", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (currentQuestionIndex >= questions.size) {
            currentQuestionIndex = 0
        }

        try {
            // Update question number and progress
            val question = questions[currentQuestionIndex]
            questionNumberTextView.text = "Question ${currentQuestionIndex + 1} of ${questions.size}"
            progressIndicator.max = questions.size
            progressIndicator.progress = currentQuestionIndex + 1

            // Update question text
            questionTextView.text = question.text

            // Update options
            optionsRadioGroup.removeAllViews()

            // Create radio buttons for each option
            val optionLetters = listOf("A", "B", "C", "D")
            question.options.forEachIndexed { index, optionText ->
                if (optionText.isNotEmpty()) {
                    val radioButton = RadioButton(this).apply {
                        id = View.generateViewId()
                        text = "${optionLetters[index]}. $optionText"
                        textSize = 16f
                        setPadding(8, 16, 8, 16)

                        // In view mode, set background color based on whether it's correct/incorrect
                        if (viewMode) {
                            isEnabled = false

                            val studentAnswer = studentAnswers[question.id] ?: -1

                            when {
                                // This is the correct answer
                                index == question.correctOptionIndex -> {
                                    setBackgroundColor(ContextCompat.getColor(this@TakeTestActivity, R.color.colorLightGreen))
                                    if (index == studentAnswer) {
                                        text = "$text ✓" // Add check mark for correct student answer
                                    }
                                }
                                // This is the wrong answer selected by student
                                index == studentAnswer && index != question.correctOptionIndex -> {
                                    setBackgroundColor(ContextCompat.getColor(this@TakeTestActivity, R.color.colorRed))
                                    text = "$text ✗" // Add X mark for wrong student answer
                                }
                            }
                        }
                    }

                    optionsRadioGroup.addView(radioButton)

                    // Check if student already selected an answer for this question
                    if (studentAnswers[question.id] == index) {
                        radioButton.isChecked = true
                    }
                }
            }

            // Update button state
            previousButton.isEnabled = currentQuestionIndex > 0

            // Update next button text
            if (viewMode) {
                nextButton.text = if (currentQuestionIndex == questions.size - 1) "Finish" else "Next"
            } else {
                nextButton.text = if (currentQuestionIndex == questions.size - 1) "Finish Test" else "Next"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying question: ${e.message}")
        }
    }

    /**
     * Save the current answer
     */
    private fun saveCurrrentAnswer() {
        val questions = getQuestionsToDisplay()
        if (questions.isEmpty() || currentQuestionIndex >= questions.size) {
            return
        }

        try {
            val checkedId = optionsRadioGroup.checkedRadioButtonId
            if (checkedId != -1) {
                // Find the index of the selected button
                for (i in 0 until optionsRadioGroup.childCount) {
                    val radioButton = optionsRadioGroup.getChildAt(i) as RadioButton
                    if (radioButton.id == checkedId) {
                        // Save the answer
                        val questionId = questions[currentQuestionIndex].id
                        studentAnswers[questionId] = i
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving answer: ${e.message}")
        }
    }

    /**
     * Show submission confirmation dialog
     */
    private fun showSubmitConfirmationDialog() {
        try {
            AlertDialog.Builder(this)
                .setTitle("Submit the test?")
                .setMessage("After submission, you cannot edit your answers. Are you sure you want to submit the test?")
                .setPositiveButton("Submit") { _, _ ->
                    submitTest()
                }
                .setNegativeButton("Back to test", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying submission dialog: ${e.message}")
        }
    }

    /**
     * Submit the test for grading
     */
    private fun submitTest() {
        try {
            // Save the last answer
            saveCurrrentAnswer()

            // Show loading indicator
            val loadingIndicator = findViewById<ProgressBar>(R.id.loadingIndicator)
            loadingIndicator?.visibility = View.VISIBLE

            // Calculate score
            var correctAnswers = 0
            val answersToSubmit = mutableListOf<StudentAnswer>()

            // Create snapshots of the questions to save with the result
            val questionSnapshots = mutableListOf<Map<String, Any>>()

            currentTest.questions.forEach { question ->
                // Create a snapshot of this question
                val questionSnapshot = mapOf(
                    "id" to question.id,
                    "text" to question.text,
                    "options" to question.options,
                    "correctOptionIndex" to question.correctOptionIndex
                )
                questionSnapshots.add(questionSnapshot)

                // Process student answer
                val studentAnswer = studentAnswers[question.id]
                if (studentAnswer != null) {
                    // Add the answer to the list of answers to submit
                    answersToSubmit.add(StudentAnswer(question.id, studentAnswer))

                    // Check if the answer is correct
                    if (studentAnswer == question.correctOptionIndex) {
                        correctAnswers++
                    }
                }
            }

            // Calculate percentage score
            val score = if (currentTest.questions.isNotEmpty()) {
                (correctAnswers.toDouble() / currentTest.questions.size) * 100
            } else {
                0.0
            }

            // Get logged-in user ID (student)
            val userId = auth.currentUser?.uid ?: "unknown"

            // Check if a previous result exists for this test
            firestore.collection("test_results")
                .whereEqualTo("studentId", userId)
                .whereEqualTo("testId", testId)
                .get()
                .addOnSuccessListener { documents ->
                    var highestPreviousScore = 0.0
                    var highestScoreDocId = ""
                    var previousResultExists = false

                    // Find the highest previous score
                    for (document in documents) {
                        previousResultExists = true
                        val previousResult = document.toObject(TestResult::class.java)
                        if (previousResult.score > highestPreviousScore) {
                            highestPreviousScore = previousResult.score
                            highestScoreDocId = document.id
                        }
                    }

                    // Only proceed if this is a new test (no previous results)
                    // OR if this score is higher than the previous best
                    if (!previousResultExists || score > highestPreviousScore) {
                        // Create test result object
                        val testResult = TestResult(
                            id = UUID.randomUUID().toString(),
                            testId = testId,
                            testTitle = currentTest.title, // Save test title
                            studentId = userId,
                            answers = answersToSubmit,
                            score = score,
                            submittedAt = System.currentTimeMillis(),
                            questionSnapshots = questionSnapshots // Save question snapshots
                        )

                        // Delete previous best score if exists (optional)
                        if (highestScoreDocId.isNotEmpty()) {
                            firestore.collection("test_results").document(highestScoreDocId)
                                .delete()
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Error deleting previous result: ${e.message}")
                                    // Continue anyway
                                }
                        }

                        // Save new best result to Firebase
                        firestore.collection("test_results").document(testResult.id)
                            .set(testResult)
                            .addOnSuccessListener {
                                loadingIndicator?.visibility = View.GONE
                                showResultDialog(score, correctAnswers, highestPreviousScore, isNewTest = !previousResultExists)
                            }
                            .addOnFailureListener { e ->
                                loadingIndicator?.visibility = View.GONE
                                Log.e(TAG, "Error saving test results: ${e.message}")
                                Toast.makeText(this, "Error saving test results: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    } else {
                        // Current score is not better than previous best
                        loadingIndicator?.visibility = View.GONE
                        showResultDialog(score, correctAnswers, highestPreviousScore, isBestScore = false)
                    }
                }
                .addOnFailureListener { e ->
                    loadingIndicator?.visibility = View.GONE
                    Log.e(TAG, "Error checking previous results: ${e.message}")
                    Toast.makeText(this, "Error checking previous results: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "General error submitting test: ${e.message}")
            Toast.makeText(this, "Error submitting test: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Show dialog with test results
     */
    private fun showResultDialog(score: Double, correctAnswers: Int, previousBestScore: Double = 0.0,
                                 isBestScore: Boolean = true, isNewTest: Boolean = true) {
        try {
            val scoreFormatted = String.format("%.1f", score)
            val previousScoreFormatted = String.format("%.1f", previousBestScore)

            val messageBuilder = StringBuilder()
            messageBuilder.append("Score: $scoreFormatted\n")
            messageBuilder.append("Correct answers: $correctAnswers out of ${currentTest.questions.size}\n")

            // Only show previous score info if this is not a new test
            if (!isNewTest) {
                if (isBestScore) {
                    // This is a new high score, show the improvement
                    messageBuilder.append("\nPrevious best: $previousScoreFormatted\n")
                    val improvement = score - previousBestScore
                    messageBuilder.append("Improvement: +${String.format("%.1f", improvement)}")
                } else {
                    // Not a high score
                    messageBuilder.append("\nYour best score: $previousScoreFormatted\n")
                    messageBuilder.append("This result will not be saved.")
                }
            }

            // Determine appropriate title
            val title = when {
                isNewTest -> "Test submitted successfully"
                isBestScore -> "New High Score!"
                else -> "Test Completed"
            }

            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(messageBuilder.toString())
                .setPositiveButton("Finish") { _, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying results dialog: ${e.message}")
            Toast.makeText(this, "Test submitted successfully. Score: $score", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}


/**
 * Class representing a student's test result
 */
