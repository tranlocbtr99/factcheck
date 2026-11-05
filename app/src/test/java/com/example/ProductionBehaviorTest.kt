package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.settings.AppSettings
import com.example.engine.FactCheckEngine
import com.example.engine.FactCheckException
import com.example.engine.FactCheckServiceManager
import com.example.model.MonitoringSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductionBehaviorTest {
    @Test
    fun unconfiguredEngineNeverInventsVerdicts() = runBlocking {
        val settings = AppSettings(ApplicationProvider.getApplicationContext<Context>())
        for (claim in
            listOf(
                "Nước chanh không chữa khỏi ung thư.",
                "Ngân hàng Nhà nước công bố lãi suất.",
                "Kem chống nắng giúp bảo vệ da.",
            )) {
            try {
                FactCheckEngine(settings).verifyClaim(claim)
                fail("Must require live evidence")
            } catch (e: FactCheckException) {
                assertTrue(e.message!!.contains("Chưa kết nối"))
            }
        }
    }

    @Test
    fun monitoringPreferencesSurviveNewStoreInstance() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        val s = AppSettings(c)
        s.saveMonitoring(
            MonitoringSettings(
                continuousMonitoring = true,
                sampleRateSeconds = 17,
                reduceTransparency = true,
                alertInsufficientEvidence = true,
            )
        )
        val restored = AppSettings(c).monitoring.value
        assertTrue(restored.continuousMonitoring)
        assertEquals(17, restored.sampleRateSeconds)
        assertTrue(restored.reduceTransparency)
        assertTrue(restored.alertInsufficientEvidence)
    }

    @Test
    fun returningToCachedScreenDoesNotCountAnotherPaidCheck() {
        val record =
            com.example.model.FactCheckRecord(
                originalClaim = "Nội dung",
                normalizedClaim = "Nội dung",
                verdict = com.example.model.FactCheckVerdict.DUNG,
                shortExplanation = "Giải thích",
                detailedExplanation = "Chi tiết",
                evidence = "Nguồn",
            )
        val before = FactCheckServiceManager.assistantState.value.totalCheckedCount
        FactCheckServiceManager.onNewFactCheckRecord(record)
        FactCheckServiceManager.clearContext()
        FactCheckServiceManager.onNewFactCheckRecord(record, countAsCheck = false)
        assertEquals(before + 1, FactCheckServiceManager.assistantState.value.totalCheckedCount)
        assertEquals(record.id, FactCheckServiceManager.assistantState.value.latestRecord?.id)
    }

    @Test
    fun sessionContextIsCleared() {
        FactCheckServiceManager.updateScreenContent("old", "old")
        FactCheckServiceManager.updateLiveAudio("old audio")
        FactCheckServiceManager.clearContext()
        assertEquals("", FactCheckServiceManager.assistantState.value.currentScreenText)
        assertEquals("", FactCheckServiceManager.assistantState.value.liveTranscribedAudio)
        assertNull(FactCheckServiceManager.assistantState.value.latestRecord)
    }
}
