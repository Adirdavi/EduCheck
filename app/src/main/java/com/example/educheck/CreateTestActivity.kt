package com.example.educheck

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.educheck.utilities.Question
import com.example.educheck.utilities.Test
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

/**
 * פעילות ליצירת מבחן חדש במערכת או עריכת מבחן קיים.
 * מאפשרת למורה להגדיר שם למבחן, להוסיף שאלות אמריקאיות ולשמור את המבחן בפיירבייס.
 */
class CreateTestActivity : AppCompatActivity() {

    // רשימת השאלות במבחן
    private val questionsList = mutableListOf<Question>()

    // מתאם למחזר התצוגה של השאלות
    private lateinit var questionsAdapter: QuestionsAdapter

    // התייחסויות לרכיבי ממשק המשתמש
    private lateinit var testTitleInput: TextInputEditText
    private lateinit var addQuestionButton: MaterialButton
    private lateinit var saveTestButton: MaterialButton
    private lateinit var questionsRecyclerView: RecyclerView

    // אובייקטים של Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // משתנה לשמירת האופציה הנבחרת כרגע
    private var selectedOptionIndex: Int = -1

    // משתנים למצב עריכה
    private var editMode = false
    private var editingTestId = ""

    companion object {
        private const val TAG = "CreateTestActivity"
    }

