package com.example.educheck.utilities

data class TestResult(
    val id: String = "",
    val testId: String = "",
    val testTitle: String = "", // Added field for test title
    val studentId: String = "",
    val answers: List<StudentAnswer> = listOf(),
    val score: Double = 0.0,
    val submittedAt: Long = 0,
    val testDeleted: Boolean = false, // Field to indicate if the test was deleted
    val questionSnapshots: List<Map<String, Any>> = listOf() // Snapshots of questions at submission time
) {
    // ID שיכול להיות מעודכן בקוד (לא נשמר בפיירבייס)
    var documentId: String = ""
}
