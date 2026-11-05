package com.example.model

enum class AlertSensitivity(val labelVi: String, val thresholdDesc: String) {
    RELAXED("Tiết kiệm (Thấp)", "Chỉ cảnh báo khi bằng chứng sai lệch tuyệt đối rõ ràng"),
    BALANCED("Cân bằng (Khuyên dùng)", "Cảnh báo thông tin Sai và Gây hiểu lầm"),
    STRICT("Nghiêm ngặt (Cao)", "Cảnh báo cả nội dung Chưa đủ bằng chứng hoặc suy đoán")
}

data class MonitoringSettings(
    val sampleRateSeconds: Int = 3, // 1: Nhanh (0.5-1s), 3: Cân bằng (2-4s), 8: Tiết kiệm (5-10s)
    val alertSensitivity: AlertSensitivity = AlertSensitivity.BALANCED,
    val alertOnlyFalse: Boolean = false,
    val alertMisleading: Boolean = true,
    val alertInsufficientEvidence: Boolean = false,
    val floatingButtonAlpha: Float = 0.95f,
    val floatingButtonSizeDp: Int = 58,
    val hapticFeedback: Boolean = true,
    val autoScrollDebounceMs: Long = 1200L,
    val preferredTopics: List<String> = listOf("Sức khỏe & Y tế", "Khoa học & Công nghệ", "Tin tức xã hội", "Kinh tế & Tài chính")
)
