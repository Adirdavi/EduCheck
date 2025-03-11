package com.example.educheck

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.educheck.utilities.Question
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlin.math.roundToInt

/**
 * Fragment for displaying statistics for a single question
 */
class QuestionStatisticsFragment : Fragment() {

    // UI components
    private lateinit var questionNumberTextView: TextView
    private lateinit var questionTextView: TextView
    private lateinit var correctAnswerRateTextView: TextView
    private lateinit var optionDistributionChart: BarChart

    // Data
    private lateinit var question: Question
    private var optionCounts = IntArray(4) // Assumes max 4 options
    private var totalAnswers: Int = 0

    companion object {
        private const val TAG = "QuestionStatsFragment"
        private const val ARG_QUESTION = "question"
        private const val ARG_OPTION_COUNTS = "option_counts"
        private const val ARG_TOTAL_ANSWERS = "total_answers"
        private const val ARG_POSITION = "position"

        /**
         * Factory method to create a new instance of this fragment
         */
        fun newInstance(question: Question, optionCounts: IntArray, totalAnswers: Int, position: Int): QuestionStatisticsFragment {
            val fragment = QuestionStatisticsFragment()
            val args = Bundle()
            args.putParcelable(ARG_QUESTION, question)
            args.putIntArray(ARG_OPTION_COUNTS, optionCounts)
            args.putInt(ARG_TOTAL_ANSWERS, totalAnswers)
            args.putInt(ARG_POSITION, position)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            question = it.getParcelable(ARG_QUESTION) ?: Question()
            optionCounts = it.getIntArray(ARG_OPTION_COUNTS) ?: IntArray(4)
            totalAnswers = it.getInt(ARG_TOTAL_ANSWERS, 0)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_question_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI components
        questionNumberTextView = view.findViewById(R.id.questionNumberTextView)
        questionTextView = view.findViewById(R.id.questionTextView)
        correctAnswerRateTextView = view.findViewById(R.id.correctAnswerRateTextView)
        optionDistributionChart = view.findViewById(R.id.optionDistributionChart)

        // Setup the chart
        setupOptionChart()

        // Update UI with question data
        updateUI()
    }

    /**
     * Setup the option distribution chart
     */
    private fun setupOptionChart() {
        try {
            // Configure chart appearance
            optionDistributionChart.description.isEnabled = false
            optionDistributionChart.setDrawGridBackground(false)
            optionDistributionChart.setDrawBarShadow(false)
            optionDistributionChart.setDrawValueAboveBar(true)
            optionDistributionChart.setPinchZoom(false)
            optionDistributionChart.isDoubleTapToZoomEnabled = false

            // Configure X axis
            val xAxis = optionDistributionChart.xAxis
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f

            // Configure left Y axis
            val leftAxis = optionDistributionChart.axisLeft
            leftAxis.setDrawGridLines(true)
            leftAxis.axisMinimum = 0f

            // Disable right Y axis
            optionDistributionChart.axisRight.isEnabled = false

            // Set "no data" text
            optionDistributionChart.setNoDataText("No data")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up option chart: ${e.message}")
        }
    }

    /**
     * Update UI with question data
     */
    private fun updateUI() {
        try {
            // Set question position (number)
            val position = arguments?.getInt(ARG_POSITION, 0) ?: 0
            questionNumberTextView.text = "Question ${position + 1}"

            // Set question text
            questionTextView.text = question.text

            if (totalAnswers > 0) {
                // Calculate correct answer rate
                val correctAnswers = optionCounts[question.correctOptionIndex]
                val correctRate = (correctAnswers.toDouble() / totalAnswers) * 100

                // Update correct answer rate text
                correctAnswerRateTextView.text = String.format("%.1f%% correct answers", correctRate)

                // Update option distribution chart
                updateOptionDistributionChart()
            } else {
                // No data for this question
                correctAnswerRateTextView.text = "No answers"
                optionDistributionChart.clear()
                optionDistributionChart.setNoDataText("No data")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating UI: ${e.message}")
        }
    }

    /**
     * Update the option distribution chart
     */
    private fun updateOptionDistributionChart() {
        try {
            // Create option labels (A, B, C, D)
            val optionLetters = listOf("A", "B", "C", "D")
            val validOptions = mutableListOf<String>()

            // Create entries for the chart
            val entries = ArrayList<BarEntry>()
            val colors = ArrayList<Int>()

            // Add entries for valid options only
            var validIndex = 0
            for (i in question.options.indices) {
                if (i < question.options.size && question.options[i].isNotEmpty()) {
                    entries.add(BarEntry(validIndex.toFloat(), optionCounts[i].toFloat()))
                    validOptions.add(optionLetters[i])

                    // Correct option is green, others are blue
                    if (i == question.correctOptionIndex) {
                        context?.let {
                            colors.add(ContextCompat.getColor(it, R.color.colorGreen))
                        }
                    } else {
                        context?.let {
                            colors.add(ContextCompat.getColor(it, R.color.colorPrimary))
                        }
                    }
                    validIndex++
                }
            }

            // Create dataset
            val dataSet = BarDataSet(entries, "Response Distribution")
            dataSet.colors = colors
            dataSet.valueTextSize = 10f

            // Add count and percentage values
            dataSet.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val count = value.toInt()
                    val percentage = if (totalAnswers > 0) {
                        (count.toDouble() / totalAnswers * 100).roundToInt()
                    } else {
                        0
                    }
                    return "$count ($percentage%)"
                }
            }

            // Create bar data
            val barData = BarData(dataSet)

            // Configure X axis labels
            optionDistributionChart.xAxis.valueFormatter = IndexAxisValueFormatter(validOptions)
            optionDistributionChart.xAxis.labelCount = validOptions.size

            // Set data to chart
            optionDistributionChart.data = barData

            // Refresh the chart
            optionDistributionChart.invalidate()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating option distribution chart: ${e.message}")
        }
    }
}