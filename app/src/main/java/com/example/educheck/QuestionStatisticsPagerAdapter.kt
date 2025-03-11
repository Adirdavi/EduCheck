package com.example.educheck

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.educheck.utilities.Question

/**
 * Adapter for the ViewPager2 that displays question statistics
 */
class QuestionStatisticsPagerAdapter(
    activity: FragmentActivity,
    private val questions: List<Question>,
    private val questionStatistics: Map<String, TestStatisticsDetailActivity.QuestionStats>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = questions.size

    override fun createFragment(position: Int): Fragment {
        val question = questions[position]
        val stats = questionStatistics[question.id]

        // Create option counts and total answers
        val optionCounts = stats?.optionCounts ?: IntArray(4)
        val totalAnswers = stats?.totalAnswers ?: 0

        // Create a new fragment for this question
        return QuestionStatisticsFragment.newInstance(
            question = question,
            optionCounts = optionCounts,
            totalAnswers = totalAnswers,
            position = position
        )
    }
}