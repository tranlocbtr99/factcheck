# LTM FactCheck 3.2.0 — Gemini không Search

Luồng hiện tại: OCR liên tục trên thiết bị → gửi nội dung mới trực tiếp đến Gemini → hiển thị **Đúng/Sai và giải thích**. Nhận định dựa trên kiến thức sẵn có của mô hình; không đối chiếu nguồn bên ngoài.

## Hành vi
- Không gửi Google Search hay bất kỳ tool nào trong yêu cầu AI.
- Một request cho một nội dung; không truy xuất nguồn, không tạo đường dẫn hoặc bằng chứng giả.
- Verdict mới chỉ có DUNG hoặc SAI. Thiếu JSON/giải thích hoặc kết quả không hợp lệ là lỗi dịch vụ, không được client tự gán Đúng/Sai.
- Mọi nhận định mới mang assessmentMode=model_knowledge, không có references/searchEntryPoint; giao diện và popup ghi rõ chưa đối chiếu nguồn.
- OCR mặc định mỗi 3 giây, hàng chờ chỉ giữ màn hình mới nhất; không gửi lại nội dung không đổi. Pause/cancel, quota diagnostics và backoff của 3.1.1 được giữ.
- Lịch sử cũ và nguồn đã lưu vẫn được giữ nguyên; nguồn cũ không bị dùng để quảng bá nhận định mới là đã xác minh.
- Kết nối cá nhân gọi Gemini trực tiếp. Kết nối máy chủ dùng POST /assess; máy chủ cũ không có route này sẽ báo lỗi thay vì âm thầm tiếp tục Search. /verify trên Worker mới là alias không Search.
- Model và quota phụ thuộc tài khoản. Bỏ Search không đồng nghĩa API vô hạn hay đảm bảo miễn phí cho mọi model.

## Build / kiểm tra
JDK 21, Android SDK 36.1, build-tools 36.0.0.
`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
`cd backend && npm ci && npm test && npx wrangler deploy --dry-run`

applicationId=vn.ltm.factcheck, versionName=3.2.0, versionCode=30200.
Release cần KEYSTORE_PATH, KEY_ALIAS, STORE_PASSWORD, KEY_PASSWORD từ khóa ký cũ. Không đưa khóa lên git. Không gỡ app cũ để nâng cấp.

## Trạng thái
Xem STATUS.md và TASKS.md trước khi tiếp tục. Bản 3.2.0 cần kiểm tra tự động qua CI; môi trường thực thi cục bộ hiện không khả dụng nên chưa build APK ký hay chạy provider thật cho thay đổi này.
