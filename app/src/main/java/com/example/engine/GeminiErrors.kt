package com.example.engine

import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject

/** Exposes classified diagnostics, never the raw response (which may contain request data). */
internal object GeminiErrors {
    fun parse(code: Int, body: String, retryHeader: String? = null): FactCheckException {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val details = error?.optJSONArray("details") ?: JSONArray()
        var retry =
            retryHeader
                ?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0 }
                ?.let { ceil(it).toLong().coerceAtMost(86400) }
        var daily = false
        var minute = false
        var zero = false
        for (i in 0 until details.length()) {
            val detail = details.optJSONObject(i) ?: continue
            if (detail.optString("@type").endsWith("google.rpc.RetryInfo")) {
                val seconds = detail.optString("retryDelay").removeSuffix("s").toDoubleOrNull()
                if (seconds != null && seconds.isFinite() && seconds > 0)
                    retry = maxOf(retry ?: 0, ceil(seconds).toLong().coerceAtMost(86400))
            }
            if (!detail.optString("@type").endsWith("google.rpc.QuotaFailure")) continue
            val violations = detail.optJSONArray("violations") ?: continue
            for (j in 0 until violations.length()) {
                val v = violations.optJSONObject(j) ?: continue
                val metric = (v.optString("quotaMetric") + " " + v.optString("quotaId")).lowercase()
                daily =
                    daily ||
                        metric.contains("perday") ||
                        metric.contains("per_day") ||
                        metric.contains("daily")
                minute =
                    minute ||
                        metric.contains("perminute") ||
                        metric.contains("per_minute") ||
                        metric.contains("persecond")
                zero = zero || v.opt("quotaValue")?.toString()?.toDoubleOrNull() == 0.0
            }
        }
        if (code == 429)
            return when {
                zero ->
                    FactCheckException(
                        "Gemini báo quota của mô hình bằng 0 (429). Kiểm tra quyền dùng mô hình và quota dự án trong AI Studio rồi nhấn Tiếp tục.",
                        pauseMonitoring = true,
                        reason = "NO_QUOTA",
                    )
                daily ->
                    FactCheckException(
                        "Gemini đã chạm quota theo ngày (429). Kiểm tra quota dự án trong AI Studio; app đã tạm dừng gửi tự động.",
                        pauseMonitoring = true,
                        reason = "DAILY_QUOTA",
                    )
                retry != null || minute ->
                    FactCheckException(
                        "Gemini đang giới hạn tốc độ (429). App sẽ chờ ít nhất ${retry ?: 60} giây trước khi thử lại.",
                        retryAfterSeconds = retry ?: 60,
                        reason = "RATE_LIMIT",
                    )
                else ->
                    FactCheckException(
                        "Gemini từ chối do hạn mức (429), chưa rõ giới hạn tốc độ hay quota ngày. Kiểm tra AI Studio rồi nhấn Tiếp tục; app đã tạm dừng gửi tự động.",
                        pauseMonitoring = true,
                        reason = "QUOTA_UNKNOWN",
                    )
            }
        return when (code) {
            401,
            403 ->
                FactCheckException(
                    "Khóa Gemini không hợp lệ hoặc chưa được cấp quyền. Kiểm tra khóa/dự án trong Cài đặt.",
                    pauseMonitoring = true,
                    reason = "ACCESS_DENIED",
                )
            404 ->
                FactCheckException(
                    "Mô hình Gemini không khả dụng. Chọn mô hình có quyền sử dụng trong Cài đặt.",
                    pauseMonitoring = true,
                    reason = "MODEL_UNAVAILABLE",
                )
            400 ->
                FactCheckException(
                    "Gemini không chấp nhận yêu cầu (400). Kiểm tra mô hình có hỗ trợ tạo nội dung JSON, cùng cấu hình dự án.",
                    pauseMonitoring = true,
                    reason = "INVALID_REQUEST",
                )
            402 ->
                FactCheckException(
                    "Gemini yêu cầu kiểm tra thanh toán hoặc số dư dự án (402). App đã tạm dừng gửi tự động.",
                    pauseMonitoring = true,
                    reason = "BILLING_REQUIRED",
                )
            else ->
                FactCheckException(
                    "Google AI gặp lỗi ($code). App sẽ chờ trước khi thử lại.",
                    retryAfterSeconds = retry,
                    reason = "PROVIDER_UNAVAILABLE",
                )
        }
    }
}

internal object RequestBackoff {
    fun seconds(failures: Int, requested: Long? = null): Long =
        maxOf((30L * (1L shl (failures - 1).coerceIn(0, 4))).coerceAtMost(300), requested ?: 0)
}
