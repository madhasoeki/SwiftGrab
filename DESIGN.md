# DESIGN.md — Spesifikasi Desain UI/UX & Alur Interaksi SwiftGrab

Dokumen `DESIGN.md` ini menjadi acuan desain visual, tata letak komponen, dan pengalaman pengguna (*User Experience*) bagi *AI Coding Agent* saat membangun antarmuka **SwiftGrab** menggunakan **Jetpack Compose** dan **Material Design 3 (M3)** yang selaras dengan identitas visual **Meteor Catch** ([`assets/logo/BRAND_GUIDELINES.md`](assets/logo/BRAND_GUIDELINES.md)).

## 1. Filosofi Desain Utama
1. **Zero-Friction, Zero-Paste (Kecepatan Satu Ketukan):** Pengguna tidak boleh dipaksa membuka aplikasi, mencari kolom input, menempelkan tautan, memilih belasan menu rumit, baru menekan tombol unduh. Unduhan harus bisa dimulai dalam **1 ketukan** langsung dari menu *Share* aplikasi lain atau otomatis saat aplikasi dibuka.
2. **Precision Kinetic Utility & Zero-Bloatware:** Antarmuka mengutamakan kejelasan teknis dan ketepatan tata letak tanpa iklan, *pop-up* promosi, efek neon/gradasi berlebihan, maupun *tab* berita yang mengganggu.
3. **Transparansi Status Real-Time:** Setiap tugas unduhan menampilkan informasi teknis yang stabil: persentase, ukuran terunduh vs. total ukuran, kecepatan unduh (`MB/s`), estimasi waktu selesai (`ETA`), ikon status non-warna, dan tombol aksi instan (**Pause**, **Resume**, **Cancel**, **Play/Share**).
4. **Edge-to-Edge & Cold-Start Polish:** Mendukung tata letak layar penuh (*edge-to-edge*) dengan `enableEdgeToEdge()`, pengelolaan `WindowInsets` (`consumeWindowInsets` dan *bottom content padding* di `LazyColumn`), serta `android:windowBackground` pada `themes.xml` (`#F8F8F6`) dan `values-night/themes.xml` (`#0E1015`) agar tidak terjadi kilatan putih/hitam saat *cold start*.

## 2. Sistem Warna, Tipografi, & Bentuk (`ui/theme/`)

Palet **Obsidian Ink & Signal Ember** (`Color.kt`, `Theme.kt`) didefinisikan lengkap untuk seluruh peran permukaan dan kontras Material 3 (rasio kontras teks/aksi utama $\ge 4.5:1$ WCAG AA) pada mode gelap dan terang, dengan `dynamicColor = false` secara default agar konsistensi identitas merek tetap terjaga.

### A. Konstanta Merek (`Color.kt`)
* `BrandObsidianInk` = `#111318`
* `BrandSignalEmber` = `#F04A24`
* `BrandEmberGlow` = `#FF5A33`
* `BrandCloudWhite` = `#F8FAFC`
* `BrandTurboMint` = `#22C58B`

### B. Token Material Design 3 (`Color.kt` & `Theme.kt`)

