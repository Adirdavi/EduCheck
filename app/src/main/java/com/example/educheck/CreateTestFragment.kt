package com.example.educheck.fragments

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.educheck.R
import com.example.educheck.TestsActivity
import com.example.educheck.utilities.Question
import com.example.educheck.utilities.Test
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

/**
 * Fragment for creating a new test or editing an existing one
 */
class CreateTestFragment : Fragment() {

    // List of questions in the test
    private val questionsList = mutableListOf<Question>()

    // Adapter for the questions recycler view
    private lateinit var questionsAdapter: QuestionsAdapter

    // UI components
    private lateinit var testTitleInput: TextInputEditText
    private lateinit var addQuestionButton: MaterialButton
    private lateinit var saveTestButton: MaterialButton
    private lateinit var questionsRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar

    // Title TextView for "Create New Test" (might be null)
    private var createNewTestTitle: TextView? = null

    // Firebase objects
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // Currently selected option index
    private var selectedOptionIndex: Int = -1

    // Edit mode variables
    private var editMode = false
    private var editingTestId = ""

    companion object {
        private const val TAG = "CreateTestFragment"

        fun newInstance(editMode: Boolean = false, testId: String = ""): CreateTestFragment {
            val fragment = CreateTestFragment()
            val args = Bundle()
            args.putBoolean("EDIT_MODE", editMode)
            args.putString("TEST_ID", testId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_create_test, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Check if we're in edit mode
        arguments?.let {
            editMode = it.getBoolean("EDIT_MODE", false)
            if (editMode) {
                editingTestId = it.getString("TEST_ID") ?: ""
            }
        }

        try {
            // Initialize UI components
            initializeUI(view)

            // Set up listeners
            setupListeners()

            // Load test if in edit mode
            if (editMode && editingTestId.isNotEmpty()) {
                loadTestFromFirebase(editingTestId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}")
            Toast.makeText(requireContext(), "Error initializing view: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI(view: View) {
        try {
            testTitleInput = view.findViewById(R.id.testTitleInput)
            addQuestionButton = view.findViewById(R.id.addQuestionButton)
            saveTestButton = view.findViewById(R.id.saveTestButton)
            questionsRecyclerView = view.findViewById(R.id.questionsRecyclerView)
            progressBar = view.findViewById(R.id.progressBar)

            // עדכון טקסט הכפתור בהתאם למצב
            saveTestButton.text = if (editMode) "Update Test" else "Save Test"

            // נסה למצוא את כותרת "Create New Test" (אם קיימת)
            try {
                // בדוק אם יש TextView עם ID מסוים שמכיל את הכותרת "Create New Test"
                createNewTestTitle = view.findViewById(R.id.headerTitle)

                // אם לא מצאנו, ננסה למצוא לפי הטקסט
                if (createNewTestTitle == null) {
                    if (view is ViewGroup) {
                        for (i in 0 until view.childCount) {
                            val child = view.getChildAt(i)
                            if (child is TextView && child.text == "Create New Test") {
                                createNewTestTitle = child
                                break
                            } else if (child is ViewGroup) {
                                // חיפוש רקורסיבי
                                createNewTestTitle = findTextViewWithText(child, "Create New Test")
                                if (createNewTestTitle != null) break
                            }
                        }
                    }
                }

                // אם מצאנו את הכותרת, נטפל בה בהתאם למצב
                if (createNewTestTitle != null) {
                    if (editMode) {
                        // במצב עריכה - הסתר את הכותרת
                        createNewTestTitle?.visibility = View.GONE
                    } else {
                        // במצב יצירה - הצג את הכותרת
                        createNewTestTitle?.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not find or modify 'Create New Test' title: ${e.message}")
            }

            Log.d(TAG, "Found all UI elements")

            // Set up recycler view and adapter
            setupRecyclerView()

        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeUI: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTrace.joinToString("\n")}")
            Toast.makeText(requireContext(), "Error initializing UI: ${e.message}", Toast.LENGTH_SHORT).show()
            throw e  // Rethrow to see in logs
        }
    }

    /**
     * חיפוש רקורסיבי של TextView עם טקסט מסוים
     */
    private fun findTextViewWithText(root: ViewGroup, text: String): TextView? {
        for (i in 0 until root.childCount) {
            val view = root.getChildAt(i)
            if (view is TextView && view.text == text) {
                return view
            } else if (view is ViewGroup) {
                val result = findTextViewWithText(view, text)
                if (result != null) return result
            }
        }
        return null
    }

    /**
     * Set up the RecyclerView and its adapter
     */
    private fun setupRecyclerView() {
        // Create adapter instance
        questionsAdapter = QuestionsAdapter()

        // Set up RecyclerView
        questionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = questionsAdapter
        }

        Log.d(TAG, "RecyclerView and adapter set up")
    }

    /**
     * Set up button listeners
     */
    private fun setupListeners() {
        // Add question button listener
        addQuestionButton.setOnClickListener {
            // Create new empty question and open edit dialog
            val newQuestion = Question(
                id = UUID.randomUUID().toString(),
                text = "",
                options = listOf("", "", "", ""),
                correctOptionIndex = -1  // Default: no correct answer
            )
            openEditQuestionDialog(newQuestion, -1)
        }

        // Save test button listener
        saveTestButton.setOnClickListener {
            saveTestToFirebase()
        }
    }

    /**
     * Delete question and update Firebase immediately
     * This is the key function for deleting questions from existing tests
     */
    private fun deleteQuestionAndUpdateFirebase(position: Int) {
        if (position < 0 || position >= questionsList.size) {
            Log.e(TAG, "Invalid position: $position")
            return
        }

        try {
            // Show progress indicator
            progressBar.visibility = View.VISIBLE

            // Check if this is an existing test (edit mode)
            if (editMode && editingTestId.isNotEmpty()) {
                // Remove the question from local list
                val removedQuestion = questionsList.removeAt(position)
                Log.d(TAG, "Question removed from local list. Updating Firebase...")

                // Check if there are no questions left
                if (questionsList.isEmpty()) {
                    // If no questions left, delete the entire test
                    deleteEntireTest()
                } else {
                    // Create updated test object with removed question
                    val updatedTest = Test(
                        id = editingTestId,
                        title = testTitleInput.text.toString().trim(),
                        questions = questionsList,
                        createdBy = auth.currentUser?.uid ?: "unknown",
                        createdAt = System.currentTimeMillis()
                    )

                    // Update test in Firebase
                    firestore.collection("tests").document(editingTestId)
                        .set(updatedTest)
                        .addOnSuccessListener {
                            progressBar.visibility = View.GONE

                            // Update adapter
                            questionsAdapter.notifyDataSetChanged()

                            Toast.makeText(requireContext(), "Question deleted and test updated", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Firebase test updated successfully after question deletion")
                        }
                        .addOnFailureListener { e ->
                            progressBar.visibility = View.GONE

                            // If failed, add the question back to the local list
                            questionsList.add(position, removedQuestion)
                            questionsAdapter.notifyDataSetChanged()

                            Toast.makeText(requireContext(), "Failed to update test: ${e.message}", Toast.LENGTH_SHORT).show()
                            Log.e(TAG, "Error updating test in Firebase: ${e.message}")
                        }
                }
            } else {
                // For a new test (not in edit mode), just remove from local list
                questionsList.removeAt(position)
                questionsAdapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE

                Toast.makeText(requireContext(), "Question deleted", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Question deleted from new test (not yet saved to Firebase)")
            }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Log.e(TAG, "Error in deleteQuestionAndUpdateFirebase: ${e.message}", e)
            Toast.makeText(requireContext(), "Error deleting question: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Delete entire test when it becomes empty
     */
    private fun deleteEntireTest() {
        // מחיקת המבחן מ-Firebase
        firestore.collection("tests").document(editingTestId)
            .delete()
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Test deleted (no questions remain)", Toast.LENGTH_SHORT).show()

                // חזור למסך הקודם
                try {
                    val activity = requireActivity() as TestsActivity
                    activity.returnToTabsAndRefresh()
                } catch (e: Exception) {
                    Log.e(TAG, "Error returning to tabs: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to delete empty test: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error deleting test: ${e.message}")
            }
    }

    /**
     * Load test from Firebase for editing
     */
    private fun loadTestFromFirebase(testId: String) {
        try {
            // Show loading indicator
            progressBar.visibility = View.VISIBLE

            // Disable save button during loading
            saveTestButton.isEnabled = false

            // Load test from Firebase
            firestore.collection("tests").document(testId)
                .get()
                .addOnSuccessListener { document ->
                    progressBar.visibility = View.GONE
                    saveTestButton.isEnabled = true

                    if (document != null && document.exists()) {
                        try {
                            // Convert document to test object
                            val test = document.toObject(Test::class.java)

                            if (test != null) {
                                // Display test title
                                testTitleInput.setText(test.title)

                                // Clear and update questions list
                                questionsList.clear()
                                // Convert to mutable list to allow modification
                                questionsList.addAll(test.questions.toMutableList())

                                // Ensure we have the correct ID
                                editingTestId = test.id

                                // Update adapter to display questions
                                questionsAdapter.notifyDataSetChanged()

                                Log.d(TAG, "Test loaded successfully with ${test.questions.size} questions")
                            } else {
                                Toast.makeText(requireContext(), "Error: Could not parse test data", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting test data: ${e.message}")
                            Toast.makeText(requireContext(), "Error loading test: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Test not found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    saveTestButton.isEnabled = true

                    Log.e(TAG, "Error loading test: ${e.message}")
                    Toast.makeText(requireContext(), "Error loading test: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            Log.e(TAG, "General error loading test: ${e.message}")
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Open dialog to edit a question
     */
    private fun openEditQuestionDialog(question: Question, position: Int) {
        try {
            // Create new dialog
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_question, null)
            val alertDialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create()

            // Find components in dialog
            val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
            val questionTextInput = dialogView.findViewById<TextInputEditText>(R.id.questionTextInput)

            // Input fields for answer options
            val option1Input = dialogView.findViewById<TextInputEditText>(R.id.option1Input)
            val option2Input = dialogView.findViewById<TextInputEditText>(R.id.option2Input)
            val option3Input = dialogView.findViewById<TextInputEditText>(R.id.option3Input)
            val option4Input = dialogView.findViewById<TextInputEditText>(R.id.option4Input)

            // Option layouts (used as alternative to RadioButtons)
            val optionLayout1 = dialogView.findViewById<LinearLayout>(R.id.optionLayout1)
            val optionLayout2 = dialogView.findViewById<LinearLayout>(R.id.optionLayout2)
            val optionLayout3 = dialogView.findViewById<LinearLayout>(R.id.optionLayout3)
            val optionLayout4 = dialogView.findViewById<LinearLayout>(R.id.optionLayout4)

            // Checkmarks for each option
            val correctMark1 = dialogView.findViewById<TextView>(R.id.correctMark1)
            val correctMark2 = dialogView.findViewById<TextView>(R.id.correctMark2)
            val correctMark3 = dialogView.findViewById<TextView>(R.id.correctMark3)
            val correctMark4 = dialogView.findViewById<TextView>(R.id.correctMark4)

            // Convenience lists
            val optionLayouts = listOf(optionLayout1, optionLayout2, optionLayout3, optionLayout4)
            val correctMarks = listOf(correctMark1, correctMark2, correctMark3, correctMark4)

            // Hide all "correct" marks initially
            correctMarks.forEach { it.visibility = View.INVISIBLE }

            // Initialize selected option variable
            selectedOptionIndex = question.correctOptionIndex

            // Mark current option as selected (if any)
            if (selectedOptionIndex >= 0 && selectedOptionIndex < 4) {
                correctMarks[selectedOptionIndex].visibility = View.VISIBLE
                optionLayouts[selectedOptionIndex].setBackgroundColor(Color.parseColor("#E3F2FD")) // Light blue background
            }

            // Set click listeners for each option layout
            for (i in optionLayouts.indices) {
                optionLayouts[i].setOnClickListener {
                    if (selectedOptionIndex == i) {
                        // Clicking on already selected option - deselect it
                        selectedOptionIndex = -1
                        correctMarks[i].visibility = View.INVISIBLE
                        optionLayouts[i].setBackgroundColor(Color.parseColor("#F5F5F5")) // Default gray background
                    } else {
                        // Clicking on a new option

                        // Deselect previous option
                        if (selectedOptionIndex >= 0 && selectedOptionIndex < 4) {
                            correctMarks[selectedOptionIndex].visibility = View.INVISIBLE
                            optionLayouts[selectedOptionIndex].setBackgroundColor(Color.parseColor("#F5F5F5"))
                        }

                        // Select new option
                        selectedOptionIndex = i
                        correctMarks[i].visibility = View.VISIBLE
                        optionLayouts[i].setBackgroundColor(Color.parseColor("#E3F2FD"))
                    }

                    Log.d(TAG, "Selected option: $selectedOptionIndex")
                }
            }

            // Update dialog title based on action type (add or edit)
            dialogTitle.text = if (position == -1) "Add New Question" else "Edit Question"

            // Fill form with existing question data (for edit mode)
            questionTextInput.setText(question.text)

            // Fill answer options
            val options = question.options
            if (options.size >= 1) option1Input.setText(options[0])
            if (options.size >= 2) option2Input.setText(options[1])
            if (options.size >= 3) option3Input.setText(options[2])
            if (options.size >= 4) option4Input.setText(options[3])

            // Cancel and save buttons
            val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
            val saveQuestionButton = dialogView.findViewById<Button>(R.id.saveQuestionButton)

            // Cancel button listener
            cancelButton.setOnClickListener {
                alertDialog.dismiss()
            }

            // Save button listener
            saveQuestionButton.setOnClickListener {
                // Check that question text is entered
                val questionText = questionTextInput.text.toString().trim()
                if (questionText.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter question text", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Create list of answer options
                val updatedOptions = listOf(
                    option1Input.text.toString().trim(),
                    option2Input.text.toString().trim(),
                    option3Input.text.toString().trim(),
                    option4Input.text.toString().trim()
                )

                // Check that at least two answer options are entered
                if (updatedOptions[0].isEmpty() || updatedOptions[1].isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter at least two answer options", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Ensure a correct answer is selected
                if (selectedOptionIndex == -1) {
                    Toast.makeText(requireContext(), "Please select a correct answer", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Check that the selected option is not empty
                if (updatedOptions[selectedOptionIndex].isEmpty()) {
                    Toast.makeText(requireContext(), "The selected correct option is empty, please enter it or select another option", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Create or update the question
                val updatedQuestion = question.copy(
                    text = questionText,
                    options = updatedOptions,
                    correctOptionIndex = selectedOptionIndex
                )

                if (position == -1) {
                    // Add new question
                    questionsList.add(updatedQuestion)
                    Log.d(TAG, "New question added. Total questions: ${questionsList.size}")
                } else {
                    // Update existing question
                    questionsList[position] = updatedQuestion
                    Log.d(TAG, "Question updated at position $position")
                }

                // Update adapter
                questionsAdapter.notifyDataSetChanged()

                // Close dialog
                alertDialog.dismiss()

                // If in edit mode, update Firebase immediately with the updated question
                if (editMode && editingTestId.isNotEmpty()) {
                    updateTestInFirebase("Question updated successfully")
                }
            }

            // Show dialog
            alertDialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in openEditQuestionDialog: ${e.message}")
            Toast.makeText(requireContext(), "Error opening question dialog: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Update test in Firebase with current data
     * Used both for saveTestToFirebase and for immediate updates
     */
    private fun updateTestInFirebase(successMessage: String) {
        try {
            // Show loading indicator
            progressBar.visibility = View.VISIBLE

            // Disable save button during saving
            saveTestButton.isEnabled = false

            // Get current user ID (teacher)
            val userId = auth.currentUser?.uid ?: "unknown"

            // Use existing ID if in edit mode
            val testId = if (editMode) editingTestId else UUID.randomUUID().toString()

            // Create test object
            val test = Test(
                id = testId,
                title = testTitleInput.text.toString().trim(),
                questions = questionsList,
                createdBy = userId,
                createdAt = System.currentTimeMillis()
            )

            // Save test to Firebase
            firestore.collection("tests").document(testId)
                .set(test)
                .addOnSuccessListener {
                    // Hide loading indicator
                    progressBar.visibility = View.GONE
                    saveTestButton.isEnabled = true

                    Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show()

                    // If not in edit mode and saving a new test, clear the form
                    if (!editMode) {
                        testTitleInput.text?.clear()
                        questionsList.clear()
                        questionsAdapter.notifyDataSetChanged()
                    }
                }
                .addOnFailureListener { e ->
                    // Hide loading indicator
                    progressBar.visibility = View.GONE
                    saveTestButton.isEnabled = true

                    Toast.makeText(requireContext(), "Error updating test: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Error updating test in Firebase: ${e.message}")
                }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            saveTestButton.isEnabled = true
            Log.e(TAG, "Error in updateTestInFirebase: ${e.message}", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Validate test data before saving
     */
    private fun validateTestData(): Boolean {
        // Check test title
        val testTitle = testTitleInput.text.toString().trim()
        if (testTitle.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a test title", Toast.LENGTH_SHORT).show()
            testTitleInput.requestFocus()
            return false
        }

        // Check that there's at least one question
        if (questionsList.isEmpty()) {
            Toast.makeText(requireContext(), "Please add at least one question", Toast.LENGTH_SHORT).show()
            return false
        }

        // Check that all questions have a correct answer defined
        val invalidQuestions = questionsList.filter { it.correctOptionIndex == -1 }
        if (invalidQuestions.isNotEmpty()) {
            Toast.makeText(requireContext(), "Some questions don't have a correct answer selected", Toast.LENGTH_SHORT).show()
            return false
        }

        // Check that all questions have text
        val emptyQuestions = questionsList.filter { it.text.isBlank() }
        if (emptyQuestions.isNotEmpty()) {
            Toast.makeText(requireContext(), "Some questions don't have text", Toast.LENGTH_SHORT).show()
            return false
        }

        // Check that all questions have at least two answer options
        for (i in questionsList.indices) {
            val question = questionsList[i]
            val validOptions = question.options.count { it.isNotEmpty() }
            if (validOptions < 2) {
                Toast.makeText(requireContext(), "Question ${i+1} needs at least 2 answer options", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        return true
    }

    /**
     * Save test to Firebase - calls updateTestInFirebase after validation
     */
    private fun saveTestToFirebase() {
        // Validate data before saving
        if (!validateTestData()) {
            return
        }

        // Call the common update method
        val message = if (editMode) "Test updated successfully" else "Test saved successfully"
        updateTestInFirebase(message)

        // Return to the list view after saving and refresh
        try {
            val activity = requireActivity() as TestsActivity
            // שימוש בפונקציה החדשה שגם מרעננת את הנתונים
            activity.returnToTabsAndRefresh()
        } catch (e: Exception) {
            Log.e(TAG, "Error returning to tabs: ${e.message}")
        }
    }

    /**
     * Adapter for the questions recycler view - with real-time Firebase updates
     */
    inner class QuestionsAdapter : RecyclerView.Adapter<QuestionsAdapter.QuestionViewHolder>() {

        inner class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val questionNumber: TextView = itemView.findViewById(R.id.questionNumber)
            val questionText: TextView = itemView.findViewById(R.id.questionText)
            val questionOptions: TextView = itemView.findViewById(R.id.questionOptions)
            val deleteButton: ImageButton = itemView.findViewById(R.id.deleteQuestionButton)

            init {
                // Set click listener for editing a question
                itemView.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        Log.d(TAG, "Question item clicked at position $position")
                        openEditQuestionDialog(questionsList[position], position)
                    }
                }

                // Set click listener for deleting a question
                // This now uses the new deleteQuestionAndUpdateFirebase method
                deleteButton.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        try {
                            Log.d(TAG, "Delete button clicked at position $position")

                            // Show confirmation dialog
                            AlertDialog.Builder(requireContext())
                                .setTitle("Delete Question")
                                .setMessage("Are you sure you want to delete this question?")
                                .setPositiveButton("Delete") { _, _ ->
                                    // Use the new method that updates Firebase immediately
                                    deleteQuestionAndUpdateFirebase(position)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()

                        } catch (e: Exception) {
                            Log.e(TAG, "Error in delete button click: ${e.message}", e)
                            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.question_item, parent, false)
            return QuestionViewHolder(view)
        }

        override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
            val question = questionsList[position]

            // Display question number
            holder.questionNumber.text = "Question ${position + 1}"

            // Display question text
            holder.questionText.text = question.text

            // Display info about answer options
            val validOptions = question.options.count { it.isNotEmpty() }
            val correctOptionLetter = when(question.correctOptionIndex) {
                0 -> "A"
                1 -> "B"
                2 -> "C"
                3 -> "D"
                else -> "Not selected"
            }
            holder.questionOptions.text = "$validOptions options | Correct answer: $correctOptionLetter"
        }

        override fun getItemCount() = questionsList.size
    }
}