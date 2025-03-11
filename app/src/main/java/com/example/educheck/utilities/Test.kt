package com.example.educheck.utilities

data class Test(
    val id: String = "", // Unique ID for the test
    val title: String = "", // Test title
    val questions: List<Question> = listOf(), // List of questions in the test
    val createdBy: String = "", // ID of the teacher who created it
    val createdAt: Long = System.currentTimeMillis() // Creation time
)
