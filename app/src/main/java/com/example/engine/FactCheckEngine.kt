package com.example.engine

import com.example.data.settings.AppSettings
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.model.ReferenceSource
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class FactCheckException(
    message: String,
    val retryAfterSeconds: Long? = null,
    val pauseMonitoring: Boolean = false,
    val reason: String? = null,
) : IOException(message)

class FactCheckEngine(private val settings: AppSettings) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(130, TimeUnit.SECONDS)
            .build()

    private suspend fun request(path: String, payload: JSONObject? = null): JSONObject {
        val config = settings.connection.value
        if (config.personalKey.isNotBlank() && config.url.isBlank())
            return PersonalGeminiClient(settings).request(path, payload)
        if (!config.configured)
            throw FactCheckException(
                "Chưa kết nối máy chủ. Mở Cài đặt để nhập địa chỉ HTTPS và mã kết nối."
            )
        val builder =
            Request.Builder()
                .url(config.url + path)
                .header("Authorization", "Bearer ${config.token}")
        if (payload != null)
            builder.post(
                payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            )
        val call = client.newCall(builder.build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive)
                            continuation.resumeWithException(
                                FactCheckException(
                                    "Không kết nối được dịch vụ. Kiểm tra mạng và thử lại."
                                )
                            )
                    }

                    override fun onResponse(call: Call, response: okhttp3.Response) {
                        response.use {
                            try {
                                val raw =
                                    it.body?.string()
                                        ?: throw FactCheckException("Máy chủ trả về dữ liệu rỗng.")
                                val json =
                                    try {
                                        JSONObject(raw)
                                    } catch (_: Exception) {
                                        throw FactCheckException(
                                            "Máy chủ trả về dữ liệu không hợp lệ."
                                        )
                                    }
                                if (!it.isSuccessful)
                                    throw FactCheckException(
                                        json.optString("error", "Dịch vụ gặp lỗi (${it.code})."),
                                        retryAfterSeconds =
                                            json
                                                .optLong("retryAfterSeconds", 0)
                                                .takeIf { value -> value > 0 }
                                                ?.coerceAtMost(86400),
                                        pauseMonitoring =
                                            json.optBoolean(
                                                "pauseMonitoring",
                                                it.code in listOf(401, 403, 404, 429),
                                            ),
                                        reason = json.optString("reason").take(80),
                                    )
                                if (continuation.isActive) continuation.resume(json)
                            } catch (e: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(e)
                            }
                        }
                    }
                }
            )
        }
    }

    suspend fun health(): String {
        val status = request("/connection", JSONObject()).optString("status")
        if (status != "ready") throw FactCheckException("Máy chủ chưa cấu hình dịch vụ AI.")
        return "Kết nối và mã truy cập hợp lệ. Máy chủ đã cấu hình dịch vụ AI."
    }

    suspend fun verifyClaim(
        extractedClaim: String,
        sourceApp: String = "Nhập thủ công",
        rawScreenText: String = "",
    ): FactCheckRecord {
        val claim = extractedClaim.trim()
        if (claim.isEmpty() || claim.length > 12000)
            throw FactCheckException("Nhập nội dung từ 1 đến 12.000 ký tự.")
        val o = KnowledgeAssessment.validate(
            request("/assess", JSONObject().put("claim", claim).put("mode", KnowledgeAssessment.MODE)),
            requireMode = true,
        )
        val refs = emptyList<ReferenceSource>()
        val verdict = FactCheckVerdict.valueOf(o.getString("verdict"))
        val s = settings.monitoring.value
        val alert =
            when (verdict) {
                FactCheckVerdict.SAI -> true
                FactCheckVerdict.GAY_HIEU_LAM -> !s.alertOnlyFalse && s.alertMisleading
                FactCheckVerdict.CHUA_DU_BANG_CHUNG ->
                    !s.alertOnlyFalse && s.alertInsufficientEvidence
                else -> false
            }
        return FactCheckRecord(
            originalClaim = claim,
            normalizedClaim = claim,
            verdict = verdict,
            shortExplanation = o.getString("shortExplanation"),
            detailedExplanation = o.getString("detailedExplanation"),
            evidence = o.optString("evidence"),
            counterEvidence = o.optString("counterEvidence"),
            references = refs,
            limitations = o.optString("limitations"),
            confidence = 0f,
            sourceApp = sourceApp,
            rawScreenText = rawScreenText,
            isAlerted = alert,
            searchEntryPoint = o.optString("searchEntryPoint"),
        )
    }

    suspend fun ask(question: String, record: FactCheckRecord): String {
        val context =
            JSONObject()
                .put("claim", record.originalClaim)
                .put("verdict", record.verdict.name)
                .put("explanation", record.detailedExplanation)
                .put("evidence", record.evidence)
                .put("counterEvidence", record.counterEvidence)
                .put("limitations", record.limitations)
                .put(
                    "references",
                    JSONArray().apply {
                        record.references.forEach {
                            put(
                                JSONObject()
                                    .put("title", it.title)
                                    .put("url", it.url)
                                    .put("relatedText", it.supportingSnippet)
                            )
                        }
                    },
                )
        return request(
                "/chat",
                JSONObject().put("question", question).put("context", context.toString()),
            )
            .getString("text")
    }

    suspend fun transcribe(wav: ByteArray, mime: String = "audio/wav"): String {
        val normalized =
            when (mime) {
                "audio/x-m4a" -> "audio/mp4"
                "audio/x-wav" -> "audio/wav"
                "audio/x-flac" -> "audio/flac"
                else -> mime
            }
        if (
            normalized !in
                listOf(
                    "audio/wav",
                    "audio/mpeg",
                    "audio/mp3",
                    "audio/mp4",
                    "audio/aac",
                    "audio/ogg",
                    "audio/flac",
                )
        )
            throw FactCheckException(
                "Định dạng âm thanh chưa được hỗ trợ. Chọn WAV, MP3, M4A, AAC, OGG hoặc FLAC."
            )
        return request(
                "/transcribe",
                JSONObject()
                    .put(
                        "audio",
                        android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP),
                    )
                    .put("mime", normalized),
            )
            .getString("text")
    }
}
