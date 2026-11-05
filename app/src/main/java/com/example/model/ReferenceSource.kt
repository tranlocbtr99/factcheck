package com.example.model

/**
 * Nguồn tham khảo có thể mở được và trích xuất bằng chứng
 */
data class ReferenceSource(
    val title: String,
    val url: String,
    val publisher: String,
    val publishDate: String,
    val supportingSnippet: String,
    val isPrimarySource: Boolean = false
)
