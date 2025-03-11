package com.example.educheck.utilities

/**
 * Model class representing a chat message
 */
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val isFromTeacher: Boolean = false,
    val text: String = "",
    val timestamp: Long = 0,
    val isRead: Boolean = false
)