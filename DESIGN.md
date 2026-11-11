# DESIGN.md — Spesifikasi Desain UI/UX & Alur Interaksi SwiftGrab

Dokumen `DESIGN.md` ini menjadi acuan desain visual, tata letak komponen, dan pengalaman pengguna (*User Experience*) bagi *AI Coding Agent* saat membangun antarmuka **SwiftGrab** menggunakan **Jetpack Compose** dan **Material Design 3 (M3)**.

## 1. Filosofi Desain Utama
1. **Zero-Friction, Zero-Paste (Kecepatan Satu Ketukan):** Pengguna tidak boleh dipaksa membuka aplikasi, mencari kolom input, menempelkan tautan, memilih belasan menu rumit, baru menekan tombol unduh. Unduhan harus bisa dimulai dalam **1 ketukan** langsung dari menu *Share* aplikasi lain atau otomatis saat aplikasi dibuka.
2. **Bersih & Bebas Gangguan (Zero-Bloatware):** Tidak ada *banner* iklan, tidak ada *pop-up* promosi, tidak ada *tab* berita atau browser bawaan yang berat. Aplikasi langsung menampilkan daftar unduhan dan kontrol cepat.
3. **Transparansi Status Real-Time:** Setiap tugas unduhan menampilkan informasi teknis yang jelas namun rapi: persentase, ukuran terunduh vs. total ukuran, kecepatan unduh (`MB/s`), estimasi waktu selesai (`ETA`), dan tombol aksi instan (**Pause**, **Resume**, **Cancel**, **Play/Share**).
4. **Adaptive Material You & Edge-to-Edge:** Mendukung *Dynamic Color* (Android 12+) dengan palet *fallback* kontras tinggi dan tata letak layar penuh (*edge-to-edge*) sesuai panduan [Skill Edge-to-Edge Android](https://github.com/android/skills/blob/main/system/edge-to-edge/SKILL.md).

## 2. Sistem Warna & Tipografi (Material Design 3 Tokens)

Jika perangkat pengguna tidak mendukung *Dynamic Color* (atau pengguna mematikan warna dinamis), gunakan skema warna **Deep Cobalt & Emerald Accent** yang nyaman di mata pada mode gelap (*Dark Mode Default*):

| Token Material 3 | Mode Gelap (Default) | Mode Terang | Penggunaan Komponen |
| :--- | :--- | :--- | :--- |
| `background` / `surface` | `#0B0F17` | `#F8FAFC` | Latar belakang utama layar (`Scaffold`). |
| `surfaceContainer` | `#161F30` | `#FFFFFF` | Latar belakang kartu unduhan (`DownloadTaskCard`). |
| `surfaceContainerHigh` | `#1E293B` | `#F1F5F9` | Latar belakang *Bottom Sheet* pemilih format dan *Quick Action Bar*. |
| `primary` | `#38BDF8` | `#0284C7` | Bilah progres aktif (`DOWNLOADING`), tombol utama, dan ikon aktif. |
| `secondary` | `#34D399` | `#059669` | Indikator selesai (`COMPLETED`), lencana kecepatan tinggi (`aria2c`). |
| `tertiary` | `#FBBF24` | `#D97706` | Status dijeda (`PAUSED`) dan bilah progres tertunda. |
| `error` | `#F87171` | `#DC2626` | Status gagal (`FAILED`), tombol hapus/batalkan. |

### Aturan Tipografi
* **Judul Aplikasi & Header:** `MaterialTheme.typography.titleLarge` (`FontWeight.Bold`).
* **Judul Video/Audio di Kartu:** `MaterialTheme.typography.titleMedium` (`maxLines = 2`, `TextOverflow.Ellipsis`).
* **Metrik Kecepatan, Ukuran, & Persentase:** `MaterialTheme.typography.labelMedium` dengan `fontFeatureSettings = "tnum"` (*tabular numbers* / angka lebar tetap) atau `FontFamily.Monospace` agar teks angka **tidak bergoyang/berkedip** saat kecepatan unduhan berubah setiap 100 milidetik.

## 3. Arsitektur Layar & Komponen UI

Aplikasi menggunakan arsitektur **Satu Layar Utama (`MainScreen`) + Satu Overlay Transparan (`ShareInterceptActivity`)** tanpa navigasi berlapis yang membingungkan.

### A. Layar 1: `ShareInterceptActivity` (Overlay Unduh Instan Tanpa Pindah Aplikasi)
Saat pengguna menekan tombol **Share (Bagikan)** di YouTube, TikTok, Instagram, X/Twitter, atau browser lalu memilih **SwiftGrab** via [Intent ACTION_SEND](https://developer.android.com/develop/ui/compose/sharing/receive), aplikasi mendukung **2 mode perilaku** (dapat diatur di Pengaturan):

1. **Mode Instan / One-Tap (Default):**
   * Tidak menampilkan jendela apa pun yang menghalangi layar.
   * Langsung mengirim tautan ke `DownloadForegroundService` menggunakan preset pilihan pengguna (misalnya: *Video Terbaik MP4 1080p* atau *Audio MP3 Terbaik*).
   * Menampilkan `Toast` / *heads-up notification* singkat: `"Mengunduh di latar belakang: [URL]"` dan pengguna tetap bisa lanjut *scrolling* di aplikasi awal.
2. **Mode Quick Format Sheet (Pilihan Cepat):**
   * Menampilkan `ModalBottomSheet` kompak di bagian bawah layar di atas aplikasi yang sedang dibuka pengguna (latar belakang *scrim* semi-transparan).
   * Menampilkan pratinjau judul video, *thumbnail*, durasi, serta 3 *chip* preset cepat:
     * **Video (MP4 Terbaik)**
     * **Hemat Kuota (720p/480p)**
     * **Audio Saja (MP3/M4A + Thumbnail)**
   * Satu tombol besar **"Mulai Unduh"** yang langsung memulai unduhan latar belakang dan menutup *Bottom Sheet*.

### B. Layar 2: `MainScreen` (Pusat Kendali Unduhan)
Terdiri dari 4 zona vertikal yang bersih:

1. **Top App Bar (`CenterAlignedTopAppBar`):**
   * Kiri: Status mesin [yt-dlp](https://github.com/yt-dlp/yt-dlp) & [aria2c](https://github.com/aria2/aria2) (lencana kecil `"yt-dlp ready"`).
   * Kanan: Tombol ikon **Pengaturan & Update Ekstraktor** (`SettingsBottomSheet`).
2. **Banner Deteksi Clipboard Otomatis (`ClipboardSuggestionBanner`):**
   * Muncul secara animatif (`AnimatedVisibility`) di bagian atas daftar **hanya ketika** `MainActivity` mendeteksi URL baru yang valid di *clipboard* saat aplikasi mendapatkan fokus sesuai aturan [Penanganan Clipboard Aman Android](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling).
   * Isi Banner: Teks URL yang terdeteksi + dua tombol aksi cepat: **"Unduh Video"** dan **"Unduh Audio"** (atau jika opsi *Auto-Start on Clipboard* diaktifkan di pengaturan, unduhan langsung berjalan otomatis saat aplikasi dibuka tanpa perlu menekan banner).
3. **Filter Chip Bar (`FilterChip` Horizontal):**
   * 4 filter status: **Semua**, **Aktif (Mengunduh/Dijeda)**, **Selesai**, dan **Gagal**.
4. **Daftar Kartu Unduhan (`LazyColumn` dengan `DownloadTaskCard`):**
   * Setiap kartu menampilkan:
     * **Baris Atas:** *Thumbnail* rasio 16:9 (`64.dp x 36.dp`, sudut membulat `8.dp`), Judul Media, dan lencana format (`MP4 1080p` atau `MP3`).
     * **Baris Tengah:** `LinearProgressIndicator` dengan sudut membulat (`StrokeCap.Round`). Warna berubah sesuai status (`primary` saat aktif, `tertiary` saat dijeda, `error` saat gagal).
     * **Baris Bawah (Metrik & Kontrol):**
       * Kiri (Font Tabular): `45.2 MB / 120.0 MB • 68% • 8.4 MB/s • ETA 00:09`
       * Kanan (Tombol Ikon `IconButton` 48x48dp untuk aksesibilitas sentuh):
         * Jika `DOWNLOADING`: Tombol **Pause (`⏸`)** dan **Cancel (`✕`)**.
         * Jika `PAUSED`: Tombol **Resume (`▶`)** dan **Cancel (`✕`)**.
         * Jika `COMPLETED`: Tombol **Buka File (`▶`)**, **Bagikan (`↗`)**, dan **Hapus (`🗑`)**.
         * Jika `FAILED`: Tombol **Coba Lagi (`↻`)** dan **Lihat Log Error (`ℹ`)**.
5. **Floating Action Button (`ExtendedFloatingActionButton`):**
   * Tombol **"+ Tautan Manual"** di pojok kanan bawah untuk berjaga-jaga jika pengguna ingin mengetik atau menempelkan beberapa URL sekaligus (*batch download*).

## 4. Desain Notifikasi Latar Belakang (`DownloadForegroundService`)
Notifikasi sistem merupakan bagian antarmuka krusial saat pengguna berada di luar aplikasi:
* **Saluran Notifikasi (`NotificationChannel`):**
  * `download_progress_channel`: Prioritas `IMPORTANCE_LOW` (tidak mengeluarkan suara atau getaran setiap kali persentase bertambah).
  * `download_complete_channel`: Prioritas `IMPORTANCE_DEFAULT` (memberi tahu saat unduhan selesai atau gagal).
* **Tata Letak Notifikasi Aktif:**
  * **Judul:** Judul video (atau `"Mengunduh 2 file..."` jika paralel).
  * **Subteks / Konten:** `68% • 8.4 MB/s • Sisa 9 dtk`
  * **Tombol Aksi (`NotificationCompat.Action`):**
    1. **Pause / Resume** (bergantung pada status saat ini, mengirim `Intent` ke `DownloadActionReceiver`).
    2. **Batal (Cancel)**.

## 5. Daftar Periksa Kualitas UI untuk AI Agent
Sebelum menyelesaikan pembuatan komponen UI di Jetpack Compose:
* Pastikan semua komponen interaktif memiliki ukuran target sentuh minimal `48.dp x 48.dp`.
* Pastikan teks kecepatan dan persentase menggunakan `fontFeatureSettings = "tnum"` agar tata letak kartu tidak bergeser saat angka berubah cepat.
* Pastikan keadaan kosong (*Empty State*) ketika belum ada riwayat unduhan menampilkan ilustrasi ikon minimalis dengan petunjuk singkat: *"Bagikan tautan dari aplikasi mana saja ke SwiftGrab, atau salin tautan lalu buka aplikasi ini untuk mengunduh otomatis."*
* Buat fungsi `@Preview(showBackground = true)` untuk `DownloadTaskCard` pada keempat status (`DOWNLOADING`, `PAUSED`, `COMPLETED`, `FAILED`) guna memudahkan verifikasi visual.
