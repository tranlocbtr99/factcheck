package com.example.model

import androidx.compose.ui.graphics.Color

/**
 * 3 Trụ cột kết luận bắt buộc:
 * 1. ĐÚNG (True)
 * 2. SAI (False / Sai lệch / Gây hiểu lầm)
 * 3. CHƯA ĐỦ CƠ SỞ (Chưa đủ bằng chứng để khẳng định)
 */
enum class PrimaryVerdict(val titleVi: String, val color: Color) {
    DUNG("ĐÚNG", Color(0xFF10B981)),
    SAI("SAI", Color(0xFFEF4444)),
    CHUA_DU_CO_SO("CHƯA ĐỦ CƠ SỞ", Color(0xFFA855F7))
}

/**
 * Hệ thống Fact-Checking của LTM FactCheck AI
 */
enum class FactCheckVerdict(
    val titleVi: String,
    val titleEn: String,
    val color: Color,
    val primaryVerdict: PrimaryVerdict,
    val description: String
) {
    DUNG(
        titleVi = "Đúng",
        titleEn = "True",
        color = Color(0xFF10B981), // Emerald Green
        primaryVerdict = PrimaryVerdict.DUNG,
        description = "Thông tin chính xác, phù hợp với các nguồn dữ liệu khoa học hoặc cơ quan chuyên môn uy tín."
    ),
    SAI(
        titleVi = "Sai",
        titleEn = "False",
        color = Color(0xFFEF4444), // Ruby Red
        primaryVerdict = PrimaryVerdict.SAI,
        description = "Tuyên bố có bằng chứng xác thực chứng minh là sai lệch, thông tin bịa đặt hoặc ngụy tạo."
    ),
    DUNG_MOT_PHAN(
        titleVi = "Đúng một phần",
        titleEn = "Partially True",
        color = Color(0xFFF59E0B), // Amber Yellow
        primaryVerdict = PrimaryVerdict.CHUA_DU_CO_SO,
        description = "Một phần thông tin có cơ sở thực tế, nhưng một số chi tiết khác không chính xác hoặc chưa kiểm chứng."
    ),
    GAY_HIEU_LAM(
        titleVi = "Gây hiểu lầm",
        titleEn = "Misleading",
        color = Color(0xFFF97316), // Vivid Orange
        primaryVerdict = PrimaryVerdict.SAI,
        description = "Thông tin bị cắt ghép, giật tít, đặt ngoài ngữ cảnh gốc hoặc dễ gây suy diễn sai sự thật."
    ),
    CHUA_DU_BANG_CHUNG(
        titleVi = "Chưa đủ cơ sở",
        titleEn = "Insufficient Evidence",
        color = Color(0xFFA855F7), // Purple
        primaryVerdict = PrimaryVerdict.CHUA_DU_CO_SO,
        description = "Hiện chưa có đủ tài liệu hoặc nghiên cứu độc lập đáng tin cậy để xác thực hoặc bác bỏ."
    ),
    KHONG_THE_KIEM_CHUNG(
        titleVi = "Không thể kiểm chứng",
        titleEn = "Unverifiable",
        color = Color(0xFF94A3B8), // Slate Gray
        primaryVerdict = PrimaryVerdict.CHUA_DU_CO_SO,
        description = "Nội dung mang tính quan điểm cá nhân, phỏng đoán tương lai hoặc châm biếm không thể định lượng."
    );

    companion object {
        fun fromString(name: String?): FactCheckVerdict {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CHUA_DU_BANG_CHUNG
        }
    }
}
