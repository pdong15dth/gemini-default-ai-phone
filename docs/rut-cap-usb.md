# Sửa lỗi rút cáp USB là mất tác dụng (Vivo OriginOS)

Script [`gemini.sh`](../scripts/gemini.sh) chạy tốt khi còn cắm cáp nhưng rút cáp ra là "Hi Jovi" không còn phản ứng. Trước khi rút, `ps -ef` vẫn thấy tiến trình:

```text
shell  759  1  ...  sh /data/local/tmp/gemini.sh
```

PPID = 1 nghĩa là tiến trình đã tách khỏi phiên `adb shell`, nhưng trên một số bản OriginOS mới như vậy vẫn chưa đủ.

## Nguyên nhân

1. **OriginOS tắt `adbd` và dọn cgroup của shell khi rút cáp.** Tiến trình tuy có PPID = 1 nhưng vẫn nằm trong cgroup UID 2000 do `adbd` quản lý. Rút cáp vật lý, OriginOS dừng hoặc khởi động lại `adbd` và giết sạch tiến trình con.
2. **`logcat` bên trong vòng lặp dính SIGHUP.** `nohup` chỉ bảo vệ tiến trình `sh` bên ngoài; `logcat` chạy trong vòng lặp không có gì chặn tín hiệu ngắt khi cổng USB đóng đột ngột, và cả script chết theo.

## Cách khắc phục

Làm trực tiếp trên Terminal (macOS) hoặc PowerShell (Windows).

### Bước 1 — Giữ `adbd` sống khi rút cáp

```bash
adb tcpip 5555
```

Lệnh này chuyển `adbd` sang chế độ nghe cổng 5555. Rút cáp, `adbd` vẫn hoạt động nên OriginOS không dọn tiến trình ngầm. Đợi khoảng 2 giây trước khi làm bước 2.

> Chỉ bật trên Wi-Fi bạn tin cậy: cổng 5555 mở cho cả mạng LAN. Quay lại chế độ USB bằng `adb usb`. Sau mỗi lần reboot phải chạy lại `adb tcpip 5555`.

### Bước 2 — Thêm `trap` chặn tín hiệu ngắt vào `gemini.sh`

```powershell
adb shell "printf '%s\n' 'trap \"\" HUP INT TERM' 'while true; do' 'logcat -b events -c' 'logcat -b events -e CopilotPanelWindow\|ACTION_BUTTON_TO_START_FUNCTIONS -m 1 | grep -q CopilotPanelWindow && input keyevent 4' 'sleep 0.5' 'input keyevent 224' 'sleep 0.5' 'input keyevent 231' 'sleep 1.5' 'done' > /data/local/tmp/gemini.sh"
```

Hoặc nếu đang có repo trong máy tính, đẩy thẳng file mới (đã có sẵn dòng `trap`):

```bash
adb push scripts/gemini.sh /data/local/tmp/gemini.sh
```

Dòng `trap "" HUP INT TERM` khiến `sh` bỏ qua tín hiệu ngắt. Tiến trình con (`logcat`, `input`) thừa hưởng trạng thái bỏ qua này nên không chết khi cổng USB đóng.

### Bước 3 — Chạy ngầm bằng `setsid`

```bash
adb shell "pkill -9 -f gemini.sh; pkill -9 -f logcat"
adb shell "setsid sh /data/local/tmp/gemini.sh </dev/null >/dev/null 2>&1 &"
```

`setsid` tách script sang session độc lập, không còn dính với phiên `adb shell`, nên đóng cổng USB không gửi được SIGHUP tới nó.

Kiểm tra:

```bash
adb shell "ps -ef | grep -v grep | grep gemini.sh"
```

Đúng phải thấy một dòng `sh /data/local/tmp/gemini.sh` với PPID = 1.

Muốn kiểm tra sau khi đã rút cáp, kết nối lại qua Wi-Fi:

```bash
adb shell ip route
adb connect <IP-của-máy>:5555
adb shell "ps -ef | grep -v grep | grep gemini.sh"
```

Hô "Hi Jovi" sau khi rút cáp để xác nhận. Máy thiếu `setsid` (Android cũ) thì dùng lại `nohup`, vẫn giữ được `trap` ở bước 2.

## Lưu ý trên điện thoại

- **Tùy chọn nhà phát triển → Tắt giám sát quyền (Disable permission monitoring):** bật nếu máy có mục này; nó giảm việc hệ thống thu hồi quyền/tiến trình khi cắm rút cáp.
- **Thông báo "Đã kết nối gỡ lỗi USB":** chỉ vuốt bỏ thông báo. Bấm vào đó là tắt Gỡ lỗi USB và script mất kết nối.
- Sau khi reboot, `adbd` trở về chế độ USB và script mất (không root): xem [Bước 4](../HUONG-DAN-SCRIPT-ADB.md#buoc-4-sau-khi-khoi-dong-lai-may) trong hướng dẫn script ADB, sau đó chạy lại `adb tcpip 5555` nếu vẫn cần rút cáp.
