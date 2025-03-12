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
import com.example.educheck.utilities.TestResult
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity that displays statistical information about the student's progress in tests
 */
class StudentProgressActivity : AppCompatActivity() {

    // UI Components
    private lateinit var lineChart: LineChart
    private lateinit var pieChart: PieChart
    private lateinit var progressBar: ProgressBar
    private lateinit var avgScoreText: TextView
    private lateinit var totalTestsText: TextView
    private lateinit var bestScoreText: TextView
    private lateinit var recentScoreText: TextView
    private lateinit var noDataText: TextView
    private lateinit var progressTitle: TextView

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // Student and Teacher Information
    private var targetStudentId: String = ""
    private var targetStudentName: String = ""
    private var isTeacherView: Boolean = false

    companion object {
        private const val TAG = "StudentProgressActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_student_progress)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Check if user is logged in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You are not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Get data from intent
        targetStudentId = intent.getStringExtra("STUDENT_ID") ?: ""
        targetStudentName = intent.getStringExtra("STUDENT_NAME") ?: ""

        // Determine if this is teacher view
        if (targetStudentId.isEmpty()) {
            // Student viewing their own data
            targetStudentId = currentUser.uid
            isTeacherView = false
        } else {
            // Teacher viewing student's data
            isTeacherView = currentUser.uid != targetStudentId
        }
        // בקובץ StudentProgressActivity.kt, בפונקציית onCreate או initializeUI, הוסף את הקוד הבא:


        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            // פעולת חזרה
            onBackPressed()
        }
        initializeUI()
        loadTestResults()
    }

    private fun initializeUI() {
        // Initialize UI components
        avgScoreText = findViewById(R.id.avgScoreText)
        totalTestsText = findViewById(R.id.totalTestsText)
        bestScoreText = findViewById(R.id.bestScoreText)
        recentScoreText = findViewById(R.id.recentScoreText)
        noDataText = findViewById(R.id.noDataText)
        progressBar = findViewById(R.id.progressBar)
        progressTitle = findViewById(R.id.progressTitle)

        // Update title based on view type
        if (isTeacherView && targetStudentName.isNotEmpty()) {
            progressTitle.text = "Progress: $targetStudentName"
        } else {
            progressTitle.text = "My Progress"
        }

        // Setup charts
        lineChart = findViewById(R.id.lineChart)
        setupLineChart()

        pieChart = findViewById(R.id.pieChart)
        setupPieChart()

        // Hide charts until we have data
        lineChart.visibility = View.GONE
        pieChart.visibility = View.GONE
        noDataText.visibility = View.GONE
    }

    private fun setupLineChart() {
        lineChart.description.isEnabled = false
        lineChart.setTouchEnabled(true)
        lineChart.isDragEnabled = true
        lineChart.setScaleEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.setDrawGridBackground(false)

        // Setup X-axis
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = 45f
        xAxis.setDrawGridLines(false)

        // Setup Y-axis
        val leftAxis = lineChart.axisLeft
        leftAxis.axisMinimum = 0f
        leftAxis.axisMaximum = 100f
        leftAxis.setDrawGridLines(true)
        lineChart.axisRight.isEnabled = false

        // Setup legend
        val legend = lineChart.legend
        legend.form = Legend.LegendForm.LINE
        legend.textSize = 12f
        legend.textColor = Color.BLACK
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)

        // No data text
        lineChart.setNoDataText("No test data available")
        lineChart.setNoDataTextColor(Color.WHITE)
    }

    private fun setupPieChart() {
        pieChart.description.isEnabled = false
        pieChart.setUsePercentValues(true)
        pieChart.setExtraOffsets(5f, 10f, 5f, 5f)
        pieChart.dragDecelerationFrictionCoef = 0.95f
        pieChart.centerText = "Score\nDistribution"
        pieChart.setCenterTextSize(16f)
        pieChart.setDrawHoleEnabled(true)
        pieChart.setHoleColor(Color.WHITE)
        pieChart.setTransparentCircleColor(Color.WHITE)
        pieChart.setTransparentCircleAlpha(110)
        pieChart.holeRadius = 40f
        pieChart.transparentCircleRadius = 45f
        pieChart.setDrawCenterText(true)
        pieChart.rotationAngle = 0f
        pieChart.isRotationEnabled = true
        pieChart.isHighlightPerTapEnabled = true

        // Setup legend
        val legend = pieChart.legend
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.xEntrySpace = 7f
        legend.yEntrySpace = 0f
        legend.yOffset = 10f
        legend.textSize = 12f
        legend.textColor = Color.BLACK

        // No data text
        pieChart.setNoDataText("No test data available")
        pieChart.setNoDataTextColor(Color.WHITE)
    }

    private fun loadTestResults() {
        // Show loading
        progressBar.visibility = View.VISIBLE
        noDataText.visibility = View.GONE

        // Simple query - get all tests for this student
        firestore.collection("test_results")
            .whereEqualTo("studentId", targetStudentId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                progressBar.visibility = View.GONE

                if (querySnapshot.isEmpty) {
                    // No data found
                    noDataText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // Convert to TestResult objects
                val results = querySnapshot.mapNotNull {
                    try {
                        it.toObject(TestResult::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.submittedAt }

                if (results.isNotEmpty()) {
                    updateLineChart(results)
                    updatePieChart(results)
                    updateStatistics(results)

                    lineChart.visibility = View.VISIBLE
                    pieChart.visibility = View.VISIBLE
                } else {
                    noDataText.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                noDataText.visibility = View.VISIBLE
                Toast.makeText(this, "Error loading data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateLineChart(results: List<TestResult>) {
        val entries = ArrayList<Entry>()
        val testLabels = ArrayList<String>()
        val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

        // Create data points
        results.forEachIndexed { index, result ->
            entries.add(Entry(index.toFloat(), result.score.toFloat()))

            // Date label
            val date = Date(result.submittedAt)
            val label = dateFormat.format(date)
            testLabels.add(label)
        }

        // Create dataset
        val dataSet = LineDataSet(entries, "Score")
        dataSet.color = ContextCompat.getColor(this, R.color.colorPrimary)
        dataSet.setCircleColor(ContextCompat.getColor(this, R.color.colorAccent))
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(true)
        dataSet.valueTextSize = 10f
        dataSet.valueTextColor = Color.BLACK
        dataSet.setDrawFilled(true)
        dataSet.fillColor = ContextCompat.getColor(this, R.color.colorPrimaryLight)
        dataSet.fillAlpha = 50
        dataSet.setDrawValues(true)
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER

        // Create dataset list
        val dataSets = ArrayList<ILineDataSet>()
        dataSets.add(dataSet)

        // Set data to chart
        val lineData = LineData(dataSets)
        lineChart.data = lineData

        // Set X-axis labels
        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(testLabels)

        // Update chart
        lineChart.invalidate()
    }

    private fun updatePieChart(results: List<TestResult>) {
        // Score categories
        val categories = mutableMapOf(
            "Excellent (90-100)" to 0,
            "Very Good (80-89)" to 0,
            "Good (70-79)" to 0,
            "Average (60-69)" to 0,
            "Fail (0-59)" to 0
        )

        // Count tests in each category
        for (result in results) {
            val score = result.score
            when {
                score >= 90 -> categories["Excellent (90-100)"] = categories["Excellent (90-100)"]!! + 1
                score >= 80 -> categories["Very Good (80-89)"] = categories["Very Good (80-89)"]!! + 1
                score >= 70 -> categories["Good (70-79)"] = categories["Good (70-79)"]!! + 1
                score >= 60 -> categories["Average (60-69)"] = categories["Average (60-69)"]!! + 1
                else -> categories["Fail (0-59)"] = categories["Fail (0-59)"]!! + 1
            }
        }

        // Create pie entries
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()

        // Add categories with values > 0
        if (categories["Excellent (90-100)"]!! > 0) {
            entries.add(PieEntry(categories["Excellent (90-100)"]!!.toFloat(), "Excellent"))
            colors.add(ContextCompat.getColor(this, R.color.colorExcellent))
        }
        if (categories["Very Good (80-89)"]!! > 0) {
            entries.add(PieEntry(categories["Very Good (80-89)"]!!.toFloat(), "Very Good"))
            colors.add(ContextCompat.getColor(this, R.color.colorVeryGood))
        }
        if (categories["Good (70-79)"]!! > 0) {
            entries.add(PieEntry(categories["Good (70-79)"]!!.toFloat(), "Good"))
            colors.add(ContextCompat.getColor(this, R.color.colorGood))
        }
        if (categories["Average (60-69)"]!! > 0) {
            entries.add(PieEntry(categories["Average (60-69)"]!!.toFloat(), "Average"))
            colors.add(ContextCompat.getColor(this, R.color.colorAverage))
        }
        if (categories["Fail (0-59)"]!! > 0) {
            entries.add(PieEntry(categories["Fail (0-59)"]!!.toFloat(), "Fail"))
            colors.add(ContextCompat.getColor(this, R.color.colorFail))
        }

        // Create dataset
        val dataSet = PieDataSet(entries, "Score Ranges")
        dataSet.colors = colors
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueFormatter = PercentFormatter(pieChart)

        // Set data to chart
        val pieData = PieData(dataSet)
        pieChart.data = pieData

        // Update chart
        pieChart.invalidate()
    }

    private fun updateStatistics(results: List<TestResult>) {
        // Average score
        val averageScore = results.map { it.score }.average()
        avgScoreText.text = String.format("%.1f", averageScore)

        // Total tests
        totalTestsText.text = results.size.toString()

        // Best score
        val bestScore = results.maxByOrNull { it.score }?.score ?: 0.0
        bestScoreText.text = String.format("%.1f", bestScore)

        // Latest score
        val latestScore = results.maxByOrNull { it.submittedAt }?.score ?: 0.0
        recentScoreText.text = String.format("%.1f", latestScore)

        // Color for latest score
        when {
            latestScore >= 90 -> recentScoreText.setTextColor(ContextCompat.getColor(this, R.color.colorExcellent))
            latestScore >= 80 -> recentScoreText.setTextColor(ContextCompat.getColor(this, R.color.colorVeryGood))
            latestScore >= 70 -> recentScoreText.setTextColor(ContextCompat.getColor(this, R.color.colorGood))
            latestScore >= 60 -> recentScoreText.setTextColor(ContextCompat.getColor(this, R.color.colorAverage))
            else -> recentScoreText.setTextColor(ContextCompat.getColor(this, R.color.colorFail))
        }
    }
}