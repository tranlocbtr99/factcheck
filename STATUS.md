# LTM FactCheck — tiến độ
Cập nhật: 05/11/2026 (giờ Việt Nam).

## Mốc hiện tại
- Nhánh: feat/complete-grounded-verification; PR #1: https://github.com/tranlocbtr99/factcheck/pull/1. Chưa nhập main.
- Phiên bản 3.1.0 / 30100 / vn.ltm.factcheck.
- Commit triển khai cục bộ: b2ae32b, f10a0fb, e2df70f. Bản đồng bộ GitHub dùng commit mới với cùng tree; xem lịch sử nhánh để lấy SHA từ xa.

## Đã xong — luồng đơn giản theo yêu cầu
- Bắt đầu phiên sẽ bật đọc màn hình liên tục. OCR trên thiết bị mặc định mỗi 3 giây, bỏ nhãn giao diện đơn lẻ; giữ nguyên phủ định, số liệu và dòng ngắn, không phân loại bằng từ khóa.
- OCR chạy độc lập khi Gemini đang xử lý. Một yêu cầu kiểm chứng màn hình mỗi lần; hàng chờ chỉ giữ nội dung mới nhất. Nội dung không đổi không gửi lại. Tạm dừng hủy yêu cầu; lỗi có khoảng chờ 30 giây trước khi tự thử lại.
- Mỗi kiểm chứng dùng một lượt Gemini với Google Search và JSON có cấu trúc: DUNG/SAI/CHUA_DU_BANG_CHUNG, giải thích và nguồn. Không còn lượt AI tổng hợp thứ hai.
- Chỉ lấy đường dẫn HTTPS từ citation metadata có liên kết; bỏ URL tự xuất trong JSON. Thiếu nguồn thì không hiển thị kết luận đúng/sai như đã xác thực.
- Nút nổi hiển thị kết quả đúng và sai, giải thích ngắn và tối đa 2 nguồn bấm mở; trang chi tiết giữ đầy đủ nguồn.
- Kết quả của nội dung đã cũ được giữ trong lịch sử nhưng không thay thế màn hình mới đã đọc. Quay lại nội dung vừa kiểm chứng có thể hiện lại kết quả mà không tăng bộ đếm yêu cầu.

## Bằng chứng
- Android: 21/21 tests, 0 failures; lintDebug 0 errors, 44 warnings; assembleRelease thành công.
- Backend: 16/16 tests; Wrangler deploy --dry-run thành công. Provider dùng phản hồi mô phỏng trong test.
- Chữ ký phải khớp bản 2.1.0; không đổi khóa ký. APK LTMFactCheck-3.1.0.apk; SHA-256 ed459c57dca4bca0510eb7730f02aac6a1a9d7374b8530465ed858ea88e4d703. apksigner xác nhận chứng thư khớp 2.1.0.
- Test bao gồm: nội dung phủ định/số liệu/dòng ngắn; mặc định liên tục; một lượt Gemini + nguồn metadata; kết quả thiếu nguồn; định dạng lỗi; khôi phục kết quả không đếm thêm; các test lịch sử, settings và Compose trước đó.

## Chưa xác nhận / bước tiếp
- Cần kiểm chứng trực tuyến bằng khóa Gemini hợp lệ; app hỗ trợ nhập khóa ngay trên thiết bị. Chọn model hỗ trợ Google Search cùng structured output (mặc định gemini-3.8-flash).
- Cần iQOO để kiểm tra quyền chia sẻ, độ trễ, chạy nền, hiển thị thực và cài nâng cấp giữ dữ liệu. Không có thiết bị adb trong phiên.
- Chưa triển khai Worker; nếu dùng máy chủ riêng, cần cập nhật Worker theo nguồn mới. Âm thanh là tính năng tùy chọn riêng, không nằm trong luồng OCR màn hình.
- Không xác nhận độ chính xác thực tế bằng các test mô phỏng. Một API request có thể chạy nhiều tìm kiếm của provider và vẫn chịu quota/chi phí.

## Tiếp tục tiết kiệm hạn mức
- Đọc STATUS và TASKS trước; xem diff liên quan thay vì đọc lại toàn dự án.
- “Báo tiến độ ngắn”: chỉ đọc trạng thái và đối chiếu HEAD, không tự build/test.
- Thực hiện một nhóm việc, kiểm tra liên quan, commit và ghi bằng chứng. Build ký ở mốc bàn giao.
- Đã được Chủ nhân cho phép đồng bộ nhánh và tạo/cập nhật PR. Git CLI không có đăng nhập; dùng kết nối GitHub đã xác thực. Không tự nhập main.