| Token Material 3 | Mode Gelap (`DarkColorScheme`) | Mode Terang (`LightColorScheme`) | Penggunaan Komponen |
| :--- | :--- | :--- | :--- |
| `background` / `surface` | `#0E1015` | `#F8F8F6` | Latar belakang utama layar (`Scaffold`) dan *TopAppBar*. |
| `surfaceContainerLowest` | `#090B0F` | `#FFFFFF` | Lapisan terdalam / area dasar tersembunyi. |
| `surfaceContainerLow` | `#13161C` | `#F3F2EF` | Latar *TopAppBar* saat daftar digulir (`scrolledContainerColor`). |
| `surfaceContainer` | `#171A21` | `#FFFFFF` | Kartu tugas (`DownloadTaskCard`), kartu preset, dan bagian pengaturan. |
| `surfaceContainerHigh` | `#222630` | `#EFEEEB` | *Bottom Sheet*, dialog, banner clipboard, dan *icon well*. |
| `surfaceContainerHighest` | `#2B303D` | `#E6E4DF` | Jalur latar (*track*) bilah progres dan elemen elevasi tertinggi. |
| `primary` / `onPrimary` | `#FF633B` / `#240800` | `#D93814` / `#FFFFFF` | Progres aktif (`DOWNLOADING`), FAB, chip filter terpilih, CTA utama. |
| `primaryContainer` / `onPrimaryContainer` | `#4A190D` / `#FFDAD0` | `#FFE4DC` / `#5C1404` | Lencana video, kartu preset terpilih, tombol aksi primer *Resume/Retry*. |
| `secondary` / `onSecondary` | `#22C58B` / `#03291B` | `#0D7A54` / `#FFFFFF` | Status selesai (`COMPLETED`), status aktif akun X, tombol *Install/Simpan* terdeteksi. |
| `secondaryContainer` / `onSecondaryContainer` | `#0B3B2A` / `#A7F3D0` | `#D6F5E8` / `#053B27` | Lencana `yt-dlp siap`, tombol `Unduh Audio`, lencana audio & `Aktif`. |
| `tertiary` / `onTertiary` | `#F5B82E` / `#332200` | `#9A6000` / `#FFFFFF` | Status dijeda (`PAUSED`) dan indikator persiapan mesin. |
| `tertiaryContainer` / `onTertiaryContainer` | `#423000` / `#FFE6A3` | `#FFF0C7` / `#3B2300` | Tombol *Pause* kontekstual, peringatan izin instalasi APK. |
| `error` / `onError` | `#FB7185` / `#450716` | `#BE123C` / `#FFFFFF` | Status gagal (`FAILED`), aksi batal/hapus, pesan error. |
| `errorContainer` / `onErrorContainer` | `#521020` / `#FFD9DF` | `#FFE0E6` / `#5C051A` | Tombol keluar akun X dan banner error dialog update. |
| `onSurface` / `onSurfaceVariant` | `#F1F3F5` / `#9BA1AE` | `#111318` / `#555B66` | Teks utama dan teks sekunder (*Obsidian Ink* / *Cloud White*). |
| `outline` / `outlineVariant` | `#343A46` / `#232833` | `#D4D6DC` / `#E6E7EB` | Garis pemisah (*divider*), border kartu `1.dp`, batas luar *Switch*. |
| `inverseSurface` / `inverseOnSurface` | `#F1F3F5` / `#111318` | `#171A21` / `#F8FAFC` | Kontainer kontras terbalik. |
| `inversePrimary` / `scrim` | `#D93814` / `#000000` | `#FF633B` / `#000000` | Aksen terbalik dan *scrim* modal/overlay. |

### C. Tipografi (`Type.kt`)
* **Font Judul & Kepala Bagian (Bundled SIL OFL 1.1):** `MontserratFontFamily` (`res/font/montserrat_medium.otf`, `montserrat_semibold.otf`, `montserrat_bold.otf`) diterapkan pada `headlineMedium`, `headlineSmall`, `titleLarge`, `titleMedium`, dan `titleSmall`.
* **Teks Isi (Body):** `FontFamily.Default` pada `bodyLarge`, `bodyMedium`, dan `bodySmall` untuk keterbacaan optimal pada teks panjang dan deskripsi.
* **Metrik & Angka Lebar Tetap (*Tabular Numerals*):** `FontFamily.Default` dengan `fontFeatureSettings = "tnum"` diterapkan pada `labelLarge`, `labelMedium`, dan `labelSmall` (menggantikan `FontFamily.Monospace`) agar angka persentase, kecepatan (`MB/s`), durasi, dan ukuran berkas tidak bergeser saat pembaruan progres tiap 100–250 ms.

### D. Skala Bentuk (`Shape.kt`)
`SwiftGrabShapes` mendefinisikan radius sudut standar Material 3:
* `extraSmall`: `6.dp` (lencana format & durasi)
* `small`: `10.dp` (thumbnail, *icon well* kartu preset, tombol aksi primer kompak)
* `medium`: `16.dp` (`DownloadTaskCard`, `ClipboardSuggestionBanner`, `SettingsSectionCard`, `PresetOptionCard`, FAB)
* `large`: `20.dp`
* `extraLarge`: `24.dp` (`ModalBottomSheet` atas & dialog utama `AlertDialog` / `SwiftGrabAlertDialog`)

## 3. Komponen Identitas Merek Bersama (`BrandMark.kt`)
* **`SwiftGrabSymbol`:** Menggabungkan dua vektor yang dapat diwarnai ulang (`ic_swiftgrab_symbol_arc` + `ic_swiftgrab_symbol_meteor`) — otomatis memakai `BrandCloudWhite`/`BrandEmberGlow` di latar gelap dan `BrandObsidianInk`/`BrandSignalEmber` di latar terang.
* **`SwiftGrabAppIconTile`:** Ubin *squircle* `BrandObsidianInk` (`#111318`, rasio sudut `size * 0.226f`) berisi `SwiftGrabSymbol(size * 0.72f, onDarkSurface = true)` dengan border `1.dp` `DarkOutline` saat berada di latar gelap agar terpisah tegas dari `#0E1015`.
* **`SwiftGrabWordmark`:** Menampilkan vektor kurva `ic_swiftgrab_wordmark` (Montserrat Bold/Medium ber-kerning optis) dengan rasio aspek terkunci `604f / 98f`.
* **`SwiftGrabHorizontalLockup`:** Lockup `SwiftGrabSymbol` + `8.dp` + `SwiftGrabWordmark` dengan `Modifier.semantics(mergeDescendants = true) { text = AnnotatedString(appName); heading() }` sehingga TalkBack membaca `"SwiftGrab"` sebagai heading dan pengujian UI `onNodeWithText("SwiftGrab")` tetap terpenuhi.
* **`SwiftGrabPresetOptionCard`:** Komponen kartu pilihan radio bersama untuk `QuickFormatSheet` dan `SettingsBottomSheet`, menampilkan ikon dalam *tonal well* `40.dp` (`shapes.small`), `preset.displayTitle`, `preset.subtitle`, border `1.5.dp` `primary` saat terpilih (`1.dp` `outlineVariant` saat tidak terpilih), `Modifier.selectable(role = Role.RadioButton)`, dan `RadioButton(onClick = null)`.

