package com.example

import com.example.engine.ClaimExtractor
import com.example.engine.FactCheckEngine
import com.example.model.FactCheckVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val claimExtractor = ClaimExtractor()
    private val factCheckEngine = FactCheckEngine()

    @Test
    fun testClaimExtraction_filtersUiChromeNoise() {
        val ocrSample = """
            09:41
            100%
            Like
            Share
            Comment
            Bác sĩ tiết lộ: Uống nước chanh nóng chữa khỏi dứt điểm ung thư không cần hóa trị
            Theo dõi
            Menu
        """.trimIndent()

        val claims = claimExtractor.extractClaims(ocrSample)
        assertFalse(claims.isEmpty())
        val topClaim = claims.first()
        assertTrue(topClaim.normalizedClaim.contains("nước chanh", ignoreCase = true))
        assertFalse(topClaim.normalizedClaim.contains("100%"))
        assertFalse(topClaim.normalizedClaim.contains("09:41"))
    }

    @Test
    fun testFactCheckEngine_detectsFalseMedicalMyth() {
        val myth = "Uống nước chanh nóng kiềm hóa chữa khỏi ung thư hoàn toàn"
        val record = factCheckEngine.verifyClaim(myth)

        assertEquals(FactCheckVerdict.SAI, record.verdict)
        assertTrue(record.isAlerted)
        assertFalse(record.references.isEmpty())
        assertEquals("Viện Nghiên cứu Ung thư Hoa Kỳ (AICR)", record.references.first().publisher)
    }

    @Test
    fun testFactCheckEngine_detects5GMyth() {
        val myth = "Sóng 5G làm lây lan virus corona và suy giảm miễn dịch"
        val record = factCheckEngine.verifyClaim(myth)

        assertEquals(FactCheckVerdict.SAI, record.verdict)
        assertTrue(record.isAlerted)
        assertTrue(record.evidence.contains("WHO") || record.evidence.contains("Tổ chức Y tế"))
    }

    @Test
    fun testFactCheckEngine_detectsSubjectiveOpinion() {
        val opinion = "Theo tôi bộ phim này hay tuyệt vời nhất năm nay"
        val record = factCheckEngine.verifyClaim(opinion)

        assertEquals(FactCheckVerdict.KHONG_THE_KIEM_CHUNG, record.verdict)
        assertFalse(record.isAlerted)
    }

    @Test
    fun testFactCheckEngine_insufficientEvidenceForUnknownClaim() {
        val unknown = "Phát hiện một mỏ đá quý mới tại làng quê xa xôi hôm qua"
        val record = factCheckEngine.verifyClaim(unknown)

        // Nguyên tắc trung thực: Không có bằng chứng thì trả về Chưa đủ bằng chứng, không được tự suy diễn thành Sai
        assertEquals(FactCheckVerdict.CHUA_DU_BANG_CHUNG, record.verdict)
        assertFalse(record.isAlerted)
    }

    @Test
    fun testPrimaryVerdict_pillarsMapping() {
        assertEquals(com.example.model.PrimaryVerdict.SAI, FactCheckVerdict.SAI.primaryVerdict)
        assertEquals(com.example.model.PrimaryVerdict.DUNG, FactCheckVerdict.DUNG.primaryVerdict)
        assertEquals(com.example.model.PrimaryVerdict.CHUA_DU_CO_SO, FactCheckVerdict.CHUA_DU_BANG_CHUNG.primaryVerdict)
    }
}
