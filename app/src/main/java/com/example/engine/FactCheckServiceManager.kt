package com.example.engine

import android.content.Context
import android.content.Intent
import com.example.model.AnalysisPhase
import com.example.model.AssistantState
import com.example.model.AssistantStatus
import com.example.model.ChatMessage
import com.example.model.FactCheckRecord
import com.example.model.MonitoringSettings
import com.example.service.FloatingAssistantService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Quản lý trạng thái và tương tác giữa UI và FloatingAssistantService
 */
object FactCheckServiceManager {

    private val _assistantState = MutableStateFlow(AssistantState())
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _settings = MutableStateFlow(MonitoringSettings())
    val settings: StateFlow<MonitoringSettings> = _settings.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _userEvents = MutableSharedFlow<UserActionEvent>(extraBufferCapacity = 16)
    val userEvents: SharedFlow<UserActionEvent> = _userEvents.asSharedFlow()

    sealed interface UserActionEvent {
        data object RequestStartMonitoring : UserActionEvent
        data object RequestPauseMonitoring : UserActionEvent
        data object RequestResumeMonitoring : UserActionEvent
        data object RequestStopMonitoring : UserActionEvent
        data object RequestInstantScreenCheck : UserActionEvent
        data class OpenChatWithScreenContext(val contextText: String) : UserActionEvent
        data object RequestToggleAudio : UserActionEvent
        data object DismissAlert : UserActionEvent
    }

    fun setAudioActive(active: Boolean) {
        _assistantState.update { it.copy(isAudioTranscriptionActive = active) }
    }

    fun updateLiveAudio(text: String) {
        _assistantState.update { it.copy(liveTranscribedAudio = text) }
    }

    fun recordFrameCaptured() {
        _assistantState.update { it.copy(framesCapturedCount = it.framesCapturedCount + 1) }
    }

    fun updateStatus(status: AssistantStatus) {
        _assistantState.update { it.copy(status = status) }
    }

    fun updateAnalysisPhase(phase: AnalysisPhase) {
        _assistantState.update { it.copy(analysisPhase = phase) }
    }

    fun updateScreenContent(
        rawText: String,
        claim: String?,
        detectedApp: String = "Màn hình trực tiếp"
    ) {
        _assistantState.update {
            it.copy(
                currentScreenText = rawText,
                currentClaim = claim,
                currentAppDetected = detectedApp
            )
        }
    }

    fun onNewFactCheckRecord(record: FactCheckRecord) {
        _assistantState.update { current ->
            val newAlertCount = if (record.isAlerted) current.totalAlertCount + 1 else current.totalAlertCount
            val newStatus = if (record.isAlerted) AssistantStatus.ALERT else current.status
            current.copy(
                latestRecord = record,
                totalCheckedCount = current.totalCheckedCount + 1,
                totalAlertCount = newAlertCount,
                lastCheckTimestamp = System.currentTimeMillis(),
                status = newStatus,
                alertMessage = if (record.isAlerted) "Phát hiện thông tin sai lệch: ${record.normalizedClaim}" else current.alertMessage
            )
        }
    }

    fun clearAlert() {
        _assistantState.update {
            it.copy(
                alertMessage = null,
                status = if (it.status == AssistantStatus.ALERT) AssistantStatus.MONITORING else it.status
            )
        }
    }

    fun setOverlayActive(active: Boolean) {
        _assistantState.update { it.copy(isOverlayActive = active) }
    }

    fun setMediaProjectionActive(active: Boolean) {
        _assistantState.update {
            it.copy(
                isMediaProjectionActive = active,
                status = if (active) AssistantStatus.MONITORING else AssistantStatus.IDLE
            )
        }
    }

    fun updateSettings(newSettings: MonitoringSettings) {
        _settings.value = newSettings
    }

    fun addChatMessage(message: ChatMessage) {
        _chatMessages.update { it + message }
    }

    fun clearChat() {
        _chatMessages.value = emptyList()
    }

    fun sendUserEvent(event: UserActionEvent) {
        _userEvents.tryEmit(event)
    }

    fun startService(context: Context, action: String? = null) {
        val intent = Intent(context, FloatingAssistantService::class.java).apply {
            if (action != null) this.action = action
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) { }
    }

    fun stopService(context: Context) {
        val intent = Intent(context, FloatingAssistantService::class.java)
        context.stopService(intent)
        setOverlayActive(false)
        setMediaProjectionActive(false)
        updateStatus(AssistantStatus.IDLE)
    }
}
