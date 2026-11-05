package com.example.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Bounded PCM chunks: no cumulative transcript, no file is retained on disk. */
class AudioCaptureEngine(private val context: Context) {
    @Volatile private var recorder: AudioRecord? = null

    suspend fun capture(
        projection: MediaProjection?,
        microphone: Boolean,
        onChunk: suspend (ByteArray) -> Unit,
    ) =
        withContext(Dispatchers.IO) {
            check(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                "Cần cấp quyền âm thanh trong ứng dụng."
            }
            if (!microphone)
                check(Build.VERSION.SDK_INT >= 29 && projection != null) {
                    "Âm thanh nội bộ cần Android 10 trở lên và phiên chia sẻ màn hình."
                }
            val format =
                AudioFormat.Builder()
                    .setSampleRate(16000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            val min =
                AudioRecord.getMinBufferSize(
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            check(min > 0) { "Thiết bị chưa hỗ trợ định dạng âm thanh." }
            val builder =
                AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(maxOf(min * 2, 32768))
            if (microphone) builder.setAudioSource(MediaRecorder.AudioSource.MIC)
            else if (Build.VERSION.SDK_INT >= 29)
                builder.setAudioPlaybackCaptureConfig(
                    AudioPlaybackCaptureConfiguration.Builder(projection!!)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build()
                )
            val audio = builder.build()
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "Không khởi tạo được bộ thu âm." }
            recorder = audio
            try {
                audio.startRecording()
                val pcm = ByteArray(16000 * 2 * 12)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    var offset = 0
                    while (offset < pcm.size) {
                        currentCoroutineContext().ensureActive()
                        val n = audio.read(pcm, offset, minOf(4096, pcm.size - offset))
                        check(n > 0) { "Luồng âm thanh đã dừng." }
                        offset += n
                    }
                    var energy = 0L
                    val bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
                    while (bb.remaining() >= 2) energy += kotlin.math.abs(bb.short.toInt())
                    check(energy / (pcm.size / 2) > 30) {
                        "Không thu được lời nói. Ứng dụng nguồn có thể chặn thu âm; thử micro hoặc nhập tệp."
                    }
                    onChunk(wav(pcm))
                }
            } finally {
                runCatching { audio.stop() }
                audio.release()
                if (recorder === audio) recorder = null
            }
        }

    fun stop() {
        runCatching { recorder?.stop() }
    }

    companion object {
        fun wav(pcm: ByteArray): ByteArray =
            ByteBuffer.allocate(44 + pcm.size)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    put("RIFF".toByteArray())
                    putInt(36 + pcm.size)
                    put("WAVEfmt ".toByteArray())
                    putInt(16)
                    putShort(1)
                    putShort(1)
                    putInt(16000)
                    putInt(32000)
                    putShort(2)
                    putShort(16)
                    put("data".toByteArray())
                    putInt(pcm.size)
                    put(pcm)
                }
                .array()
    }
}
