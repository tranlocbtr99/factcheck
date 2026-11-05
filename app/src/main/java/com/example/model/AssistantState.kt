package com.example.model

/**
 * Trạng thái của trợ lý nổi:
 * - Xám: Chưa giám sát
 * - Xanh: Đang giám sát
 * - Vàng: Tạm dừng
 * - Đỏ: Có cảnh báo cần chú ý
 */
enum class AssistantStatus(val displayNameVi: String) {
    IDLE("Chưa giám sát"),
    MONITORING("Đang giám sát"),
    PAUSED("Tạm dừng"),
    ALERT("Cảnh báo thông tin")
}

enum class AnalysisPhase {
    IDLE,
    CAPTURING_FRAME,
    RUNNING_OCR,
    EXTRACTING_CLAIMS,
    TRANSCRIBING_AUDIO,
    RETRIEVING_EVIDENCE,
    FINISHED
}

data class AssistantState(
    val status: AssistantStatus = AssistantStatus.IDLE,
    val analysisPhase: AnalysisPhase = AnalysisPhase.IDLE,
    val isOverlayActive: Boolean = false,
    val isMediaProjectionActive: Boolean = false,
    val isAudioTranscriptionActive: Boolean = false,
    val currentScreenText: String = "",
    val liveTranscribedAudio: String = "",
    val currentClaim: String? = null,
    val latestRecord: FactCheckRecord? = null,
    val totalCheckedCount: Int = 0,
    val totalAlertCount: Int = 0,
    val lastCheckTimestamp: Long = 0L,
    val framesCapturedCount: Long = 0L,
    val alertMessage: String? = null,
    val currentAppDetected: String = "Thiết bị Android"
)
