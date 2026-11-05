# LTM FactCheck — công việc
Mục tiêu: app kiểm chứng hoạt động thật, có nguồn đối chiếu; giao diện iOS Liquid Glass.
Quy ước: [x] chỉ khi có bằng chứng; việc báo cáo trước nhưng chưa đối chiếu vẫn để [ ].

## Mốc 0 — xác định đúng nguồn
- [x] T00: thiết lập STATUS.md và TASKS.md, ghi mốc repository và phân biệt báo cáo cũ với kết quả đã xác nhận.
- [x] T01: tìm mã nguồn của đợt chỉnh sửa gần nhất và đối chiếu main 9e0e5558e1e0ba0746d1e65ed21fa349bf955004. Hoàn thành khi ghi được commit/nhánh chứa thay đổi; nếu không tìm thấy, ghi rõ phần thiếu trước khi thực hiện lại.
- [x] T02: đối chiếu APK 2.1.0 với nguồn, version và chứng thư ký nếu có artifact. Ghi bằng chứng hoặc đánh dấu chưa xác định; giữ khóa ký hiện có.

## Mốc 1 — kiểm chứng thật
- [x] T03: rà luồng nhập nội dung → dịch vụ tìm nguồn → kết luận có dẫn chứng. Hoàn thành khi có kiểm thử cho có nguồn, thiếu nguồn và lỗi dịch vụ; không trả kết luận mẫu như kết quả thật.
- [ ] T04: kiểm thử trực tuyến bằng cấu hình hợp lệ; ghi commit, môi trường và kết quả. Nếu thiếu cấu hình thì ghi chặn, không báo đã đạt.
- [ ] T05: kiểm tra lưu khóa, lịch sử/migration, chat theo hồ sơ và xử lý hủy/lỗi; chỉ sửa và kiểm thử phần liên quan.

## Mốc 2 — trải nghiệm
- [ ] T06: kiểm tra Liquid Glass, sáng/tối, bàn phím, cuộn và vùng an toàn trên các màn chính; ghi màn/thiết bị đã kiểm tra.
- [ ] T07: kiểm tra chữ/ảnh/âm thanh và quyền chiếu màn hình theo tính năng hiện có; ghi ca lỗi và kết quả.

## Mốc 3 — bàn giao
- [x] T08: chạy kiểm tra tổng thể phù hợp; ghi lệnh, kết quả và commit. Không suy ra test đã đạt từ việc build thành công.
- [x] T09: tăng phiên bản phù hợp, build APK ký bằng khóa hiện có; ghi SHA-256, versionCode/versionName và commit nguồn.
- [ ] T10: kiểm tra cài/nâng cấp giữ dữ liệu trên thiết bị hoặc môi trường có ghi rõ; nêu giới hạn chưa kiểm tra.

## Quy trình mỗi đợt
1. Chọn một nhóm việc liên quan, ghi mục đang làm trong STATUS.
2. Đọc/sửa đúng phạm vi; chạy kiểm tra cần thiết.
3. Commit và cập nhật STATUS với bằng chứng, đánh dấu TASKS.
4. Báo ngắn: đã xong / kiểm tra / còn vướng / bước tiếp.

## Bằng chứng đợt 3.0.1
- T01: tìm lại 2004b41, hợp nhất tại 44c96e6; sửa mới c814856.
- T02: đối chiếu APK 2.1.0 và chứng thư ký; chưa có commit nguồn của APK cũ, đã ghi rõ trong STATUS.
- T03: 16 test backend + 14 test Android, gồm nguồn thiếu/hợp lệ, ID không hợp lệ, lỗi provider; không phải kết quả live.
- T08: testDebugUnitTest + lintDebug + assembleRelease thành công; wrangler dry-run thành công.
- T09: 3.0.1/30001; chứng thư khớp 2.1.0; SHA-256 APK trong STATUS.
- T05 mới kiểm tra settings monitoring, round-trip dữ liệu và import lịch sử bằng test; chưa nghiệm thu toàn bộ Keystore/chat trên thiết bị.
- T06 có Compose smoke tests sáng/tối; chưa kiểm tra ảnh giao diện thực.
- T04, T07, T10 vẫn cần cấu hình/thiết bị; không đánh dấu hoàn tất.
