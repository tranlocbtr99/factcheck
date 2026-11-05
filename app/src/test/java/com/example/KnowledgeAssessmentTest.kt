package com.example

import com.example.engine.FactCheckException
import com.example.engine.KnowledgeAssessment
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KnowledgeAssessmentTest {
    private fun judgement(verdict: String) = JSONObject()
        .put("verdict", verdict).put("shortExplanation", "Nhận định")
        .put("detailedExplanation", "Giải thích của Gemini")
        .put("limitations", "Có thể thiếu thông tin cập nhật.")

    @Test fun requestHasNoSearchOrOtherToolsAndOnlyBinaryVerdicts() {
        val body = KnowledgeAssessment.requestBody("Nước chanh không chữa ung thư.")
        assertFalse(body.has("tools"))
        assertFalse(body.has("toolConfig"))
        val prompt = body.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(prompt.contains("Nước chanh không chữa ung thư."))
        val choices = body.getJSONObject("generationConfig").getJSONObject("responseJsonSchema")
            .getJSONObject("properties").getJSONObject("verdict").getJSONArray("enum")
        assertEquals(listOf("DUNG", "SAI"), (0 until choices.length()).map { choices.getString(it) })
    }

    @Test fun trueAndFalseArePreservedWithoutReferences() {
        for (verdict in listOf("DUNG", "SAI")) {
            val result = KnowledgeAssessment.validate(judgement(verdict))
            assertEquals(verdict, result.getString("verdict"))
            assertEquals("model_knowledge", result.getString("assessmentMode"))
            assertEquals(0, result.getJSONArray("references").length())
            assertTrue(result.getString("limitations").contains(KnowledgeAssessment.NOTICE))
        }
    }

    @Test fun generatedSourcesAndProofClaimsCannotBecomeExternalEvidence() {
        val raw = judgement("DUNG").put("references", JSONArray().put(JSONObject().put("url", "https://invented.test")))
            .put("evidence", "Verified online").put("searchEntryPoint", "<div>Search</div>")
        val result = KnowledgeAssessment.validate(raw)
        assertEquals(0, result.getJSONArray("references").length())
        assertEquals("", result.getString("evidence"))
        assertEquals("", result.getString("searchEntryPoint"))
    }

    @Test fun malformedJudgementsNeverTurnIntoAClientInventedVerdict() {
        for (raw in listOf(judgement("UNKNOWN"), judgement("CHUA_DU_BANG_CHUNG"),
            judgement("DUNG").put("shortExplanation", " "), judgement("SAI").put("detailedExplanation", 42))) {
            try { KnowledgeAssessment.validate(raw); fail("Malformed judgement accepted") }
            catch (_: FactCheckException) {}
        }
    }

    @Test fun olderBackendMustNotSilentlyUseSearch() {
        try { KnowledgeAssessment.validate(judgement("DUNG"), requireMode = true); fail("Old backend accepted") }
        catch (_: FactCheckException) {}
        val result = KnowledgeAssessment.validate(judgement("SAI").put("assessmentMode", KnowledgeAssessment.MODE), requireMode = true)
        assertEquals("SAI", result.getString("verdict"))
    }
}
