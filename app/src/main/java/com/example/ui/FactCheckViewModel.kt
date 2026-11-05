package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FactCheckApplication
import com.example.engine.FactCheckServiceManager
import com.example.engine.OcrProcessor
import com.example.model.ChatMessage
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.model.MonitoringSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FactCheckViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FactCheckApplication
    private val repository = app.repository
    private val engine = repository.engine
    val assistantState = FactCheckServiceManager.assistantState
    val settings = FactCheckServiceManager.settings
    val connection = app.settings.connection
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    private fun addChatMessage(message: ChatMessage) {
        _chatMessages.update { it + message }
    }

    private fun clearChat() {
        _chatMessages.value = emptyList()
    }

    val draft = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val chatBusy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val connectionStatus = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _selectedVerdictFilter = MutableStateFlow<FactCheckVerdict?>(null)
    val selectedVerdictFilter = _selectedVerdictFilter.asStateFlow()
    val allHistory =
        repository.allRecords.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )
    val filteredHistory =
        combine(allHistory, _searchQuery, _selectedVerdictFilter) { records, q, f ->
                records.filter {
                    (f == null || it.verdict == f) &&
                        (q.isBlank() ||
                            it.originalClaim.contains(q, true) ||
                            it.shortExplanation.contains(q, true))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _selectedDetailRecord = MutableStateFlow<FactCheckRecord?>(null)
    val selectedDetailRecord = _selectedDetailRecord.asStateFlow()
    private var selectionJob: Job? = null
    private var checkJob: Job? = null
    private var chatJob: Job? = null
    val chatContext = MutableStateFlow<FactCheckRecord?>(null)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setVerdictFilter(filter: FactCheckVerdict?) {
        _selectedVerdictFilter.value = filter
    }

    fun selectRecordForDetail(id: String) {
        selectionJob?.cancel()
        if (id.isBlank()) {
            _selectedDetailRecord.value = null
            return
        }
        selectionJob =
            viewModelScope.launch {
                _selectedDetailRecord.value = repository.getRecordById(id).first()
            }
    }

    fun selectRecordDirect(record: FactCheckRecord) {
        _selectedDetailRecord.value = record
    }

    fun useChatContext(record: FactCheckRecord) {
        chatJob?.cancel()
        chatBusy.value = false
        chatContext.value = record
        clearChat()
    }

    fun toggleFloatingAssistant(context: Context, enable: Boolean) {
        if (enable) FactCheckServiceManager.startService(context)
        else FactCheckServiceManager.stopService(context)
    }

    fun pauseMonitoring() {
        FactCheckServiceManager.sendUserEvent(
            FactCheckServiceManager.UserActionEvent.RequestPauseMonitoring
        )
    }

    fun resumeMonitoring() {
        FactCheckServiceManager.sendUserEvent(
            FactCheckServiceManager.UserActionEvent.RequestResumeMonitoring
        )
    }

    fun stopMonitoring() {
        FactCheckServiceManager.sendUserEvent(
            FactCheckServiceManager.UserActionEvent.RequestStopMonitoring
        )
    }

    fun verifyManualClaim(claimText: String, sourceApp: String = "Nhập thủ công") {
        if (claimText.isBlank() || busy.value) return
        checkJob =
            viewModelScope.launch {
                busy.value = true
                error.value = null
                try {
                    val record = engine.verifyClaim(claimText, sourceApp)
                    repository.saveRecord(record)
                    _selectedDetailRecord.value = record
                    useChatContext(record)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value = e.message ?: "Không hoàn tất kiểm chứng."
                } finally {
                    busy.value = false
                }
            }
    }

    fun cancelCheck() {
        checkJob?.cancel()
    }

    fun askAiAboutScreen(question: String) {
        if (question.isBlank() || chatBusy.value) return
        val record =
            chatContext.value
                ?: run {
                    error.value = "Chọn một kết quả kiểm chứng để bắt đầu hỏi đáp."
                    return
                }
        chatJob =
            viewModelScope.launch {
                chatBusy.value = true
                error.value = null
                addChatMessage(
                    ChatMessage(
                        isUser = true,
                        text = question,
                        onScreenContext = record.originalClaim,
                    )
                )
                try {
                    val answer = engine.ask(question, record)
                    if (chatContext.value?.id == record.id)
                        addChatMessage(
                            ChatMessage(
                                isUser = false,
                                text = answer,
                                relatedSources = record.references,
                            )
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value = e.message
                } finally {
                    chatBusy.value = false
                }
            }
    }

    fun loadImage(uri: android.net.Uri) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            error.value = null
            val ocr = OcrProcessor()
            var bitmap: Bitmap? = null
            try {
                bitmap =
                    withContext(Dispatchers.IO) {
                        val bounds =
                            android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                        app.contentResolver.openInputStream(uri).use {
                            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                        }
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                            throw IllegalArgumentException("Không đọc được định dạng ảnh.")
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
                        val decoded =
                            app.contentResolver.openInputStream(uri).use {
                                android.graphics.BitmapFactory.decodeStream(
                                    it,
                                    null,
                                    android.graphics.BitmapFactory.Options().apply {
                                        inSampleSize = sample
                                    },
                                )
                            } ?: throw IllegalArgumentException("Không đọc được ảnh.")
                        val orientation =
                            runCatching {
                                    app.contentResolver.openInputStream(uri).use {
                                        android.media
                                            .ExifInterface(it!!)
                                            .getAttributeInt(
                                                android.media.ExifInterface.TAG_ORIENTATION,
                                                1,
                                            )
                                    }
                                }
                                .getOrDefault(1)
                        val matrix =
                            android.graphics.Matrix().apply {
                                when (orientation) {
                                    2 -> setScale(-1f, 1f)
                                    3 -> setRotate(180f)
                                    4 -> setScale(1f, -1f)
                                    5 -> {
                                        setRotate(90f)
                                        postScale(-1f, 1f)
                                    }
                                    6 -> setRotate(90f)
                                    7 -> {
                                        setRotate(270f)
                                        postScale(-1f, 1f)
                                    }
                                    8 -> setRotate(270f)
                                }
                            }
                        if (orientation in 2..8)
                            android.graphics.Bitmap.createBitmap(
                                    decoded,
                                    0,
                                    0,
                                    decoded.width,
                                    decoded.height,
                                    matrix,
                                    true,
                                )
                                .also { if (it !== decoded) decoded.recycle() }
                        else decoded
                    }
                val text = ocr.recognizeText(bitmap!!)
                if (text.isBlank())
                    error.value = "Không đọc được chữ. Thử ảnh rõ hơn hoặc nhập văn bản."
                else draft.value = text.take(12000)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Không đọc được ảnh."
            } finally {
                ocr.close()
                bitmap?.recycle()
                busy.value = false
            }
        }
    }

    fun importAudio(uri: android.net.Uri) {
        if (busy.value) return
        checkJob =
            viewModelScope.launch {
                busy.value = true
                error.value = null
                try {
                    val mime = app.contentResolver.getType(uri) ?: "audio/wav"
                    val bytes =
                        withContext(Dispatchers.IO) {
                            app.contentResolver.openInputStream(uri)?.use { stream ->
                                val buffer = java.io.ByteArrayOutputStream()
                                val chunk = ByteArray(8192)
                                while (true) {
                                    val n = stream.read(chunk)
                                    if (n < 0) break
                                    if (buffer.size() + n > 8 * 1024 * 1024)
                                        throw IllegalArgumentException(
                                            "Chọn tệp âm thanh tối đa 8 MB."
                                        )
                                    buffer.write(chunk, 0, n)
                                }
                                buffer.toByteArray()
                            } ?: throw IllegalArgumentException("Không mở được tệp.")
                        }
                    val text = engine.transcribe(bytes, mime)
                    if (text.isBlank()) error.value = "Không nhận diện được lời nói."
                    else draft.value = text.take(12000)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value = e.message
                } finally {
                    busy.value = false
                }
            }
    }

    fun exportHistory(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val records = repository.allRecords.first()
                val json = org.json.JSONArray()
                records.forEach { r ->
                    json.put(
                        org.json
                            .JSONObject()
                            .put("id", r.id)
                            .put("claim", r.originalClaim)
                            .put("verdict", r.verdict.name)
                            .put("explanation", r.detailedExplanation)
                            .put("checkedAt", r.checkedAt)
                            .put("limitations", r.limitations)
                            .put(
                                "sources",
                                org.json.JSONArray().apply {
                                    r.references.forEach {
                                        put(
                                            org.json
                                                .JSONObject()
                                                .put("title", it.title)
                                                .put("url", it.url)
                                        )
                                    }
                                },
                            )
                    )
                }
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use {
                        it.write(json.toString(2).toByteArray())
                    } ?: throw IllegalArgumentException("Không mở được tệp xuất.")
                }
                connectionStatus.value = "Đã xuất lịch sử kiểm chứng."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = "Không xuất được lịch sử: ${e.message}"
            }
        }
    }

    fun saveConnection(url: String, token: String) {
        try {
            app.settings.saveConnection(url, token)
            connectionStatus.value = "Đã lưu. Nhấn kiểm tra kết nối."
            error.value = null
        } catch (e: Exception) {
            error.value = e.message
        }
    }

    fun savePersonalKey(key: String, model: String) {
        try {
            app.settings.savePersonalKey(key, model)
            connectionStatus.value = "Đã lưu khóa cá nhân trên thiết bị."
            error.value = null
        } catch (e: Exception) {
            error.value = e.message
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            connectionStatus.value = "Đang kiểm tra…"
            try {
                connectionStatus.value = engine.health()
            } catch (e: Exception) {
                connectionStatus.value = e.message
            }
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            repository.deleteById(id)
            if (_selectedDetailRecord.value?.id == id) _selectedDetailRecord.value = null
            if (chatContext.value?.id == id) {
                chatJob?.cancel()
                chatContext.value = null
                clearChat()
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
            _selectedDetailRecord.value = null
            chatJob?.cancel()
            chatContext.value = null
            clearChat()
        }
    }

    fun updateSettings(s: MonitoringSettings) {
        FactCheckServiceManager.updateSettings(s)
    }

    fun dismissAlert() {
        FactCheckServiceManager.clearAlert()
    }
}
