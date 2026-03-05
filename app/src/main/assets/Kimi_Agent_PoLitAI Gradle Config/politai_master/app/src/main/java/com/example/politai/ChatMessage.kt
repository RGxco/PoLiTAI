package com.example.politai

/**
 * Chat Message Data Class - Used across the app for UI and logic
 * Master Grade Edition
 */
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long,
    val isError: Boolean = false
)
