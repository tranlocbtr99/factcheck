# LTM FactCheck — tiến độ
Cập nhật: 05/11/2026 (giờ Việt Nam).

## Mốc hiện tại
- Nhánh: feat/complete-grounded-verification; PR #1: https://github.com/tranlocbtr99/factcheck/pull/1. Chưa nhập main.
- Phiên bản 3.1.1 / 30101 / vn.ltm.factcheck.
- Commit triển khai 3.1.1 cục bộ: c43bd18. Commit 3.1.0 trước đó: b2ae32b, f10a0fb, e2df70f. Bản đồng bộ GitHub dùng commit mới với cùng tree; xem lịch sử nhánh để lấy SHA từ xa.

## Đã xong — luồng đơn giản theo yêu cầu
- Bắt đầu phiên sẽ bật đọc màn hình liên tục. OCR trên thiết bị mặc định mỗi 3 giây, bỏ nhãn giao diện đơn lẻ; giữ nguyên phủ định, số liệu và dòng ngắn, không phân loại bằng từ khóa.
- OCR chạy độc lập khi Gemini đang xử lý. Một yêu cầu kiểm chứng màn hình mỗi lần; hàng chờ chỉ giữ nội dung mới nhất. Nội dung không đổi không gửi lại. Tạm dừng hủy yêu cầu; lỗi tạm thời có thời gian chờ tăng dần; tuân thủ RetryInfo/Retry-After. Quota ngày/bằng 0 hoặc 429 không rõ nguyên nhân sẽ tạm dừng gửi tự động cho đến khi người dùng chủ động tiếp tục.
- Mỗi kiểm chứng dùng một lượt Gemini với Google Search và JSON có cấu trúc: DUNG/SAI/CHUA_DU_BANG_CHUNG, giải thích và nguồn. Không còn lượt AI tổng hợp thứ hai.
- Chỉ lấy đường dẫn HTTPS từ citation metadata có liên kết; bỏ URL tự xuất trong JSON. Thiếu nguồn thì không hiển thị kết luận đúng/sai như đã xác thực.
- Nút nổi hiển thị kết quả đúng và sai, giải thích ngắn và tối đa 2 nguồn bấm mở; trang chi tiết giữ đầy đủ nguồn.
- Kết quả của nội dung đã cũ được giữ trong lịch sử nhưng không thay thế màn hình mới đã đọc. Quay lại nội dung vừa kiểm chứng có thể hiện lại kết quả mà không tăng bộ đếm yêu cầu.

## Thay đổi 3.1.1 theo ảnh phản hồi
- Ảnh thiết bị xác nhận đã đọc được chữ nhưng Gemini trả lỗi tương ứng HTTP 429. Bản 3.1.0 bỏ chi tiết nên chưa thể biết đó là giới hạn phút, ngày hay quota mô hình.
- Đọc chi tiết QuotaFailure và RetryInfo an toàn; không hiển thị hoặc ghi log nguyên phản hồi có thể chứa dữ liệu riêng.
- Phân biệt lỗi quota của Google với giới hạn 100 tác vụ/ngày trong app và giới hạn máy chủ.
- Thêm nút mở hạn mức Gemini trong AI Studio. Bản sửa không tăng hay cấp quota cho tài khoản.
- Chủ nhân không muốn dùng Google Fact Check Tools. Luồng chỉ gọi Gemini, tìm bài gốc qua Google Search nội bộ; chặn toolbox.google.com và factchecktools.googleapis.com trong nguồn mới và liên kết hiển thị ở hồ sơ cũ. Không chuyển sang Fact Check Tools khi Gemini lỗi.

## Bằng chứng
- Android: 28/28 tests, 0 failures; lintDebug 0 errors, 46 warnings; assembleRelease thành công.
- Backend: 20/20 tests; Wrangler deploy --dry-run thành công. Provider dùng phản hồi mô phỏng trong test.
- Chữ ký phải khớp bản 2.1.0; không đổi khóa ký. APK LTMFactCheck-3.1.1.apk; SHA-256 54b99f3dae79ddaccefe11cb93d5bbd5d1c450893a3a4673d39030595a10cc32. apksigner xác nhận chứng thư khớp 2.1.0.
- Test bao gồm: nội dung phủ định/số liệu/dòng ngắn; mặc định liên tục; một lượt Gemini + nguồn metadata; kết quả thiếu nguồn; định dạng lỗi; khôi phục kết quả không đếm thêm; các test lịch sử, settings và Compose trước đó.

## Chưa xác nhận / bước tiếp
- Bước tiếp: dùng 3.1.1 trên thiết bị để xem loại quota cụ thể và kiểm tra project/model trong AI Studio. Không yêu cầu gửi khóa qua chat.
- Cần kiểm chứng trực tuyến bằng khóa Gemini hợp lệ; app hỗ trợ nhập khóa ngay trên thiết bị. Chọn model hỗ trợ Google Search cùng structured output (mặc định gemini-3.8-flash).
- Cần iQOO để kiểm tra quyền chia sẻ, độ trễ, chạy nền, hiển thị thực và cài nâng cấp giữ dữ liệu. Không có thiết bị adb trong phiên.
- Chưa triển khai Worker; nếu dùng máy chủ riêng, cần cập nhật Worker theo nguồn mới. Âm thanh là tính năng tùy chọn riêng, không nằm trong luồng OCR màn hình.
- Không xác nhận độ chính xác thực tế bằng các test mô phỏng. Một API request có thể chạy nhiều tìm kiếm của provider và vẫn chịu quota/chi phí.

## Tiếp tục tiết kiệm hạn mức
- Đọc STATUS và TASKS trước; xem diff liên quan thay vì đọc lại toàn dự án.
- “Báo tiến độ ngắn”: chỉ đọc trạng thái và đối chiếu HEAD, không tự build/test.
- Thực hiện một nhóm việc, kiểm tra liên quan, commit và ghi bằng chứng. Build ký ở mốc bàn giao.
- Đã được Chủ nhân cho phép đồng bộ nhánh và tạo/cập nhật PR. Git CLI không có đăng nhập; dùng kết nối GitHub đã xác thực. Không tự nhập main.
