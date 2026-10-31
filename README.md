# AI Key Bridge

[![Phiên bản](https://img.shields.io/github/v/release/pdong15dth/gemini-default-ai-phone?label=Phi%C3%AAn%20b%E1%BA%A3n&logo=github&color=0969da)](https://github.com/pdong15dth/gemini-default-ai-phone/releases/latest)
[![Lượt tải](https://img.shields.io/github/downloads/pdong15dth/gemini-default-ai-phone/total?label=L%C6%B0%E1%BB%A3t%20t%E1%BA%A3i&logo=android&logoColor=white&color=2ea44f)](https://github.com/pdong15dth/gemini-default-ai-phone/releases)
[![Stars](https://img.shields.io/github/stars/pdong15dth/gemini-default-ai-phone?label=Stars&logo=github&color=eab308)](https://github.com/pdong15dth/gemini-default-ai-phone/stargazers)
[![Build APK](https://img.shields.io/github/actions/workflow/status/pdong15dth/gemini-default-ai-phone/android.yml?label=Build%20APK&logo=githubactions&logoColor=white)](https://github.com/pdong15dth/gemini-default-ai-phone/actions/workflows/android.yml)
[![Android](https://img.shields.io/badge/Android-11%2B-3DDC84?logo=android&logoColor=white)](#cai-dat-va-thiet-lap)
[![Giấy phép](https://img.shields.io/github/license/pdong15dth/gemini-default-ai-phone?label=Gi%E1%BA%A5y%20ph%C3%A9p&color=6e7781)](LICENSE)

**Hô "Hi Jovi". Gemini trả lời.**

App Android mã nguồn mở thay trợ lý gốc của ROM (Tiểu V / Jovi trên Vivo OriginOS và các hãng khác) bằng **Google Gemini** hoặc **Google Circle to Search** — cả khẩu lệnh giọng nói, nút cứng lẫn cử chỉ, tự bật Micro. Tích hợp sẵn **Engine nội bộ** (ghép nối 6 số Gỡ lỗi không dây, không cần cài Shizuku, không cần máy tính) và khóa Gỡ lỗi 1 chạm (**Bank-Safe**) để dùng bình thường với mọi ứng dụng Ngân hàng & VNeID.

<p align="center">
  <img src="docs/images/10-overview.webp" width="240" alt="Tab 1: Trang chủ">
  &nbsp;
  <img src="docs/images/11-modes.webp" width="240" alt="Tab 2: Tùy chỉnh AI">
  &nbsp;
  <img src="docs/images/14-logs.webp" width="240" alt="Tab 3: Hệ thống">
</p>

<p align="center">
  <a href="https://github.com/pdong15dth/gemini-default-ai-phone/releases/latest/download/ai-key-bridge.apk"><b>Tải APK</b></a>
  ·
  <a href="https://pdong15dth.github.io/gemini-default-ai-phone/"><b>Trang giới thiệu</b></a>
  ·
  <a href="HUONG-DAN-SCRIPT-ADB.md"><b>Dùng bằng script ADB (không cài app)</b></a>
</p>

> Android 11+ · không root · không Trợ năng · không quảng cáo · không thu thập dữ liệu. Đã kiểm chứng trực tiếp trên **vivo X300 Pro (`V2502DA`, OriginOS)**.

## Mục lục

- [Chức năng](#chuc-nang)
- [Cơ chế hoạt động](#co-che-hoat-dong)
- [Hãng được hỗ trợ](#hang-duoc-ho-tro)
- [Cài đặt và thiết lập (3 bước trên Trang chủ)](#cai-dat-va-thiet-lap)
- [Giao diện 3 tab của ứng dụng](#cac-man-hinh-trong-app)
- [Lưu ý trên OriginOS & Ngân hàng (Bank-Safe)](#luu-y-tren-originos)
- [Báo lỗi](#bao-loi)
- [Cách khác: script ADB](#cach-khac-script-adb)
- [Tự build và deploy](#tu-build-va-deploy)
- [Cấu trúc repo](#cau-truc-repo)

<a name="chuc-nang"></a>

## Chức năng

| | Chức năng | Mô tả |
| --- | --- | --- |
| 🎙️ | **Thay "Hi Jovi"** | Bắt trợ lý gốc vừa hiện, đóng ngay và mở lớp phủ Gemini đang nghe — bằng Script nền chạy độc lập trong RAM (không dùng Trợ năng). |
| 🔘 | **Tùy biến từng thao tác** | Chọn hành động riêng cho **Khẩu lệnh ("Hi Jovi")**, **Nút cứng / Phím cạnh AI**, và **Lối tắt "Gọi AI"**: mở Gemini Voice, khoanh tròn tìm kiếm (**Google · Circle to Search**), ChatGPT Voice, Claude hoặc bất kỳ app nào. |
| ⚡ | **Engine nội bộ (Không cần Shizuku / PC)** | Tự ghép nối 6 số Gỡ lỗi không dây ngay trên thanh thông báo để chạy lệnh hệ thống và khởi chạy Script nền — không cần cài thêm Shizuku hay cắm máy tính (vẫn hỗ trợ Shizuku làm dự phòng). |
| 🛡️ | **Khóa Gỡ lỗi 1 chạm (Bank-Safe)** | Sau khi Script nền đã chạy, bấm 1 chạm trên **Trang chủ** để tắt sạch *Tùy chọn nhà phát triển* & *Gỡ lỗi USB* — mọi app Ngân hàng và VNeID hoạt động 100% bình thường trong khi Script vẫn chạy ngầm. |
| 🎤 | **Tự bật Micro** | Tự chạm nút Micro ngay khi lớp phủ Gemini mở lên, bạn nói luôn không cần chạm tay. |
| 🔒 | **Màn hình khóa** | Hô "Hi Jovi" khi màn hình đang tắt/khóa vẫn đánh thức máy và mở lớp phủ Gemini. |
| ⌨️ | **Chạm mặt lưng & Khởi chạy nhanh** | Tích hợp lối tắt **Gọi AI** để gán vào *Chạm mặt lưng* hoặc *Nhấn giữ nút Giảm âm lượng khi tắt màn hình* trên Vivo OriginOS. |
| 🛠️ | **Kích hoạt & vá lỗi tự động** | Tự chạy và xác minh 9 lệnh hệ thống: đặt Google làm trợ lý mặc định, bật khoanh tròn màn hình, cho Tiểu V chạy nền, vá crash locale tiếng Việt (`en-US`). |
| 🔍 | **Dò cửa sổ trợ lý (8 giây)** | ROM chưa có profile? Bấm Dò rồi gọi trợ lý gốc, app tự bắt tên gói và tên cửa sổ làm trigger. |
| 📊 | **Biểu đồ 7 ngày & Xuất chẩn đoán `.zip`** | Theo dõi tỉ lệ chuyển hướng thành công, lịch sử từng lượt gọi AI, log kỹ thuật và xuất gói `.zip` để gửi báo lỗi. |
| ↩️ | **Khôi phục mặc định ban đầu** | Trả trợ lý về Tiểu V và gỡ mọi bản vá chỉ bằng một nút trong tab **Hệ thống**. |

<a name="co-che-hoat-dong"></a>

## Cơ chế hoạt động

```
"Hi Jovi" / nút AI ──► cửa sổ Tiểu V hiện ──► Script nền bắt sự kiện (≈50 ms)
                                                   │
                                                   ▼
                                     Back: đóng panel, nhả audio (500 ms)
                                                   │
                                                   ▼
                                     Mở lớp phủ Gemini / Circle to Search
```

- **Script nền độc lập (Bank-Safe, không dùng Trợ năng)**: Script shell (`/data/local/tmp/akb-gemini.sh`) được đẩy và khởi chạy bằng **Engine nội bộ** (Gỡ lỗi không dây) hoặc **Shizuku**, đọc kênh `events` của logcat và lọc đúng sự kiện `CopilotPanelWindow` / nút lối tắt. Khi bắt được sự kiện, script gửi Back để đóng Tiểu V, đánh thức màn hình và gọi lớp phủ Gemini (`keyevent 219`) hoặc Google Circle to Search.
- **Tách hoàn toàn khỏi `adbd` và ứng dụng**: Script chạy trong phiên độc lập (`setsid` + `trap "" HUP INT TERM`), nên sau khi bật xong bạn **tắt Tùy chọn nhà phát triển, tắt Gỡ lỗi USB, dừng Shizuku hay vuốt tắt app khỏi đa nhiệm** thì script vẫn tiếp tục chạy trong RAM cho tới khi khởi động lại máy.
- **Tuyệt đối riêng tư**: Không dùng `AccessibilityService`, không đọc văn bản trên màn hình, không chụp màn hình, không có quyền `RECORD_AUDIO` và không có quyền `INTERNET`.

<a name="hang-duoc-ho-tro"></a>

## Hãng được hỗ trợ

| Hãng | Trợ lý gốc | ROM | Trạng thái |
| --- | --- | --- | --- |
| Vivo / iQOO | Tiểu V (Jovi) | OriginOS | ✅ Đã kiểm chứng (vivo X300 Pro) |
| Honor / Huawei | YOYO / Celia | MagicOS | Beta |
| Nubia / RedMagic | Mora | MyOS | Beta |
| OPPO / OnePlus / Realme | Breeno | ColorOS | Beta |
| Xiaomi / Redmi / POCO | XiaoAI | HyperOS | Beta |
| Hãng khác | — | — | Dùng **Dò cửa sổ trợ lý** |

Profile nằm ở [`android/app/src/main/assets/profiles/`](android/app/src/main/assets/profiles/) — rất mong bạn đóng góp profile cho máy của mình (xem [CONTRIBUTING.md](CONTRIBUTING.md)).

<a name="cai-dat-va-thiet-lap"></a>

## Cài đặt và thiết lập (3 bước trên Trang chủ)

Ứng dụng mở thẳng vào **Trang chủ** (không còn các màn hình onboarding rườm rà). Toàn bộ lộ trình thiết lập được gói gọn trong **3 thẻ bước tự động thu gọn** khi hoàn tất:

### 1. Tải và cài APK

Tải [`ai-key-bridge.apk`](https://github.com/pdong15dth/gemini-default-ai-phone/releases/latest/download/ai-key-bridge.apk) và cài đặt. Cài sẵn **Google** và **Google Gemini** từ Play Store.

### 2. Làm theo 3 bước ngay trên Trang chủ

<table>
  <tr>
    <td width="33%"><img src="docs/images/04-permissions.webp" alt="Bước 1: Cấp quyền cơ bản"></td>
    <td width="33%"><img src="docs/images/20-script.webp" alt="Bước 2: Bật Script nền"></td>
    <td width="33%"><img src="docs/images/10-overview.webp" alt="Bước 3: Khóa Gỡ lỗi (Bank-Safe)"></td>
  </tr>
  <tr>
    <td><b>Bước 1 — Cấp quyền cơ bản (4/4 đạt).</b> Bấm <b>Mở ↗</b> ở từng mục để nhảy thẳng tới trang quyền của Google, Gemini, Pin dưới nền và Trợ lý mặc định; hoặc bấm <b>Ảnh</b> để xem hình chụp khoanh vùng từng công tắc.</td>
    <td><b>Bước 2 — Bật Script nền.</b> Ghép nối 6 số Gỡ lỗi không dây ngay trên thanh thông báo bằng <b>Engine nội bộ</b> (hoặc dùng Shizuku) rồi bấm <b>Bật Script nền</b>. Script tự tách khỏi <code>adbd</code> và chạy ngầm trong RAM.</td>
    <td><b>Bước 3 — Khóa Gỡ lỗi (Bank-Safe).</b> Bấm 1 chạm <b>Khóa Gỡ lỗi ngay (Bank-Safe)</b> để tắt sạch <i>Tùy chọn nhà phát triển</i> &amp; <i>Gỡ lỗi USB</i> — an toàn 100% cho app Ngân hàng &amp; VNeID.</td>
  </tr>
</table>

#### Bộ ảnh hướng dẫn khoanh vùng (tích hợp sẵn trong nút `Ảnh` trên Trang chủ)

<table>
  <tr>
    <td width="25%"><img src="docs/images/guides/guide_vivo_perm_google.webp" alt="Quyền của Google"></td>
    <td width="25%"><img src="docs/images/guides/guide_vivo_perm_gemini.webp" alt="Quyền của Gemini"></td>
    <td width="25%"><img src="docs/images/guides/guide_battery_bg.webp" alt="Kiểm soát pin dưới nền"></td>
    <td width="25%"><img src="docs/images/guides/guide_default_assistant.webp" alt="Trợ lý kỹ thuật số mặc định"></td>
  </tr>
  <tr>
    <td><b>1. Quyền của Google:</b> bật <i>Tự động khởi động</i>, <i>Cửa sổ nổi</i>, <i>Hiển thị trên màn hình khóa</i>.</td>
    <td><b>2. Quyền của Gemini:</b> bật đủ 5 mục <i>Tự động khởi động</i>, <i>Khởi động liên kết</i>, <i>Cửa sổ nổi</i>, <i>Màn hình khóa</i>, <i>Cửa sổ bật lên dưới nền</i>.</td>
    <td><b>3. Pin dưới nền:</b> bấm chữ <b>G</b> bên phải → chọn <b>Cho phép sử dụng pin ngầm</b> cho cả <b>Gemini</b> và <b>Google</b>.</td>
    <td><b>4. Trợ lý mặc định:</b> chọn <b>Google</b> và bật <b>Dùng dữ liệu của màn hình và ứng dụng</b>.</td>
  </tr>
  <tr>
    <td><img src="docs/images/guides/guide_gemini_lockscreen.webp" alt="Gemini trên màn hình khoá"></td>
    <td><img src="docs/images/guides/guide_voice_wakeup.webp" alt="Đánh thức bằng giọng nói Hi Jovi"></td>
    <td><img src="docs/images/guides/guide_shizuku.webp" alt="Gỡ lỗi không dây"></td>
    <td><img src="docs/images/guides/guide_dev_options.webp" alt="Tắt Tùy chọn nhà phát triển"></td>
  </tr>
  <tr>
    <td><b>5. Gemini trên màn khóa:</b> <i>Avatar Gemini → Cài đặt → Điều khiển thiết bị → Gemini trên màn hình khoá</i> → bật.</td>
    <td><b>6. Khẩu lệnh "Hi Jovi":</b> bật <i>Đánh thức bằng giọng nói</i> trong cài đặt Tiểu V / BlueLM.</td>
    <td><b>7. Gỡ lỗi không dây:</b> ghép nối mã 6 số để Engine nội bộ (hoặc Shizuku) chạy 9 lệnh hệ thống &amp; bật Script nền.</td>
    <td><b>8. Khóa Gỡ lỗi (Bank-Safe):</b> tắt công tắc <i>Tùy chọn nhà phát triển</i> sau khi Script đã chạy.</td>
  </tr>
</table>

<a name="cac-man-hinh-trong-app"></a>

## Giao diện 3 tab của ứng dụng

Toàn bộ tính năng được tổ chức gọn trong **3 tab chính** (`Trang chủ`, `Tùy chỉnh AI`, `Hệ thống`), các thông số kỹ thuật nâng cao được ẩn trong ngăn thu gọn:

<table>
  <tr>
    <td width="25%"><img src="docs/images/10-overview.webp" alt="Tab 1: Trang chủ"></td>
    <td width="25%"><img src="docs/images/11-modes.webp" alt="Tab 2: Tùy chỉnh AI"></td>
    <td width="25%"><img src="docs/images/12-modes-2.webp" alt="Tab 2: Chạm mặt lưng & Lối tắt"></td>
    <td width="25%"><img src="docs/images/14-logs.webp" alt="Tab 3: Hệ thống (Hoạt động 7 ngày)"></td>
  </tr>
  <tr>
    <td><b>Tab 1: Trang chủ</b> — Thẻ trạng thái tổng thể, thống kê nhanh và lộ trình 3 bước tự thu gọn.</td>
    <td><b>Tab 2: Tùy chỉnh AI</b> — Chọn hành động riêng cho Khẩu lệnh, Nút cứng, Lối tắt và chọn Ứng dụng AI mặc định.</td>
    <td><b>Chạm mặt lưng &amp; Lối tắt</b> — Gán "Gọi AI", nút <b>Thử Gọi AI</b> / <b>Thử Khoanh tròn</b> ngay lập tức.</td>
    <td><b>Tab 3: Hệ thống</b> — Biểu đồ hoạt động 7 ngày, chuyển đổi Lịch sử gọi AI / Log kỹ thuật, Xuất chẩn đoán.</td>
  </tr>
  <tr>
    <td><img src="docs/images/15-settings.webp" alt="Tab 3: Giao diện & Bảo trì"></td>
    <td><img src="docs/images/25-setup.webp" alt="Kích hoạt & vá lỗi (9 lệnh)"></td>
    <td><img src="docs/images/23-profiles.webp" alt="Profile ROM"></td>
    <td><img src="docs/images/22-triggers.webp" alt="Dò trợ lý"></td>
  </tr>
  <tr>
    <td><b>Giao diện &amp; Bảo trì</b> — Chế độ Sáng/Tối, chọn công cụ Shell (Tự động / Shizuku / Engine nội bộ), Khôi phục mặc định.</td>
    <td><b>Kích hoạt &amp; vá lỗi</b> — Xem chi tiết và xác minh từng lệnh trong 9 lệnh hệ thống hoặc copy mã ADB.</td>
    <td><b>Profile ROM</b> — Tự nhận diện hãng điện thoại hoặc chọn profile thủ công.</td>
    <td><b>Dò trợ lý</b> — Tự bắt cửa sổ trợ lý gốc trong 8 giây cho các dòng máy mới.</td>
  </tr>
</table>

<a name="luu-y-tren-originos"></a>

## Lưu ý trên OriginOS & Ngân hàng (Bank-Safe)

- **Hoạt động độc lập hoàn toàn với ứng dụng**: Script nền chạy dưới quyền `shell` trong phiên độc lập (`setsid`). **Vuốt AI Key Bridge khỏi đa nhiệm, dọn RAM hay tắt Gỡ lỗi USB đều không ảnh hưởng** — hô "Hi Jovi" hoặc bấm nút AI vẫn lên Gemini.
- **Tương thích 100% ứng dụng Ngân hàng & VNeID**: Sau khi Bước 2 báo `Đang chạy`, bạn chỉ cần chạm nút **Khóa Gỡ lỗi ngay (Bank-Safe)** ở Bước 3 trên **Trang chủ** để ứng dụng tự tắt *Tùy chọn nhà phát triển* và *Gỡ lỗi USB*.
- **Sau khi khởi động lại máy (Reboot)**: Vì không root nên tiến trình trong `/data/local/tmp` sẽ mất khi tắt nguồn. Sau khi bật máy lại, bạn chỉ cần bật lại *Gỡ lỗi không dây*, mở app (Engine nội bộ tự kết nối lại mà **không cần nhập mã 6 số lần 2**) rồi bấm **Bật Script nền** và **Khóa Gỡ lỗi**.

<a name="bao-loi"></a>

## Báo lỗi

1. Vào tab **Hệ thống → Xuất chẩn đoán** để tạo file `.zip`.
2. Gửi file cho người phát triển, hoặc mở [issue](https://github.com/pdong15dth/gemini-default-ai-phone/issues/new/choose) mô tả lỗi.

| File trong `.zip` | Nội dung |
| --- | --- |
| `history.log`, `log.txt`, `attempts.txt` | Nhật ký chuyển hướng và log kỹ thuật của app |
| `exit-reasons.txt` | Các lần app bị tắt và lý do do Android ghi nhận |
| `logcat-system.txt`, `system-state.txt` | Log hệ thống và trạng thái trợ lý mặc định, quyền, tiến trình script |
| `device.txt`, `settings.txt`, `profile.json` | Thông tin máy, cài đặt và profile đang dùng |

> Log hệ thống có thể chứa tên gói ứng dụng hoặc tên Wi-Fi. Chỉ gửi riêng cho người phát triển, đừng đăng công khai.

<a name="cach-khac-script-adb"></a>

## Cách khác: script ADB

Không muốn cài app? [HUONG-DAN-SCRIPT-ADB.md](HUONG-DAN-SCRIPT-ADB.md) hướng dẫn làm điều tương tự chỉ bằng ADB trên máy tính và một script shell chạy nền.

Nếu từng chạy script thủ công rồi chuyển sang app, dừng script cũ trước để tránh hai tiến trình cùng chạy:

```bash
adb shell "pkill -9 -f gemini.sh; pkill -9 -f logcat"
```

<a name="tu-build-va-deploy"></a>

## Tự build và deploy

Hướng dẫn đầy đủ: [docs/build-va-deploy.md](docs/build-va-deploy.md).

```bash
cd android
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # bản R8 ~2 MB
```

- **APK phát hành:** đẩy tag `v*`, workflow [`android.yml`](.github/workflows/android.yml) build và gắn `ai-key-bridge.apk` vào GitHub Release.
- **Trang giới thiệu:** nằm trong `web/`, workflow [`pages.yml`](.github/workflows/pages.yml) deploy lên GitHub Pages khi có thay đổi trong `web/` trên `main`.

<a name="cau-truc-repo"></a>

## Cấu trúc repo

```
.
├── android/                  # app Kotlin + Jetpack Compose (3 tab: Trang chủ, Tùy chỉnh AI, Hệ thống)
│   └── app/src/main/
│       ├── assets/profiles/  # profile ROM (JSON)
│       └── java/com/aikeybridge/
├── web/                      # trang giới thiệu tĩnh, deploy GitHub Pages
├── docs/
│   ├── images/               # ảnh chụp màn hình thực tế từ vivo X300 Pro
│   ├── build-va-deploy.md    # build APK, ký, phát hành, Pages
│   ├── rut-cap-usb.md
│   └── spec-app.md
├── scripts/
│   └── gemini.sh             # script cho cách ADB thủ công
├── HUONG-DAN-SCRIPT-ADB.md   # hướng dẫn dùng bằng script ADB
├── CONTRIBUTING.md
├── LICENSE
└── README.md
```

## Giấy phép

[MIT](LICENSE). Dự án cá nhân, không liên quan đến Vivo, Google hay các hãng điện thoại được nhắc tới. Tự chịu trách nhiệm với máy của mình.
