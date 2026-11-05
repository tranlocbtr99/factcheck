package com.example.engine

import android.graphics.Bitmap
import kotlin.math.abs

/**
 * Phát hiện thay đổi khung hình màn hình để tránh gửi lại các khung hình trùng lặp
 * Tối ưu CPU, pin và tài nguyên AI
 */
class ScreenFrameDetector(
    private val changeThresholdPercent: Float = 0.08f // 8% thay đổi được coi là nội dung mới
) {
    private var lastFrameSignature: IntArray? = null
    private val sampleGridSize = 16 // 16x16 grid = 256 sample points

    /**
     * Kiểm tra xem khung hình hiện tại có sự khác biệt đáng kể so với khung hình trước hay không
     * Trả về true nếu nội dung màn hình đã thay đổi
     */
    fun hasFrameChanged(bitmap: Bitmap): Boolean {
        val currentSignature = computeSignature(bitmap)
        val previousSignature = lastFrameSignature

        if (previousSignature == null) {
            lastFrameSignature = currentSignature
            return true
        }

        var diffCount = 0
        val total = sampleGridSize * sampleGridSize

        for (i in 0 until total) {
            val prevPixel = previousSignature[i]
            val currPixel = currentSignature[i]

            val rDiff = abs(((prevPixel shr 16) and 0xFF) - ((currPixel shr 16) and 0xFF))
            val gDiff = abs(((prevPixel shr 8) and 0xFF) - ((currPixel shr 8) and 0xFF))
            val bDiff = abs((prevPixel and 0xFF) - (currPixel and 0xFF))

            if (rDiff + gDiff + bDiff > 45) {
                diffCount++
            }
        }

        val diffRatio = diffCount.toFloat() / total
        if (diffRatio >= changeThresholdPercent) {
            lastFrameSignature = currentSignature
            return true
        }

        return false
    }

    private fun computeSignature(bitmap: Bitmap): IntArray {
        val sig = IntArray(sampleGridSize * sampleGridSize)
        val stepX = (bitmap.width / sampleGridSize).coerceAtLeast(1)
        val stepY = (bitmap.height / sampleGridSize).coerceAtLeast(1)

        var idx = 0
        for (y in 0 until sampleGridSize) {
            for (x in 0 until sampleGridSize) {
                val px = (x * stepX).coerceIn(0, bitmap.width - 1)
                val py = (y * stepY).coerceIn(0, bitmap.height - 1)
                sig[idx++] = bitmap.getPixel(px, py)
            }
        }
        return sig
    }

    fun reset() {
        lastFrameSignature = null
    }
}
