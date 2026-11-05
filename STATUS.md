# LTM FactCheck — tiến độ
Cập nhật: 05/11/2026 (giờ Việt Nam).

## Yêu cầu hiện tại
- Chủ nhân yêu cầu vẫn đưa nhận định ĐÚNG/SAI, không Search và không cần nguồn.
- Luồng: OCR liên tục → một request Gemini không tools → nhận định Đúng/Sai cùng giải thích.
- Kết quả phải ghi rõ: nhận định từ kiến thức Gemini, chưa đối chiếu nguồn ngoài; không tự tạo bằng chứng.

## Mã mới 3.2.0 / 30200
- Nhánh feat/complete-grounded-verification, PR #1: https://github.com/tranlocbtr99/factcheck/pull/1. Không nhập main.
- PersonalGeminiClient dùng KnowledgeAssessment.requestBody, không tools/google_search.
- Worker /assess (và alias /verify mới) đánh giá theo kiến thức, chỉ nhận DUNG/SAI; bỏ nguồn và search attribution kể cả provider tự xuất chúng.
- FactCheckEngine không còn bắt buộc nguồn cho mode=model_knowledge. Yêu cầu response mode rõ ràng; route /assess tránh dùng nhầm máy chủ cũ còn Search.
- UI và popup hiển thị Đúng/Sai, giải thích và nhãn chưa đối chiếu nguồn ngoài. Lịch sử cũ không bị xóa.
- Giữ OCR 3 giây, chống trùng nội dung, hàng chờ mới nhất và xử lý quota của 3.1.1.

## Kiểm tra / bàn giao
- Môi trường exec/build cục bộ hiện unavailable. Đã build và kiểm thử 3.2.0 qua GitHub CI; đã có APK preview cài song song, chưa có APK release cài đè bằng khóa cũ.
- Đã bổ sung test no-tools, binary verdict không cần nguồn, bỏ nguồn giả, lỗi output sai định dạng và từ chối backend cũ thiếu mode. CI cần chạy trên commit này.
- APK ký gần nhất vẫn là 3.1.1; bản đó còn Search, không đáp ứng yêu cầu mới.
- Chưa gọi Gemini thật hoặc kiểm tra trên iQOO. Không đưa khóa API/khóa ký lên git.
- Bước tiếp: xem CI; khi môi trường build phục hồi, build 3.2.0 bằng khóa cũ, kiểm tra chữ ký, lưu APK và thử trực tiếp trên thiết bị.

- CI đầu tiên (42055486668) lỗi trước khi build: setup-android mặc định yêu cầu package `tools` đã không còn. Đã đổi sang `platform-tools`, tách job backend và chạy lại CI. JavaScript syntax + hàm đánh giá model-only đã được kiểm tra trực tiếp trong runtime điều phối.

## Bàn giao APK thử 3.2.0
- CI https://github.com/tranlocbtr99/factcheck/actions/runs/42055980251 đã thành công cả backend và Android cho code không Search. Backend 21/21 test; cần xem báo cáo để lấy số Android chính xác.
- GitHub chỉ có khóa debug, không có khóa release cũ. Debug variant được đổi thành vn.ltm.factcheck.preview, tên LTM FactCheck thử nghiệm để cài song song mà không gỡ/ghi đè app cũ. Release variant vẫn vn.ltm.factcheck và giữ cấu hình khóa ký cũ.
- APK preview có dữ liệu/cài đặt riêng, cần nhập lại API key; không tự chuyển lịch sử hoặc khóa từ app cũ. Đây không phải APK nâng cấp cài đè.
- CI preview đã thành công: https://github.com/tranlocbtr99/factcheck/actions/runs/42059523750 . APK: https://github.com/tranlocbtr99/factcheck/actions/runs/42059523750/artifacts/13431701505 (ZIP chỉ chứa LTMFactCheck-3.2.0-preview.apk).

- APK preview đã kiểm tra chữ ký và package: vn.ltm.factcheck.preview / 3.2.0-preview / 30200. SHA-256 APK 6f1754d73a1b8f5b5098f66d6a3e6ff37dbb67928e5268a7f84db7994ba53d41. Chứng thư debug 001de2cc2f4d7930c85f4915d4c9f199df106720c87f617442c675876cc56b97, khác release cũ; chỉ cài song song. Backend và Android CI thành công. Đã sửa test tên app theo debug/release variant.
- APK release cài đè bằng khóa cũ vẫn chưa build được do môi trường cục bộ unavailable; không gỡ app cũ để cài preview. Cần nhập API key ở app mới.