    /**
     * מתודה שנקראת בעת יצירת הפעילות.
     * מגדירה את ממשק המשתמש ומתחילה את האתחול של רכיבי המסך.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // הפעלת תמיכה בקצוות המסך
        enableEdgeToEdge()

        // טעינת קובץ הלייאאוט של המסך
        setContentView(R.layout.activity_create_test)

        // אתחול Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // בדוק אם אנחנו במצב עריכה
        editMode = intent.getBooleanExtra("EDIT_MODE", false)
        if (editMode) {
            // קבל את מזהה המבחן לעריכה
            editingTestId = intent.getStringExtra("TEST_ID") ?: ""

            // אם לא סופק מזהה, סגור את המסך
            if (editingTestId.isEmpty()) {
                Toast.makeText(this, "Error: No test ID provided", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        // הגדרת מאזין לעדכוני insets של המערכת
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // אתחול רכיבי ממשק המשתמש
        initializeUI()

        // הגדרת מאזיני אירועים
        setupListeners()

        // טען את המבחן אם במצב עריכה
        if (editMode) {
            loadTestFromFirebase(editingTestId)
        }
    }

    /**
     * אתחול רכיבי ממשק המשתמש
     */
    private fun initializeUI() {
        testTitleInput = findViewById(R.id.testTitleInput)
        addQuestionButton = findViewById(R.id.addQuestionButton)
        saveTestButton = findViewById(R.id.saveTestButton)
        questionsRecyclerView = findViewById(R.id.questionsRecyclerView)

        // עדכן את טקסט הכפתור בהתאם למצב
        saveTestButton.text = if (editMode) "Update Test" else "Save Test"

        // עדכן את כותרת המסך
        setTitle(if (editMode) "Edit Test" else "Create New Test")

        // הגדרת מתאם למחזר התצוגה
        questionsAdapter = QuestionsAdapter(questionsList) { question, position ->
            // כאשר לוחצים על שאלה, פותחים דיאלוג עריכה
            openEditQuestionDialog(question, position)
        }

        // הגדרת מחזר התצוגה
        questionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CreateTestActivity)
            adapter = questionsAdapter
        }
    }

    /**
     * הגדרת מאזיני אירועים לכפתורים ורכיבים אחרים
     */
    private fun setupListeners() {
        // מאזין ללחיצה על כפתור הוספת שאלה
        addQuestionButton.setOnClickListener {
            // יצירת שאלה חדשה ריקה ופתיחת דיאלוג עריכה
            val newQuestion = Question(
                id = UUID.randomUUID().toString(),
                text = "",
                options = listOf("", "", "", ""),
                correctOptionIndex = -1  // ברירת מחדל: אין תשובה נכונה
            )
            openEditQuestionDialog(newQuestion, -1)
        }

        // מאזין ללחיצה על כפתור שמירת המבחן
        saveTestButton.setOnClickListener {
            saveTestToFirebase()
        }
    }

    /**
     * טעינת מבחן מהפיירבייס לעריכה
     */
    private fun loadTestFromFirebase(testId: String) {
        try {
            // הצג מחוון טעינה
            val loadingIndicator = findViewById<ProgressBar>(R.id.progressBar)
            loadingIndicator?.visibility = View.VISIBLE

            // השבת את כפתור השמירה בזמן הטעינה
            saveTestButton.isEnabled = false

            // טען את המבחן מהפיירבייס
            firestore.collection("tests").document(testId)
                .get()
                .addOnSuccessListener { document ->
                    loadingIndicator?.visibility = View.GONE
                    saveTestButton.isEnabled = true

                    if (document != null && document.exists()) {
                        try {
                            // המר את המסמך לאובייקט מבחן
                            val test = document.toObject(Test::class.java)

                            if (test != null) {
                                // הצג את כותרת המבחן
                                testTitleInput.setText(test.title)

                                // נקה ועדכן את רשימת השאלות
                                questionsList.clear()
                                questionsList.addAll(test.questions)

                                // וודא שיש לנו את ה-ID הנכון
                                editingTestId = test.id

                                // עדכן את המתאם כדי להציג את השאלות
                                if (::questionsAdapter.isInitialized) {
                                    questionsAdapter.notifyDataSetChanged()
                                }

                                Log.d(TAG, "Test loaded successfully with ${test.questions.size} questions")
                            } else {
                                Toast.makeText(this, "Error: Could not parse test data", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting test data: ${e.message}")
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
                    saveTestButton.isEnabled = true

                    Log.e(TAG, "Error loading test: ${e.message}")
                    Toast.makeText(this, "Error loading test: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
        } catch (e: Exception) {
            Log.e(TAG, "General error loading test: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * פתיחת דיאלוג לעריכת שאלה
     * @param question השאלה לעריכה
     * @param position מיקום השאלה ברשימה, או -1 אם זו שאלה חדשה
     */
    private fun openEditQuestionDialog(question: Question, position: Int) {
        // יצירת דיאלוג חדש
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_question, null)
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // מציאת הרכיבים בדיאלוג
        val dialogTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val questionTextInput = dialogView.findViewById<TextInputEditText>(R.id.questionTextInput)

        // תיבות קלט לאפשרויות התשובה
        val option1Input = dialogView.findViewById<TextInputEditText>(R.id.option1Input)
        val option2Input = dialogView.findViewById<TextInputEditText>(R.id.option2Input)
        val option3Input = dialogView.findViewById<TextInputEditText>(R.id.option3Input)
        val option4Input = dialogView.findViewById<TextInputEditText>(R.id.option4Input)

        // תיבות שמייצגות את האפשרויות (משמשות כחלופה ל-RadioButtons)
        val optionLayout1 = dialogView.findViewById<LinearLayout>(R.id.optionLayout1)
        val optionLayout2 = dialogView.findViewById<LinearLayout>(R.id.optionLayout2)
        val optionLayout3 = dialogView.findViewById<LinearLayout>(R.id.optionLayout3)
        val optionLayout4 = dialogView.findViewById<LinearLayout>(R.id.optionLayout4)

        // תיבות סימון עבור כל אפשרות
        val correctMark1 = dialogView.findViewById<TextView>(R.id.correctMark1)
        val correctMark2 = dialogView.findViewById<TextView>(R.id.correctMark2)
        val correctMark3 = dialogView.findViewById<TextView>(R.id.correctMark3)
        val correctMark4 = dialogView.findViewById<TextView>(R.id.correctMark4)

        // רשימות לנוחות
        val optionLayouts = listOf(optionLayout1, optionLayout2, optionLayout3, optionLayout4)
        val correctMarks = listOf(correctMark1, correctMark2, correctMark3, correctMark4)

        // הסתרת כל סימוני ה-"נכון" בהתחלה
        correctMarks.forEach { it.visibility = View.INVISIBLE }

        // אתחול המשתנה ששומר את האופציה הנבחרת
        selectedOptionIndex = question.correctOptionIndex

        // סימון האופציה הנוכחית כנבחרת (אם יש)
        if (selectedOptionIndex >= 0 && selectedOptionIndex < 4) {
            correctMarks[selectedOptionIndex].visibility = View.VISIBLE
            optionLayouts[selectedOptionIndex].setBackgroundColor(Color.parseColor("#E3F2FD")) // רקע כחול בהיר
        }

        // הגדרת מאזיני לחיצה לכל תיבת אפשרות
        for (i in optionLayouts.indices) {
            optionLayouts[i].setOnClickListener {
                if (selectedOptionIndex == i) {
                    // לחיצה על אפשרות שכבר נבחרה - ביטול הבחירה
                    selectedOptionIndex = -1
                    correctMarks[i].visibility = View.INVISIBLE
                    optionLayouts[i].setBackgroundColor(Color.parseColor("#F5F5F5")) // רקע אפור (רגיל)
                } else {
                    // לחיצה על אפשרות חדשה

                    // ביטול הבחירה הקודמת
                    if (selectedOptionIndex >= 0 && selectedOptionIndex < 4) {
                        correctMarks[selectedOptionIndex].visibility = View.INVISIBLE
                        optionLayouts[selectedOptionIndex].setBackgroundColor(Color.parseColor("#F5F5F5"))
                    }

                    // בחירת האפשרות החדשה
                    selectedOptionIndex = i
                    correctMarks[i].visibility = View.VISIBLE
                    optionLayouts[i].setBackgroundColor(Color.parseColor("#E3F2FD"))
                }

                Log.d(TAG, "נבחרה אפשרות: $selectedOptionIndex")
            }
        }

        // עדכון כותרת הדיאלוג בהתאם לסוג הפעולה (הוספה או עריכה)
        dialogTitle.text = if (position == -1) "הוספת שאלה חדשה" else "עריכת שאלה"

        // מילוי הטופס בנתוני השאלה הקיימת (אם מדובר בעריכה)
        questionTextInput.setText(question.text)

        // מילוי אפשרויות התשובה
        val options = question.options
        if (options.size >= 1) option1Input.setText(options[0])
        if (options.size >= 2) option2Input.setText(options[1])
        if (options.size >= 3) option3Input.setText(options[2])
        if (options.size >= 4) option4Input.setText(options[3])

        // כפתורי ביטול ושמירה
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val saveQuestionButton = dialogView.findViewById<Button>(R.id.saveQuestionButton)

        // מאזין ללחיצה על כפתור הביטול
        cancelButton.setOnClickListener {
            alertDialog.dismiss()
        }

        // מאזין ללחיצה על כפתור השמירה
        saveQuestionButton.setOnClickListener {
            // בדיקה שהוזן טקסט לשאלה
            val questionText = questionTextInput.text.toString().trim()
            if (questionText.isEmpty()) {
                Toast.makeText(this, "נא להזין טקסט לשאלה", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // יצירת רשימת אפשרויות התשובה
            val updatedOptions = listOf(
                option1Input.text.toString().trim(),
                option2Input.text.toString().trim(),
                option3Input.text.toString().trim(),
                option4Input.text.toString().trim()
            )

            // בדיקה שהוזנו לפחות שתי אפשרויות תשובה
            if (updatedOptions[0].isEmpty() || updatedOptions[1].isEmpty()) {
                Toast.makeText(this, "נא להזין לפחות שתי אפשרויות תשובה", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // וידוא שנבחרה תשובה נכונה
            if (selectedOptionIndex == -1) {
                Toast.makeText(this, "נא לבחור תשובה נכונה", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // בדיקה שהאופציה הנבחרת לא ריקה
            if (updatedOptions[selectedOptionIndex].isEmpty()) {
                Toast.makeText(this, "האופציה הנבחרת ריקה, נא להזין אותה או לבחור אופציה אחרת", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // וידוא שנבחרה תשובה נכונה ושהיא תקינה
            Log.d(TAG, "נבחרה תשובה נכונה: אופציה ${selectedOptionIndex + 1}")

            // עדכון או הוספת השאלה
            val updatedQuestion = question.copy(
                text = questionText,
                options = updatedOptions,
                correctOptionIndex = selectedOptionIndex
            )

            if (position == -1) {
                // הוספת שאלה חדשה
                questionsList.add(updatedQuestion)
            } else {
                // עדכון שאלה קיימת
                questionsList[position] = updatedQuestion
            }

            // עדכון המתאם
            questionsAdapter.notifyDataSetChanged()

            // סגירת הדיאלוג
            alertDialog.dismiss()
        }

        // הצגת הדיאלוג
        alertDialog.show()
    }

    /**
     * בדיקת תקינות של נתוני המבחן לפני שמירה
     */
    private fun validateTestData(): Boolean {
        // בדיקת כותרת המבחן
        val testTitle = testTitleInput.text.toString().trim()
        if (testTitle.isEmpty()) {
            Toast.makeText(this, "Please enter a test title", Toast.LENGTH_SHORT).show()
            testTitleInput.requestFocus()
            return false
        }

        // בדיקה שיש לפחות שאלה אחת
        if (questionsList.isEmpty()) {
            Toast.makeText(this, "Please add at least one question", Toast.LENGTH_SHORT).show()
            return false
        }

        // בדיקה שלכל השאלות יש תשובה נכונה מוגדרת
        val invalidQuestions = questionsList.filter { it.correctOptionIndex == -1 }
        if (invalidQuestions.isNotEmpty()) {
            Toast.makeText(this, "Some questions don't have a correct answer selected", Toast.LENGTH_SHORT).show()
            return false
        }

        // בדיקה שלכל השאלות יש טקסט
        val emptyQuestions = questionsList.filter { it.text.isBlank() }
        if (emptyQuestions.isNotEmpty()) {
            Toast.makeText(this, "Some questions don't have text", Toast.LENGTH_SHORT).show()
            return false
        }

        // בדיקה שלכל שאלה יש לפחות שתי אפשרויות תשובה
        for (i in questionsList.indices) {
            val question = questionsList[i]
            val validOptions = question.options.count { it.isNotEmpty() }
            if (validOptions < 2) {
                Toast.makeText(this, "Question ${i+1} needs at least 2 answer options", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        return true
    }

    /**
     * שמירת המבחן בפיירבייס
     */
    private fun saveTestToFirebase() {
        // בדיקת תקינות לפני שמירה
        if (!validateTestData()) {
            return
        }

        // הצגת מחוון טעינה (אם יש)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar?.visibility = View.VISIBLE

        // השבתת כפתור השמירה בזמן השמירה
        saveTestButton.isEnabled = false

        // קבלת מזהה המשתמש המחובר (המורה)
        val userId = auth.currentUser?.uid ?: "unknown"

        // השתמש במזהה הקיים אם במצב עריכה
        val testId = if (editMode) editingTestId else UUID.randomUUID().toString()

        // יצירת אובייקט מבחן
        val test = Test(
            id = testId,
            title = testTitleInput.text.toString().trim(),
            questions = questionsList,
            createdBy = userId, // מזהה המורה המחוברת
            createdAt = System.currentTimeMillis()
        )

        // שמירת המבחן בפיירבייס
        firestore.collection("tests").document(testId)
            .set(test)
            .addOnSuccessListener {
                // הסתרת מחוון הטעינה
                progressBar?.visibility = View.GONE
                saveTestButton.isEnabled = true

                // עדכן את הודעת ההצלחה
                val message = if (editMode) "המבחן עודכן בהצלחה" else "המבחן נשמר בהצלחה"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

                finish() // סגירת המסך וחזרה למסך הקודם
            }
            .addOnFailureListener { e ->
                // הסתרת מחוון הטעינה
                progressBar?.visibility = View.GONE
                saveTestButton.isEnabled = true

                val action = if (editMode) "עדכון" else "שמירת"
                Toast.makeText(this, "שגיאה ב$action המבחן: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    /**
     * מתאם למחזר התצוגה של השאלות
     */
    inner class QuestionsAdapter(
        private val questions: List<Question>,
        private val onItemClick: (Question, Int) -> Unit
    ) : RecyclerView.Adapter<QuestionsAdapter.QuestionViewHolder>() {

        inner class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val questionNumber: TextView = itemView.findViewById(R.id.questionNumber)
            val questionText: TextView = itemView.findViewById(R.id.questionText)
            val questionOptions: TextView = itemView.findViewById(R.id.questionOptions)

            init {
                itemView.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        onItemClick(questions[position], position)
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
            val question = questions[position]

            // הצגת מספר השאלה
            holder.questionNumber.text = "שאלה ${position + 1}"

            // הצגת טקסט השאלה
            holder.questionText.text = question.text

            // הצגת מידע על אפשרויות התשובה
            val validOptions = question.options.count { it.isNotEmpty() }
            val correctOptionLetter = when(question.correctOptionIndex) {
                0 -> "א'"
                1 -> "ב'"
                2 -> "ג'"
                3 -> "ד'"
                else -> "לא נבחרה"
            }
            holder.questionOptions.text = "${validOptions} אפשרויות | תשובה נכונה: $correctOptionLetter"
        }

        override fun getItemCount() = questions.size
    }
}