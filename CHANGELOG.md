# Changelog

Semua perubahan penting pada proyek **SwiftGrab** didokumentasikan di berkas ini.

Format mengikuti [Keep a Changelog](https://keepachangelog.com/id-ID/1.1.0/) dan penomoran versi mengikuti [Semantic Versioning](https://semver.org/).

> **Catatan:** Catatan rilis tiap versi (`## [X.Y.Z]`) diambil otomatis oleh workflow `.github/workflows/release-apk.yml` untuk mengisi deskripsi GitHub Release sekaligus ditampilkan di dialog pembaruan dalam aplikasi (*In-App Auto-Updater*). Tulis poin secara ringkas, jelas, dan mudah dipahami pengguna.

## [Unreleased]

### Ditambahkan
- Paket identitas merek lengkap **SwiftGrab** berbasis simbol **Meteor Catch** (`assets/logo/swiftgrab_logo.svg`, `swiftgrab_lockup_light.svg`, `swiftgrab_lockup_dark.svg`, dan `assets/logo/BRAND_GUIDELINES.md`).
- Dukungan ikon aplikasi Android adaptif (`drawable/ic_launcher_foreground.xml`) serta ikon bertema monokrom Android 13+ (`drawable/ic_launcher_monochrome.xml`).
- Aset fondasi desain **Meteor Catch**: font **Montserrat** berlisensi SIL OFL 1.1 (`res/font/montserrat_{medium,semibold,bold}.otf`), skala tipografi & bentuk (`Type.kt`, `Shape.kt`), vektor merek dalam aplikasi (`ic_swiftgrab_symbol_{arc,meteor}.xml`, `ic_swiftgrab_wordmark.xml`, `ic_stat_swiftgrab.xml`), komponen bersama `BrandMark.kt` (`SwiftGrabSymbol`, `SwiftGrabAppIconTile`, `SwiftGrabWordmark`, `SwiftGrabHorizontalLockup`, `SwiftGrabPresetOptionCard`), serta pengujian UI Robolectric (`BrandAndSheetsUiTest.kt`).

### Diubah
- Pembaruan ikon peluncur (*launcher icon*) aplikasi di seluruh densitas (`mdpi`–`xxxhdpi`) ke desain **Meteor Catch** dengan latar `#111318`.
- Penyelarasan palet tema Material Design 3 aplikasi (`Color.kt`, `Theme.kt`, `DESIGN.md`, dan `README.md`) dari *Deep Cobalt & Emerald* ke skema **Obsidian Ink & Signal Ember** lengkap dengan token `surfaceContainer*`, `inverse*`, `tertiaryContainer`/`errorContainer`, `android:windowBackground` anti-kilatan *cold start* (`themes.xml` & `values-night/themes.xml`), serta penyesuaian kontras WCAG AA pada mode terang (`secondary` `#0D7A54`, `tertiary` `#9A6000`).
- Perombakan menyeluruh antarmuka Jetpack Compose selaras **Meteor Catch**: `TopAppBar` rata kiri dengan `SwiftGrabHorizontalLockup`, `EmptyStateView` berbingkai busur orbital, kartu saran papan klip *Signal Ember Catch Card* (`ClipboardSuggestionBanner`), kartu tugas ber-border kontekstual dengan ikon status non-warna & metrik tabular (`DownloadTaskCard`), serta tata letak baru pada `QuickFormatSheet`, `SettingsBottomSheet` (dengan kartu identitas merek dan hierarki CTA), dan `UpdateAvailableDialog`.
- Penyelarasan krom sistem: ikon Quick Settings Tile (`QuickClipboardTileService`) dan notifikasi unduhan/pembaruan (`DownloadForegroundService`, `AppUpdateManager`) kini memakai vektor resmi `ic_stat_swiftgrab` dengan aksen warna `#F04A24`.

### Diperbaiki
- Perbaikan kontras warna teks tombol **Simpan & Selesai** pada `XLoginDialog` agar menggunakan `onSecondary` saat sesi login X terdeteksi (`isAuthDetected`).

## [0.0.5] - 2026-11-19

### Keamanan
- Penguatan WebView `XLoginDialog` dengan menonaktifkan akses berkas lokal (`allowFileAccess = false`, `allowContentAccess = false`), pembatasan navigasi hanya untuk skema HTTPS dan domain resmi/otentikasi, serta validasi token sesi (`auth_token`) sebelum menyimpan cookies.
- Isolasi berkas `x_cookies.txt` hanya untuk domain resmi X/Twitter (`CookieStorageManager.isXDomain`).
- Penonaktifan backup data aplikasi (`android:allowBackup="false"`) pada `AndroidManifest.xml`.
- Penolakan alamat IP privat, loopback, localhost, dan kredensial userinfo pada `UrlValidator`.

### Diubah
- Optimasi indeks basis data Room pada kolom `status` dan `createdAt` di `DownloadTaskEntity` (migrasi versi 3) serta konfigurasi ekspor skema Room via KSP.
- Peningkatan target sentuh minimal 48dp (`heightIn(min = 48.dp)`) pada tombol interaktif `ClipboardSuggestionBanner` dan `SettingsBottomSheet`.
- Perbaikan semantik aksesibilitas TalkBack untuk opsi preset (`Modifier.selectableGroup()` dan `Modifier.selectable(role = Role.RadioButton)` dengan `RadioButton(onClick = null)`) di `QuickFormatSheet` dan `SettingsBottomSheet`.
- Migrasi penggunaan API `TopAppBarDefaults.centerAlignedTopAppBarColors` yang usang ke `topAppBarColors` pada `MainScreen` dan `XLoginDialog`.

### Diperbaiki
- Pemisahan ID notifikasi tugas aktif (`NOTIFICATION_FOREGROUND_ID = 1001`) dan tugas terjeda (`pausedNotificationId`) agar notifikasi terjeda beserta tombol Lanjutkan tetap bertahan di status bar saat unduhan aktif selesai.
- Penanganan Partial WakeLock (`PARTIAL_WAKE_LOCK`, tag `SwiftGrab::DownloadWakeLock`) dengan batas waktu aman 60 menit saat unduhan berjalan, serta pelepasan otomatis saat antrean kosong, timeout FGS Android 15, maupun saat service dihancurkan.
- Propagasi `CancellationException` pada pembersihan coroutine (`NonCancellable`) di foreground service untuk mematuhi prinsip structured concurrency.
- Perbaikan regex parser ETA `aria2c` untuk mendukung durasi jam (`ETA:1h5m20s`) dan format `>= 1 jam` (`01:05:20`).
- Penambahan query antrean FIFO (`observeTasksByStatusesAsc`) pada `DownloadDao` agar tugas diantrekan secara urut berdasarkan waktu pembuatan (`createdAt ASC`).
- Penanganan insets keyboard (`imePadding`) pada dialog login X (`XLoginDialog`) agar bidang input tidak tertutup papan ketik virtual.
- Perbaikan normalisasi pemisah path sistem operasi silang (Windows `\` vs Linux `/`) pada `DownloadFileManager.isPathWithinAllowedRoots` dan `StorageHelper.resolvePathFromDocumentId`.

## [0.0.4] - 2026-11-15

### Ditambahkan
- Pengambilan metadata & thumbnail media asinkron (`MediaMetadataExtractor`) menggunakan `yt-dlp --dump-single-json --no-download` untuk menampilkan judul asli, gambar pratinjau, dan durasi media (`durationSeconds`) pada kartu unduhan serta dialog `QuickFormatSheet`.
- Migrasi skema Room Database (`v1` ke `v2`) untuk menambahkan kolom `durationSeconds` pada tabel `download_tasks`.
- Tombol aksi **Buka** dan **Bagikan** langsung pada notifikasi unduhan selesai (`NotificationFileActionActivity` dan `FileSharingHelper`).
- Dukungan unduhan batch multi-baris pada `AddManualUrlDialog` melalui pemisahan banyak URL (`UrlValidator.extractUrls`).
- Pengujian unit dan UI Jetpack Compose untuk `DownloadViewModel`, `MainScreen`, dan `DownloadTaskCard`, serta pemeriksaan `lintDebug` di CI.

### Diubah
- Isolasi rekomposisi progres unduhan pada `LazyColumn` dengan memindahkan progres langsung (*progress*, *speed*, *ETA*, *bytes*) ke `StateFlow<Map<String, LiveProgress>>` dan pembacaan *deferred* di `DownloadTaskCard` agar UI daftar tidak ikut direkomposisi setiap 250 ms.
- Otomatisasi review Pull Request menggunakan PR-Agent serta integrasi ekstraksi catatan rilis dari `CHANGELOG.md` pada workflow `Release APK`.

### Diperbaiki
- Pemulihan otomatis tugas unduhan berstatus `DOWNLOADING` yang terhenti akibat *crash* atau *force-kill* menjadi terjeda (`PAUSED`) saat aplikasi atau layanan dimulai ulang.
- Pembersihan berkas sementara (*staging*) di penyimpanan privat saat pengguna menghapus tugas unduhan.

## [0.0.3] - 2026-11-15

### Ditambahkan
- Fitur **In-App Auto-Updater** langsung dari GitHub Releases (`AppUpdateManager` dan `UpdateAvailableDialog`) dengan pemeriksaan SemVer, pemilihan APK sesuai ABI perangkat, progres unduhan via `DownloadManager`, dan instalasi lewat `FileProvider`.
- Dukungan penyimpanan publik ke folder `Download/SwiftGrab` dengan kompatibilitas Xiaomi HyperOS serta pemindaian `MediaStore` otomatis agar hasil unduhan langsung muncul di Galeri.

### Diubah
- Pembaruan dependensi `androidx.compose:compose-bom` ke `2026.09.00` dan `androidx.core:core-ktx` ke `1.19.1`.

## [0.0.2] - 2026-11-11

### Ditambahkan
- Dukungan *ABI splits* pada build rilis (`arm64-v8a`, `armeabi-v7a`, `x86_64`, dan `universal`) agar ukuran APK lebih ringan.

### Diperbaiki
- Kegagalan inisialisasi mesin unduhan (`Download engine failed to initialize`) pada build rilis ProGuard/R8 beserta mekanisme *auto-retry*.
- Fallback *signing* pada workflow `Release APK` ketika secret keystore rilis belum dikonfigurasi.

## [0.0.1] - 2026-11-11

### Ditambahkan
- Rilis awal **SwiftGrab**: pengunduh video & audio Android bebas iklan (*zero-ads, zero-tracking*) berbasis `yt-dlp`, `FFmpeg`, dan `aria2c`.
- Dukungan *Zero-Paste Share Sheet* (`ShareInterceptActivity`), deteksi *clipboard* aman (`onWindowFocusChanged`), dan Quick Settings Tile.
- Layanan unduhan latar belakang berbasis Foreground Service (`dataSync`), notifikasi progres, serta *pause/resume* aman dengan `--continue`.
- Antarmuka Jetpack Compose Material 3 dengan dukungan *edge-to-edge*, preset format/kualitas, dan riwayat unduhan di Room Database.

### Diperbaiki
- Penyesuaian aturan ProGuard/R8 untuk `youtubedl-android` serta penyederhanaan tata letak dan aksesibilitas UI.

[Unreleased]: https://github.com/madhasoeki/SwiftGrab/compare/v0.0.5...HEAD
[0.0.5]: https://github.com/madhasoeki/SwiftGrab/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/madhasoeki/SwiftGrab/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/madhasoeki/SwiftGrab/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/madhasoeki/SwiftGrab/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.1
