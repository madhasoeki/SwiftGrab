# Panduan Berkontribusi pada SwiftGrab 🚀

Terima kasih atas minat Anda untuk berkontribusi pada **SwiftGrab**! Proyek ini berkomitmen untuk menyediakan aplikasi pengunduh media Android yang cepat, modern, bebas iklan (*zero-ads*), tanpa pelacak analitik (*zero-tracking*), dan sepenuhnya *open-source*.

---

## 1. Filosofi Proyek & Nilai Utama
1. **Zero-Ads & Zero-Tracking:** Kami **menolak keras** penambahan SDK periklanan (AdMob, Unity, dsb.), analitik pihak ketiga (Firebase Analytics, Mixpanel, dsb.), atau telemetri terselubung.
2. **Zero-Paste Experience:** Pengguna harus dapat memulai unduhan dalam 1 ketukan langsung dari *Share Sheet* aplikasi lain atau melalui deteksi aman di *clipboard*.
3. **Performa & Stabilitas:** Antarmuka Jetpack Compose harus mulus (60–120 FPS) tanpa *recomposition jank* saat kecepatan unduhan diperbarui secara intensif.
4. **Kepatuhan Privasi Android Modern:** Selalu patuhi pembatasan *clipboard* Android 10–15 dan aturan *Foreground Service* Android 14/15 (`dataSync`).

---

## 2. Persyaratan Lingkungan Pengembangan
* **JDK:** Java 17 (misalnya Eclipse Temurin atau OpenJDK 17).
* **Android SDK:** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26` (Android 8.0 Oreo).
* **IDE yang Direkomendasikan:** Android Studio Ladybug / Meerkat atau yang lebih baru, atau VS Code dengan ekstensi Android & Kotlin.
* **Gradle:** Menggunakan Gradle Wrapper bawaan (`./gradlew`).

---

## 3. Menyiapkan Proyek Secara Lokal

1. **Fork & Klon Repositori:**
   ```bash
   git clone https://github.com/madhasoeki/SwiftGrab.git
   cd SwiftGrab
   ```
2. **Verifikasi Lingkungan Build:**
   ```bash
   ./gradlew testDebugUnitTest
   ./gradlew assembleDebug
   ```
   *Catatan Windows PowerShell:* Gunakan `.\gradlew.bat testDebugUnitTest` atau `.\gradlew.bat assembleDebug`.

---

## 4. Aturan Penulisan Kode (Architecture Guidelines)

Sebelum mengajukan *Pull Request* (PR), pastikan kode Anda mengikuti panduan standar di [`AGENTS.md`](./AGENTS.md) dan [`DESIGN.md`](./DESIGN.md):

### A. Jetpack Compose & UI
* **Unidirectional Data Flow (UDF):** Seluruh composable layar harus *stateless* (menerima state dan memancarkan event).
* **Isolasi Rekomposisi:** Jangan memicu rekomposisi seluruh item `LazyColumn` setiap kali progres persentase berubah (gunakan lambda `() -> Float` dan `key = { task.id }`).
* **Edge-to-Edge:** Pastikan antarmuka memperhitungkan insets sistem (`systemBars`, `ime`) dengan `Scaffold(contentPadding)`.

### B. Background Download & Mesin yt-dlp
* **Inisialisasi Asinkron:** Panggilan `YoutubeDL.getInstance().init(...)` wajib berada di thread latar belakang (`Dispatchers.IO`) untuk mencegah *Application Not Responding* (ANR).
* **Pause / Resume Aman:** Jeda unduhan dengan `destroyProcessById(taskId)` tanpa menghapus berkas parsial `.part` atau `.aria2`.
* **Foreground Service:** Layanan menggunakan `foregroundServiceType="dataSync"` dan selalu memanggil `startForeground()` dalam kurun waktu 5 detik.

### C. ProGuard / R8
* Jika menambahkan dependensi yang menggunakan refleksi atau serialisasi native, perbarui [`app/proguard-rules.pro`](./app/proguard-rules.pro).

---

## 5. Menjalankan Uji Otomatis & Lint

Jalankan perintah berikut sebelum melakukan commit:

| Pemeriksaan | Perintah | Catatan |
| :--- | :--- | :--- |
| **Unit Test** | `./gradlew testDebugUnitTest` | Memastikan logika validasi URL & parser progres berjalan benar |
| **Android Lint** | `./gradlew lintDebug` | Memeriksa kepatuhan manifest & resource |
| **Kompilasi Release** | `./gradlew assembleRelease` | Memastikan aturan R8/ProGuard tidak memicu `Missing classes` |

---

## 6. Alur Kerja Git & Pengajuan Pull Request (PR)

1. Buat branch baru dari `main`:
   ```bash
   git checkout -b feat/nama-fitur-baru
   # atau
   git checkout -b fix/deskripsi-perbaikan
   ```
2. Tulis pesan commit yang jelas dan deskriptif (mengikuti konvensi *Conventional Commits* seperti `feat:`, `fix:`, `docs:`, `refactor:`, `build:`).
3. Pastikan tidak ada berkas temporer (`local.properties`, `build/`, `*.jks`) yang terunggah.
4. Buka *Pull Request* ke branch `main`.
5. Pastikan semua *checks* di GitHub Actions CI lulus.

---

## 7. Lisensi

Dengan berkontribusi pada proyek ini, Anda menyetujui bahwa semua kontribusi kode Anda akan dilisensikan di bawah [GNU General Public License v3.0 (GPLv3)](./LICENSE).
