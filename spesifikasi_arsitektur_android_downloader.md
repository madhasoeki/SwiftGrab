# Spesifikasi Teknis & Blueprint Arsitektur: Aplikasi Android Media Downloader ("SwiftGrab")

Dokumen spesifikasi ini dirancang sebagai **System Prompt & Blueprint Implementasi** siap pakai untuk diserahkan langsung kepada *AI Coding Agent* (seperti Cursor, Claude Code, atau Windsurf) guna membangun aplikasi pengunduh video/audio Android yang bebas iklan, ringan, mendukung *background download*, *pause/resume*, serta unduhan instan tanpa perlu menempelkan (*paste*) tautan secara manual.

## 1. Keputusan Arsitektur: Kotlin Murni vs. Hibrida Kotlin + Rust

Pengembangan aplikasi Android berbasis [yt-dlp](https://github.com/yt-dlp/yt-dlp) memiliki dua pendekatan arsitektur utama yang dapat dipilih berdasarkan target performa dan pemeliharaan:

| Aspek Evaluasi | Opsi A: Kotlin + `youtubedl-android` (Direkomendasikan) | Opsi B: Hibrida Kotlin (UI) + Rust Engine via `UniFFI` |
| :--- | :--- | :--- |
| **Ekstraktor Media** | Membungkus ekosistem Python [yt-dlp](https://github.com/yt-dlp/yt-dlp) melalui pustaka [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android) (atau fork Kotlin modern [Darkstar085/youtubedl-kotlin](https://github.com/Darkstar085/youtubedl-kotlin)). | Menggunakan [yt-dlp](https://github.com/yt-dlp/yt-dlp) untuk ekstraksi URL JSON (`--dump-json`), lalu mengunduh aliran byte menggunakan mesin HTTP multi-segmen yang ditulis dalam Rust dan dihubungkan via [Mozilla UniFFI](https://github.com/mozilla/uniffi-rs). |
| **Mesin Unduhan & Pause/Resume** | Memanfaatkan modul biner C++ [aria2c](https://github.com/aria2/aria2) bawaan di dalam [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android) dengan argumen `--continue`. | Menulis pengelola *chunk* HTTP Range (`Range: bytes=start-end`) kustom di Rust (`tokio` + `reqwest`) dengan kontrol *atomic pause/resume* level memori. |
| **Kompatibilitas Situs** | Mendukung >1.000 situs secara instan dan memiliki fitur *auto-update* biner [yt-dlp](https://github.com/yt-dlp/yt-dlp) langsung dari dalam aplikasi (`YoutubeDL.getInstance().updateYoutubeDL()`). | Ekstraktor murni berbasis Rust sering rusak saat platform video mengubah algoritma *throttling*; tetap membutuhkan [yt-dlp](https://github.com/yt-dlp/yt-dlp) untuk ekstraksi URL mentah. |
| **Kompleksitas Build bagi AI Agent** | Sangat ramah bagi *AI Coding Agent* karena hanya menggunakan ekosistem standar Gradle + Kotlin tanpa kompilasi silang (*cross-compilation*) NDK. | Membutuhkan konfigurasi `cargo-ndk`, target ARM64 (`aarch64-linux-android`), dan pembuatan *binding* [Panduan Pengguna UniFFI](https://mozilla.github.io/uniffi-rs/). |
| **Referensi Open-Source Teruji** | Digunakan oleh aplikasi populer [JunkFood02/Seal](https://github.com/junkfood02/Seal) dan [deniscerri/ytdlnis](https://github.com/deniscerri/ytdlnis). | Digunakan pada aplikasi lintas platform skala besar yang membagi logika bisnis antara Android dan iOS via [Mozilla UniFFI](https://github.com/mozilla/uniffi-rs). |

**Rekomendasi Utama untuk AI Coding Agent:**
Gunakan **Opsi A (100% Kotlin + Jetpack Compose + `youtubedl-android` dengan modul `aria2c` dan `ffmpeg`)** untuk rilis pertama (MVP) agar AI Agent dapat menghasilkan APK yang langsung berfungsi tanpa kendala toolchain NDK. Jika di kemudian hari Anda ingin bereksperimen dengan **Rust**, tambahkan modul Rust melalui [Mozilla UniFFI](https://github.com/mozilla/uniffi-rs) khusus untuk pengunduh paralel HTTP langsung (*direct file downloader*) dan manajemen antrean kecepatan tinggi.

## 2. Solusi "Copy Link Langsung Download" di Android Modern

Sejak diberlakukannya kebijakan [Perubahan Privasi Android 10](https://developer.android.com/about/versions/10/privacy/changes) dan [Penanganan Clipboard Aman Android](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling), aplikasi yang berjalan di latar belakang (*background*) **diblokir oleh sistem operasi untuk membaca clipboard** kecuali aplikasi tersebut sedang tampil di layar (*foreground focus*) atau menjadi papan ketik bawaan (*default IME*).

Agar pengalaman pengguna tetap instan tanpa harus membuka aplikasi utama dan menekan tombol *paste*, rancang **3 jalur pemicu cepat (Zero-Paste Triggers)**:

1. **Jalur Utama — Share Sheet One-Tap (`ACTION_SEND` Transparent Activity):**
   * Saat pengguna menekan tombol **Bagikan (Share)** di YouTube, TikTok, Instagram, atau browser lalu memilih ikon aplikasi kita, sistem memanggil `ShareInterceptActivity` yang menggunakan tema transparan (`Theme.Translucent.NoTitleBar`) sesuai dokumentasi [Menerima Data Sederhana dari Aplikasi Lain](https://developer.android.com/develop/ui/compose/sharing/receive).
   * Aktivitas ini tidak membuka halaman utama aplikasi, melainkan langsung mengekstrak URL dari `Intent.EXTRA_TEXT`, memicu *Foreground Download Service* dengan konfigurasi kualitas bawaan (atau menampilkan *Bottom Sheet* mengambang selama 2 detik), lalu menutup dirinya sendiri (`finish()`). Pengguna tetap berada di aplikasi awal tanpa gangguan.
2. **Jalur Kedua — Tombol "Salin & Unduh" di Quick Settings Tile (`TileService`) / Floating Bubble:**
   * Ketika pengguna menyalin tautan dan menarik panel notifikasi lalu menekan ubin *Quick Settings* aplikasi, atau mengetuk tombol mengambang kecil, sebuah aktivitas transparan mengambil fokus selama <100 milidetik untuk membaca `ClipboardManager` secara sah menurut aturan [Penanganan Clipboard Aman Android](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling), memulai unduhan otomatis, dan langsung menghilang.
3. **Jalur Ketiga — Auto-Clipboard Listener saat Aplikasi Dibuka (`onWindowFocusChanged`):**
   * Jika pengguna menyalin tautan lalu mengetuk ikon aplikasi di *home screen*, aplikasi otomatis mendeteksi URL baru di *clipboard* saat fokus jendela aktif dan langsung memulai unduhan tanpa perlu menekan kolom input.

## 3. Spesifikasi Fitur & Arsitektur Modul

### A. Stack Teknologi Utama
* **Bahasa & UI:** Kotlin 2.0+, Jetpack Compose (Material Design 3, tema dinamis gelap/terang, 0% SDK iklan atau pelacak analitik).
* **Inti Pengunduh:** [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android) (mencakup bundel Python + [yt-dlp](https://github.com/yt-dlp/yt-dlp), `ffmpeg` untuk penggabungan audio/video, dan [aria2c](https://github.com/aria2/aria2) untuk akselerasi *multi-connection* serta *pause/resume*).
* **Layanan Latar Belakang:** Android *Foreground Service* dengan tipe layanan `dataSync` / *User-Initiated Data Transfer* yang mematuhi aturan [Tipe Foreground Service Android 14+](https://developer.android.com/about/versions/14/changes/fgs-types-required) dan [Batas Waktu Foreground Service Android 15](https://developer.android.com/develop/background-work/services/fgs/timeout).
* **Penyimpanan Status Lokal:** Room Database (SQLite) + Kotlin Coroutines `Flow` untuk menyimpan progres byte, argumen perintah, lokasi file `.part`, dan status antrean (`QUEUED`, `DOWNLOADING`, `PAUSED`, `COMPLETED`, `FAILED`).

### B. Mekanisme Pause, Resume, dan Background Download
1. **Identifikasi Proses (`processId`):** Setiap tugas unduhan diberi `taskId` unik (UUID). Saat memanggil `YoutubeDL.getInstance().execute(request, taskId, callback)`, `taskId` ini digunakan untuk mengontrol siklus hidup proses.
2. **Pause (Jeda):**
   * Ketika pengguna menekan tombol **Pause** di notifikasi atau di dalam aplikasi, panggil `YoutubeDL.getInstance().destroyProcessById(taskId)`.
   * Karena kita menyertakan flag `--continue` (`-c`) dan `--downloader aria2c` pada [yt-dlp](https://github.com/yt-dlp/yt-dlp), file sementara (`.part` dan `.aria2`) tetap tersimpan utuh di direktori unduhan.
   * Perbarui status tugas di Room Database menjadi `PAUSED`.
3. **Resume (Lanjutkan):**
   * Ketika pengguna menekan tombol **Resume**, *Service* menjalankan kembali `YoutubeDLRequest` yang sama dengan `taskId` dan direktori tujuan yang identik.
   * Biner [yt-dlp](https://github.com/yt-dlp/yt-dlp) dan [aria2c](https://github.com/aria2/aria2) secara otomatis mendeteksi file `.part` serta melanjutkan byte dari posisi terakhir tanpa mengulang dari 0%.
4. **Notifikasi Interaktif:**
   * Menampilkan bilah progres (*progress bar*), kecepatan unduhan (MB/s), estimasi waktu (ETA), serta tombol aksi langsung (`PendingIntent` ke `DownloadActionReceiver`) untuk **Pause**, **Resume**, dan **Cancel**.

## 4. Struktur Proyek untuk AI Coding Agent

Berikan struktur direktori berikut kepada AI Coding Agent Anda:

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/example/swiftgrab/
│   ├── SwiftGrabApp.kt                 # Inisialisasi YoutubeDL, FFmpeg, Aria2c, & NotificationChannel
│   ├── data/
│   │   ├── db/
│   │   │   ├── DownloadTaskEntity.kt   # Skema Room: id, url, title, status, progress, speed, filePath
│   │   │   ├── DownloadDao.kt          # Query CRUD & Flow<List<DownloadTaskEntity>>
│   │   │   └── AppDatabase.kt
│   │   └── repository/
│   │       └── DownloadRepository.kt   # Jembatan antara UI, Room DB, dan DownloadService
│   ├── service/
│   │   ├── DownloadForegroundService.kt # Mengelola antrean Coroutine, eksekusi yt-dlp, & WakeLock
│   │   ├── DownloadActionReceiver.kt    # BroadcastReceiver untuk tombol Pause/Resume/Cancel di Notifikasi
│   │   └── QuickClipboardTileService.kt # Quick Settings Tile untuk unduh 1-klik dari clipboard
│   ├── ui/
│   │   ├── MainActivity.kt             # UI Utama (Daftar Unduhan Aktif, Selesai, & Pengaturan Format)
│   │   ├── ShareInterceptActivity.kt   # Activity Transparan untuk menangkap Intent ACTION_SEND & Clipboard
│   │   └── components/                 # Komponen Jetpack Compose (DownloadCard, FormatSelectorSheet)
│   └── util/
│       └── UrlValidator.kt             # Ekstraksi & validasi URL menggunakan Regex dari teks campuran
```

## 5. Konfigurasi Kode Inti (Siap Diimplementasikan oleh AI Agent)

### A. Konfigurasi Gradle (`settings.gradle.kts` & `app/build.gradle.kts`)
Tambahkan repositori [JitPack untuk youtubedl-android](https://jitpack.io/p/yausername/youtubedl-android) dan dependensi pustaka di `app/build.gradle.kts`:

```kotlin
// Di settings.gradle.kts:
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// Di app/build.gradle.kts:
android {
    namespace = "com.example.swiftgrab"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.swiftgrab"
        minSdk = 26
        targetSdk = 35
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true // Wajib agar biner libpython, libffmpeg, dan libaria2c dapat dieksekusi
        }
    }
}

dependencies {
    val ytdlpVersion = "0.15.0"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlpVersion")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdlpVersion")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:$ytdlpVersion")

    // Jetpack Compose, Room, Lifecycle, & Coroutines
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
```

### B. Deklarasi `AndroidManifest.xml`
Mendeklarasikan izin *Foreground Service* sesuai [Dokumentasi Layanan Latar Belakang Android](https://developer.android.com/develop/background-work/services/fgs/service-types) dan *Intent Filter* untuk [Filter Intent ACTION_SEND](https://developer.android.com/training/basics/intents/filters):

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".SwiftGrabApp"
        android:extractNativeLibs="true"
        android:label="SwiftGrab"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">

        <!-- Activity Utama -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Activity Transparan Penangkap Share Link & Clipboard -->
        <activity
            android:name=".ui.ShareInterceptActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:taskAffinity=""
            android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>

        <!-- Layanan Unduhan Latar Belakang -->
        <service
            android:name=".service.DownloadForegroundService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="dataSync" />

        <!-- Receiver Aksi Tombol Notifikasi (Pause / Resume / Cancel) -->
        <receiver
            android:name=".service.DownloadActionReceiver"
            android:exported="false" />
    </application>
</manifest>
```

### C. Logika Eksekusi `yt-dlp` + `aria2c` dengan Dukungan Pause/Resume
Cuplikan kode inti untuk membangun perintah unduhan cepat dan tahan putus koneksi:

```kotlin
fun buildDownloadRequest(url: String, outputDir: File, audioOnly: Boolean): YoutubeDLRequest {
    return YoutubeDLRequest(url).apply {
        addOption("--no-mtime")
        addOption("-v")
        // Gunakan aria2c untuk multi-connection speed & resume yang stabil
        addOption("--downloader", "libaria2c.so")
        addOption("--downloader-args", "aria2c:\"-c -x 8 -s 8 -k 1M --file-allocation=none\"")
        // Lanjutkan file .part jika sebelumnya dijeda (Paused)
        addOption("--continue")
        addOption("--newline")
        if (audioOnly) {
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--embed-thumbnail")
            addOption("--embed-metadata")
        } else {
            addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
            addOption("--merge-output-format", "mp4")
        }
        addOption("-o", File(outputDir, "%(title).150B [%(id)s].%(ext)s").absolutePath)
    }
}
```

### D. Opsional: Integrasi Modul Rust via `UniFFI` (Jika Ingin Menambahkan Mesin Rust)
Jika Anda ingin meminta AI Agent membuat modul khusus di Rust (misalnya untuk mengunduh file langsung non-ekstraktor atau memvalidasi *checksum* berkecepatan tinggi), instruksikan AI Agent untuk menggunakan [Mozilla UniFFI](https://github.com/mozilla/uniffi-rs):

1. Buat crate Rust di `rust-core/src/lib.rs` dengan makro `#[uniffi::export]`.
2. Definisikan fungsi `start_chunked_download(url: String, dest_path: String, task_id: String)` dan `pause_download(task_id: String)` menggunakan runtime asinkron `tokio` dan klien HTTP `reqwest` dengan header `Range: bytes=${downloaded_bytes}-`.
3. Gunakan plugin Gradle [uniffi-kotlin-multiplatform-bindings](https://github.com/UbiqueInnovation/uniffi-kotlin-multiplatform-bindings) atau `cargo-ndk` untuk menghasilkan file `.so` ke dalam `app/src/main/jniLibs/` beserta file pembungkus Kotlin secara otomatis.

## 6. Prompt Siap Pakai untuk Diserahkan ke AI Coding Agent

Salin instruksi di bawah ini untuk memulai sesi bersama AI Coding Agent Anda:

> **Prompt untuk AI Coding Agent:**
> "Bangun aplikasi Android lengkap bernama **SwiftGrab** menggunakan Kotlin dan Jetpack Compose berdasarkan dokumen spesifikasi arsitektur ini. Pastikan:
> 1. Mengintegrasikan `youtubedl-android` (`library`, `ffmpeg`, `aria2c`) dengan `useLegacyPackaging = true` pada `build.gradle.kts`.
> 2. Mengimplementasikan `ShareInterceptActivity` berlatar transparan yang menangkap `Intent.ACTION_SEND` (`text/plain`), mengekstrak URL pertama menggunakan Regex, dan langsung mengirimkannya ke `DownloadForegroundService` tanpa membuka layar utama (Zero-Paste Direct Download), ditambah deteksi clipboard otomatis saat `MainActivity` menerima fokus.
> 3. Mengimplementasikan `DownloadForegroundService` (`foregroundServiceType="dataSync"`) dan `Room Database` yang mendukung fitur **Pause**, **Resume** (menggunakan `destroyProcessById(taskId)` dan flag `--continue` pada `yt-dlp`/`aria2c`), **Cancel**, serta pembaruan bilah progres di notifikasi secara *real-time*.
> 4. Menyediakan fitur pembaruan biner `yt-dlp` satu klik di menu Pengaturan agar ekstraktor selalu kompatibel dengan perubahan situs terbaru."
