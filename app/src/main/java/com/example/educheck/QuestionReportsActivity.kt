package com.example.educheck

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.educheck.utilities.QuestionReport
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity for teachers to view and manage question reports from students
 */
class QuestionReportsActivity : AppCompatActivity() {

    // UI components
    private lateinit var reportsRecyclerView: RecyclerView
    private lateinit var progressIndicator: ProgressBar
    private lateinit var noReportsMessage: TextView
    private lateinit var reportsAdapter: ReportsAdapter

    // Data
    private val reportsList = mutableListOf<QuestionReport>()
    private val allReportsList = mutableListOf<QuestionReport>()
    private var hideResolved = false

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var teacherId: String = ""

    companion object {
        private const val TAG = "QuestionReportsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_question_reports)

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

        // Load reports
        loadQuestionReports()
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI() {
        try {
            reportsRecyclerView = findViewById(R.id.reportsRecyclerView)
            progressIndicator = findViewById(R.id.progressIndicator)
            noReportsMessage = findViewById(R.id.noReportsMessage)

            // Hide the "No reports" message initially
            noReportsMessage.visibility = View.GONE

            // Set up the adapter for the recycler view
            reportsAdapter = ReportsAdapter()

            // Set up the recycler view
            reportsRecyclerView.layoutManager = LinearLayoutManager(this)
            reportsRecyclerView.adapter = reportsAdapter

            val backButton: ImageButton = findViewById(R.id.backButton)
            backButton.setOnClickListener {
                onBackPressed()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UI: ${e.message}")
            Toast.makeText(this, "Error initializing UI: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Load question reports from Firebase
     */
    private fun loadQuestionReports() {
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE

            // Query reports for this teacher - without using orderBy to avoid index issues
            firestore.collection("question_reports")
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener { documents ->
                    progressIndicator.visibility = View.GONE

                    if (documents.isEmpty) {
                        // No reports found
                        noReportsMessage.visibility = View.VISIBLE
                        return@addOnSuccessListener
                    }

                    // Process reports
                    allReportsList.clear()
                    reportsList.clear()

                    for (document in documents) {
                        try {
                            val report = document.toObject(QuestionReport::class.java)
                            allReportsList.add(report)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting document to QuestionReport: ${e.message}")
                        }
                    }

                    // Sort reports by reported date (newest first) - client-side sorting
                    allReportsList.sortByDescending { it.reportedAt }

                    // Add all reports to displayed list
                    reportsList.addAll(allReportsList)

                    // Update adapter
                    reportsAdapter.notifyDataSetChanged()
                }
                .addOnFailureListener { e ->
                    progressIndicator.visibility = View.GONE
                    noReportsMessage.visibility = View.VISIBLE
                    Log.e(TAG, "Error loading reports: ${e.message}")
                    Toast.makeText(this, "Error loading reports: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progressIndicator.visibility = View.GONE
            Log.e(TAG, "General error loading reports: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Update report status in Firebase
     */
    private fun updateReportStatus(report: QuestionReport, response: String, resolved: Boolean) {
        try {
            // Show loading indicator
            progressIndicator.visibility = View.VISIBLE

            // Update the report
            firestore.collection("question_reports")
                .document(report.id)
                .update(
                    mapOf(
                        "teacherResponse" to response,
                        "resolved" to resolved
                    )
                )
                .addOnSuccessListener {
                    progressIndicator.visibility = View.GONE
                    Toast.makeText(this, "Report updated successfully", Toast.LENGTH_SHORT).show()

                    // Update local data
                    val index = allReportsList.indexOfFirst { it.id == report.id }
                    if (index >= 0) {
                        val updatedReport = report.copy(
                            teacherResponse = response,
                            resolved = resolved
                        )
                        allReportsList[index] = updatedReport

                        // Update the display list
                        val displayIndex = reportsList.indexOfFirst { it.id == report.id }
                        if (displayIndex >= 0) {
                            reportsList[displayIndex] = updatedReport
                            reportsAdapter.notifyItemChanged(displayIndex)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    progressIndicator.visibility = View.GONE
                    Log.e(TAG, "Error updating report: ${e.message}")
                    Toast.makeText(this, "Error updating report: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progressIndicator.visibility = View.GONE
            Log.e(TAG, "General error updating report: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Adapter for the recycler view
     */
    inner class ReportsAdapter : RecyclerView.Adapter<ReportsAdapter.ReportViewHolder>() {

        inner class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val reportCard: MaterialCardView = itemView.findViewById(R.id.reportCard)
            val testTitle: TextView = itemView.findViewById(R.id.testTitle)
            val questionPreview: TextView = itemView.findViewById(R.id.questionPreview)
            val reportDate: TextView = itemView.findViewById(R.id.reportDate)
            val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
            val markResolvedButton: MaterialButton = itemView.findViewById(R.id.markResolvedButton)

            init {
                // אין מאזין לחיצה על הכרטיס - רק על הכפתור

                markResolvedButton.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val report = reportsList[position]
                        // רק אם עדיין לא פתור
                        if (!report.resolved) {
                            updateReportStatus(report, report.teacherResponse, true)
                        }
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.question_report_item, parent, false)
            return ReportViewHolder(view)
        }

        override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
            val report = reportsList[position]

            // Set test title
            holder.testTitle.text = report.testTitle

            // Set question preview (truncate if too long)
            val maxQuestionLength = 60
            val questionText = if (report.questionText.length > maxQuestionLength) {
                "${report.questionText.substring(0, maxQuestionLength)}..."
            } else {
                report.questionText
            }
            holder.questionPreview.text = questionText

            // Set student name

            // Set report date
            try {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = Date(report.reportedAt)
                holder.reportDate.text = dateFormat.format(date)
            } catch (e: Exception) {
                holder.reportDate.text = ""
            }

            // Set status indicator color and button text
            if (report.resolved) {
                holder.statusIndicator.setBackgroundColor(ContextCompat.getColor(this@QuestionReportsActivity, R.color.colorGreen))
                holder.markResolvedButton.text = "Resolved"
                holder.markResolvedButton.isEnabled = false
                holder.markResolvedButton.alpha = 0.5f
            } else {
                holder.statusIndicator.setBackgroundColor(ContextCompat.getColor(this@QuestionReportsActivity, R.color.colorRed))
                holder.markResolvedButton.text = "Resolve"
                holder.markResolvedButton.isEnabled = true
                holder.markResolvedButton.alpha = 1.0f
            }
        }

        override fun getItemCount() = reportsList.size
    }
}