package com.example

import com.example.engine.FactCheckException
import com.example.engine.GroundedResponse
import com.example.engine.ScreenTextExtractor
import com.example.model.MonitoringSettings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContinuousScreenTest {
    @Test
    fun extractionPreservesShortLinesNegationsAndNumbersWithoutKeywordFiltering() {
        val text = "09:41\nLike\n2026\nKhông\ncó chuyện này.\n30%\nTheo tôi đây là tin đúng.\nShare"
        assertEquals(
            "2026\nKhông\ncó chuyện này.\n30%\nTheo tôi đây là tin đúng.",
            ScreenTextExtractor.extract(text),
        )
        assertEquals("", ScreenTextExtractor.extract("Like\n09:41\nShare"))
    }

    @Test
    fun interfaceSpacingDoesNotChangeExtractedContent() {
        assertEquals(
            ScreenTextExtractor.extract("Tin   mới\nShare"),
            ScreenTextExtractor.extract("  Tin mới  \nLike\n"),
        )
    }

    @Test
    fun screenReadingDefaultsToContinuousEveryThreeSeconds() {
        assertTrue(MonitoringSettings().continuousMonitoring)
        assertEquals(3, MonitoringSettings().sampleRateSeconds)
    }

    private fun response(verdict: String, linked: Boolean) =
        JSONObject(
            """{
      "candidates":[{"content":{"parts":[{"text":"{\"verdict\":\"$verdict\",\"shortExplanation\":\"Kết luận\",\"detailedExplanation\":\"Giải thích\",\"references\":[{\"url\":\"https://invented.test\"}]}"}]},
      "groundingMetadata":{"groundingChunks":[{"web":{"uri":"https://example.org/source","title":"Source"}}],"groundingSupports":${if(linked) """[{"segment":{"text":"Giải thích có nguồn"},"groundingChunkIndices":[0]}]""" else "[]"}}}]
    }"""
        )

    @Test
    fun singleResponseUsesOnlyLinkedProviderSources() {
        val result = GroundedResponse.singlePass(response("DUNG", true))
        assertEquals("DUNG", result.getString("verdict"))
        assertEquals(1, result.getJSONArray("references").length())
        assertEquals(
            "https://example.org/source",
            result.getJSONArray("references").getJSONObject(0).getString("url"),
        )
    }

    @Test
    fun aYesOrNoWithoutSourcesIsNeverShownAsVerified() {
        for (verdict in listOf("DUNG", "SAI")) {
            val result = GroundedResponse.singlePass(response(verdict, false))
            assertEquals("CHUA_DU_BANG_CHUNG", result.getString("verdict"))
            assertEquals(0, result.getJSONArray("references").length())
        }
    }

    @Test
    fun malformedSingleResponseCannotBecomeASavedVerdict() {
        try {
            GroundedResponse.singlePass(response("UNKNOWN", true))
            fail("Invalid result accepted")
        } catch (_: FactCheckException) {}
    }
}
