# OPTIMZE.md — Rencana Eksekusi Optimasi & Perbaikan SwiftGrab

Dokumen ini adalah panduan eksekusi terstruktur bagi **AI Coding Agent** untuk melakukan perbaikan bug kritis, pengerasan keamanan, optimasi performa Jetpack Compose, pembersihan Android Lint, dan optimasi build/R8 pada proyek **SwiftGrab** (`com.example.swiftgrab`).

---

## 1. Konteks Proyek & Kondisi Lingkungan Terverifikasi

* **Identitas Proyek:** SwiftGrab (`com.example.swiftgrab`) — aplikasi Android pengunduh video/audio bebas iklan berbasis `yt-dlp`, `aria2c`, dan `FFmpeg`.
* **Stack Build Saat Ini:**
  * Android Gradle Plugin (AGP): `9.4.1` (`build.gradle.kts`)
  * Kotlin Compose Plugin: `2.4.10`
  * KSP: `2.3.9`
  * Gradle Wrapper: `9.8.1` (`gradle/wrapper/gradle-wrapper.properties`)
  * SDK: `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35` (`app/build.gradle.kts`)
* **Lingkungan Pengembangan Lokal:**
  * Android SDK: `C:\Users\USER\AppData\Local\Android\Sdk` (platform terpasang: `android-35`, `android-37.0`)
  * Android CLI (`android-cli-plugin`): terpasang di `C:\Users\USER\AppData\AndroidCLI\android.exe` (versi `1.1.16919734`; dapat dipanggil sebagai `android` pada sesi shell baru atau menggunakan path penuh di shell lama).
* **Baseline Verifikasi Awal (`./gradlew testDebugUnitTest lintDebug`):**
  * Semua unit test yang ada **LULUS**.
  * `lintDebug` melaporkan peringatan pada dependensi tertinggal (`compose-bom`, `core-ktx`, `activity-compose`, `lifecycle-*`, `kotlinx-coroutines-*`, `gradle`, `compileSdk`/`targetSdk`), `MonochromeLauncherIcon`, `ObsoleteSdkInt`, `UnusedResources` (5 string), dan `UseKtx` (`UserPreferences.kt`).

---

## 2. Aturan Wajib untuk AI Agent Eksekutor (Hard Constraints)

Patuhi seluruh batasan dari `AGENTS.md` dan `DESIGN.md` berikut sebelum mengubah satu baris kode pun:

1. **Jangan Rusak Native Binary Packaging:**
   * **DILARANG** menghapus `packaging { jniLibs { useLegacyPackaging = true } }` di `app/build.gradle.kts` maupun `android:extractNativeLibs="true"` di `app/src/main/AndroidManifest.xml`. Biner `libpython.so`, `libffmpeg.so`, dan `libaria2c.so` dari `youtubedl-android` wajib diekstrak ke filesystem agar bisa dieksekusi oleh `ProcessBuilder`.
2. **Privasi Mutlak (Zero-Ads, Zero-Tracking):**
   * **DILARANG** menambahkan SDK iklan, analitik, crash reporter berpelacak, atau telemetri pihak ketiga dalam bentuk apa pun.
3. **Aturan Performa Jetpack Compose (Strong Skipping Mode):**
   * **DILARANG** menambahkan `@Immutable` / `@Stable` pada model yang berisi koleksi atau mengganti `List<DownloadTaskEntity>` dengan `ImmutableList`. Di Compose Strong Skipping Mode, `kotlin.collections.List` sudah dibandingkan dengan `===` dalam waktu `O(1)`; memaksanya menjadi `ImmutableList` justru memicu `.equals()` struktural `O(N)` pada setiap tick progres.
4. **Strategi Eksekusi Bertahap:**
   * Kerjakan **satu fase per iterasi secara berurutan (Fase 1 → Fase 6)**.
   * Setelah menyelesaikan satu fase, jalankan gate verifikasi:
     ```bash
     ./gradlew testDebugUnitTest assembleDebug lintDebug
     ```
   * Perbarui status checkbox `- [ ]` menjadi `- [x]` pada dokumen ini hanya setelah implementasi dan verifikasi fase terkait benar-benar lulus.
5. **Konvensi Unit Test:**
   * Gunakan **Fake DAO / Fake Repository** (`FakeDownloadDao`, dsb.). Jangan menambahkan Mockito.
6. **Sinkronisasi Knowledge Graph (jika tersedia):**
   * Jika direktori `graphify-out/` telah dibuat di workspace, jalankan `graphify update .` setelah memodifikasi berkas kode.

---

## 3. Ringkasan Fase & Prioritas

