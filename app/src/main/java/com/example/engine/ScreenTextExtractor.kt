package com.example.engine

/** Removes standalone interface chrome; Gemini decides which statements can be verified. */
object ScreenTextExtractor {
    private val noise =
        listOf(
            Regex(
                "^(like|thích|chia sẻ|share|bình luận|comment|theo dõi|follow|đăng ký|subscribe|trang chủ|bảng tin|thông báo|tin nhắn|cá nhân|menu)$",
                RegexOption.IGNORE_CASE,
            ),
            Regex("^\\d{1,2}:\\d{2}(\\s?(am|pm))?$", RegexOption.IGNORE_CASE),
            Regex("^(5g|4g|lte|wifi|volte)$", RegexOption.IGNORE_CASE),
        )

    fun extract(raw: String): String =
        raw.lineSequence()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() && noise.none { pattern -> pattern.matches(it) } }
            .joinToString("\n")
            .take(12000)
}
