package com.example

import com.example.engine.GeminiErrors
import com.example.engine.RequestBackoff
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GeminiErrorsTest {
    @Test
    fun factCheckExplorerIsNotAnEvidenceSource() {
        assertFalse(
            com.example.engine.GroundedResponse.validUrl(
                "https://toolbox.google.com/factcheck/explorer"
            )
        )
        assertFalse(
            com.example.engine.GroundedResponse.validUrl(
                "https://factchecktools.googleapis.com/v1alpha1/claims:search"
            )
        )
        assertTrue(
            com.example.engine.GroundedResponse.validUrl("https://www.who.int/news-room/example")
        )
    }

    @Test
    fun unknown429DoesNotClaimDailyQuotaAndPauses() {
        val e = GeminiErrors.parse(429, "<html>unavailable</html>")
        assertEquals("QUOTA_UNKNOWN", e.reason)
        assertTrue(e.pauseMonitoring)
        assertFalse(e.message!!.contains("đã chạm quota theo ngày"))
    }

    @Test
    fun retryInfoRoundsUpAndHidesRawProviderMessage() {
        val e =
            GeminiErrors.parse(
                429,
                """{"error":{"message":"private-key-or-content","details":[{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"72.4s"}]}}""",
            )
        assertEquals("RATE_LIMIT", e.reason)
        assertEquals(73L, e.retryAfterSeconds)
        assertFalse(e.pauseMonitoring)
        assertFalse(e.message!!.contains("private-key"))
    }

    @Test
    fun dailyAndZeroQuotaPauseEvenWhenRetryInfoExists() {
        for ((v, reason) in
            listOf(
                "\"quotaId\":\"GenerateRequestsPerDayPerProject\"" to "DAILY_QUOTA",
                "\"quotaValue\":\"0\"" to "NO_QUOTA",
            )) {
            val e =
                GeminiErrors.parse(
                    429,
                    """{"error":{"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{$v}]},{"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"10s"}]}}""",
                )
            assertEquals(reason, e.reason)
            assertTrue(e.pauseMonitoring)
        }
    }

    @Test
    fun minuteLimitWithoutDelayWaitsAtLeastOneMinute() {
        val e =
            GeminiErrors.parse(
                429,
                """{"error":{"details":[{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"GenerateRequestsPerMinutePerProject"}]}]}}""",
            )
        assertFalse(e.pauseMonitoring)
        assertEquals(60L, e.retryAfterSeconds)
        assertEquals(90L, GeminiErrors.parse(429, "{}", "90").retryAfterSeconds)
    }

    @Test
    fun persistentAccessAndModelErrorsNeverAutoRetry() {
        for (code in listOf(400, 401, 402, 403, 404)) assertTrue(
            GeminiErrors.parse(code, "{}").pauseMonitoring
        )
        assertFalse(GeminiErrors.parse(503, "{}").pauseMonitoring)
    }

    @Test
    fun repeatedFailuresBackOffAndHonorLongerProviderDelay() {
        assertEquals(
            listOf(30L, 60L, 120L, 240L, 300L, 300L),
            (1..6).map { RequestBackoff.seconds(it) },
        )
        assertEquals(600L, RequestBackoff.seconds(1, 600L))
    }
}
