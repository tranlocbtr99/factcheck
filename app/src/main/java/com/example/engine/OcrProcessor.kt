package com.example.engine

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class OcrProcessor {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            // ML Kit tasks outlive coroutine cancellation. Own a copy until task completion.
            val owned = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            if (owned == null) {
                continuation.resumeWithException(
                    FactCheckException("Không tạo được ảnh để đọc chữ.")
                )
                return@suspendCancellableCoroutine
            }
            try {
                recognizer
                    .process(InputImage.fromBitmap(owned, 0))
                    .addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(it.text)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive)
                            continuation.resumeWithException(
                                FactCheckException(
                                    "Bộ đọc chữ gặp lỗi. Hãy thử lại với ảnh rõ hơn."
                                )
                            )
                    }
                    .addOnCompleteListener { owned.recycle() }
            } catch (e: Exception) {
                owned.recycle()
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }

    fun close() {
        runCatching { recognizer.close() }
    }
}