## 4. Arsitektur Layar & Komponen UI

Aplikasi mempertahankan arsitektur **Satu Layar Utama (`MainScreen`) + Satu Overlay Transparan (`ShareInterceptActivity`)**.

### A. `ShareInterceptActivity` & `QuickFormatSheet`
1. **Mode Instan / One-Tap (Default):** Langsung meneruskan tautan valid ke `DownloadForegroundService` tanpa membuka layar penuh dan menampilkan `Toast` konfirmasi singkat.
2. **Mode Lembar Format Cepat (`QuickFormatSheet`):**
   * `ModalBottomSheet` (`surfaceContainerHigh`, sudut atas `24.dp`, drag handle `outline`) membungkus `QuickFormatSheetContent`.
   * *Header* menampilkan `SwiftGrabAppIconTile(42.dp)`, judul `format_sheet_title`, dan domain tautan.
   * `MediaPreviewCard` (`surfaceContainer`, border `1.dp` `outlineVariant`) menampilkan *thumbnail* 16:9 (`96.dp x 54.dp`, radius `10.dp`, border `1.dp`) dan `DurationBadge` tabular.
   * Daftar `DownloadPreset` dirender dengan `SwiftGrabPresetOptionCard` di dalam `Modifier.selectableGroup()`, diikuti tombol `Batal` dan `Mulai Unduh` setinggi minimum `48.dp`.

### B. `MainScreen` (Pusat Kendali Unduhan)
1. **Left-Aligned `TopAppBar` (`TopAppBarDefaults.pinnedScrollBehavior()`):**
   * `title` menampilkan `SwiftGrabHorizontalLockup(24.dp, 16.dp)` di atas lencana `EngineStatusBadge` (`yt-dlp siap` / `Menyiapkan mesin…`).
   * `actions` menyediakan `TopBarActionButton` (target sentuh `48.dp` dengan kontainer *squircle* `36.dp` `surfaceContainerHigh` ber-border `1.dp` `outlineVariant`) untuk **Bersihkan Riwayat Selesai** (`hasCompleted`) dan **Pengaturan**.
2. **`FilterChipRow` & Pemisah:**
   * 4 `SwiftGrabFilterChip` (`Semua`, `Aktif`, `Selesai`, `Gagal`) dengan hitungan tabular, `leadingIcon = null` (tanpa lompatan tata letak saat dipilih), warna terpilih `primary`/`onPrimary`, dan `HorizontalDivider(1.dp, outlineVariant)` di bawahnya.
   * `LazyColumn` memakai `bottom = innerPadding.calculateBottomPadding() + 92.dp` agar daftar dapat digulir mulus di belakang bilah navigasi maupun di atas FAB.
3. **Banner Deteksi Clipboard (`ClipboardSuggestionBanner` — *Signal Ember Catch Card*):**
   * Kartu `surfaceContainerHigh` ber-border `1.dp` `primary.copy(alpha = 0.55f)` dengan garis aksen vertikal `4.dp` warna `primary` di tepi awal, `SwiftGrabAppIconTile(38.dp)`, hierarki 2 baris (`labelMedium` label + `titleMedium` domain), tombol tutup `48.dp`, serta tombol **Unduh Video** (`primary`/`onPrimary`) dan **Unduh Audio** (`secondaryContainer`/`onSecondaryContainer`) setinggi minimal `48.dp`.
4. **Status Kosong (`EmptyStateView` — *Meteor Catch Brand Showcase*):**
   * Untuk `TaskFilter.ALL`, menampilkan *orbital arc* geometris minimal (`Canvas` dalam `outlineVariant` & `primary.copy(alpha = 0.14f)`) di sekeliling `SwiftGrabAppIconTile(76.dp)`, judul `titleLarge`, deskripsi `bodyMedium` (`widthIn(max = 300.dp)`), serta 2 pil kemampuan (*Bagikan 1-Ketukan* dan *Deteksi Papan Klip*).
