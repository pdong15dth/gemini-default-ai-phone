# Build app và deploy landing page

Tài liệu này dành cho người fork repo hoặc tự phát hành bản APK. Không cần làm theo nếu bạn chỉ cài APK từ GitHub Releases.

## Mục lục

- [Yêu cầu](#yeu-cau)
- [Build APK](#build-apk)
- [Ký APK phát hành](#ky-apk-phat-hanh)
- [Phát hành lên GitHub Releases](#phat-hanh-len-github-releases)
- [Deploy landing page lên GitHub Pages](#deploy-landing-page-len-github-pages)
- [Chuyển từ script ADB sang app](#chuyen-tu-script-adb-sang-app)

<a name="yeu-cau"></a>

## Yêu cầu

- JDK 17. Android Studio mới đã kèm JBR 21 nhưng Gradle vẫn cần JDK 17 trở lên.
- Android SDK 35 và Build Tools 35.
- Windows, macOS hoặc Linux.

Đặt đường dẫn SDK trong `android/local.properties` (file này đã nằm trong `.gitignore`):

```properties
sdk.dir=C\:\\dev\\android-sdk
```

<a name="build-apk"></a>

## Build APK

```bash
cd android
./gradlew assembleDebug
```

Windows:

```bat
cd android
gradlew.bat assembleDebug
```

APK debug nằm ở `android/app/build/outputs/apk/debug/app-debug.apk` (khoảng 24 MB vì chưa minify).

Bản release bật R8, khoảng 2 MB:

```bash
./gradlew assembleRelease
```

APK ở `android/app/build/outputs/apk/release/app-release.apk`.

Chạy unit test:

```bash
./gradlew testDebugUnitTest
```

<a name="ky-apk-phat-hanh"></a>

## Ký APK phát hành

Khi không có biến môi trường, Gradle ký bản release bằng debug keystore để bản beta vẫn cài được. Cách đó **không phù hợp để phát hành lâu dài**: mỗi máy CI tạo một debug keystore khác nhau, người dùng sẽ phải gỡ app trước khi cập nhật.

Muốn phát hành đúng, tạo một keystore cố định:

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias ai-key-bridge \
  -keyalg RSA -keysize 4096 -validity 10000
```

Đặt 4 secret trong GitHub repo (Settings → Secrets and variables → Actions):

| Secret | Giá trị |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Nội dung file keystore mã hoá base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Mật khẩu keystore |
| `ANDROID_KEY_ALIAS` | `ai-key-bridge` |
| `ANDROID_KEY_PASSWORD` | Mật khẩu key |

Mã hoá keystore trên Windows:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))
```

Không commit keystore vào repo. Nếu làm mất keystore, không thể phát hành bản cập nhật cho người dùng cũ.

Build local với keystore:

```powershell
$env:ANDROID_KEYSTORE_PATH = "F:\path\release.keystore"
$env:ANDROID_KEYSTORE_PASSWORD = "..."
$env:ANDROID_KEY_ALIAS = "ai-key-bridge"
$env:ANDROID_KEY_PASSWORD = "..."
.\gradlew.bat assembleRelease
```

<a name="phat-hanh-len-github-releases"></a>

## Phát hành lên GitHub Releases

Workflow `.github/workflows/android.yml` chạy khi:

- push lên `main` có thay đổi trong `android/**`;
- mở pull request;
- đẩy tag dạng `v*`.

Khi đẩy tag, workflow build release và gắn file `ai-key-bridge.apk` vào release. Landing page tải đúng file này qua:

```text
https://github.com/pdong15dth/gemini-default-ai-phone/releases/latest/download/ai-key-bridge.apk
```

Ví dụ phát hành bản beta đầu tiên:

```bash
git tag v0.9.0
git push origin v0.9.0
```

Kiểm tra APK trước khi đẩy tag:

```bash
apksigner verify --print-certs android/app/build/outputs/apk/release/app-release.apk
```

<a name="deploy-landing-page-len-github-pages"></a>

## Deploy landing page lên GitHub Pages

Landing page là HTML/CSS/JS tĩnh trong `web/`. Không cần build, không cần Node.

### 1. Bật Pages một lần

Repo → **Settings → Pages → Build and deployment → Source: GitHub Actions**.

Không chọn "Deploy from a branch". Workflow `pages.yml` upload trực tiếp thư mục `web/` và deploy bằng `actions/deploy-pages`.

### 2. Đẩy thay đổi

```bash
git add web .github/workflows/pages.yml
git commit -m "Update landing page"
git push origin main
```

Workflow tự chạy khi `web/**` thay đổi, hoặc bấm **Run workflow** trong tab Actions.

URL sau khi deploy:

```text
https://pdong15dth.github.io/gemini-default-ai-phone/
```

Lần deploy đầu có thể mất vài phút. Nếu workflow báo `Get Pages site failed`, kiểm tra lại bước 1.

### 3. Xem trước trên máy

Mở trực tiếp `web/index.html` vẫn xem được, nhưng nên chạy một static server để đường dẫn tương đối hoạt động giống production:

```bash
npx serve web
```

Các file cần có trước khi deploy:

- `web/index.html`
- `web/privacy.html`
- `web/terms.html`
- `web/404.html`
- `web/sitemap.xml`, `web/robots.txt`
- `web/.nojekyll`
- `web/assets/`

<a name="chuyen-tu-script-adb-sang-app"></a>

## Chuyển từ script ADB sang app

App dùng cùng cơ chế với `scripts/gemini.sh` nhưng tự động quản lý qua **Engine nội bộ** (Gỡ lỗi không dây) hoặc **Shizuku**. Nếu đang dùng script thủ công cũ:

1. Dừng script cũ để tránh hai tiến trình cùng chạy:
   ```bash
   adb shell "pkill -9 -f gemini.sh; pkill -9 -f logcat"
   ```
2. Cài APK và mở thẳng **Trang chủ**.
3. Làm theo 3 bước trên **Trang chủ**: kiểm tra 4 nhóm quyền ở **Bước 1**, bật Script nền ở **Bước 2** (qua Engine nội bộ hoặc Shizuku), rồi bấm **Khóa Gỡ lỗi ngay (Bank-Safe)** ở **Bước 3**.

App không sửa phân vùng hệ thống và không cần root.
