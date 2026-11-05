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

## Đợt 3.1.0 — đơn giản hóa luồng màn hình
- [x] T11: đọc OCR liên tục mặc định 3 giây, tách khỏi thời gian chờ Gemini; chỉ giữ nội dung mới nhất trong hàng chờ.
- [x] T12: một lượt gọi Gemini có Google Search + structured output; nhận đúng/sai/chưa đủ bằng chứng cùng giải thích và nguồn metadata. Test backend xác nhận đúng 1 request.
- [x] T13: hiển thị cả kết quả đúng/sai và đường dẫn mở được trên nút nổi; không lấy URL do model tự tạo trong JSON.
- [x] T14: kiểm tra bổ sung: 21 Android tests, 16 backend tests, lintDebug và signed assembleRelease; vẫn chưa phải nghiệm thu trên thiết bị.
- [ ] T15: thử trên iQOO: đổi nội dung khi Gemini đang xử lý, không gửi lại màn hình đứng yên, tạm dừng/hủy, đọc đúng/sai/nguồn và kiểm tra quota thực tế.
- [ ] T16: dùng khóa hợp lệ chạy một lượt kiểm chứng thật, xác nhận model hỗ trợ Search cùng JSON và mở nguồn đối chiếu. Cập nhật Worker nếu dùng backend riêng.

## Đợt 3.1.1 — phản hồi lỗi hạn mức và bỏ Fact Check Tools
- [x] T17: phân loại 429 theo QuotaFailure/RetryInfo, tuân thủ thời gian chờ; quota ngày/0/không rõ thì tạm dừng gửi tự động, không tiếp tục gọi mỗi 30 giây.
- [x] T18: bỏ Fact Check Tools khỏi nguồn kiểm chứng mới và liên kết hồ sơ cũ; prompt yêu cầu Gemini tìm bài gốc, không chuyển hướng tra cứu thay kết luận.
- [x] T19: 28 Android tests, 20 backend tests; kiểm tra các loại quota, thông tin bí mật không lọt vào thông báo, chờ tăng dần và chặn nguồn không muốn dùng.
- [ ] T20: xác định quota thật bằng bản mới trên thiết bị và chạy một lần kiểm chứng có nguồn sau khi project/model có quota. Không thể sửa quota tài khoản chỉ bằng APK.

## Đợt 3.2.0 — nhận định Đúng/Sai không Search
Yêu cầu mới thay thế yêu cầu tìm nguồn ở các mốc trước.
- [x] T21: thay request Gemini bằng model-only, bỏ Search/tools và nguồn; binary verdict DUNG/SAI với giải thích.
- [x] T22: cập nhật engine/UI/popup, nhãn chưa đối chiếu nguồn, không yêu cầu references để nhận Đúng/Sai; giữ lịch sử cũ.
- [x] T23: thêm test cho contract không Search và sửa test UI/backend liên quan.
- [ ] T24: chạy CI và xử lý lỗi; chưa có kết quả test của 3.2.0.
- [ ] T25: build APK ký 3.2.0 khi môi trường build trở lại, đối chiếu khóa cũ, bàn giao và thử trên iQOO.
