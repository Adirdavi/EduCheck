package com.example.educheck.utilities

/**
 * Message class for chat functionality
 */
data class Message(
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val chatId: String = "",
    val isTeacher: Boolean = false,
    val text: String = "",
    val timestamp: Long = 0,
    val dateTime: String = "",
    val isRead: Boolean = false
)