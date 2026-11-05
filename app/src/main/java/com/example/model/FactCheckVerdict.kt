package com.example.model

import androidx.compose.ui.graphics.Color

/**
 * 6 kết luận chuẩn theo quy định hệ thống Fact-Checking của LTM FactCheck AI:
 * 1. Đúng (True)
 * 2. Sai (False)
 * 3. Đúng một phần (Partially True)
 * 4. Gây hiểu lầm (Misleading)
 * 5. Chưa đủ bằng chứng (Insufficient Evidence)
 * 6. Không thể kiểm chứng (Unverifiable)
 */
enum class FactCheckVerdict(
    val titleVi: String,
    val titleEn: String,
    val color: Color,
    val description: String
) {
    DUNG(
        titleVi = "Đúng",
        titleEn = "True",
        color = Color(0xFF10B981), // Emerald Green
        description = "Thông tin chính xác, phù hợp với các nguồn dữ liệu khoa học hoặc cơ quan chuyên môn uy tín."
    ),
    SAI(
        titleVi = "Sai",
        titleEn = "False",
        color = Color(0xFFEF4444), // Ruby Red
        description = "Tuyên bố có bằng chứng đáng tin cậy chứng minh là sai lệch, thông tin bịa đặt hoặc ngụy tạo."
    ),
    DUNG_MOT_PHAN(
        titleVi = "Đúng một phần",
        titleEn = "Partially True",
        color = Color(0xFFF59E0B), // Amber Yellow
        description = "Một phần thông tin có cơ sở thực tế, nhưng một số chi tiết khác không chính xác hoặc chưa kiểm chứng."
    ),
    GAY_HIEU_LAM(
        titleVi = "Gây hiểu lầm",
        titleEn = "Misleading",
        color = Color(0xFFF97316), // Vivid Orange
        description = "Thông tin bị cắt ghép, giật tít, đặt ngoài ngữ cảnh gốc hoặc dễ gây suy diễn sai sự thật."
    ),
    CHUA_DU_BANG_CHUNG(
        titleVi = "Chưa đủ bằng chứng",
        titleEn = "Insufficient Evidence",
        color = Color(0xFFA855F7), // Purple
        description = "Hiện chưa có đủ tài liệu hoặc nghiên cứu độc lập đáng tin cậy để xác thực hoặc bác bỏ."
    ),
    KHONG_THE_KIEM_CHUNG(
        titleVi = "Không thể kiểm chứng",
        titleEn = "Unverifiable",
        color = Color(0xFF94A3B8), // Slate Gray
        description = "Nội dung mang tính quan điểm cá nhân, phỏng đoán tương lai hoặc châm biếm không thể định lượng."
    );

    companion object {
        fun fromString(name: String?): FactCheckVerdict {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: KHONG_THE_KIEM_CHUNG
        }
    }
}
