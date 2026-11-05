package com.example

import com.example.engine.FactCheckException
import com.example.engine.GroundedResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroundedResponseTest {
    private fun raw(ids: JSONArray = JSONArray().put(0)): JSONObject =
        JSONObject()
            .put("verdict", "DUNG")
            .put("shortExplanation", "Có nguồn")
            .put("detailedExplanation", "Chi tiết")
            .put("sourceIds", ids)

    private fun sources() =
        JSONArray()
            .put(
                JSONObject()
                    .put("id", 0)
                    .put("url", "https://example.org/source")
                    .put("title", "Source")
            )

    @Test
    fun thoughtPartsCannotBecomeEvidence() {
        val data =
            JSONObject(
                """{"candidates":[{"content":{"parts":[{"thought":true,"text":"Unverified reasoning"},{"text":"Published response"}]}}]}"""
            )
        assertEquals("Published response", GroundedResponse.text(data))
    }

    @Test
    fun malformedVerdictsAndEmptyExplanationsFailClosed() {
        for (value in
            listOf(
                raw().put("verdict", "MADE_UP"),
                raw().put("shortExplanation", "  "),
                raw().put("detailedExplanation", 42),
            )) {
            try {
                GroundedResponse.assessment(value, sources())
                fail("Malformed result accepted")
            } catch (_: FactCheckException) {}
        }
    }

    @Test
    fun invalidSourceIdsCannotAuthorizeAVerdict() {
        for (id in listOf<Any>("0", 0.9, true, -1, 99)) {
            val result = GroundedResponse.assessment(raw(JSONArray().put(id)), sources())
            assertEquals("CHUA_DU_BANG_CHUNG", result.getString("verdict"))
            assertEquals("", result.getString("evidence"))
            assertEquals(0, result.getJSONArray("references").length())
        }
        assertEquals("DUNG", GroundedResponse.assessment(raw(), sources()).getString("verdict"))
    }

    @Test
    fun onlyValidLinkedSourcesAreExtracted() {
        val metadata =
            JSONObject(
                """{"groundingChunks":[{"web":{"uri":"https://example.org/a","title":"A"}},{"web":{"uri":"https://user:pass@example.org/b","title":"B"}},{"web":{"uri":"https://example.org/c","title":"C"}},{"web":{"uri":"https://example.org/d","title":""}}],"groundingSupports":[{"segment":{"text":"Supported text"},"groundingChunkIndices":[0,1,3]},{"segment":{"text":"Invalid string ID"},"groundingChunkIndices":["2"]}]}"""
            )
        val found = GroundedResponse.sources(metadata)
        assertEquals(1, found.length())
        assertEquals("https://example.org/a", found.getJSONObject(0).getString("url"))
        assertEquals("", found.getJSONObject(0).getString("publishDate"))
    }
}
