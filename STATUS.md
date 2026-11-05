# LTM FactCheck — tiến độ
Cập nhật: 05/11/2026 (giờ Việt Nam).

## Mốc đã đối chiếu
- Nhánh: main; commit mã nguồn: 9e0e5558e1e0ba0746d1e65ed21fa349bf955004.
- Commit mô tả bổ sung phiên âm âm thanh và hỗ trợ chiếu màn hình; chưa xác nhận hành vi chạy thực tế trong phiên thiết lập này.
- app/build.gradle.kts tại commit trên: versionName 1.0, versionCode 1.
- Đã tạo STATUS.md và TASKS.md để tiếp tục công việc theo từng đợt.
- Phiên này chỉ thay đổi tài liệu; không build APK, không chạy kiểm thử ứng dụng.

## Báo cáo trước đó — cần đối chiếu, chưa coi là bằng chứng nghiệm thu
- Hội thoại trước báo đã sửa lõi tìm nguồn, Keystore, migration, UI Liquid Glass, nhập chữ/ảnh/âm thanh, lịch sử, chat theo hồ sơ; báo đã build debug và qua 10 test backend.
- Chưa có commit và log tương ứng để xác nhận các kết quả trên nằm trong main.
- Hội thoại ghi nhận đã gửi APK 2.1.0; chưa xác định commit nguồn và chữ ký của APK. Không đồng nhất APK này với version 1.0 trong repository.
- Trước đó báo thiếu API key để kiểm thử trực tuyến và vướng kiểm thử Android; cần kiểm tra lại khi bắt đầu đợt liên quan. Không ghi khóa hay mật khẩu vào tài liệu.

## Việc đang làm / bước tiếp theo
- Đợt kế tiếp: T01 trong TASKS.md — xác định và đồng bộ đúng mã nguồn của đợt sửa gần nhất trước khi sửa tiếp.
- Chưa đủ bằng chứng xác nhận app hoàn thiện hoặc luồng kiểm chứng trực tuyến chạy thành công.

## Cách tiếp tục tiết kiệm hạn mức
- Đọc STATUS.md và TASKS.md trước; chỉ đọc mã và diff liên quan đến mục đang làm.
- “Báo tiến độ ngắn”: đọc hai file, kiểm tra nhanh HEAD; báo đã xong / kiểm tra / còn vướng / bước tiếp. Không tự build hay chạy toàn bộ test.
- Nếu HEAD khác mốc đã ghi, chỉ xem tên file và diff từ mốc đó; cập nhật trạng thái có bằng chứng.
- “Tiếp tục theo STATUS”: thực hiện mục ưu tiên đầu tiên chưa xong, gom lỗi liên quan thành một đợt.
- Sau mỗi đợt: commit mã, ghi SHA, tệp liên quan, lệnh kiểm tra và kết quả, vướng mắc, bước tiếp theo. Phân biệt đã viết mã / đã build / đã kiểm thử / đã chạy thiết bị.
- Chạy kiểm tra liên quan sau sửa; kiểm tra tổng thể và APK ký tại mốc bàn giao. Chỉ lặp lại khi có thay đổi, lỗi hoặc bằng chứng mới.
- Giữ tài liệu ngắn; không lưu log dài hay toàn bộ hội thoại. Dẫn đường dẫn log/CI khi có.
