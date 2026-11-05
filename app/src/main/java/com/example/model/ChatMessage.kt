package com.example.model

import java.util.UUID

/**
 * Tin nhắn trong phiên "Hỏi AI" theo ngữ cảnh màn hình
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val onScreenContext: String? = null,
    val relatedSources: List<ReferenceSource> = emptyList(),
    val isAnalyzing: Boolean = false
)