| Fase | Fokus Utama | Dampak | Risiko Regresi | Cakupan Berkas Utama |
| :--- | :--- | :--- | :--- | :--- |
| **Fase 1** | Keamanan & Privasi | Tinggi (kebocoran cookie, WebView, SSRF/URI) | Rendah | `CookieStorageManager.kt`, `AndroidManifest.xml`, `XLoginDialog.kt`, `UrlValidator.kt`, `FileSharingHelper.kt` |
| **Fase 2** | Bug Kritis Service, Concurrency, Notifikasi & Parser | Kritis (hilang tombol Resume, race condition antrean, tugas macet, bug ETA) | Sedang | `DownloadForegroundService.kt`, `DownloadActionReceiver.kt`, `DownloadDao.kt`, `DownloadProgressParser.kt`, `DownloadRequestBuilder.kt`, `DownloadViewModel.kt`, `SwiftGrabApp.kt` |
| **Fase 3** | Penyimpanan Publik (`MediaStore`), Thumbnail/Metadata & Batch Download | Tinggi (media tampil di Galeri/Downloads & tidak hilang saat uninstall) | Sedang | `DownloadFileManager.kt`, `FileSharingHelper.kt`, `DownloadForegroundService.kt`, `MainScreen.kt`, `UrlValidator.kt` |
| **Fase 4** | Performa Compose, Edge-to-Edge & Aksesibilitas | Tinggi (bebas Disk I/O di UI thread, isolasi rekomposisi 250 ms, target sentuh 48dp) | Sedang | `DownloadViewModel.kt`, `MainScreen.kt`, `DownloadTaskCard.kt`, `SettingsBottomSheet.kt`, `QuickFormatSheet.kt`, `ClipboardSuggestionBanner.kt`, `XLoginDialog.kt`, `strings.xml` |
| **Fase 5** | Room Schema, R8, ABI Splits, Lint & CI/CD | Sedang-Tinggi (APK ~60% lebih kecil per ABI, nol lint warnings, rilis aman) | Sedang | `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `proguard-rules.pro`, `AppDatabase.kt`, `DownloadTaskEntity.kt`, `UserPreferences.kt`, `.github/workflows/*` |
| **Fase 6** | Pengujian Unit Lengkap & Audit Android CLI (`android-cli-plugin`) | Tinggi (validasi visual, aksesibilitas & alur E2E di emulator) | Rendah | `app/src/test/...`, sidecar Compose Preview & Device Hub via `android` CLI |

---

## 4. Fase 1 — Keamanan & Privasi (Prioritas Tertinggi)
*Status: [x] Selesai & Terverifikasi di branch `feat/security-privacy-hardening` (commit `f61f50b`)*

### 1.1 Isolasi Cookies X/Twitter hanya ke Domain X/Twitter
- [x] **Tugas:** Jangan pernah mengirimkan `x_cookies.txt` (berisi `auth_token` dan `ct0`) ke situs lain (YouTube, TikTok, Instagram, dll.).
  * **Berkas:**
    * `app/src/main/java/com/example/swiftgrab/data/cookies/CookieStorageManager.kt`
    * `app/src/main/java/com/example/swiftgrab/service/DownloadForegroundService.kt` (`executeDownload`, baris 160–166)
    * `app/src/test/java/com/example/swiftgrab/data/CookieStorageManagerTest.kt`
    * `app/src/test/java/com/example/swiftgrab/service/DownloadRequestBuilderTest.kt`
  * **Langkah Implementasi:**
    1. Tambahkan `fun isXDomain(url: String): Boolean` di `CookieStorageManager` yang mem-parse `java.net.URI(url).host?.lowercase()` dan mengembalikan `true` hanya untuk host atau subdomain dari `x.com`, `twitter.com`, `t.co`, `fxtwitter.com`, `vxtwitter.com`.
    2. Di `DownloadForegroundService.executeDownload()`, ganti pembacaan cookie menjadi:
       ```kotlin
       val cookiesFile = if (CookieStorageManager.isXDomain(task.url)) {
           CookieStorageManager.getXCookiesFile(this)
       } else {
           null
       }
       ```
    3. Tambahkan unit test positif dan negatif (`https://x.com/...`, `https://mobile.twitter.com/...`, `https://youtube.com/...`, `https://evilx.com/...`) di `CookieStorageManagerTest.kt`.

### 1.2 Proteksi Cookies & Database dari Android Auto Backup
- [x] **Tugas:** Cegah `files/cookies/x_cookies.txt` dan `swiftgrab.db` ikut tersinkronisasi ke Android Auto Backup / `adb backup`.
  * **Berkas:**
    * `app/src/main/AndroidManifest.xml` (tag `<application>`, baris 10–17)
    * *(Opsional jika memilih rule selektif)* `app/src/main/res/xml/data_extraction_rules.xml` & `app/src/main/res/xml/backup_rules.xml`
  * **Langkah Implementasi:**
    * Disarankan setel `android:allowBackup="false"` pada `<application>` di `AndroidManifest.xml` agar selaras dengan prinsip privasi ketat aplikasi (atau definisikan `android:dataExtractionRules` dan `android:fullBackupContent` yang mengecualikan `cookies/` serta `swiftgrab.db`).

### 1.3 Hardening WebView & Validasi Sesi di `XLoginDialog`
- [x] **Tugas:** Tutup akses file/content di WebView dan pastikan tombol simpan tidak menyimpan sesi tamu kosong.
  * **Berkas:** `app/src/main/java/com/example/swiftgrab/ui/components/XLoginDialog.kt` (baris 128–154, 271–310)
  * **Langkah Implementasi:**
    1. Pada `WebView.settings`:
       ```kotlin
       allowFileAccess = false
       allowContentAccess = false
       ```
       *(Penting karena `minSdk = 26`; pada API < 30 nilai default `allowFileAccess` masih `true`.)*
    2. Override `shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean` pada `WebViewClient`: hanya izinkan skema `https` untuk domain autentikasi resmi (`x.com`, `twitter.com`, `api.x.com`, `accounts.google.com`, `appleid.apple.com`, `challenges.cloudflare.com`, beserta subdomainnya); blokir `file://`, `content://`, `javascript:`, `intent://`, dan skema tidak dikenal.
    3. Pada tombol **"Simpan & Selesai"**:
       * Panggil `cm.flush()`.
       * Periksa `CookieStorageManager.containsAuthTokens(merged)`. Jika `false`, tampilkan `Toast` peringatan bahwa login belum selesai (`auth_token`/`twid` belum ditemukan) dan **jangan** memanggil `saveXCookies()` atau `onLoginSuccess()`.
       * Jika `true`, jalankan `withContext(Dispatchers.IO) { CookieStorageManager.saveXCookies(context, merged) }` melalui `rememberCoroutineScope()` sebelum memicu toast sukses dan `onLoginSuccess()`.

### 1.4 Perketat Validasi URL (`UrlValidator`)
- [x] **Tugas:** Cegah bypass host palsu, `userInfo`, `localhost`, dan alamat IP privat/loopback.
  * **Berkas:**
    * `app/src/main/java/com/example/swiftgrab/util/UrlValidator.kt` (baris 56–78)
    * `app/src/test/java/com/example/swiftgrab/util/UrlValidatorTest.kt`
  * **Langkah Implementasi:**
    1. Gunakan `java.net.URI(trimmed)` di `isValidWebUrl(url)` (tangkap `URISyntaxException` dan kembalikan `false`).
    2. Pastikan `uri.scheme` adalah `http` atau `https` (case-insensitive), `uri.userInfo == null`, dan `uri.host` tidak null/blank serta mengandung titik (`.`).
    3. Tolak host lokal/privat/link-local: `localhost`, `*.local`, `0.0.0.0`, `127.*`, `10.*`, `192.168.*`, `169.254.*`, `172.16.*`–`172.31.*`, serta IPv6 loopback/private (`::1`, `[::1]`, `fc*`, `fd*`, `fe80*`).
    4. Tambahkan kasus uji di `UrlValidatorTest.kt` untuk `http://127.0.0.1/a`, `http://192.168.1.10/a`, `http://user@x.com@localhost/a`, dan `https://[::1]/a`.

### 1.5 Perbaikan `ClipData` URI Permission di `FileSharingHelper`
- [x] **Tugas:** Pastikan preview Android Sharesheet (Android 10+) selalu memiliki izin baca URI.
  * **Berkas:** `app/src/main/java/com/example/swiftgrab/util/FileSharingHelper.kt` (baris 57–65)
  * **Langkah Implementasi:** Tambahkan `clipData = ClipData.newRawUri(null, uri)` pada `shareIntent` sebelum memanggil `Intent.createChooser(shareIntent, ...)`.

**Verifikasi Fase 1:**
```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

---

## 5. Fase 2 — Bug Kritis Concurrency, Notifikasi, Lifecycle Service & Parser
*Status: [x] Selesai & Terverifikasi di branch `fix/service-concurrency-notification-eta` (commit `f0b8dae`) dan `main`*

### 2.1 Perbaiki Arsitektur Notifikasi Foreground vs Paused & Multi-Download
- [x] **Tugas:** Hilangkan bug notifikasi saling timpa, notifikasi Paused terhapus saat service berhenti, dan kedipan `"Menyiapkan unduhan…"`.
  * **Berkas:**
    * `app/src/main/java/com/example/swiftgrab/service/DownloadForegroundService.kt` (baris 65–124, 269–372, 401–531)
    * `app/src/main/java/com/example/swiftgrab/service/DownloadActionReceiver.kt`
  * **Langkah Implementasi:**
    1. Simpan status foreground pada `private val isForegroundRunning = AtomicBoolean(false)`. Di `ensureForeground()`, hanya jalankan `ServiceCompat.startForeground(...)` jika `isForegroundRunning.compareAndSet(false, true)`. Reset menjadi `false` saat `stopForeground(STOP_FOREGROUND_REMOVE)` dipanggil (di `checkAndStopIfIdle()` dan `onTimeout()`) atau di `onDestroy()`.
       > **Pitfall Android 8–15:** Jika `onStartCommand` dipanggil melalui `ContextCompat.startForegroundService()` saat service dalam kondisi stopped/created (`isForegroundRunning == false`), `ServiceCompat.startForeground(...)` **wajib** dipanggil dalam < 5 detik. Gunakan `startService()` (bukan `startForegroundService()`) untuk perintah aksi saat service sudah berjalan atau tetap panggil `ensureForeground()` hanya jika `!isForegroundRunning.get()`.
    2. Pisahkan ID notifikasi:
       * `NOTIFICATION_FOREGROUND_ID = 1001` hanya untuk unduhan yang sedang **aktif** (`DOWNLOADING` / antrean `QUEUED`).
       * Notifikasi **Paused** menggunakan ID terpisah per-task: `private fun pausedNotificationId(taskId: String): Int = 200_000 + (taskId.hashCode() and 0x0FFF_FFFF)`.
       * Dengan pemisahan ID ini, ketika semua unduhan aktif dijeda dan `checkAndStopIfIdle()` memanggil `stopForeground(STOP_FOREGROUND_REMOVE)`, notifikasi Paused + tombol **Resume** tetap bertahan di panel notifikasi!
       * Panggil `notificationManager.cancel(pausedNotificationId(taskId))` saat task tersebut di-resume, di-cancel, atau selesai.
    3. Di `cancelTask()` dan `handleDownloadError()` (cabang `canceledTaskIds`): jangan panggil `notificationManager.cancel(NOTIFICATION_FOREGROUND_ID)` jika `activeJobs` masih memiliki tugas lain; biarkan `checkAndStopIfIdle()` yang menutup notifikasi foreground ketika benar-benar kosong.
    4. Simpan progres terbaru tiap tugas aktif di `ConcurrentHashMap<String, ActiveNotificationState>` agar saat `activeJobs.size > 1`, `updateForegroundProgressNotification()` menampilkan ringkasan agregat: judul `"Mengunduh ${activeCount} file…"` dan rata-rata progres (sesuai `DESIGN.md` §4), sehingga dua tugas paralel tidak saling menimpa setiap 250 ms.
    5. Bungkus seluruh pemanggilan `ContextCompat.startForegroundService(context, intent)` (di `DownloadActionReceiver` dan companion `DownloadForegroundService`) dengan `try/catch` yang menangani `IllegalStateException` / `ForegroundServiceStartNotAllowedException` (API 31+) dengan log yang jelas alih-alih crash.

### 2.2 Hilangkan Race Condition Antrean & Perbaiki Structured Concurrency
- [x] **Tugas:** Pastikan penjadwalan antrean 100% atomik, FIFO, menghormati `MAX_CONCURRENT_DOWNLOADS`, dan tidak menelan `CancellationException`.
  * **Berkas:**
    * `app/src/main/java/com/example/swiftgrab/data/db/DownloadDao.kt` (baris 17–18)
    * `app/src/main/java/com/example/swiftgrab/data/repository/DownloadRepository.kt`
    * `app/src/main/java/com/example/swiftgrab/service/DownloadForegroundService.kt` (baris 126–151, 260–301, 326–337)
    * `app/src/main/java/com/example/swiftgrab/ui/DownloadViewModel.kt` (baris 216–219)
  * **Langkah Implementasi:**
    1. Tambahkan query antrean FIFO di `DownloadDao`:
       ```kotlin
       @Query("SELECT * FROM download_tasks WHERE status IN (:statuses) ORDER BY createdAt ASC")
       fun observeTasksByStatusesAsc(statuses: List<DownloadStatus>): Flow<List<DownloadTaskEntity>>
       ```
       *(Jangan ubah `observeAllTasks()` yang memang `DESC` untuk UI; gunakan query `ASC` khusus untuk `startQueueObserver()`).*
    2. Di `DownloadForegroundService.resumeTask()`: hapus pemanggilan langsung `startDownloadJob(task)`. Cukup perbarui status di DB menjadi `DownloadStatus.QUEUED` (dan batalkan notifikasi paused untuk `taskId`). Biarkan `startQueueObserver()` yang menjadwalkan eksekusinya agar batas `MAX_CONCURRENT_DOWNLOADS = 2` selalu terjaga.
    3. Gunakan `Mutex` antrean + `activeJobs.putIfAbsent(task.id, job)` (atau cek slot di bawah `Mutex` sebelum meluncurkan coroutine dan gunakan `CoroutineStart.LAZY` sebelum `putIfAbsent` + `job.start()`) sehingga mustahil dua job/proses `yt-dlp` berjalan bersamaan untuk `taskId` yang sama.
    4. Di `handleDownloadError()`: pada cabang `e is CancellationException`, lakukan update DB dan notifikasi di dalam `withContext(NonCancellable) { ... }` (jika bukan paused/canceled oleh user), lalu **wajib `throw e`** agar pembatalan terpropagasi dengan benar.
    5. Di `DownloadViewModel.updateYoutubeDL()` (baris 216): tambahkan `catch (e: CancellationException) { throw e }` sebelum `catch (e: Exception)`.

### 2.3 Pemulihan Tugas `DOWNLOADING` Macet Setelah Process Death
- [x] **Tugas:** Cegah tugas terjebak di status `DOWNLOADING` selamanya jika OS mematikan proses (`START_NOT_STICKY`). (Selesai di PR #21)
  * **Berkas:**
    * `app/src/main/java/com/example/swiftgrab/data/db/DownloadDao.kt`
    * `app/src/main/java/com/example/swiftgrab/data/repository/DownloadRepository.kt`
    * `app/src/main/java/com/example/swiftgrab/SwiftGrabApp.kt`
  * **Langkah Implementasi:**
    1. Tambahkan di `DownloadDao`:
       ```kotlin
       @Query("UPDATE download_tasks SET status = :pausedStatus, updatedAt = :updatedAt WHERE status = :downloadingStatus")
       suspend fun resetStaleDownloadingTasks(
           pausedStatus: DownloadStatus = DownloadStatus.PAUSED,
           downloadingStatus: DownloadStatus = DownloadStatus.DOWNLOADING,
           updatedAt: Long = System.currentTimeMillis()
       )
       ```
    2. Ekspos `suspend fun recoverStaleDownloads()` di `DownloadRepository` dan panggil sekali di `SwiftGrabApp.onCreate()` melalui `appScope.launch { repository.recoverStaleDownloads() }` sebelum observer/service bekerja.

### 2.4 Implementasi `WakeLock` Aman Saat Unduhan Berjalan
- [x] **Tugas:** Gunakan izin `WAKE_LOCK` yang sudah dideklarasikan secara aman dan berbatas waktu, atau hapus izin jika diputuskan tidak memakai `WakeLock`. (Direkomendasikan implementasi `PARTIAL_WAKE_LOCK` agar unduhan besar tidak putus saat layar mati / Doze).
  * **Berkas:** `app/src/main/java/com/example/swiftgrab/service/DownloadForegroundService.kt`
  * **Langkah Implementasi:**
    * Buat `PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`, tag `"SwiftGrab::DownloadWakeLock"`, `setReferenceCounted(false)`).
    * Acquire dengan timeout aman (mis. `60 * 60 * 1000L` ms) saat ada job aktif, dan pastikan `if (wakeLock.isHeld) wakeLock.release()` selalu dipanggil saat `activeJobs.isEmpty()`, `onTimeout()`, dan `onDestroy()`.

### 2.5 Perbaiki Regex ETA `aria2c` > 1 Jam
- [x] **Tugas:** Perbaiki parsing `ETA:1h5m20s` dan cegah pencocokan string kosong.
  * **Berkas:**
    * `app/src/main/java/com/example/swiftgrab/service/DownloadProgressParser.kt` (baris 31, 134–139)
    * `app/src/test/java/com/example/swiftgrab/service/DownloadProgressParserTest.kt`
  * **Langkah Implementasi:**
    1. Ubah `ARIA2C_ETA_PATTERN` menjadi:
       ```kotlin
       private val ARIA2C_ETA_PATTERN = Pattern.compile(
           """ETA:(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?""",
           Pattern.CASE_INSENSITIVE
       )
       ```
    2. Di `formatEta()`, pastikan minimal satu dari grup `h`, `m`, atau `s` tidak null (`if (hStr == null && mStr == null && sStr == null) return ""`). Jika `h > 0`, format `"%02d:%02d:%02d"`, selain itu `"%02d:%02d"`.
    3. Tambahkan test untuk `ETA:1h5m20s` (`01:05:20`), `ETA:2h10m` (`02:10:00`), `ETA:45s` (`00:45`), dan `ETA:` (`""`).

### 2.6 Optimasi Flag `yt-dlp` & `aria2c` di `DownloadRequestBuilder`
- [ ] **Tugas:** Kurangi overhead parsing log di release dan cegah kegagalan `aria2c` pada stream HLS/DASH.
  * **Berkas:**
    * `app/build.gradle.kts` (`buildFeatures { buildConfig = true }`)
    * `app/src/main/java/com/example/swiftgrab/service/DownloadRequestBuilder.kt` (baris 42–50)
    * `app/src/main/java/com/example/swiftgrab/service/DownloadForegroundService.kt`
    * `app/src/test/java/com/example/swiftgrab/service/DownloadRequestBuilderTest.kt`
  * **Langkah Implementasi:**
    1. Tambahkan parameter `useAria2c: Boolean = true` dan `verbose: Boolean = false` pada `DownloadRequestBuilder.build()` (pemanggil di `DownloadForegroundService` meneruskan `verbose = BuildConfig.DEBUG`). Hapus `-v` saat `verbose == false` agar callback tidak dibanjiri debug trace (jika butuh info kesalahan saat gagal, kumpulkan ringkasan baris `ERROR:` / ekor output secukupnya).
    2. Gunakan `--downloader "http,https:libaria2c.so"` (alih-alih global `"libaria2c.so"`) agar stream manifest HLS/DASH (`.m3u8`/native) ditangani oleh downloader native `yt-dlp`, sementara unduhan HTTP/HTTPS langsung tetap dipercepat multi-koneksi oleh `aria2c`. *(Pertimbangkan juga fallback otomatis: jika percobaan pertama gagal dengan `useAria2c = true` bukan karena pause/cancel, coba ulang 1× dengan `useAria2c = false`).*
    3. Perbarui/sesuaikan `DownloadRequestBuilderTest.kt`.

### 2.7 Bersihkan Folder Staging Saat `deleteTask()`
- [x] **Tugas:** Hapus file sementara `.staging_<taskId>` saat pengguna menghapus task `PAUSED` atau `FAILED` dari riwayat. (Selesai di PR #20)

**Verifikasi Fase 2:**
```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

---

## 6. Fase 3 — Penyimpanan Media Publik (`MediaStore`), Metadata/Thumbnail & Batch Download
*Status: [x] Selesai & Terverifikasi di `main` (PR #17, PR #18, PR #23, PR #25)*

### 3.1 Ekspor Media Selesai ke Penyimpanan Publik (`Downloads/SwiftGrab`)
- [x] **Tugas:** Pastikan file unduhan selesai muncul di folder publik `Downloads/SwiftGrab` (tidak hilang saat uninstall & terlihat di aplikasi Files/Galeri) dan pilih file hasil yang tepat. (Selesai di PR #17 & PR #23)

### 3.2 Isi `thumbnailUrl` & Judul Media yang Akurat
- [x] **Tugas:** Saat ini `DownloadTaskEntity.thumbnailUrl` tidak pernah diisi dari hasil ekstraksi `yt-dlp`, sehingga `MediaThumbnail` selalu menampilkan ikon placeholder. (Selesai di PR #25)

### 3.3 Hubungkan Fitur Batch Download ke `AddManualUrlDialog`
- [x] **Tugas:** `DownloadViewModel.startBatchDownloads()` sudah tersedia namun belum terhubung ke UI. (Selesai di PR #18)
  * **Berkas:**
    * `app/src/main/java/com/example/swiftgrab/util/UrlValidator.kt`
    * `app/src/main/java/com/example/swiftgrab/ui/MainScreen.kt` (baris 489–619)
    * `app/src/main/java/com/example/swiftgrab/ui/DownloadViewModel.kt` (baris 143–148)
  * **Langkah Implementasi:**
    1. Tambahkan `UrlValidator.extractAllUrls(text: String?): List<String>` yang mengekstrak seluruh URL valid dan unik secara berurutan dari teks multi-baris.
    2. Perbarui `AddManualUrlDialog` agar mendukung input multi-baris (`singleLine = false`, `maxLines = 5`), menampilkan jumlah tautan valid yang terdeteksi jika > 1, dan memanggil `viewModel.startBatchDownloads(urls, selectedPreset)` saat lebih dari satu URL valid dimasukkan.
    3. Di `DownloadViewModel.startBatchDownloads()`, pastikan URL sudah divalidasi dan perbarui `lastHandledClipboardUrl` bila relevan.

**Verifikasi Fase 3:**
```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

---

## 7. Fase 4 — Performa Jetpack Compose, Edge-to-Edge & Aksesibilitas
*Status: [x] Selesai & Terverifikasi di branch `perf/compose-a11y-room-indices` (commit `eb1751d`) dan `main` (PR #24, PR #26)*

### 4.1 Pindahkan Seluruh Disk I/O keluar dari Main Thread & Composition Body
- [x] **Tugas:** Hapus pemanggilan sinkron ke filesystem/SharedPreferences/native binary dari body Composable dan constructor ViewModel. (Selesai di PR #24 & PR #26)

### 4.2 Isolasi Rekomposisi & Kurangi Frekuensi Write Room DB
- [x] **Tugas:** Cegah seluruh `MainScreen` dan `LazyColumn` terekomposisi + menulis SQLite 4× per detik saat unduhan berjalan. (Selesai di PR #24)

### 4.3 Edge-to-Edge, IME, Aksesibilitas & Eliminasi Duplikasi Komponen
- [x] **Tugas:** Perbaiki inset `LazyColumn`, IME `XLoginDialog`, ukuran target sentuh 48dp, komponen duplikat, dan resource string. (Selesai di branch `perf/compose-a11y-room-indices`)
  * **Berkas:**
    * `app/src/main/java/com/example/swiftgrab/ui/MainScreen.kt` (baris 264–301)
    * `app/src/main/java/com/example/swiftgrab/ui/components/XLoginDialog.kt` (baris 184–188)
    * `app/src/main/java/com/example/swiftgrab/ui/components/ClipboardSuggestionBanner.kt` (baris 159, 182)
    * `app/src/main/java/com/example/swiftgrab/ui/components/SettingsBottomSheet.kt` (baris 347, 374, 397, 553, 599–670)
    * `app/src/main/java/com/example/swiftgrab/ui/components/QuickFormatSheet.kt` (baris 189–260)
    * `app/src/main/res/values/strings.xml`
  * **Langkah Implementasi:**
    1. **Edge-to-Edge `LazyColumn`:** Di `MainScreen.kt`, jangan terapkan `innerPadding` bottom pada parent `Column` sehingga memotong list di atas navigation bar; terapkan top/horizontal padding pada header `FilterChipRow` (`.padding(top = innerPadding.calculateTopPadding())`), dan teruskan `bottom = innerPadding.calculateBottomPadding() + 88.dp` ke `LazyColumn(contentPadding = ...)` dengan `.consumeWindowInsets(innerPadding)`.
    2. **IME di `XLoginDialog`:** Tambahkan `.consumeWindowInsets(paddingValues).imePadding()` pada `Column` pembungkus WebView agar keyboard tidak menutupi kolom password X.
    3. **Target Sentuh Minimal 48dp:** Ubah `.height(44.dp)` menjadi `.heightIn(min = 48.dp)` pada semua tombol aksi di `ClipboardSuggestionBanner.kt` dan `SettingsBottomSheet.kt`.
    4. **Konsolidasi `PresetOptionCard` & Semantics TalkBack:**
       * Ekstrak komponen bersama (mis. `ui/components/PresetOptionCard.kt`) untuk menggantikan duplikasi `PresetOptionCard` (`QuickFormatSheet.kt`) dan `PresetSelectCard` (`SettingsBottomSheet.kt`).
       * Pada kontainer grup preset, tambahkan `Modifier.selectableGroup()`.
       * Pada kartu preset, ganti `.clickable(onClick = ...)` dengan `Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)` dan setel `RadioButton(selected = isSelected, onClick = null)` agar screen reader tidak membacakan 2 elemen terpisah.
    5. **Manfaatkan `UnusedResources` & Hapus Hardcoded Strings:**
       * Gunakan `@StringRes` pada `TaskFilter` (`R.string.filter_all`, `R.string.filter_active`, `R.string.filter_completed`, `R.string.filter_failed`) dan `DownloadPreset` (`displayTitleRes`, `subtitleRes`), serta manfaatkan `R.string.settings_format_sheet` di `SettingsBottomSheet.kt`.
       * Pindahkan string hardcoded lain di `MainScreen.kt`, `DownloadTaskCard.kt`, `SettingsBottomSheet.kt`, `XLoginDialog.kt`, dan `FileSharingHelper.kt` ke `strings.xml`.
    6. **Tambahkan `@Preview`:** Tambahkan `@Preview(showBackground = true)` untuk `QuickFormatSheet` dan `SettingsBottomSheet` guna keperluan audit `android-cli-plugin`.

**Verifikasi Fase 4:**
```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

---

## 8. Fase 5 — Room Schema, Build, R8, Dependensi, Android Lint & CI/CD
*Status: [x] Selesai & Terverifikasi di branch `perf/compose-a11y-room-indices` (commit `eb1751d`) dan `main` (commit `86ffa38`, PR #15, PR #19)*

### 5.1 Room Schema Export & Indexing
- [x] **Tugas:** Siapkan fondasi migrasi Room yang aman dan indeks query. (Selesai di branch `perf/compose-a11y-room-indices`)

### 5.2 Optimasi R8, ABI Splits & Release Signing
- [x] **Tugas:** Perkecil ukuran APK per arsitektur, bersihkan rule R8 redundan, dan amankan signing release. (Selesai di commit `86ffa38` & PR #15)

### 5.3 Bersihkan Warning Android Lint & Modernisasi Gradle
- [x] **Tugas:** Nol-kan semua warning Lint yang dapat diperbaiki dan perbarui dependensi. (Selesai di PR #6, #10, #19, #24)

### 5.4 Penguatan CI/CD GitHub Actions
- [x] **Tugas:** Pastikan lint dan unit test berjalan di semua alur CI & Release, serta versi rilis dinamis. (Selesai di PR #19, #22)
  * **Berkas:**
    * `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` & `ic_launcher_round.xml`
    * `app/src/main/java/com/example/swiftgrab/SwiftGrabApp.kt` (baris 49)
    * `app/src/main/java/com/example/swiftgrab/data/prefs/UserPreferences.kt` (baris 31, 39, 47, 55)
    * `gradle/libs.versions.toml` (baru), `build.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`
  * **Langkah Implementasi:**
    1. Pindahkan `app/src/main/res/mipmap-anydpi-v26/` ke `app/src/main/res/mipmap-anydpi/` (`minSdk = 26` membuat `-v26` usang) dan tambahkan elemen `<monochrome android:drawable="@mipmap/ic_launcher_foreground" />` (atau vektor monokrom khusus) pada `ic_launcher.xml` dan `ic_launcher_round.xml`.
    2. Hapus `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)` di `SwiftGrabApp.kt` L49.
    3. Gunakan ekstensi KTX `prefs.edit { put... }` (import `androidx.core.content.edit`) pada seluruh setter di `UserPreferences.kt`.
    4. Migrasikan versi dependensi ke `gradle/libs.versions.toml` dan perbarui ke versi yang direkomendasikan Lint:
       * Gradle Wrapper: `9.8.1` → `9.9.1`
       * `androidx.core:core-ktx`: `1.15.0` → `1.19.1`
       * `androidx.activity:activity-compose`: `1.10.0` → `1.13.0`
       * `androidx.lifecycle:*`: `2.8.7` → `2.11.0`
       * `androidx.compose:compose-bom`: `2024.09.00` → `2026.09.00`
       * `org.jetbrains.kotlinx:kotlinx-coroutines-*`: `1.9.0` → `1.11.0`
       > **Pitfall Upgrade Compose BOM 2024 → 2026:** Periksa kompatibilitas API Material3 (`LinearProgressIndicator`, `ModalBottomSheet`, `CenterAlignedTopAppBar`) dan `computedStateOf` (Compose 1.13+); verifikasi kompilasi dengan `./gradlew assembleDebug testDebugUnitTest`.
    5. Aktifkan `org.gradle.configuration-cache=true` di `gradle.properties` dan verifikasi kelulusan build (jika ada task pihak ketiga yang belum kompatibel, catat di temuan).
    6. *(Opsional untuk waktu build & APK)* Pertimbangkan mengganti `androidx.compose.material:material-icons-extended` (~25 ikon dipakai) dengan XML vector drawable lokal agar kompilasi dan shrinker jauh lebih cepat.

### 5.4 Penguatan CI/CD GitHub Actions
- [ ] **Tugas:** Pastikan lint dan unit test berjalan di semua alur CI & Release, serta versi rilis dinamis.
  * **Berkas:**
    * `.github/workflows/android-ci.yml`
    * `.github/workflows/release-apk.yml`
    * `app/build.gradle.kts`
  * **Langkah Implementasi:**
    1. Di `android-ci.yml`: tambahkan langkah `./gradlew lintDebug --no-daemon` setelah `testDebugUnitTest`.
    2. Di `release-apk.yml`: tambahkan `./gradlew testDebugUnitTest --no-daemon` sebelum `assembleRelease`.
    3. Suntikkan `RELEASE_VERSION_NAME` (dari tag tanpa awalan `v`) dan `RELEASE_VERSION_CODE` di `app/build.gradle.kts` dengan fallback ke `"1.0.0"` / `1` pada build lokal.
    4. Jika ABI Splits diaktifkan di §5.2, perbarui langkah `Prepare Release Assets` di `release-apk.yml` agar menyalin dan menghitung `sha256sum` untuk `app-arm64-v8a-release.apk`, `app-armeabi-v7a-release.apk`, `app-x86_64-release.apk`, dan `app-universal-release.apk` (periksa nama file aktual di `app/build/outputs/apk/release/`).

**Verifikasi Fase 5:**
```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleRelease -PallowDebugReleaseSigning=true
./gradlew :app:analyzeReleaseR8Config
```

---

## 9. Fase 6 — Pengujian Unit Lengkap & Audit Android CLI (`android-cli-plugin`)

### 6.1 Refaktor & Perluasan Unit Tests (Tanpa Mockito)
- [x] **Tugas:** Tutup celah cakupan pengujian pada file manager, ViewModel, dan progress parser. (Selesai di PR #26 dan swarm branches)

### 6.2 Audit Visual & Interaktif Menggunakan `android-cli-plugin`
> **Catatan Path CLI:** Jika perintah `android` belum terbaca di `PATH` sesi aktif, gunakan `C:\Users\USER\AppData\AndroidCLI\android.exe` (atau buka terminal baru setelah instalasi).

- [x] **Tugas A — Verifikasi Compose Previews & Component Tree:**
  Audit `bounds` (target sentuh minimal `48dp`), `semantics.contentDescription`, `semantics.role`, dan kontras mode gelap/terang untuk seluruh preview (`MainScreenPreview`, `ClipboardSuggestionBannerPreview`, 4 preview `DownloadTaskCard`, `QuickFormatSheetPreview`, `SettingsBottomSheetPreview`).

- [x] **Tugas B — Pengujian End-to-End di Emulator / Device Hub:**
  Verifikasi alur kritis menggunakan `android layout --pretty`, `android screen capture --annotate`, dan `adb`:
  - [x] **Alur 1 (Share Sheet):** Bagikan tautan `https://...` dari browser ke `SwiftGrab` → uji mode *One-Tap* dan mode *Quick Format Sheet*.
  - [x] **Alur 2 (Quick Settings Tile):** Tekan ubin *"Unduh dari Klip"* → verifikasi klip terbaca dan tugas masuk antrean.
  - [x] **Alur 3 (Pause & Resume Notifikasi):** Mulai unduhan → tekan **Jeda** di notifikasi → pastikan notifikasi *Paused* & tombol **Lanjutkan** tetap terlihat → tekan **Lanjutkan** dan pastikan progres berlanjut (`--continue`).
  - [x] **Alur 4 (Multi-Download Paralel):** Mulai 3 tautan sekaligus → pastikan maksimal 2 aktif paralel (`MAX_CONCURRENT_DOWNLOADS`), ke-3 menunggu di `QUEUED`, dan notifikasi menampilkan ringkasan agregat tanpa berkedip.
  - [x] **Alur 5 (IME & Edge-to-Edge):** Buka `AddManualUrlDialog` dan `XLoginDialog` → tampilkan keyboard IME dan pastikan kolom teks tidak tertutup keyboard maupun navigation bar.
  - [x] **Alur 6 (Penyimpanan Publik):** Setelah selesai, pastikan media tersimpan di `Downloads/SwiftGrab` dan bisa dibuka/dibagikan via tombol kartu.

### 6.3 Roadmap Lanjutan (Opsional Pasca-Fase 6)
- [ ] **User-Initiated Data Transfer (UIDT) API (Android 14/15+):** Pertimbangkan migrasi bertahap unduhan jangka panjang ke `JobInfo.Builder.setUserInitiated(true)` karena batas kuota FGS `dataSync` Android 15 (6 jam per 24 jam).
- [ ] **Baseline Profiles:** Tambahkan modul `androidx.benchmark` / Baseline Profile Generator untuk mempercepat cold start dan scrolling pertama `LazyColumn`.

---

## 10. Checklist Verifikasi Akhir (Definition of Done)

Sebelum menyatakan seluruh pekerjaan selesai, pastikan seluruh gerbang berikut **hijau**:

- [x] `./gradlew testDebugUnitTest` → **100% PASS** (termasuk test baru di `DownloadFileManagerTest`, `DownloadViewModelTest`, `DownloadProgressParserTest`, `CookieStorageManagerTest`, `UrlValidatorTest`)
- [x] `./gradlew lintDebug` → **0 errors**, semua warning yang dapat ditindaklanjuti pada Fase 5 bersih
- [x] `./gradlew assembleDebug` → **BUILD SUCCESSFUL**
- [x] `./gradlew assembleRelease -PallowDebugReleaseSigning=true` (atau keystore rilis) → **BUILD SUCCESSFUL**
- [x] `./gradlew :app:analyzeReleaseR8Config` → diverifikasi, tidak ada rule `youtubedl-android` / Jackson yang terhapus secara tidak aman
- [x] Seluruh `@Preview` ter-render bersih melalui **Android Compose Preview** (`android-cli-plugin`) dan seluruh skenario utama lolos di emulator/perangkat.
