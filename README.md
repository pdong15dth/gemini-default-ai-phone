# Thay Tiểu V (Jovi / BlueLM) bằng Google Gemini trên Vivo OriginOS nội địa

Gọi thẳng bảng nổi (overlay) của **Google Gemini** bằng cả câu lệnh giọng nói **"Hi Jovi"** lẫn **Nút lối tắt** bên hông máy, trên Vivo chạy OriginOS nội địa.

- Không cần root.
- Không cần MacroDroid / Tasker / app tự động hóa nặng máy.
- Chỉ cần ADB (hoặc Shizuku) và một lần cấu hình.

> Đã chạy thực tế trên máy Vivo OriginOS nội địa qua ADB từ Windows PowerShell. Các bản ROM khác có thể đặt tên gói khác — xem mục [Xử lý sự cố](#xử-lý-sự-cố).

## Mục lục

- [Kết quả đạt được](#kết-quả-đạt-được)
- [Cơ chế hoạt động](#cơ-chế-hoạt-động)
- [Yêu cầu](#yêu-cầu)
- [Bước 1 — Cài app và cấp quyền trên máy](#bước-1--cài-app-và-cấp-quyền-trên-máy)
- [Bước 2 — ADB: chuyển trợ lý mặc định và vá lỗi crash](#bước-2--adb-chuyển-trợ-lý-mặc-định-và-vá-lỗi-crash)
- [Bước 3 — Script chạy ngầm chuyển hướng sang Gemini](#bước-3--script-chạy-ngầm-chuyển-hướng-sang-gemini)
- [Bước 4 — Sau khi khởi động lại máy](#bước-4--sau-khi-khởi-động-lại-máy)
- [Bộ lệnh rút gọn](#bộ-lệnh-rút-gọn)
- [Xử lý sự cố](#xử-lý-sự-cố)
- [Khôi phục về Tiểu V](#khôi-phục-về-tiểu-v)
- [Giới hạn và ghi chú](#giới-hạn-và-ghi-chú)

## Kết quả đạt được

| Thao tác | Trước | Sau |
| --- | --- | --- |
| Hô **"Hi Jovi"** | Tiểu V nhá lên rồi tắt (hoặc crash) | Panel Gemini trồi lên |
| Nhấn giữ **Nút lối tắt** | Không có hành động / mở Tiểu V | Panel Gemini trồi lên |
| Nhấn hai lần Nút lối tắt | Đổi Chuông/Rung | Giữ nguyên |

## Cơ chế hoạt động

```
"Hi Jovi"  ──────► CopilotPanelWindow ──────┐
                                            ├──► gemini.sh ──► keyevent 219 ──► Gemini overlay
Nút lối tắt ─────► ACTION_BUTTON_TO_START_  │
                   FUNCTIONS (kênh events)  │
                                            └─ nếu là CopilotPanelWindow:
                                               keyevent 4 (Back) để đóng panel
```

Ba quyết định quan trọng làm nên cách này:

1. **Chuyển trợ lý mặc định sang Google.** `keyevent 219` (`KEYCODE_ASSIST`) sẽ gọi đúng `GsaVoiceInteractionService` của app Google thay vì Tiểu V.
2. **Không dùng `am force-stop` để tắt Tiểu V.** Force-stop giết luôn dịch vụ nghe giọng nói, khiến "Hi Jovi" chết hẳn ở các lần gọi sau. Chỉ cần bấm Back (`keyevent 4`) để đóng panel.
3. **Vá lỗi crash `NumberFormatException` của Tiểu V khi máy để tiếng Việt.** Đây là lý do panel chỉ "nhá lên 0.5 giây rồi tắt". Chi tiết ở [Bước 2](#bước-2--adb-chuyển-trợ-lý-mặc-định-và-vá-lỗi-crash).

## Yêu cầu

- Máy Vivo chạy OriginOS nội địa (Jovi = Tiểu V = BlueLM = 蓝心小V).
- PC có ADB (Android Platform Tools) và cáp dữ liệu.
  - Windows: `winget install Google.PlatformTools`
  - macOS: `brew install --cask android-platform-tools`
- Đã bật **Tùy chọn nhà phát triển → Gỡ lỗi USB** trên điện thoại.
- Đã cài từ Play Store: **Google** (`com.google.android.googlequicksearchbox`) và **Google Gemini** (`com.google.android.apps.bard`).

> Toàn bộ thao tác dưới đây chỉ đổi cấu hình trên tài khoản người dùng hiện tại, không sửa phân vùng system. Vẫn nên sao lưu dữ liệu quan trọng trước khi vọc.

## Bước 1 — Cài app và cấp quyền trên máy

OriginOS kiểm soát chạy ngầm rất gắt. Thiếu quyền thì panel Gemini sẽ hiện lên rồi tự tắt.

1. Mở app **Gemini**, làm theo hướng dẫn để đặt Gemini làm trợ lý chính bên trong hệ sinh thái Google.
2. Vào **Cài đặt → Ứng dụng → Thông tin ứng dụng**, tìm **Google** và **Gemini**, bật đủ 4 mục cho **cả hai app**:

   | Quyền | Giá trị |
   | --- | --- |
   | Tự khởi chạy (Autostart) | Bật |
   | Khởi chạy liên kết (Associated startup) | Bật |
   | Cửa sổ nổi (Floating window) | Bật |
   | Pin (Battery) | Không hạn chế (Don't restrict background usage) |

3. Bật sẵn tín hiệu gốc của Vivo để mượn sự kiện:
   - **Giọng nói:** Cài đặt → Tiểu V (Jovi / 蓝心小V) → Đánh thức bằng giọng nói → bật **"Hi Jovi"**.
   - **Nút cứng:** Cài đặt → Nút lối tắt → mục **Nhấn và giữ** chọn **Không có hành động nào**. Mục **Nhấn hai lần** giữ nguyên **Chuông/Rung**.

## Bước 2 — ADB: chuyển trợ lý mặc định và vá lỗi crash

Cắm cáp, mở PowerShell (Windows) hoặc Terminal (macOS), chạy lần lượt.

### 1. Kiểm tra kết nối

```bash
adb devices
```

Trên điện thoại sẽ hiện hộp thoại **Cho phép gỡ lỗi USB?** — tích "Luôn cho phép từ máy tính này" rồi đồng ý. Kết quả đúng phải có chữ `device`:

```
List of devices attached
10AFA20AJT0027X    device
```

Nếu hiện `unauthorized`, bấm OK trên điện thoại rồi chạy lại `adb devices`.

### 2. Ép hệ thống nhận Google/Gemini làm trợ lý mặc định

```bash
adb shell settings put secure assistant com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService
```

```bash
adb shell settings put secure voice_interaction_service com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService
```

Chạy xong không báo gì là thành công. Kiểm tra lại:

```bash
adb shell settings get secure assistant
```

Kết quả đúng phải trả về đúng chuỗi `com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService`.

### 3. Mở quyền chạy ngầm cho Jovi (để "Hi Jovi" còn nghe được)

```bash
adb shell cmd appops set com.vivo.agent RUN_IN_BACKGROUND allow
```

```bash
adb shell cmd appops set com.vivo.agent RUN_ANY_IN_BACKGROUND allow
```

### 4. Vá lỗi crash khiến panel chỉ nhá lên 0.5 giây

Trên bản ROM nội địa để ngôn ngữ **Tiếng Việt**, hiệu ứng viền sáng của Tiểu V xuất ra số thập phân dùng dấu phẩy (ví dụ `0,81`). Code của Vivo đọc bằng `Float.parseFloat()` nên ném `NumberFormatException`, app `com.vivo.ai.copilot` crash và bị hệ thống bật lại liên tục — đó chính là hiện tượng "sáng lên rồi tắt ngay".

Cách xử lý: chỉ đổi locale riêng của app Tiểu V sang `en-US`, toàn bộ máy vẫn giữ tiếng Việt.

```bash
adb shell cmd locale set-app-locales com.vivo.ai.copilot --locales en-US
```

### 5. Test nhanh

```bash
adb shell input keyevent 219
```

Panel Gemini phải trồi lên từ cạnh dưới màn hình và đứng yên. Nếu nó nhá lên rồi tắt, quay lại kiểm tra 4 quyền ở Bước 1.

## Bước 3 — Script chạy ngầm chuyển hướng sang Gemini

Script lắng nghe kênh **events** của logcat, bắt hai tín hiệu:

| Tín hiệu | Khi nào | Hành động |
| --- | --- | --- |
| `CopilotPanelWindow` | Hô "Hi Jovi" | `keyevent 4` đóng panel, đợi 0.3s, rồi `keyevent 219` |
| `ACTION_BUTTON_TO_START_FUNCTIONS` | Nhấn giữ Nút lối tắt | `keyevent 219` ngay |

Nội dung script (có sẵn tại [`scripts/gemini.sh`](scripts/gemini.sh)):

```sh
while true; do
  logcat -b events -c
  logcat -b events -e CopilotPanelWindow\|ACTION_BUTTON_TO_START_FUNCTIONS -m 1 | grep -q CopilotPanelWindow && input keyevent 4
  sleep 0.3
  input keyevent 219
  sleep 1.5
done
```

Dấu `\|` là cố ý: shell sẽ bỏ dấu `\` và truyền regex `CopilotPanelWindow|ACTION_BUTTON_TO_START_FUNCTIONS` cho `logcat`, đồng thời không bị hiểu nhầm thành pipe.

### Cách A — Tạo file trực tiếp bằng ADB (đã test)

```powershell
adb shell "printf '%s\n' 'while true; do' 'logcat -b events -c' 'logcat -b events -e CopilotPanelWindow\|ACTION_BUTTON_TO_START_FUNCTIONS -m 1 | grep -q CopilotPanelWindow && input keyevent 4' 'sleep 0.3' 'input keyevent 219' 'sleep 1.5' 'done' > /data/local/tmp/gemini.sh"
```

### Cách B — Đẩy file có sẵn trong repo

```bash
adb push scripts/gemini.sh /data/local/tmp/gemini.sh
```

### Kích hoạt chạy ngầm

Dọn tiến trình cũ (nếu có), rồi chạy script bằng `nohup` để rút cáp vẫn sống:

```bash
adb shell "pkill -9 -f gemini.sh; pkill -9 -f logcat"
```

```bash
adb shell "nohup sh /data/local/tmp/gemini.sh </dev/null >/dev/null 2>&1 &"
```

Kiểm tra:

```bash
adb shell "ps -ef | grep -v grep | grep gemini.sh"
```

Thấy một dòng dạng `shell 2396 1 ... sh /data/local/tmp/gemini.sh` là xong, rút cáp được.

> **Đừng chạy `pkill` sau bước này.** Lệnh `pkill -9 -f 'while'` sẽ giết luôn script vừa bật, và đó là lý do phổ biến khiến "cài xong mà không thấy Gemini đâu".

## Bước 4 — Sau khi khởi động lại máy

Không root nên tiến trình `nohup` chỉ nằm trong RAM. File `/data/local/tmp/gemini.sh` vẫn còn, chỉ cần cắm cáp chạy lại một dòng:

```bash
adb shell "nohup sh /data/local/tmp/gemini.sh </dev/null >/dev/null 2>&1 &"
```

Muốn không phải cắm cáp mỗi lần reboot, xem [Giới hạn và ghi chú](#giới-hạn-và-ghi-chú).

## Bộ lệnh rút gọn

Dán lần lượt từng khối vào PowerShell/Terminal sau khi đã cấp đủ quyền ở Bước 1.

```bash
adb shell settings put secure assistant com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService
```

```bash
adb shell settings put secure voice_interaction_service com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService
```

```bash
adb shell cmd appops set com.vivo.agent RUN_IN_BACKGROUND allow
adb shell cmd appops set com.vivo.agent RUN_ANY_IN_BACKGROUND allow
adb shell cmd locale set-app-locales com.vivo.ai.copilot --locales en-US
```

```powershell
adb shell "printf '%s\n' 'while true; do' 'logcat -b events -c' 'logcat -b events -e CopilotPanelWindow\|ACTION_BUTTON_TO_START_FUNCTIONS -m 1 | grep -q CopilotPanelWindow && input keyevent 4' 'sleep 0.3' 'input keyevent 219' 'sleep 1.5' 'done' > /data/local/tmp/gemini.sh"
```

```bash
adb shell "pkill -9 -f gemini.sh; pkill -9 -f logcat"
adb shell "nohup sh /data/local/tmp/gemini.sh </dev/null >/dev/null 2>&1 &"
adb shell "ps -ef | grep -v grep | grep gemini.sh"
```

## Xử lý sự cố

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Hô "Hi Jovi" thấy viền sáng nhá lên 0.5s rồi tắt, Gemini không lên | Crash `NumberFormatException` do locale tiếng Việt | Chạy `adb shell cmd locale set-app-locales com.vivo.ai.copilot --locales en-US` |
| Jovi tắt rồi mà Gemini không hiện | Script không chạy, hoặc đã bị `pkill` | `adb shell "ps -ef \| grep gemini.sh"`, nếu trống thì chạy lại lệnh `nohup` |
| Gọi "Hi Jovi" lần đầu được, lần sau không nghe nữa | Đã dùng `am force-stop` giết dịch vụ nghe | Bỏ force-stop, dùng `keyevent 4`; mở app Tiểu V một lần để đánh thức lại |
| Rút cáp là mất | Chưa chạy `nohup`, hoặc mới reboot | Chạy lại [Bước 4](#bước-4--sau-khi-khởi-động-lại-máy) |
| Bấm Nút lối tắt không có phản ứng | Sai tên sự kiện hoặc sai kênh log | Soi log: `adb shell "logcat -c; logcat -b all \| grep -iE 'shortcut\|START u0\|KeyEvent'"` |
| PowerShell báo `The module 'ACTION_BUTTON_TO_START_FUNCTIONS' could not be loaded` | Dấu `\|` trong chuỗi `"..."` bị PowerShell hiểu là lệnh | Dùng [Cách B](#cách-b--đẩy-file-có-sẵn-trong-repo) để đẩy file `.sh` thay vì nhồi cả script vào một dòng |
| `Failure [not installed for 0]` | Bản ROM không có gói đó | Bỏ qua, tùy máy dùng `com.vivo.ai.copilot` hoặc `com.vivo.agent` |
| `Failure [only root can delete system app for a particular user]` | Vivo khóa gỡ app hệ thống | Bỏ qua, không cần gỡ Tiểu V vẫn dùng được |
| `package not found` khi `pm grant` | App chưa được cài | Cài app đó trước rồi chạy lại |

Muốn biết chính xác gói nào đang mở panel, hô "Hi Jovi" rồi chạy:

```bash
adb shell "logcat -c; logcat -b all -v brief | grep -iE 'vivo\.agent|copilot|vtrigger|wakeup|START u0'"
```

## Khôi phục về Tiểu V

Dừng script:

```bash
adb shell "pkill -9 -f gemini.sh; pkill -9 -f logcat"
```

Trả trợ lý mặc định về Tiểu V: vào **Cài đặt → Ứng dụng → Ứng dụng mặc định → Ứng dụng trợ lý kỹ thuật số**, chọn Tiểu V / Jovi (hoặc chọn lại trong app Tiểu V).

Trả ngôn ngữ app: **Cài đặt → Ứng dụng → Tiểu V → Ngôn ngữ → Theo hệ thống**.

Nếu đã lỡ gỡ Tiểu V bằng `pm uninstall -k --user 0`, cài lại:

```bash
adb shell cmd package install-existing com.vivo.ai.copilot
```

```bash
adb shell cmd package install-existing com.vivo.agent
```

## Giới hạn và ghi chú

- **Không root thì script mất sau reboot.** File `gemini.sh` vẫn còn, chỉ cần chạy lại một dòng ở Bước 4. Các hướng "dính vĩnh viễn" (AccessibilityService tự build, Shizuku/Brevent, MacroDroid + quyền `READ_LOGS`) đều phải cài thêm app hoặc build APK — đánh đổi sạch máy lấy sự tiện lợi.
- **Không nên `pm uninstall` app hệ thống** nếu không cần. Trên bản ROM khóa, lệnh này thường fail; và kể cả khi thành công thì cũng chỉ cần thiết khi bạn muốn Tiểu V biến mất hoàn toàn.
- **Không dùng `am force-stop com.vivo.ai.copilot`** trong vòng lặp. Nó giết luôn service nhận giọng nói, làm "Hi Jovi" chết dần.
- Cách này phụ thuộc vào tên sự kiện logcat của ROM. Lên bản OriginOS khác, tên có thể đổi — dùng mục soi log ở trên để cập nhật lại script.
- Thủ thuật cá nhân, không liên quan đến Vivo hay Google. Tự chịu trách nhiệm với máy của mình.

## Cấu trúc repo

```
.
├── README.md
└── scripts/
    └── gemini.sh
```
