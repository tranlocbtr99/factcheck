package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FactCheckEntity
import com.example.model.FactCheckRecord
import com.example.model.FactCheckVerdict
import com.example.model.ReferenceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LTM FactCheck AI", appName)
    }

    @Test
    fun `test entity conversion roundtrip`() {
        val record = FactCheckRecord(
            id = "test-123",
            originalClaim = "Tuyên bố mẫu",
            normalizedClaim = "Tuyên bố đã chuẩn hóa",
            verdict = FactCheckVerdict.SAI,
            shortExplanation = "Giải thích ngắn",
            detailedExplanation = "Giải thích chi tiết",
            evidence = "Bằng chứng",
            counterEvidence = "Phản bác",
            references = listOf(
                ReferenceSource(
                    title = "Nguồn 1",
                    url = "https://example.com",
                    publisher = "Tổ chức Y tế",
                    publishDate = "01/01/2024",
                    supportingSnippet = "Trích đoạn",
                    isPrimarySource = true
                )
            ),
            isAlerted = true
        )

        val entity = FactCheckEntity.fromDomain(record)
        val domain = entity.toDomain()

        assertEquals(record.id, domain.id)
        assertEquals(record.normalizedClaim, domain.normalizedClaim)
        assertEquals(record.verdict, domain.verdict)
        assertEquals(1, domain.references.size)
        assertEquals("Nguồn 1", domain.references.first().title)
        assertEquals(true, domain.isAlerted)
    }
}
