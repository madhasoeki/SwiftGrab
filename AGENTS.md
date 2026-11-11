# AGENTS.md — Panduan Kerja AI Coding Agent untuk Proyek SwiftGrab

Dokumen `AGENTS.md` ini mengikuti standar terbuka [AGENTS.md](https://agents.md/) dan panduan [Kustomisasi Agent dengan AGENTS.md di Android Studio](https://developer.android.com/studio/gemini/agent-files). Setiap *AI Coding Agent* (GitHub Copilot, Claude Code, Cline, Roo Code, Cursor, atau Gemini di Android Studio) **wajib membaca dan mematuhi aturan di bawah ini** sebelum membuat atau memodifikasi kode.

## 1. Konteks & Tujuan Proyek
* **Nama Aplikasi:** SwiftGrab (`com.example.swiftgrab`)
* **Deskripsi Singkat:** Aplikasi Android pengunduh video dan audio bebas iklan (*zero-ads, zero-tracking*) berbasis [yt-dlp](https://github.com/yt-dlp/yt-dlp), [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android), dan [aria2c](https://github.com/aria2/aria2) yang mendukung *background download*, *pause/resume*, serta unduhan instan tanpa *paste* manual (*Zero-Paste Share Sheet & Auto-Clipboard*).
* **Target SDK:** `minSdk = 26` (Android 8.0), `targetSdk = 35` / `compileSdk = 35` (Android 15).
* **Referensi Arsitektur Utama:** Mengadopsi praktik terbaik dari proyek *open-source* [JunkFood02/Seal](https://github.com/junkfood02/Seal) dan [deniscerri/ytdlnis](https://github.com/deniscerri/ytdlnis).

## 2. Perintah Build, Lint, dan Verifikasi (Terminal Commands)
Saat menyelesaikan perubahan kode, jalankan perintah berikut untuk memastikan tidak ada regresi kompilasi:

| Tindakan | Perintah Terminal | Catatan Penting |
| :--- | :--- | :--- |
| **Build Debug APK** | `./gradlew assembleDebug` | Wajib lulus tanpa *error* sebelum menyelesaikan tugas. |
| **Pemeriksaan Lint & KSP** | `./gradlew lintDebug` | Memastikan anotasi Room (`ksp`) dan aturan Android Manifest valid. |
| **Menjalankan Unit Test** | `./gradlew testDebugUnitTest` | Gunakan *Fake Repository* (hindari Mockito sesuai rekomendasi [Android Skills](https://github.com/android/skills)). |
| **Instalasi ke Emulator/Perangkat** | `./gradlew installDebug` | Gunakan [Android CLI](https://github.com/android/skills/blob/main/devtools/android-cli/SKILL.md) atau `adb` jika perangkat terhubung. |

## 3. Aturan Arsitektur & Penulisan Kode (Hard Constraints)

### A. Pola UI & Jetpack Compose (Compose-First)
1. **Unidirectional Data Flow (UDF) & State Hoisting:**
   * Semua layar `@Composable` harus bersifat *stateless* (menerima `uiState` dan lambda *event* sebagai parameter) sesuai panduan [Android Compose UI Skills](https://www.skills.sh/humanshell/android-skills/android-compose-ui).
   * Gunakan `StateFlow` di dalam `ViewModel` dengan `SharingStarted.WhileSubscribed(5_000)` dan konsumsi di UI menggunakan `collectAsStateWithLifecycle()`.
2. **Isolasi Rekomposisi Progres Unduhan:**
   * Progres unduhan dari [yt-dlp](https://github.com/yt-dlp/yt-dlp) diperbarui setiap 100–250 milidetik. **Jangan** memicu rekomposisi seluruh layar `LazyColumn` setiap kali progres satu item berubah.
   * Gunakan `key = { task.id }` pada `LazyColumn` dan kirimkan pembacaan progres secara *deferred* (lambda `() -> Float`) ke komponen `LinearProgressIndicator` agar mengikuti praktik [Jetpack Compose Performance](https://www.skills.sh/android/skills).
3. **Dukungan Edge-to-Edge:**
   * Panggil `enableEdgeToEdge()` di `MainActivity` dan kelola `WindowInsets` (`systemBars`, `ime`) menggunakan `Scaffold` `contentPadding` sesuai panduan [Skill Edge-to-Edge Resmi Android](https://github.com/android/skills/blob/main/system/edge-to-edge/SKILL.md).

### B. Integrasi `youtubedl-android`, `aria2c`, dan Native Packaging
1. **Konfigurasi Packaging Wajib:**
   * Di dalam `app/build.gradle.kts`, blok `packaging { jniLibs { useLegacyPackaging = true } }` dan `android:extractNativeLibs="true"` di `AndroidManifest.xml` **tidak boleh dihapus**. Biner native `libpython.so`, `libffmpeg.so`, dan `libaria2c.so` dari [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android) membutuhkan ekstraksi ke sistem file perangkat agar dapat dieksekusi oleh `ProcessBuilder`.
2. **Inisialisasi Asinkron (Anti-ANR):**
   * Panggilan `YoutubeDL.getInstance().init(appContext)`, `FFmpeg.getInstance().init(appContext)`, dan `Aria2c.getInstance().init(appContext)` mengekstrak arsip biner pada saat pertama kali dijalankan.
   * **Wajib** dijalankan di dalam `CoroutineScope(Dispatchers.IO)` (bukan di *Main Thread* `Application.onCreate()` secara sinkron) untuk mencegah *Application Not Responding* (ANR).
3. **Mekanisme Pause & Resume yang Aman:**
   * Setiap unduhan harus memiliki `taskId` (UUID String) yang disimpan di `Room Database` (`DownloadTaskEntity`).
   * **Pause:** Panggil `YoutubeDL.getInstance().destroyProcessById(taskId)` di thread latar belakang, lalu ubah status database menjadi `PAUSED`. Jangan menghapus file `.part` atau `.aria2`.
   * **Resume:** Bangun ulang `YoutubeDLRequest` dengan flag `--continue` (`-c`) dan `--downloader libaria2c.so` ke path direktori yang sama, lalu eksekusi ulang dengan `taskId` yang sama.
   * **Cancel:** Panggil `destroyProcessById(taskId)`, hapus file sementara `.part` dan `.aria2` terkait di disk, lalu hapus atau tandai `CANCELED` di database.

### C. Keamanan Intent, Clipboard, & Foreground Service
1. **Kepatuhan Android 10–15 untuk Clipboard & Share Sheet:**
   * Jangan pernah membuat *service* latar belakang yang melakukan *polling* `ClipboardManager` secara diam-diam karena diblokir oleh kebijakan [Penanganan Clipboard Aman Android](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling).
   * Gunakan `ShareInterceptActivity` (`Theme.Translucent.NoTitleBar`) untuk menangkap [Intent ACTION_SEND](https://developer.android.com/develop/ui/compose/sharing/receive), validasi URL menggunakan Regex (tolak URI `file://` atau `content://` lokal yang mencurigakan sesuai prinsip `android-intent-security` di [Direktori Android Skills](https://www.skills.sh/android/skills)), kirim ke `DownloadForegroundService`, tampilkan `Toast` konfirmasi singkat, dan langsung panggil `finish()`.
   * Untuk pembacaan *clipboard* otomatis saat membuka aplikasi utama, baca `ClipboardManager` di dalam `onWindowFocusChanged(hasFocus = true)` pada `MainActivity`.
2. **Kepatuhan Foreground Service Android 14 & 15:**
   * Deklarasikan `android:foregroundServiceType="dataSync"` di `AndroidManifest.xml` beserta izin `FOREGROUND_SERVICE_DATA_SYNC` sesuai aturan [Tipe Foreground Service Android 14+](https://developer.android.com/about/versions/14/changes/fgs-types-required).
   * Panggil `ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)` dalam waktu kurang dari 5 detik sejak layanan dimulai.
   * Tangani callback `Service.onTimeout(startId, fgsType)` untuk mematuhi aturan [Batas Waktu Foreground Service Android 15](https://developer.android.com/develop/background-work/services/fgs/timeout) dengan menjeda (*pause*) unduhan aktif secara aman dan menyimpan state ke Room Database.

### D. Aturan ProGuard / R8
* Jika `isMinifyEnabled = true` diaktifkan pada build `release`, pastikan aturan di `proguard-rules.pro` mempertahankan kelas-kelas JNI dan Jackson/Gson milik [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android):
  ```proguard
  -keep class com.yausername.youtubedl_android.** { *; }
  -keep class com.yausername.ffmpeg.** { *; }
  -keep class com.yausername.aria2c.** { *; }
  -keep class com.fasterxml.jackson.** { *; }
  ```

## 4. Konvensi Penamaan & Struktur Paket
* `com.example.swiftgrab.data.db` — Entitas Room (`DownloadTaskEntity`), DAO (`DownloadDao`), dan `AppDatabase`.
* `com.example.swiftgrab.data.repository` — `DownloadRepository` sebagai *Single Source of Truth* (SSOT).
* `com.example.swiftgrab.service` — `DownloadForegroundService`, `DownloadActionReceiver`, dan `QuickClipboardTileService`.
* `com.example.swiftgrab.ui` — `MainActivity`, `ShareInterceptActivity`, `DownloadViewModel`, dan komponen UI di subpaket `components` serta `theme`.
* **Larangan Keras:** Jangan menambahkan SDK periklanan (AdMob, Unity Ads, Meta Audience Network), SDK pelacak analitik pihak ketiga, atau telemetri tersembunyi dalam bentuk apa pun.
