package com.example.educheck.utilities

/**
 * Represents a student's answer to a question
 */
data class StudentAnswer(
    val questionId: String = "",
    val selectedOptionIndex: Int = -1
)