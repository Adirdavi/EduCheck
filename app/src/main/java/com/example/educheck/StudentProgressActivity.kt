package com.example.educheck

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.educheck.utilities.TestResult
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
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
import com.google.firebase.firestore.Query
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

    // Firebase Database
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var studentId: String

    companion object {
        private const val TAG = "StudentProgressActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_student_progress)

        // Initialize insets system
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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

        studentId = currentUser.uid

        // Initialize UI components
        initializeUI()

        // Load test data
        loadTestResults()
    }

    /**
     * Initialize UI components
     */
    private fun initializeUI() {
        try {
            // Initialize text views
            avgScoreText = findViewById(R.id.avgScoreText)
            totalTestsText = findViewById(R.id.totalTestsText)
            bestScoreText = findViewById(R.id.bestScoreText)
            recentScoreText = findViewById(R.id.recentScoreText)
            noDataText = findViewById(R.id.noDataText)
            progressBar = findViewById(R.id.progressBar)

            // Initialize and setup the line chart
            lineChart = findViewById(R.id.lineChart)
            setupLineChart()

            // Initialize and setup the pie chart
            pieChart = findViewById(R.id.pieChart)
            setupPieChart()

            // Hide charts until we have data
            lineChart.visibility = View.GONE
            pieChart.visibility = View.GONE
            noDataText.visibility = View.GONE

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing UI: ${e.message}")
            Toast.makeText(this, "Error initializing UI: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Setup the line chart for scores over time
     */
    private fun setupLineChart() {
        try {
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

            // Setup Y-axis (left)
            val leftAxis = lineChart.axisLeft
            leftAxis.axisMinimum = 0f
            leftAxis.axisMaximum = 100f
            leftAxis.setDrawGridLines(true)

            // Setup Y-axis (right) - not needed
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

            // Setup text for when there's no data - SET TEXT COLOR TO WHITE
            lineChart.setNoDataText("No test data available")
            lineChart.setNoDataTextColor(Color.WHITE)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up line chart: ${e.message}")
        }
    }

    /**
     * Setup the pie chart for score distribution
     */
    private fun setupPieChart() {
        try {
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

            // Setup text for when there's no data - SET TEXT COLOR TO WHITE
            pieChart.setNoDataText("No test data available")
            pieChart.setNoDataTextColor(Color.WHITE)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up pie chart: ${e.message}")
        }
    }

    /**
     * Load test results from Firestore
     * Updated version that sorts results on client side, doesn't require an index
     */
    private fun loadTestResults() {
        try {
            // Show loading indicator
            progressBar.visibility = View.VISIBLE
            noDataText.visibility = View.GONE

            // Simpler query without ordering - doesn't require an index
            firestore.collection("test_results")
                .whereEqualTo("studentId", studentId)
                .get()
                .addOnSuccessListener { documents ->
                    progressBar.visibility = View.GONE

                    if (documents.isEmpty) {
                        noDataText.visibility = View.VISIBLE
                        return@addOnSuccessListener
                    }

                    // Convert documents to TestResult objects
                    val results = documents.mapNotNull { it.toObject(TestResult::class.java) }
                        // Sort results by submission time in ascending order, on the client side
                        .sortedBy { it.submittedAt }

                    if (results.isNotEmpty()) {
                        // Update scores over time chart
                        updateLineChart(results)

                        // Update score distribution pie chart
                        updatePieChart(results)

                        // Update numeric statistics
                        updateStatistics(results)

                        // Show the charts
                        lineChart.visibility = View.VISIBLE
                        pieChart.visibility = View.VISIBLE
                    } else {
                        noDataText.visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    noDataText.visibility = View.VISIBLE
                    Log.e(TAG, "Error loading test results: ${e.message}")
                    Toast.makeText(this, "Error loading test data: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            noDataText.visibility = View.VISIBLE
            Log.e(TAG, "General error loading results: ${e.message}")
            Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Update the scores over time line chart
     */
    private fun updateLineChart(results: List<TestResult>) {
        try {
            val entries = ArrayList<Entry>()
            val testLabels = ArrayList<String>()
            val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

            // Create data points for the chart
            results.forEachIndexed { index, result ->
                entries.add(Entry(index.toFloat(), result.score.toFloat()))

                // Create date label for X-axis
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

            // Create list of datasets (in case we want to add more sets in the future)
            val dataSets = ArrayList<ILineDataSet>()
            dataSets.add(dataSet)

            // Create LineData object and set it to the chart
            val lineData = LineData(dataSets)
            lineChart.data = lineData

            // Set labels for X-axis
            lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(testLabels)

            // Update the chart
            lineChart.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating line chart: ${e.message}")
        }
    }

    /**
     * Update the pie chart with score distribution
     */
    private fun updatePieChart(results: List<TestResult>) {
        try {
            // Define score ranges
            val categories = mutableMapOf(
                "Excellent (90-100)" to 0,
                "Very Good (80-89)" to 0,
                "Good (70-79)" to 0,
                "Average (60-69)" to 0,
                "Fail (0-59)" to 0
            )

            // Count tests in each score range
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

            // Create list of values for the pie chart
            val entries = ArrayList<PieEntry>()
            val colors = ArrayList<Int>()

            // Add values only for categories with value > 0
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

            // Create data array
            val dataSet = PieDataSet(entries, "Score Ranges")
            dataSet.colors = colors
            dataSet.sliceSpace = 3f
            dataSet.selectionShift = 5f
            dataSet.valueTextSize = 12f
            dataSet.valueTextColor = Color.WHITE
            dataSet.valueFormatter = PercentFormatter(pieChart)

            // Create pie chart data
            val pieData = PieData(dataSet)
            pieChart.data = pieData

            // Update the chart
            pieChart.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating pie chart: ${e.message}")
        }
    }

    /**
     * Update numeric statistics
     */
    private fun updateStatistics(results: List<TestResult>) {
        try {
            // Average score
            val averageScore = results.map { it.score }.average()
            avgScoreText.text = String.format("%.1f", averageScore)

            // Number of tests taken
            totalTestsText.text = results.size.toString()

            // Highest score
            val bestScore = results.maxByOrNull { it.score }?.score ?: 0.0
            bestScoreText.text = String.format("%.1f", bestScore)

            // Latest score
            val latestScore = results.maxByOrNull { it.submittedAt }?.score ?: 0.0
            recentScoreText.text = String.format("%.1f", latestScore)

            // Add colors to the latest score based on score range
            when {
                latestScore >= 90 -> recentScoreText.setTextColor(ContextCompat.getColor(this, R.color.colorExcellent))
                latestScore >= 80 -> recentScoreText.setTextColor(ContextCompat.getColor(this, R.color.colorVeryGood))
                latestScore >= 70 -> recentScoreText.setTextColor(ContextCompat.getColor(this, R.color.colorGood))
                latestScore >= 60 -> recentScoreText.setTextColor(ContextCompat.getColor(this, R.color.colorAverage))
                else -> recentScoreText.setTextColor(ContextCompat.getColor(this, R.color.colorFail))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating statistics: ${e.message}")
        }
    }
}