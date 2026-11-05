package com.example

import com.example.engine.AudioCaptureEngine
import com.example.engine.ClaimExtractor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun extractionPreservesNegationAndNumbers() {
        val text = "Nước chanh không chữa khỏi ung thư."
        val claims = ClaimExtractor().extractClaims("09:41\n100%\nLike\n$text\nShare")
        assertEquals(text, claims.first().normalizedClaim)
        assertFalse(claims.first().normalizedClaim.contains("100%"))
    }

    @Test
    fun wavHeaderMatchesPcmPayload() {
        val pcm = ByteArray(32000)
        val wav = AudioCaptureEngine.wav(pcm)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4)))
        assertEquals(32036, b.getInt(4))
        assertEquals(16000, b.getInt(24))
        assertEquals(32000, b.getInt(40))
        assertEquals(32044, wav.size)
    }
}
