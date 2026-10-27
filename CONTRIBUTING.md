# Đóng góp

Dự án ưu tiên hai thứ: app không đọc nội dung màn hình, và profile ROM phải được thử trên máy thật.

## Gửi lỗi

1. Mở app → **Nhật ký** → **Xuất log zip**.
2. Dùng mẫu [Gửi log chẩn đoán](https://github.com/pdong15dth/gemini-default-ai-phone/issues/new?template=diagnostics.yml).
3. Ghi rõ máy, ROM, phiên bản Gemini và hiện tượng. Không dán ảnh có nội dung cá nhân.

## Gửi profile ROM mới

Profile nằm ở `android/app/src/main/assets/profiles/`. Mỗi hãng một file JSON theo schema trong `docs/spec-app.md`.

Yêu cầu tối thiểu:

- `trigger.package` và `trigger.class_regex` khớp cửa sổ trợ lý gốc.
- `target.packages` là các gói Gemini/GSA thực tế trên máy.
- `provisioning` chỉ chứa lệnh trong whitelist của spec, không có lệnh shell tùy ý.
- Kèm log chẩn đoán trước/sau khi chuyển hướng thành công trên máy thật.

## Build và thử

Xem [docs/build-va-deploy.md](docs/build-va-deploy.md).

## Quy ước code

- Kotlin + Jetpack Compose, không thêm SDK quảng cáo/telemetry.
- Không đọc `AccessibilityNodeInfo.text` hay chụp màn hình.
- Log không chứa nội dung màn hình, tên riêng, số điện thoại.
- Pull request được hiểu là đóng góp theo MIT.
