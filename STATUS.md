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
- Môi trường exec/build cục bộ hiện unavailable. Chưa chạy test/build APK ký cho 3.2.0; không dùng số test 3.1.1 làm bằng chứng cho thay đổi mới.
- Đã bổ sung test no-tools, binary verdict không cần nguồn, bỏ nguồn giả, lỗi output sai định dạng và từ chối backend cũ thiếu mode. CI cần chạy trên commit này.
- APK ký gần nhất vẫn là 3.1.1; bản đó còn Search, không đáp ứng yêu cầu mới.
- Chưa gọi Gemini thật hoặc kiểm tra trên iQOO. Không đưa khóa API/khóa ký lên git.
- Bước tiếp: xem CI; khi môi trường build phục hồi, build 3.2.0 bằng khóa cũ, kiểm tra chữ ký, lưu APK và thử trực tiếp trên thiết bị.

- CI đầu tiên (42055486668) lỗi trước khi build: setup-android mặc định yêu cầu package `tools` đã không còn. Đã đổi sang `platform-tools`, tách job backend và chạy lại CI. JavaScript syntax + hàm đánh giá model-only đã được kiểm tra trực tiếp trong runtime điều phối.
