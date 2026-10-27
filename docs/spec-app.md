# Spec dự án — AI Assistant Remapper cho ROM nội địa

Tên tạm: **AI Key Bridge**. Không dùng "Gemini", "Jovi", "Vivo" trong tên app/icon — chỉ nhắc tới như tên sản phẩm của bên thứ ba trong phần mô tả.

> **Trạng thái triển khai 1.0 (V1):** mã nguồn mở MIT, không Pro, không quảng cáo, không Play Billing. Đã có: onboarding 3 bước, checklist quyền, Provisioner qua Shizuku/ADB (lệnh ghép theo đích + hãng), AccessibilityService chuyển hướng, tự bật Micro, màn hình khóa, **profile đa hãng** (Vivo/iQOO đã kiểm chứng; Xiaomi, OPPO/OnePlus/Realme, Honor/Huawei, Nubia ở mức beta), tự nhận diện hãng + chọn tay, **dò cửa sổ trợ lý** (trigger sniffer), **chọn đích** (Gemini, ChatGPT Voice beta, Claude beta, app tuỳ chọn), tinh chỉnh độ trễ/timeout, chống trùng, lịch sử lượt chạy 7 ngày, kiểm tra thay đổi ROM theo fingerprint, chẩn đoán/xuất log, khôi phục mặc định. Gemini mặc định mở thẳng app (không cần đổi trợ lý mặc định, không cần Shizuku); lớp phủ nổi qua keyevent khi có Shizuku + Google là trợ lý mặc định. Chế độ nút cứng B đọc event log bằng READ_LOGS (cấp qua Shizuku/ADB), có Dò log để lưu mẫu. Đích thêm Claude (beta). Hai cách dùng: Đầy đủ (Trợ năng thay “Hi Jovi”, tự bật Micro) hoặc Cấu hình một lần (không Trợ năng, gán phím/chạm mặt lưng/Khởi chạy nhanh vào lối tắt “Gọi AI”). Onboarding: Máy của bạn → Cách dùng → Cấp quyền/Kích hoạt → Gán phím. Trên OriginOS lần đầu mở app khác sẽ có hộp “muốn mở … → Luôn mở”. Không khoá tính năng nào. Chưa làm: remote config có ký Ed25519, gán nhấn đúp nút cứng.

## Mục lục

