# AI Key Bridge — quy tắc ưu tiên cao nhất

**Mục đích duy nhất của app:** hô "Hi Jovi" (hoặc bấm nút AI) thì hiện **lớp phủ Gemini AI** (overlay đang nghe) thay cho lớp phủ Tiểu V/Jovi. Không phải mở app Gemini toàn màn hình, không phải Google Search, không phải để Jovi và Gemini cùng hiện.

**Điều kiện phải đúng trong mọi trường hợp**, kể cả khi:

- đã tắt "Gỡ lỗi USB", tắt công tắc tổng "Tuỳ chọn nhà phát triển" (bắt buộc để dùng app ngân hàng);
- Shizuku không chạy;
- app bị kill (vuốt khỏi đa nhiệm, hệ thống dọn RAM, khởi động lại máy, cập nhật app);
- màn hình tắt / đang khoá / đang ở app khác / lớp phủ Gemini đang mở sẵn.

Bất kỳ trường hợp nào "Hi Jovi" không ra lớp phủ Gemini (hoặc Jovi vẫn hiện đè) là **app chưa đúng**, không được báo là xong.

## Cách làm việc

- Mỗi thay đổi phải thử **đủ ma trận trường hợp** ở trên trên máy thật, không chỉ trường hợp suôn sẻ. Ghi rõ trường hợp nào đã thử, trường hợp nào chưa.
- Không âm thầm đổi mục tiêu (ví dụ mở app Gemini thay cho lớp phủ). Nếu một trường hợp không làm được với Android hiện tại, nói thẳng giới hạn và hỏi người dùng trước khi chọn cách thay thế.
- **Không commit, không tạo PR, không tạo release/tag** khi người dùng chưa xác nhận đã sửa xong và chưa tự yêu cầu. Thử trên máy, báo kết quả, rồi chờ.
- Trả lời bằng tiếng Việt.
