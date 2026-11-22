# SwiftGrab — Brand & Logo Guidelines

> Panduan ringkas penggunaan identitas visual **SwiftGrab** untuk layar (*digital*), Android launcher, web, dan cetak (*print*).

## 1. Ide & Arsitektur Logo
- **Ide Utama (*Meteor Catch*):** Busur penangkap (*Obsidian Ink*) yang menyongsong komet/tetesan 45° (*Signal Ember* / *Ember Glow*) — melambangkan tangkapan instan (*Zero-Paste 1-Tap*) yang cepat, terarah, dan bebas gangguan.
- **Varian Resmi:**
  - **App Icon** (`swiftgrab-app-icon.svg`) — Simbol di atas ubin *squircle* `Obsidian Ink` (`#111318`).
  - **Horizontal Lockup** (`swiftgrab-horizontal-color.svg` / `-on-dark.svg`) — Lockup utama untuk README, header web, dan banner rilis.
  - **Stacked Lockup** (`swiftgrab-stacked-color.svg` / `-on-dark.svg`) — Untuk ruang persegi, kartu rilis, dan media sosial.
  - **Symbol-Only** (`swiftgrab-symbol-color.svg` / `-on-dark.svg` / `-black.svg` / `-white.svg` / `-mono-f04a24.svg`) — Avatar, badge, dan ikon aplikasi.
  - **Wordmark-Only** (`swiftgrab-wordmark-dark.svg` / `-white.svg`) — Saat simbol sudah tampil di dekatnya.
- **Lokasi File:**
  - Master VectorCraft (`.vectorcraft`): `out/logo-kit/source/`
  - Digital SVG & PNG (sRGB): `out/logo-kit/digital/`
  - Paket Web & Favicon (`favicon.ico`, PWA, `site.webmanifest`): `out/logo-kit/digital/web-icons/`
  - Print Vector PDF: `out/logo-kit/print/`
  - Aset Repositori Aktif: `assets/logo/`

## 2. Zona Aman (*Clear Space*)
Sisakan ruang kosong minimal **1 × $u$** di seluruh sisi logo, dengan **$u$ = lebar ketebalan lengan busur penangkap** (`36 pt` pada kanvas simbol `256 × 256`, atau setara **~14% lebar simbol**). Ruang aman ini selalu mengikuti skala logo secara proporsional.

## 3. Ukuran Minimum
| Varian | Layar (*Screen*) | Cetak (*Print*) | Catatan |
| :--- | :--- | :--- | :--- |
| **Horizontal Lockup** | Lebar min. `120 px` | Lebar min. `32 mm` | Jangan memakai tagline di bawah `160 px`. |
| **Stacked Lockup** | Lebar min. `88 px` | Lebar min. `24 mm` | Gunakan symbol-only bila ruang lebih sempit. |
| **Symbol-Only** | `24 px` (standar) / `16 px` (`-small`) | `6 mm` | Gunakan `swiftgrab-symbol-small.svg` / `favicon-*` untuk ukuran ≤ `24 px`. |

## 4. Sistem Warna (*Obsidian Ink & Signal Ember*)
| Nama Warna | HEX | RGB | CMYK *(perkiraan)* | Pantone *(perkiraan)* | Penggunaan Utama |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Signal Ember** | `#F04A24` | `240, 74, 36` | `0, 79, 92, 0` | `1655 C` | Komet pada latar terang, versi mono merek, aksen utama Light UI (`#D93814`). |
| **Ember Glow** | `#FF5A33` | `255, 90, 51` | `0, 74, 86, 0` | `171 C` | Komet pada ubin `#111318` / latar gelap, aksen aktif Dark UI (`#FF633B`). |
| **Obsidian Ink** | `#111318` | `17, 19, 24` | `78, 70, 62, 85` | `Black 6 C` | Busur penangkap (latar terang), wordmark, latar ubin App Icon. |
| **Cloud White** | `#F8FAFC` | `248, 250, 252` | `2, 1, 0, 0` | — | Busur penangkap & wordmark di atas latar gelap, latar terang (`Paper`). |
| **Turbo Mint** *(UI)* | `#22C58B` | `34, 197, 139` | `73, 0, 56, 0` | `3395 C` | Status selesai (`COMPLETED`) dan lencana akselerasi `aria2c` di UI aplikasi. |

- **Pasangan Latar yang Disetujui:**
  1. Full color terang (`#111318` + `#F04A24`) di atas `#FFFFFF` / `#F8F8F6`.
  2. Full color gelap (`#F8FAFC` + `#FF5A33`) di atas `#111318` / `#0E1015`.
  3. Satu warna hitam (`#000000`) di atas putih/terang.
  4. Satu warna putih (*reversed*, versi yang ditipiskan `-white.svg`) di atas latar gelap atau foto bernuansa tenang.

## 5. Tipografi
- **Wordmark:** Komposisi dua bobot (*dual-weight*) **`Swift`** (`Montserrat Bold`) + **`Grab`** (`Montserrat Medium`) dengan *kerning* optis khusus yang telah dikonversi 100% menjadi kurva vektor (*outlined paths*).
- **Tipografi UI Aplikasi:** Sistem default Material 3 / Sans-Serif dengan `fontFeatureSettings = "tnum"` (angka lebar tetap) untuk metrik kecepatan dan persentase unduhan.
- **Lisensi Font:** `Montserrat` berlisensi **SIL Open Font License 1.1** (bebas untuk proyek komersial & *open-source*).

## 6. Larangan Penggunaan (*Don'ts*)
- Jangan menarik/memipihkan proporsi logo secara tidak seragam (*stretch/squash*).
- Jangan mengganti warna di luar palet resmi atau memberi efek *drop shadow*, *outer neon glow*, *bevel*, maupun gradasi tambahan pada logo master.
- Jangan memutar sudut komet/busur (orientasi resmi tetap 45° dari kanan atas ke pusat fokus).
- Jangan mengetik ulang kata "SwiftGrab" sebagai pengganti file wordmark/lockup resmi.
- Jangan menaruh simbol langsung di atas foto/video ramai tanpa kontainer `#111318` atau versi monokrom kontras tinggi.

## 7. Catatan Penyerahan (*Handover Notes*)
- Seluruh file SVG master telah diaudit (`svg_audit.py`: skor **99–100/100**, `0` live `<text>`, `0` filter, `0` *near-miss angle*).
- Pencocokan CMYK dan Pantone bersifat perkiraan digital — verifikasi dengan panduan cetak fisik sebelum produksi sablon/kemasan massal.
- Untuk penggunaan publik/merek dagang komersial luas, disarankan melakukan penelusuran merek dagang (*trademark & reverse image search*) terlebih dahulu.
