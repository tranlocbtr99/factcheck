package com.example.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Bộ chuyển đổi âm thanh video trực tiếp thành văn bản (Speech-to-Text)
 * Hỗ trợ nhận diện tiếng nói từ video TikTok, YouTube, Facebook khi phát qua loa hoặc ghi âm
 */
class AudioSpeechEngine(
    private val context: Context,
    private val onSpeechRecognized: (text: String, isFinal: Boolean) -> Unit,
    private val onErrorOccurred: (errorMsg: String) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tag = "AudioSpeechEngine"

    private var currentAccumulatedText = StringBuilder()

    fun isListening(): Boolean = isListening

    fun startListening() {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    onErrorOccurred("Thiết bị chưa hỗ trợ SpeechRecognizer dịch vụ nhận dạng tiếng nói.")
                    return@post
                }

                stopListeningInternal()

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            Log.d(tag, "Sẵn sàng thu âm thanh video")
                        }

                        override fun onBeginningOfSpeech() {
                            Log.d(tag, "Bắt đầu phát hiện giọng nói trong video")
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            // Cường độ âm thanh
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            Log.d(tag, "Kết thúc đoạn nói")
                        }

                        override fun onError(error: Int) {
                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Lỗi thu âm thanh"
                                SpeechRecognizer.ERROR_CLIENT -> "Lỗi client nhận diện"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Chưa cấp quyền RECORD_AUDIO"
                                SpeechRecognizer.ERROR_NETWORK -> "Lỗi kết nối mạng nhận diện"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Hết thời gian mạng"
                                SpeechRecognizer.ERROR_NO_MATCH -> "Chưa nhận diện rõ câu nói"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Dịch vụ đang bận"
                                SpeechRecognizer.ERROR_SERVER -> "Lỗi máy chủ nhận diện"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không phát hiện thêm tiếng nói"
                                else -> "Lỗi nhận dạng âm thanh ($error)"
                            }
                            Log.w(tag, "SpeechRecognizer onError: $errorMsg")

                            // Tự động khởi động lại nếu đang trong chế độ giám sát liên tục
                            if (isListening) {
                                mainHandler.postDelayed({
                                    if (isListening) {
                                        restartListening()
                                    }
                                }, 1000)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val text = matches[0]
                                if (text.isNotBlank()) {
                                    currentAccumulatedText.append(" ").append(text)
                                    onSpeechRecognized(currentAccumulatedText.toString().trim(), true)
                                }
                            }

                            // Tiếp tục nghe đoạn tiếp theo của video
                            if (isListening) {
                                mainHandler.postDelayed({
                                    if (isListening) {
                                        restartListening()
                                    }
                                }, 400)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val partial = matches[0]
                                if (partial.isNotBlank()) {
                                    val full = "${currentAccumulatedText.toString().trim()} $partial".trim()
                                    onSpeechRecognized(full, false)
                                }
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "vi-VN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.startListening(intent)
                isListening = true
                Log.i(tag, "Bắt đầu chuyển đổi âm thanh video sang văn bản...")
            } catch (e: Exception) {
                e.printStackTrace()
                onErrorOccurred("Không thể khởi động bộ nhận dạng âm thanh: ${e.message}")
            }
        }
    }

    private fun restartListening() {
        if (!isListening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {}
    }

    fun stopListening() {
        isListening = false
        mainHandler.post {
            stopListeningInternal()
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}
    }

    fun clearBuffer() {
        currentAccumulatedText.clear()
    }
}
