package com.example.educheck.utilities

import java.util.UUID


data class QuestionReport(
    val id: String = UUID.randomUUID().toString(),
    val testId: String = "",
    val testTitle: String = "",
    val questionId: String = "",
    val questionText: String = "",
    val reportedBy: String = "", // Student ID
    val studentName: String = "",
    val reportText: String = "",
    val reportedAt: Long = System.currentTimeMillis(),
    val resolved: Boolean = false,
    val teacherResponse: String = "",
    val teacherId: String = ""
)