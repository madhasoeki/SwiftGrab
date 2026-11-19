# Changelog

Semua perubahan penting pada proyek **SwiftGrab** didokumentasikan di berkas ini.

Format mengikuti [Keep a Changelog](https://keepachangelog.com/id-ID/1.1.0/) dan penomoran versi mengikuti [Semantic Versioning](https://semver.org/).

> **Catatan:** Catatan rilis tiap versi (`## [X.Y.Z]`) diambil otomatis oleh workflow `.github/workflows/release-apk.yml` untuk mengisi deskripsi GitHub Release sekaligus ditampilkan di dialog pembaruan dalam aplikasi (*In-App Auto-Updater*). Tulis poin secara ringkas, jelas, dan mudah dipahami pengguna.

## [Unreleased]

### Diubah
- Optimasi indeks basis data Room pada kolom `status` dan `createdAt` di `DownloadTaskEntity` serta konfigurasi ekspor skema Room via KSP.
- Peningkatan target sentuh minimal 48dp (`heightIn(min = 48.dp)`) pada tombol interaktif `ClipboardSuggestionBanner` dan `SettingsBottomSheet`.
- Perbaikan semantik aksesibilitas TalkBack untuk opsi preset (`Modifier.selectableGroup()` dan `Modifier.selectable(role = Role.RadioButton)` dengan `RadioButton(onClick = null)`) di `QuickFormatSheet` dan `SettingsBottomSheet`.
- Migrasi penggunaan API `TopAppBarDefaults.centerAlignedTopAppBarColors` yang usang ke `topAppBarColors` pada `MainScreen` dan `XLoginDialog`.

### Diperbaiki
- Penanganan insets keyboard (`imePadding`) pada dialog login X (`XLoginDialog`) agar bidang input tidak tertutup papan ketik virtual.

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

[Unreleased]: https://github.com/madhasoeki/SwiftGrab/compare/v0.0.4...HEAD
[0.0.4]: https://github.com/madhasoeki/SwiftGrab/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/madhasoeki/SwiftGrab/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/madhasoeki/SwiftGrab/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.1