- [Tóm tắt](#tom-tat)
- [4 rủi ro có thể giết dự án](#4-rui-ro-co-the-giet-du-an)
- [Vấn đề và người dùng](#van-de-va-nguoi-dung)
- [Phạm vi](#pham-vi)
- [Trải nghiệm chính](#trai-nghiem-chinh)
- [Kiến trúc kỹ thuật](#kien-truc-ky-thuat)
- [Yêu cầu chức năng](#yeu-cau-chuc-nang)
- [Yêu cầu phi chức năng](#yeu-cau-phi-chuc-nang)
- [Cấu hình OEM và schema JSON](#cau-hinh-oem-va-schema-json)
- [Ma trận quyền](#ma-tran-quyen)
- [Spike bắt buộc trước khi build](#spike-bat-buoc-truoc-khi-build)
- [Mô hình kinh doanh](#mo-hinh-kinh-doanh)
- [Chính sách và pháp lý](#chinh-sach-va-phap-ly)
- [Phát hành và hỗ trợ](#phat-hanh-va-ho-tro)
- [Lộ trình](#lo-trinh)
- [Câu hỏi mở](#cau-hoi-mo)
- [Phụ lục A — Bảng OEM](#phu-luc-a--bang-oem)
- [Phụ lục B — Ma trận test](#phu-luc-b--ma-tran-test)

<a name="tom-tat"></a>

## Tóm tắt

Ứng dụng Android (Kotlin) biến việc "thay trợ lý gốc bằng Gemini" từ một chuỗi lệnh ADB thành vài cú chạm:

- Bắt sự kiện trợ lý gốc của hãng vừa trồi lên, đóng nó, mở Gemini, tự bấm Micro.
- Cấu hình một lần qua Shizuku/ADB: đặt trợ lý mặc định, vá crash locale, cấp quyền.
- Chạy độc lập bằng AccessibilityService: không cần PC, tự sống lại sau reboot (kèm quyền tự khởi chạy), không chết khi rút cáp.
- Mở rộng đa hãng bằng profile JSON cập nhật từ xa; đích đến có thể là Gemini, ChatGPT Voice hoặc app khác.

Mô hình: miễn phí có quảng cáo + Pro mua một lần; kênh Play Store và APK trực tiếp cho cộng đồng máy nội địa Việt Nam.

<a name="4-rui-ro-co-the-giet-du-an"></a>

## 4 rủi ro có thể giết dự án

Nói thẳng trước khi viết dòng code nào:

1. **Không thể tự bấm phím cứng từ app thường.** `input keyevent 231` cần shell UID. Nếu `Intent.ACTION_ASSIST` không mở được Gemini overlay trên máy thật, app buộc phải có Shizuku chạy nền — mà Shizuku không root **phải kích hoạt lại sau mỗi lần reboot**. Đây là spike số 1, làm trước mọi thứ khác.
2. **Google Play siết AccessibilityService.** App dùng Trợ năng để điều khiển app khác thường bị từ chối hoặc gỡ. Phải chuẩn bị kênh phân phối APK riêng, đừng đặt cược vào Play.
3. **Quyền `READ_LOGS` không đáng tin trên Android 13+.** Có báo cáo grant xong vẫn chỉ đọc được log của chính app. Vì vậy kiến trúc phải lấy Accessibility làm đường chính, logcat chỉ là tính năng phụ có thể bỏ.
4. **Bán Pro ngoài Play bằng chuyển khoản là vùng xám về thuế/kế toán và chống bẻ khóa.** Đừng coi đó là "in tiền miễn phí"; cần license ký số và quy trình hỗ trợ hoàn tiền.

<a name="van-de-va-nguoi-dung"></a>

## Vấn đề và người dùng

### Vấn đề

Máy nội địa Trung Quốc khóa cứng khẩu lệnh và nút AI vào trợ lý của hãng. Người dùng muốn Gemini nhưng phải:

- Cắm cáp, bật gỡ lỗi USB, gõ lệnh PowerShell/ADB.
- Đối mặt với script chết khi rút cáp hoặc reboot.
- Tự mò từng bước cấp quyền, tự đo tọa độ nút Micro.

Đây là quy trình cho người rành kỹ thuật, không phải cho người dùng phổ thông mua máy xách tay.

### Người dùng mục tiêu

| Nhóm | Nhu cầu chính | Mức sẵn sàng trả |
| --- | --- | --- |
| Người dùng phổ thông máy nội địa | Bấm 1 nút là xong, không cần PC | Cao nếu < 50k và cài đặt dễ |
| Power user / người Việt hóa ROM | Gán nút cứng, chọn nhiều AI đích, can thiệp sâu | Cao, chấp nhận Shizuku |
| Người bán máy / dịch vụ cài đặt | Cài sẵn cho khách, cần quy trình lặp lại được | Trung bình, ưu tiên ổn định |

### Job to be done

> "Tôi hô 'Hi Jovi' hoặc giữ nút bên hông, và Gemini mở lên nói được ngay — kể cả khi máy đang khóa, không cần cắm máy tính."

<a name="pham-vi"></a>

## Phạm vi

### MVP (chỉ Vivo/iQOO OriginOS)

- Checklist quyền + chẩn đoán.
- Cấu hình 1 chạm qua Shizuku (hoặc hướng dẫn ADB nếu chưa có Shizuku).
- Bắt `CopilotPanelWindow` bằng Accessibility, đóng bằng Back, mở Gemini.
- Tự bấm Micro trên bảng Gemini (node → fallback tọa độ theo bounds cửa sổ).
- Chế độ màn hình khóa: đánh thức + gọi Gemini khi đủ quyền.
- Chế độ nút lối tắt (chỉ khi đường logcat hoạt động).
- Bản quyền Pro + quảng cáo theo quy tắc ở dưới.
- Xuất log chẩn đoán.

### V1

- Profile cho Xiaomi/HyperOS, OPPO/OnePlus/Realme, Honor, Nubia.
- Remote config JSON có ký số, cập nhật không cần phát hành app.
- Trigger sniffer: dò gói/cửa sổ khi người dùng bấm nút AI lạ.
- Chọn đích: Gemini, ChatGPT Voice, app tùy chọn.
- Gán nút cứng: nhấn giữ gọi AI, nhấn đúp đèn pin/chụp màn hình/QR.

### Không nằm trong phạm vi

- Không yêu cầu root, không ship system app, không dùng Xposed/LSPosed trong bản chính.
- Không xử lý giọng nói trên cloud của mình; không ghi âm, không lưu nội dung màn hình.
- Không hứa "sống mãi sau reboot" khi chưa có cơ chế tự khởi động được kiểm chứng trên từng ROM.
- Không quảng cáo trên màn hình khóa, không overlay quảng cáo lên app khác.

<a name="trai-nghiem-chinh"></a>

## Trải nghiệm chính

### Onboarding (3 màn)

1. **Kiểm tra thiết bị:** nhận diện `Build.MANUFACTURER`, ROM, phiên bản Gemini; nếu chưa có profile thì báo "chưa hỗ trợ, gửi log".
2. **Cấp quyền:** Trợ năng (bắt buộc), Tự khởi chạy + Pin không hạn chế (OriginOS), thông báo (tùy chọn), Shizuku (cho cấu hình 1 chạm).
3. **Kích hoạt 1 chạm:** chạy lệnh cấu hình, hiển thị từng bước xanh/đỏ, cho phép chạy lại.

### Màn hình chính

Bốn thẻ trạng thái:

| Thẻ | Nội dung | Trạng thái |
| --- | --- | --- |
| Thiết bị | Máy, ROM, profile đang dùng | Xanh/đỏ |
| Quyền | Trợ năng, tự khởi chạy, pin, thông báo | Xanh/đỏ từng mục |
| Cấu hình | Trợ lý mặc định, locale fix, quyền màn hình khóa | Xanh/đỏ |
| Chế độ | Micro tự động, màn hình khóa, nút cứng | Bật/tắt + badge Pro |

Nút lớn: **"Kích hoạt & vá lỗi"**. Nút phụ: "Chẩn đoán", "Xuất log", "Khôi phục mặc định".

### Luồng voice (đường chính)

```text
"Hi Jovi"
  -> AccessibilityEvent TYPE_WINDOW_STATE_CHANGED (com.vivo.ai.copilot)
  -> performGlobalAction(BACK)            (~50-100ms)
  -> đợi 400-500ms cho Tiểu V nhả audio focus
  -> mở Gemini (Intent hoặc Shizuku keyevent)
  -> Accessibility thấy cửa sổ Gemini
  -> tìm node Micro / fallback chạm theo bounds  (~<1.5s tổng)
```

Mọi bước ghi vào ring buffer để xuất log khi lỗi.

### Luồng nút cứng

- **Chế độ A (khuyến nghị):** đặt nút "Nhấn giữ" mở trợ lý gốc, app bắt cửa sổ trợ lý rồi chuyển hướng như luồng voice. Không cần logcat, không cần Shizuku lúc chạy.
- **Chế độ B (nâng cao):** nút "Không hành động", app đọc event log `ACTION_BUTTON_TO_START_FUNCTIONS`. Chỉ bật khi quyền logcat hoạt động; nếu không, ẩn tính năng thay vì bán tính năng hỏng.

### Luồng màn hình khóa

1. Người dùng bật "Hiển thị trên màn hình khóa" cho Google/Gemini và "Dùng Gemini mà không cần mở khóa" trong app Gemini (app có deep link hướng dẫn).
2. Khi trigger: mở `TriggerActivity` trong suốt (`setShowWhenLocked`, `setTurnScreenOn`), activity gọi Gemini rồi finish.
3. Fallback: Shizuku gửi `keyevent 224` rồi `231`.
4. Nếu vẫn thất bại: hiển thị đúng nguyên nhân (thiếu quyền nào), không im lặng.

<a name="kien-truc-ky-thuat"></a>

## Kiến trúc kỹ thuật

### Sơ đồ

```text
[Trigger sources]                [Core]                     [Actions]
Accessibility window event ─┐                                performGlobalAction(BACK)
Logcat event (optional) ────┼─> TriggerEngine ─> Planner ─> launchAssistant()
Hardware shortcut intent ───┘   (profile, dedupe,           wakeScreen()
                                 cooldown, state)           tapMic()
                                                            intent/deep link

[Provisioner] Shizuku/ADB -> settings, appops, locale, pm grant
[ProfileRepository] bundled JSON + remote signed JSON
[Billing/Ads] Play Billing, license file, AdMob
[Diagnostics] ring buffer, export zip, redacted
```

### Thành phần

| Module | Trách nhiệm | Ghi chú |
| --- | --- | --- |
| `AssistantAccessibilityService` | Nhận `TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOWS_CHANGED`; đóng panel; tìm/click node | `canRetrieveWindowContent`, `canPerformGestures`, `notificationTimeout=50` |
| `TriggerEngine` | Khớp profile, chống trùng, cooldown, state machine | Bỏ qua event của chính app; chống loop khi Gemini mở |
| `ActionExecutor` | Back, mở assistant, đánh thức, click Mic | Tách interface để test bằng fake |
| `Provisioner` | Chạy lệnh cấu hình qua Shizuku/ADB; xác minh sau khi chạy | Idempotent, có log từng lệnh |
| `ProfileRepository` | Profile bundled + remote đã ký; version, rollback | Không nhận lệnh shell tùy ý từ remote |
| `BillingController` | Play Billing + license ký số cho kênh APK | Cache quyền offline, grace period |
| `AdsController` | Banner/Interstitial/Rewarded theo quy tắc | Không hiện khi redirect/lock screen |
| `Diagnostics` | Ring buffer, đếm tỉ lệ thành công, xuất log | Che dữ liệu cá nhân trước khi xuất |

### State machine

```text
IDLE
 -> PANEL_DETECTED      (window event khớp profile)
 -> DISMISSING          (BACK)
 -> COOLDOWN            (chờ nhả audio focus, 400-500ms)
 -> LAUNCHING           (intent/keyevent)
 -> WAITING_VOICE_UI    (timeout 1500ms)
 -> TAPPING_MIC         (node click / gesture)
 -> DONE | FAILED(reason)
```

Mỗi chuyển trạng thái có deadline; quá hạn thì về `IDLE`, không treo service.

### Nguồn trigger và hành động

| Nguồn | Độ tin cậy | Quyền cần | Dùng khi |
| --- | --- | --- | --- |
| Accessibility window | Cao, không cần shell | Trợ năng | Mặc định |
| Logcat events | Phụ thuộc ROM/Android | `READ_LOGS` hoặc Shizuku | Nút cứng không mở UI |
| Shortcut intent của app | Cao | Không | Phím tắt do app tự tạo |

Hành động khai báo trong profile (whitelist): `global_back`, `sleep`, `wake`, `launch_assistant` (`assist_key`: 219/231 hoặc intent), `tap_node`, `tap_bounds`, `open_intent`. **Không** cho phép `shell` tùy ý từ remote config.

### Cấu hình 1 chạm (Provisioner)

Chạy qua Shizuku (`Shizuku.newProcess` hoặc UserService), hoặc in lệnh ADB cho người dùng dán:

```bash
settings put secure assistant com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService
settings put secure voice_interaction_service com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService
cmd appops set com.vivo.agent RUN_IN_BACKGROUND allow
cmd appops set com.vivo.agent RUN_ANY_IN_BACKGROUND allow
cmd locale set-app-locales com.vivo.ai.copilot --locales en-US
cmd appops set com.google.android.googlequicksearchbox SYSTEM_ALERT_WINDOW allow
cmd appops set com.google.android.apps.bard SYSTEM_ALERT_WINDOW allow
```

Nguyên tắc:

- Chạy xong **đọc lại** giá trị để xác nhận, không tin exit code.
- Lệnh nào fail thì báo đúng lệnh đó, kèm gợi ý (ROM khác tên gói, thiếu quyền).
- Không bắt buộc Shizuku ở runtime cho đường Accessibility; app chỉ dùng Shizuku để cấu hình và, nếu đã cấp quyền, làm dự phòng `keyevent 231`.

### Tự bật Micro

1. Chờ cửa sổ Gemini (`com.google.android.apps.bard` hoặc `com.google.android.googlequicksearchbox`).
2. Tìm node theo thứ tự: `viewId` trong profile → `contentDescription` regex ("micro", "voice", "nói") → node cuối góc phải dưới.
3. `ACTION_CLICK`; nếu không click được, `dispatchGesture` tại tâm bounds của node (tọa độ tương đối theo cửa sổ, không hard-code độ phân giải).
4. Timeout 1.5s; thất bại thì ghi log và để người dùng tự bấm, không retry vô hạn.

### Màn hình khóa

- `TriggerActivity` với `setShowWhenLocked(true)` + `setTurnScreenOn(true)`, sau đó mở assistant và `finish()`.
- Kiểm tra background activity start: AccessibilityService được hệ thống bind nên thường được phép; vẫn phải test.
- Không dùng `PowerManager.wakeUp()` trực tiếp trừ khi xin được quyền phù hợp.

### Remote config

- File JSON ký Ed25519, public key nhúng trong app; từ chối payload sai chữ ký.
- Có `min_app_version`, `schema`; app giữ bản tốt gần nhất, fallback bản bundled.
- Tải qua HTTPS, ETag, timeout ngắn; không chặn khởi động nếu offline.

<a name="yeu-cau-chuc-nang"></a>

## Yêu cầu chức năng

| ID | Yêu cầu | Ưu tiên | Tiêu chí chấp nhận |
| --- | --- | --- | --- |
| FR-01 | Checklist quyền tự kiểm tra | Must | Phản ánh đúng trạng thái trong < 1s, bấm vào mở đúng màn hình cài đặt |
| FR-02 | Cấu hình 1 chạm qua Shizuku | Must | 7 lệnh chạy được; đọc lại xác nhận; lỗi hiển thị rõ |
| FR-03 | Chuyển hướng voice | Must | Từ window event đến Back < 150ms; Gemini mở < 2s |
| FR-04 | Tự bật Micro | Must (Pro) | Thành công ≥ 90% trên máy test; fallback gesture hoạt động |
| FR-05 | Màn hình khóa | Should (Pro) | Gemini mở khi khóa máy với đủ 3 điều kiện quyền |
| FR-06 | Nút cứng chế độ A | Should | Bắt được cửa sổ trợ lý gốc, chuyển hướng như voice |
| FR-07 | Nút cứng chế độ B | Could | Chỉ hiện khi logcat đọc được; có nút tự test |
| FR-08 | Profile đa hãng | Should | Thêm hãng mới bằng JSON, không cần build lại |
| FR-09 | Trigger sniffer | Could | Lưu được package/cửa sổ người dùng chọn trong 5s |
| FR-10 | Chọn đích AI | Could | Gemini mặc định; ChatGPT Voice qua intent/deep link |
| FR-11 | Pro entitlement | Must | Offline vẫn xác thực; mất mạng không khóa oan |
| FR-12 | Quảng cáo theo quy tắc | Must | Không hiện ở lock screen/lúc redirect; tần suất có giới hạn |
| FR-13 | Xuất log chẩn đoán | Must | File zip < 1MB, đã che text màn hình/nội dung cá nhân |
| FR-14 | Khôi phục mặc định | Must | Trả assistant về Tiểu V, bỏ locale fix, tắt service |

<a name="yeu-cau-phi-chuc-nang"></a>

## Yêu cầu phi chức năng

| Hạng mục | Mục tiêu |
| --- | --- |
| Độ trễ | Window event → Back < 150ms; mở Gemini < 2s; bấm Mic < 1.5s sau khi UI hiện |
| Pin | Không bật logcat mặc định; idle < 1%/ngày trên máy test; đo bằng Battery Historian |
| Ổn định | Crash-free > 99.5%; service tự phục hồi sau khi bị ROM kill |
| Tương thích | Android 11–15; OriginOS 3/4/5; HyperOS, ColorOS, MagicOS (V1) |
| Riêng tư | Không gửi nội dung màn hình/âm thanh ra ngoài; telemetry opt-in, không text |
| Offline | Cấu hình và chuyển hướng chạy hoàn toàn offline sau khi cài |
| Trợ năng | Không phá TalkBack; mô tả service rõ ràng; tôn trọng người dùng tắt service |
| Kích thước | APK < 15MB (không nhúng model AI) |

<a name="cau-hinh-oem-va-schema-json"></a>

## Cấu hình OEM và schema JSON

```json
{
  "schema": 2,
  "oem": "vivo",
  "os": "OriginOS",
  "min_app_version": 1,
  "assistant_pkg": "com.vivo.ai.copilot",
  "locale_fix": { "pkg": "com.vivo.ai.copilot", "locale": "en-US" },
  "targets": {
    "gemini": {
      "packages": ["com.google.android.apps.bard", "com.google.android.googlequicksearchbox"],
      "launch": { "type": "intent", "action": "android.intent.action.ASSIST" },
      "fallback": { "type": "assist_key", "value": 231 }
    }
  },
  "triggers": [
    {
      "id": "vivo_voice",
      "source": "accessibility_window",
      "match": { "package": "com.vivo.ai.copilot", "class_regex": ".*CopilotPanelWindow.*" },
      "actions": [
        { "type": "global_back" },
        { "type": "sleep", "ms": 500 },
        { "type": "wake" },
        { "type": "launch_assistant", "target": "gemini" }
      ],
      "cooldown_ms": 1500
    }
  ],
  "voice_ui": {
    "mic_view_id_regex": ".*(mic|voice).*",
    "mic_content_desc_regex": "(?i)micro|voice|nói|speak",
    "fallback": "bounds_bottom_right"
  }
}
```

Quy tắc an toàn:

- Chỉ nhận các `type` trong whitelist; không có trường thực thi shell.
- Kiểm tra `schema` và `min_app_version`; sai thì bỏ qua.
- Ký số toàn bộ payload; không tải profile qua HTTP thường.
- Giới hạn kích thước file (ví dụ 256KB) và số trigger (ví dụ 20).

<a name="ma-tran-quyen"></a>

## Ma trận quyền

| Quyền / cấu hình | Cách cấp | Sống sau reboot? | Bắt buộc |
| --- | --- | --- | --- |
| Trợ năng (AccessibilityService) | Người dùng bật trong Cài đặt | Có (nếu ROM không chặn) | Có |
| Tự khởi chạy + Pin không hạn chế | Cài đặt OriginOS | Có | Có trên Vivo |
| Trợ lý mặc định (`secure assistant`) | Shizuku/ADB | Có | Có |
| Locale app Tiểu V (`en-US`) | Shizuku/ADB | Có | Có (vá crash) |
| `SYSTEM_ALERT_WINDOW` cho Google/Gemini | appops | Có | Cho màn hình khóa |
| Shizuku | Wireless debugging/root | **Không** (không root phải bật lại) | Chỉ lúc cấu hình |
| `READ_LOGS` | `pm grant` qua shell | Có, nếu grant được | Không (chế độ nút cứng B) |
| Thông báo | Runtime permission | Có | Không |
| Bỏ tối ưu pin | Cài đặt hệ thống | Có | Khuyến nghị |

<a name="spike-bat-buoc-truoc-khi-build"></a>

## Spike bắt buộc trước khi build

Làm trên máy Vivo thật + Gemini bản mới nhất. Kết quả quyết định toàn bộ kiến trúc.

| ID | Câu hỏi | Cách test | Nếu thất bại |
| --- | --- | --- | --- |
| S1 | App thường mở được Gemini overlay không cần shell? | App nhỏ gọi `Intent(ACTION_ASSIST)` + `FLAG_ACTIVITY_NEW_TASK`; thử thêm `setPackage("com.google.android.googlequicksearchbox")` | Thử deep link/activity của GSA; nếu vẫn fail → cần Shizuku runtime, phải định vị lại sản phẩm |
| S2 | Accessibility thấy `CopilotPanelWindow` và Back được? | Dump `AccessibilityEvent` + `getWindows()` khi hô "Hi Jovi" | Thử `TYPE_WINDOWS_CHANGED`, tăng `notificationTimeout`, kiểm tra quyền Trợ năng |
| S3 | Back xong có đóng panel và nhả audio focus? | Đo thời gian, thử sleep 300/500/700ms | Tăng delay theo profile, thêm retry 1 lần |
| S4 | Bấm được Mic trên Gemini bằng node? | Dump cây node của overlay; kiểm tra `viewIdResourceName`, `contentDescription`, `isClickable` | Dùng `dispatchGesture` theo bounds node/cửa sổ |
| S5 | Đánh thức + mở Gemini khi khóa máy? | `TriggerActivity` + setShowWhenLocked/setTurnScreenOn; test khóa bằng PIN | Shizuku `keyevent 224` + `231`; nếu vẫn fail → cắt tính năng khỏi Pro |
| S6 | Shizuku chạy đủ 7 lệnh và kết quả sống sau reboot? | Chạy Provisioner, reboot, đọc lại settings | Bỏ lệnh nào không persist; hướng dẫn ADB |
| S7 | `pm grant READ_LOGS` đọc được event log Android 13/14? | Grant, chạy `logcat -b events` trong app, so sánh với adb | Bỏ chế độ nút cứng B, chỉ dùng Accessibility |
| S8 | Pin khi bật service 24h? | Battery Historian, 8h idle | Giảm event types, bỏ logcat, thêm cooldown |

<a name="mo-hinh-kinh-doanh"></a>

## Mô hình kinh doanh

### Bảng Freemium

| Tính năng | Free (có quảng cáo) | Pro (mua 1 lần) |
| --- | --- | --- |
| Chuyển hướng "Hi Jovi" → Gemini | Có | Có |
| Vá crash tiếng Việt 1 chạm | Có | Có + tự kiểm tra lại sau cập nhật ROM |
| Tự bật Micro | Xem rewarded ad, dùng 72 giờ | Vĩnh viễn |
| Màn hình khóa | Không | Có |
| Gán nút cứng | Giới hạn 1 hành động | Đầy đủ (giữ/đúp, đèn pin, chụp màn hình, QR) |
| Đa hãng (Xiaomi/OPPO/Honor/Nubia) | Chỉ profile mặc định | Tất cả + cập nhật profile |
| Quảng cáo | Banner ở màn hình cài đặt, interstitial có giới hạn | Không |

Giá đề xuất: **39.000–49.000đ** cho Pro (một lần), trial rewarded 72 giờ. Không dùng subscription ở giai đoạn đầu — khó giữ chân và dễ bị phàn nàn.

### Quảng cáo

- Chỉ AdMob trong màn hình cài đặt/chẩn đoán; **không** hiện khi đang chuyển hướng, không hiện trên màn hình khóa, không overlay lên app khác.
- Rewarded ad để mở auto-voice 72 giờ; phải nói rõ "xem quảng cáo để dùng 3 ngày".
- Tần suất: tối đa 1 interstitial/ngày; không interstitial lúc mở app.
- Doanh thu quảng cáo ở VN thấp; đây chỉ là phễu, Pro mới là nguồn chính.

### Thanh toán

- **Kênh Play:** bắt buộc Google Play Billing cho tính năng số.
- **Kênh APK trực tiếp:** VietQR/chuyển khoản, cấp license ký Ed25519 gắn với `ANDROID_ID` + attestation; có hạn dùng offline và quy trình hoàn tiền.
- Ghi rõ giá, chính sách hoàn tiền, hỗ trợ trước khi thu tiền. Doanh thu chuyển khoản là thu nhập chịu thuế — tự hạch toán.

### Chỉ số theo dõi

- Tỉ lệ cài đặt → cấu hình thành công (activation rate).
- Tỉ lệ chuyển hướng thành công theo từng trigger và từng ROM.
- Tỉ lệ bấm Mic thành công (node vs gesture).
- D1/D7 retention; trial → Pro; hoàn tiền.
- Crash-free, ANR, số log chẩn đoán gửi về.

<a name="chinh-sach-va-phap-ly"></a>

## Chính sách và pháp lý

Đây là phần dễ bị bỏ qua nhất và cũng dễ làm mất app nhất.

| Vấn đề | Rủi ro | Cách giảm |
| --- | --- | --- |
| Play policy với AccessibilityService | App bị từ chối/gỡ vì dùng Trợ năng để điều khiển app khác | Khai báo trung thực `isAccessibilityTool=false`, video demo, mô tả rõ; giữ bản Play ít tính năng, phát hành full qua APK |
| Play Billing | Bán Pro trong bản Play bằng kênh ngoài sẽ bị gỡ | Bản Play chỉ dùng Play Billing |
| AdMob | Quảng cáo trên lock screen/overlay bị coi là invalid traffic | Tuân thủ quy tắc đặt quảng cáo ở trên |
| Thương hiệu | Dùng "Gemini", "Jovi", "Vivo" trong tên/icon dễ bị coi là mạo danh | Tên trung tính; chỉ nhắc tên bên thứ ba trong mô tả, kèm disclaimer "không liên kết" |
| Quyền riêng tư | Trợ năng đọc được nội dung màn hình | Không lưu/không gửi text; chính sách riêng tư rõ; Data Safety khai báo đúng |
| Shizuku | Giấy phép Apache-2.0 | Ghi attribution, không bán lại như sản phẩm của mình |
| Bán hàng trực tiếp | Thuế, hoàn tiền, tranh chấp | Hợp đồng/điều khoản đơn giản, hóa đơn, kênh hỗ trợ |
| Bảo mật thiết bị | Đổi trợ lý mặc định, cấp appops ảnh hưởng hệ thống | Có nút khôi phục mặc định; cảnh báo trước khi chạy |

Không phải tư vấn pháp lý. Trước khi thu tiền, nên hỏi người làm kế toán/thuế về hình thức bán và nghĩa vụ hóa đơn.

<a name="phat-hanh-va-ho-tro"></a>

## Phát hành và hỗ trợ

- **Kênh chính:** APK ký số + website/GitHub Releases; phân phối qua nhóm Facebook Vivo/iQOO/OriginOS Việt Nam, kèm hướng dẫn cài và video.
- **Kênh phụ:** Play Store với bản giới hạn (chẩn đoán + cấu hình + chuyển hướng cơ bản), dùng làm điểm tin cậy.
- **Cập nhật:** remote config có ký; không cần phát hành app khi ROM đổi tên cửa sổ.
- **Hỗ trợ:** nút "Xuất log" tạo file chẩn đoán; mẫu trả lời lỗi phổ biến; kênh Zalo/Facebook.
- **Hoàn tiền:** chính sách 7 ngày nếu app không chạy trên máy người dùng (kênh APK).

<a name="lo-trinh"></a>

## Lộ trình

| Giai đoạn | Nội dung | Điều kiện qua cổng |
| --- | --- | --- |
| P0 — Spike | S1–S8 trên 2-3 máy Vivo thật | S1 và S5 phải có đường không cần Shizuku runtime, hoặc chấp nhận định vị lại |
| P1 — MVP | Onboarding, Provisioner, AccessibilityService, auto-voice, Vivo profile | Chuyển hướng ≥ 95%, auto-voice ≥ 90% trên máy test |
| P2 — Tiền | Pro, ads, license, xuất log | Thanh toán chạy, không khóa oan khi offline |
| P3 — Đa hãng | Xiaomi/OPPO/Honor/Nubia, remote config, sniffer | Thêm hãng mới chỉ bằng JSON |
| P4 — Mở rộng | ChatGPT Voice, gán nút, thị trường quốc tế | Không phá kiến trúc profile |

<a name="cau-hoi-mo"></a>

## Câu hỏi mở

1. `Intent.ACTION_ASSIST` có mở Gemini overlay trên OriginOS không? Nếu không, có activity/deep link nào ổn định?
2. Bản Play có được duyệt với AccessibilityService mô tả đúng? Có nên bỏ Play ngay từ đầu?
3. `READ_LOGS` còn grant được trên Android 13+ không, và có đọc được event log hệ thống?
4. Giá Pro hợp lý cho tệp người dùng VN: 29k, 39k hay 49k?
5. Có nên hỗ trợ ChatGPT Voice ngay từ MVP để mở rộng tệp người dùng ngoài máy nội địa?
6. Ai chịu trách nhiệm hỗ trợ khi ROM cập nhật làm hỏng profile — cộng đồng hay bản Pro?

<a name="phu-luc-a--bang-oem"></a>

## Phụ lục A — Bảng OEM

Tên gói/cửa sổ chỉ là điểm khởi đầu, phải xác minh trên từng bản ROM.

| Hãng / OS | Trợ lý gốc | Gói | Tín hiệu / cửa sổ | Ghi chú |
| --- | --- | --- | --- | --- |
| Vivo / iQOO (OriginOS) | Tiểu V / Jovi | `com.vivo.ai.copilot`, `com.vivo.agent` | `CopilotPanelWindow`, `ACTION_BUTTON_TO_START_FUNCTIONS` | Locale `en-US` cho copilot để tránh crash |
| Xiaomi / Redmi (HyperOS China) | XiaoAI | `com.miui.voiceassist` | `VoiceAssistActivity`, `WakeUpService` | Chặn autostart gắt; cần khóa app trong đa nhiệm |
| OPPO / OnePlus / Realme (ColorOS China) | Breeno | `com.heytap.speechassist` | `SpeechAssistActivity`, `FloatWindow` | Nút Action/VIP Slider dễ bắt sự kiện |
| Honor / Huawei (MagicOS) | YOYO | `com.hihonor.magicvoice`, `com.huawei.vassistant` | `VoiceAssistantActivity` | Thường chỉ cần Back để đóng lớp phủ |
| Nubia / RedMagic | Mora | `cn.nubia.voiceassist` | `VoiceAssistService` | Có thể tận dụng phím trigger cạnh máy |

<a name="phu-luc-b--ma-tran-test"></a>

## Phụ lục B — Ma trận test

| Nhóm | Trường hợp | Kỳ vọng |
| --- | --- | --- |
| Cơ bản | Hô "Hi Jovi" khi máy sáng, mở khóa | Gemini mở, Mic bật trong 2s |
| Khóa máy | Hô khi màn hình tắt, khóa PIN | Gemini mở, Mic bật (nếu đủ quyền) |
| Nút cứng | Nhấn giữ nút lối tắt | Chuyển hướng đúng, không mở Tiểu V ở lại |
| Rút cáp | Đang chạy, rút cáp | Không mất service, vẫn hoạt động |
| Reboot | Khởi động lại máy | Service tự lên, cấu hình còn, chuyển hướng chạy |
| Cập nhật | Gemini cập nhật phiên bản mới | Profile cũ vẫn chạy hoặc remote config vá |
| Quyền thiếu | Tắt Trợ năng | App báo đúng, không crash |
| Pin | Tiết kiệm pin bật | Có hướng dẫn loại trừ, không im lặng thất bại |
| Trợ năng | Bật TalkBack | Không xung đột, không tự bấm loạn |
| Đa nhiệm | Mở app khác rồi hô | Không nhầm cửa sổ, không đóng app khác |
