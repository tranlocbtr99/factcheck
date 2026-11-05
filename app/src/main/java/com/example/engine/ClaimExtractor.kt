package com.example.engine

/**
 * Phân tích và trích xuất các tuyên bố có thể kiểm chứng từ văn bản OCR
 * Loại bỏ rác giao diện (UI Chrome), phân biệt ý kiến vs tuyên bố sự thật
 */
class ClaimExtractor {

    // Các từ khoá rác giao diện phổ biến trên TikTok, Facebook, YouTube, Chrome
    private val uiNoisePatterns = listOf(
        Regex("^(like|thích|chia sẻ|share|bình luận|comment|theo dõi|follow|đăng ký|subscribe)$", RegexOption.IGNORE_CASE),
        Regex("^\\d{1,2}:\\d{2}(\\s?(am|pm))?$", RegexOption.IGNORE_CASE), // Giờ hệ thống
        Regex("^\\d+%$"), // Pin
        Regex("^(5g|4g|lte|wifi|volte)$", RegexOption.IGNORE_CASE),
        Regex("^(trang chủ|bảng tin|thông báo|tin nhắn|cá nhân|menu)$", RegexOption.IGNORE_CASE),
        Regex("^@\\w+$") // Handle username đơn lẻ
    )

    // Các từ khóa chỉ điểm tuyên bố có thể kiểm chứng
    private val claimIndicatorKeywords = listOf(
        "chữa khỏi", "trị dứt điểm", "phát hiện", "nghiên cứu", "chứng minh",
        "thủ tướng", "bộ y tế", "who", "cdc", "vắc xin", "ung thư",
        "đổi tiền", "tỷ usd", "nghìn tỷ", "lợi nhuận", "lãi suất", "nguy hiểm",
        "gây tử vong", "cấm lưu hành", "bắt giữ", "gian lận", "chính thức",
        "bí mật", "tuyệt mật", "đột phá", "sự thật về", "bị lừa", "khẩn cấp"
    )

    data class ExtractedClaim(
        val rawSentence: String,
        val normalizedClaim: String,
        val isVerifiable: Boolean,
        val claimCategory: String, // "Sự kiện", "Y tế/Khoa học", "Chính sách/Xã hội", "Kinh tế", "Ý kiến/Cảm xúc"
        val detectedAppName: String
    )

    /**
     * Lọc và trích xuất các tuyên bố từ văn bản OCR màn hình
     */
    fun extractClaims(rawOcrText: String): List<ExtractedClaim> {
        if (rawOcrText.isBlank()) return emptyList()

        // 1. Nhận diện ứng dụng nếu có dấu vết
        val detectedApp = detectApp(rawOcrText)

        // 2. Tách dòng và làm sạch
        val lines = rawOcrText.lines()
            .map { it.trim() }
            .filter { line ->
                line.length >= 6 && uiNoisePatterns.none { it.matches(line) }
            }

        // 3. Gom các câu có nghĩa
        val sentences = mutableListOf<String>()
        val currentBuffer = StringBuilder()

        for (line in lines) {
            if (line.endsWith(".") || line.endsWith("!") || line.endsWith("?")) {
                currentBuffer.append(" ").append(line)
                sentences.add(currentBuffer.toString().trim())
                currentBuffer.clear()
            } else if (line.length > 30) {
                if (currentBuffer.isNotEmpty()) {
                    sentences.add(currentBuffer.toString().trim())
                    currentBuffer.clear()
                }
                sentences.add(line)
            } else {
                if (currentBuffer.isNotEmpty()) currentBuffer.append(" ")
                currentBuffer.append(line)
            }
        }
        if (currentBuffer.isNotEmpty()) {
            sentences.add(currentBuffer.toString().trim())
        }

        // 4. Phân loại tuyên bố
        val results = mutableListOf<ExtractedClaim>()
        for (sentence in sentences) {
            val normalized = normalizeClaim(sentence)
            if (normalized.length < 10) continue

            val isVerifiable = isLikelyVerifiableClaim(normalized)
            val category = classifyCategory(normalized)

            if (isVerifiable) {
                results.add(
                    ExtractedClaim(
                        rawSentence = sentence,
                        normalizedClaim = normalized,
                        isVerifiable = true,
                        claimCategory = category,
                        detectedAppName = detectedApp
                    )
                )
            }
        }

        // Nếu không tách được câu rõ ràng nhưng cả đoạn có từ khóa quan trọng, trích xuất nguyên đoạn làm 1 claim
        if (results.isEmpty() && lines.isNotEmpty()) {
            val combined = lines.take(3).joinToString(" ")
            if (combined.length >= 15) {
                results.add(
                    ExtractedClaim(
                        rawSentence = combined,
                        normalizedClaim = normalizeClaim(combined),
                        isVerifiable = isLikelyVerifiableClaim(combined),
                        claimCategory = classifyCategory(combined),
                        detectedAppName = detectedApp
                    )
                )
            }
        }

        return results
    }

    private fun detectApp(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("tiktok") || lower.contains("âm thanh gốc") || lower.contains("fyp") -> "TikTok"
            lower.contains("facebook") || lower.contains("thích") && lower.contains("chia sẻ") -> "Facebook"
            lower.contains("youtube") || lower.contains("đăng ký") && lower.contains("shorts") -> "YouTube"
            lower.contains("zalo") -> "Zalo"
            lower.contains("vnexpress") || lower.contains("dantri") || lower.contains("tuoitre") -> "Báo điện tử"
            else -> "Màn hình ứng dụng"
        }
    }

    private fun normalizeClaim(sentence: String): String {
        return sentence
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^[•\\-*\\d.]+\\s*"), "")
            .trim()
    }

    private fun isLikelyVerifiableClaim(text: String): Boolean {
        val lower = text.lowercase()
        // Nếu chứa các từ khóa chỉ cảm xúc cá nhân đơn thuần, bỏ qua
        if (lower.startsWith("tôi nghĩ") || lower.startsWith("mình thấy") || lower.startsWith("theo mình") || lower.contains("dễ thương quá")) {
            return false
        }
        // Có từ khóa chỉ điểm hoặc có số liệu/tên riêng
        val hasKeyword = claimIndicatorKeywords.any { lower.contains(it) }
        val hasNumber = Regex("\\d+").containsMatchIn(text)
        return hasKeyword || (hasNumber && text.length > 20) || text.length > 35
    }

    private fun classifyCategory(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("uống") || lower.contains("thuốc") || lower.contains("trị") || lower.contains("bệnh") || lower.contains("ung thư") || lower.contains("vắc xin") -> "Y tế & Sức khỏe"
            lower.contains("tiền") || lower.contains("lãi") || lower.contains("đầu tư") || lower.contains("ngân hàng") || lower.contains("chứng khoán") -> "Kinh tế & Tài chính"
            lower.contains("chính phủ") || lower.contains("bộ trưởng") || lower.contains("luật") || lower.contains("quy định") || lower.contains("bắt") -> "Chính sách & Pháp luật"
            lower.contains("khoa học") || lower.contains("vũ trụ") || lower.contains("ai") || lower.contains("công nghệ") || lower.contains("5g") -> "Khoa học & Công nghệ"
            else -> "Sự kiện & Xã hội"
        }
    }
}
