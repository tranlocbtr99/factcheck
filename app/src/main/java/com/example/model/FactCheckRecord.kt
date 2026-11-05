package com.example.model

import java.util.UUID

/**
 * Bản ghi kiểm chứng thông tin hoàn chỉnh
 */
data class FactCheckRecord(
    val id: String = UUID.randomUUID().toString(),
    val originalClaim: String,
    val normalizedClaim: String,
    val verdict: FactCheckVerdict,
    val shortExplanation: String,
    val detailedExplanation: String,
    val evidence: String,
    val counterEvidence: String = "",
    val references: List<ReferenceSource> = emptyList(),
    val checkedAt: Long = System.currentTimeMillis(),
    val limitations: String = "Kiểm chứng dựa trên các nguồn dữ liệu công khai tại thời điểm phân tích.",
    val confidence: Float = 0.92f,
    val sourceApp: String = "Màn hình trực tiếp",
    val rawScreenText: String = "",
    val isAlerted: Boolean = false
)