5. **Kartu Tugas Unduhan (`DownloadTaskCard`):**
   * Membaca `liveProgressProvider()` secara *deferred* di `LinearProgressIndicator(progress = progressProvider)` dan `derivedStateOf` pada `MetricsText` guna mencegah rekomposisi seluruh `LazyColumn`.
   * Kartu `MaterialTheme.shapes.medium` (`surfaceContainer`) dengan border `1.dp` kontekstual (`primary` saat mengunduh, `error` saat gagal, `outlineVariant` lainnya), *thumbnail* `80.dp x 46.dp` ber-border `1.dp`, `FormatBadge` + tabular `DurationBadge`, indikator progres `6.dp` di atas `surfaceContainerHighest`, ikon status `14.dp` non-warna di samping `MetricsText` tabular, dan tombol aksi kontekstual primer di dalam sumur *tonal* `36.dp` bertarget sentuh `48.dp`.
6. **Dialog (`AddManualUrlDialog`, `TaskErrorDialog`, `UpdateAvailableDialog`, `XLoginDialog`):**
   * `AddManualUrlDialog` dan `TaskErrorDialog` memakai kerangka `SwiftGrabAlertDialog` (`extraLarge` `24.dp`, `surfaceContainerHigh`, border `1.dp` `outlineVariant`, tombol minimum `48.dp`).
   * `UpdateAvailableDialog` memakai `SwiftGrabAppIconTile(52.dp)`, lencana versi tabular, kotak *changelog* ber-border `1.dp` `outlineVariant`, progres unduh APK tabular, dan tombol minimum `48.dp`.
   * `XLoginDialog` memakai `TopAppBar` rata kiri dengan subjudul `x.com`, `HorizontalDivider(1.dp)`, perbaikan kontras tombol **Simpan & Selesai** (`onSecondary` saat `isAuthDetected`, `onPrimary` sebaliknya), banner status *Turbo Mint* (`secondaryContainer`/`onSecondaryContainer`), serta penguatan keamanan `WebView` (`allowFileAccess = false`, `allowContentAccess = false`, pembatasan URI HTTPS/domain resmi, `imePadding()`).
7. **Lembar Pengaturan (`SettingsBottomSheet` & `SettingsBottomSheetContent`):**
   * Memisahkan `internal fun SettingsBottomSheetContent(...)` agar *stateless* dan dapat diuji serta di-*preview* di mode terang & gelap.
   * Diawali `BrandIdentityHeaderCard` (`SwiftGrabAppIconTile(44.dp)`, `SwiftGrabWordmark(17.dp)`, versi aplikasi tabular, tombol tutup `48.dp`), diikuti kartu bagian seragam (`SettingsSectionCard` `16.dp` ber-border `1.dp` `outlineVariant`), gaya `Switch` konsisten (`swiftGrabSwitchColors()`), `SwiftGrabPresetOptionCard`, serta hierarki tombol CTA yang jelas (`Button` solid untuk aksi utama vs `FilledTonalButton` ber-border `1.dp` `outline` untuk cek pembaruan rutin).

## 5. Krom Sistem, Notifikasi, & Quick Settings Tile
* **Ikon Status & QS Tile Resmi (`drawable/ic_stat_swiftgrab.xml`):** Vektor monokrom putih `24dp` dari potongan optis kecil **Meteor Catch** (`swiftgrab-symbol-small.svg`).
* **Quick Settings Tile (`AndroidManifest.xml`):** `QuickClipboardTileService` menggunakan `android:icon="@drawable/ic_stat_swiftgrab"`.
* **Notifikasi Latar Belakang (`DownloadForegroundService` & `AppUpdateManager`):** Menggunakan `R.drawable.ic_stat_swiftgrab` untuk notifikasi persiapan, progres aktif, dan pembaruan aplikasi, serta aksen warna merek `.setColor(0xFFF04A24.toInt())` pada seluruh pembangun notifikasi.

## 6. Daftar Periksa Kualitas UI untuk AI Agent
* Target sentuh minimum `48.dp x 48.dp` untuk seluruh `IconButton` dan `heightIn(min = 48.dp)` untuk tombol aksi utama.
* Angka dinamis menggunakan `fontFeatureSettings = "tnum"` (`labelLarge`, `labelMedium`, `labelSmall`) agar tata letak tidak bergeser.
* Seluruh ikon interaktif memiliki `contentDescription` bermakna dari `strings.xml`, sementara ikon dekoratif menggunakan `contentDescription = null`.
* Seluruh komponen utama memiliki `@Preview` dalam mode **Dark** dan **Light** menggunakan `SwiftGrabTheme(darkTheme = ...)`.
