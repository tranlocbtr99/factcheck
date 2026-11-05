# LTM FactCheck — tiến độ
Cập nhật: 05/11/2026 (giờ Việt Nam).

## Mốc mã nguồn
- Nhánh triển khai: feat/complete-grounded-verification. Đã đồng bộ lên GitHub tại commit 58c9d169601dfb16fc221a697111f3cb6832ea8d; PR #1: https://github.com/tranlocbtr99/factcheck/pull/1. main giữ nguyên đến khi PR được chấp thuận.
- Đã tìm lại commit 2004b41 trong workspace phiên trước, hợp nhất với main tại 44c96e6. Hai nhánh trên GitHub lúc kiểm tra đều chưa chứa đợt sửa đó.
- Commit sửa lõi mới: c814856. Phiên bản 3.0.1, versionCode 30001, applicationId vn.ltm.factcheck.
- APK 2.1.0 đã đối chiếu package và chứng thư. Chưa xác định được commit nguồn của APK cũ; không suy diễn nó trùng main cũ.

## Đã xong
- Khôi phục mã 3.0.0: dịch vụ kiểm chứng có tìm nguồn, nhập chữ/ảnh/âm thanh, chat theo hồ sơ, Keystore, migration lịch sử, UI Liquid Silver sáng/tối.
- 3.0.1: kiểm tra chặt định dạng kết luận, giải thích và ID nguồn; không chấp nhận ID chuỗi/phân số như ID hợp lệ; bỏ phần thought khỏi nội dung trả lời; lọc URL nguồn có thông tin đăng nhập.
- Không có nguồn liên kết thì trả CHUA_DU_BANG_CHUNG, bỏ lần gọi AI tổng hợp thứ hai; không tạo kết luận mẫu.
- Backend xử lý JSON upstream lỗi bằng 502; hỏi đáp chấp nhận hồ sơ dài đến 200.000 ký tự thay vì 24.000.
- Workflow bỏ qua thay đổi chỉ có tài liệu và hủy run cũ cùng nhánh để giảm kiểm tra lặp.

## Bằng chứng kiểm tra
- Android: testDebugUnitTest, lintDebug, assembleRelease đều thành công. 14 tests, 0 failures/errors; Lint 0 errors, 41 warnings. Có kiểm tra Compose sáng/tối và điều hướng, không phải kiểm tra ảnh chụp giao diện.
- Backend: node --test, 16/16 pass; wrangler deploy --dry-run thành công. Test provider dùng phản hồi mô phỏng.
- APK LTMFactCheck-3.0.1.apk; SHA-256 b5e9f59d07e2ad7e7fde3585ec14b5bb5b4e604c7757c18065c1e4c5d9e86264.
- apksigner xác nhận chứng thư SHA-256 603bef85b172df9d535a0ef70fe31f1e65356082fc971cbf2ee13caa689a495a, khớp 2.1.0. aapt xác nhận package/version/minSdk 24/targetSdk 36.
- Báo cáo chi tiết: docs/VALIDATION.md. Không lưu khóa hoặc mật khẩu trong git.

## Còn vướng / bước tiếp
- T04: chưa có cấu hình AI hợp lệ để gọi trực tuyến. Có thể nhập khóa Gemini cá nhân trong Cài đặt trên điện thoại hoặc cấu hình Worker riêng. Kiểm tra kết nối không thay thế một lần kiểm chứng thật có nguồn.
- T06/T07/T10: cần thiết bị để kiểm tra hình ảnh UI, cài nâng cấp giữ dữ liệu, quyền chiếu màn hình, âm thanh và chạy nền trên iQOO. Không có thiết bị adb trong phiên này.
- Chưa triển khai backend, chưa chứng minh độ chính xác bằng tập dữ liệu gán nhãn. Không báo app đã nghiệm thu toàn bộ.
- Bước tiếp theo: theo dõi kiểm tra của PR #1; thực hiện một lần kiểm chứng trực tuyến có nguồn, kiểm tra cập nhật từ 2.1.0 trên thiết bị và mở nguồn gốc đối chiếu.

## Quy trình tiết kiệm hạn mức
- Đọc STATUS và TASKS trước; kiểm tra HEAD và diff liên quan khi khác mốc. Không rà toàn dự án mỗi lần.
- “Báo tiến độ ngắn”: chỉ báo đã xong / kiểm tra / còn vướng / bước tiếp, không tự build/test lại.
- “Tiếp tục theo STATUS”: thực hiện một nhóm việc liên quan, commit rồi ghi bằng chứng và bước tiếp.
- Chỉ chạy lại kiểm tra liên quan sau thay đổi; kiểm tra tổng thể/build ký ở mốc bàn giao hoặc khi có lỗi mới.
- Chủ nhân đã xác nhận cho phép đẩy nhánh và tạo PR. Git CLI không có thông tin đăng nhập; sử dụng kết nối GitHub đã xác thực. Commit mã kiểm thử cục bộ: c814856; bản đồng bộ có thể dùng commit mới nhưng giữ nguyên nội dung mã.

- APK đã lưu thành công: library_file_id libfile_f4ba765319348191aede6dd0d5e1ce57.
