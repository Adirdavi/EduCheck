package com.example.educheck.utilities

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.Exclude

@IgnoreExtraProperties
data class TestResult(
    val id: String = "",
    val testId: String = "",
    var testTitle: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val score: Double = 0.0,
    val submittedAt: Long = 0,
    val answers: List<StudentAnswer> = listOf(),
    var totalQuestions: Int = 0,
    var answeredQuestions: Int = 0,
    val questionSnapshots: List<Map<String, Any>> = listOf()
) {
    // Document ID is not stored in Firestore - it's the document ID itself
    @get:Exclude
    var documentId: String = ""

    /**
     * Calculate the number of correct answers based on score and total questions
     */
    @get:Exclude
    val correctAnswers: Int
        get() = if (totalQuestions > 0) {
            ((score * totalQuestions) / 100.0).toInt()
        } else {
            0
        }
}