# 📱 JAGA RUMAH (Native Android - Kotlin)

> **Aplikasi Smart Home untuk keamanan dan kendali hunian — Native Android (Kotlin & Jetpack Compose)**
> Hasil migrasi lengkap dari versi React Native ke Native Kotlin modern dengan arsitektur MVVM, Material 3 Design, dan Jetpack Navigation.

---

## 📑 Daftar Isi

- [Gambaran Umum](#-gambaran-umum)
- [Teknologi & Dependensi](#-teknologi--dependensi)
- [Arsitektur Proyek](#-arsitektur-proyek)
- [Performa & Strategi Caching](#-performa--strategi-caching)
- [Pemetaan Migrasi (React Native ke Kotlin)](#-pemetaan-migrasi-react-native-ke-kotlin)
- [Struktur Direktori](#-struktur-direktori)
- [Konfigurasi API & Lingkungan](#-konfigurasi-api--lingkungan)
- [Cara Membuka & Menjalankan](#-cara-membuka--menjalankan)
- [Fitur Utama](#-fitur-utama)
- [Arah Pengembangan Smart Home](#-arah-pengembangan-smart-home)

---

## 📖 Gambaran Umum

**JAGA RUMAH** adalah aplikasi mobile klien native yang dibangun menggunakan Kotlin dan Jetpack Compose. Aplikasi berawal dari fitur pemantauan CCTV dan dikembangkan menuju satu tempat untuk memantau keamanan serta mengendalikan perangkat rumah:
- **UI Murni Deklaratif**: Dibangun 100% menggunakan **Jetpack Compose** dan **Material 3**.
- **Live CCTV Streamer**: Meminta URL playback bertoken dari backend dan memutar WebRTC (WHEP), HLS (fMP4 low latency) & RTSP dengan Media3; polling snapshot dinamis otomatis untuk dashboard, serta MJPEG tetap didukung untuk endpoint legacy.
- **Perekaman Video & Snapshot Lokal**: Perekaman siaran langsung CCTV langsung tersimpan ke penyimpanan publik galeri (`Movies/NayakaCCTV`) dalam format MP4/fMP4 tanpa freeze timestamp, lengkap dengan thumbnail otomatis dan manajemen hapus rekaman.
- **Kontrol Kamera (PTZ & Sirine)**: Kontrol arah (Atas, Bawah, Kiri, Kanan, Zoom In/Out) dengan feedback status responsif, pembunyian sirine darurat langsung dari live view, serta audio dua arah (Push-to-Talk).
- **Keamanan Sesi & Toleransi Kunci (Grace Period)**: Menggunakan `BiometricPrompt` & `EncryptedSharedPreferences` dengan toleransi 30 detik (grace period) saat berpindah aplikasi sebelum layar kunci aktif kembali.
- **Mode Layar Penuh (Fullscreen Landscape)**: Rotasi dinamis ke landscape dengan menyembunyikan system bar untuk pemantauan CCTV dan video playback yang imersif.
- **Auto-Refresh Token Interceptor**: Mutex berbasis `OkHttp Authenticator` yang memperbarui JWT access token secara mulus tanpa membuat pengguna logout paksa.

---

## 🛠 Teknologi & Dependensi

| Komponen | Pustaka / Tools | Versi | Keterangan |
| :--- | :--- | :--- | :--- |
| **Language** | Kotlin | `2.0.21` | Bahasa utama proyek |
| **Build System** | Gradle & AGP | `8.7.3` | Menggunakan Gradle Version Catalog (`libs.versions.toml`) |
| **Target SDK** | Android SDK | `API 35` | Min SDK 24 (Android 7.0+) |
| **UI Framework** | Jetpack Compose | BOM `2024.11.00` | UI deklaratif modern |
| **Design System** | Material 3 | Latest | Komponen tombol, kartu, navigasi, dialog |
| **Navigasi** | Navigation Compose | `2.8.4` | NavHost single-activity pattern |
| **Networking** | Retrofit 2 + OkHttp 3 | `2.11.0` / `4.12.0` | Client REST API dengan HttpLoggingInterceptor |
| **Serialization** | Gson | `2.11.0` | Parser JSON envelope `{ data, meta }` |
| **Keamanan** | Security Crypto & Biometric | `1.1.0-alpha06` / `1.2.0` | AES-256 GCM EncryptedSharedPreferences & Sidik Jari/Wajah |
| **Image Loading** | Coil Compose | `2.7.0` | Pemuat thumbnail kamera & live snapshot polling |
| **Video Stream** | AndroidX Media3 & WebRTC | `1.5.1` / `1.0.32006` | Pemutar native WebRTC (WHEP), HLS (Low-latency fMP4), RTSP, dan fallback MJPEG |

---

## 🏗 Arsitektur Proyek

Aplikasi menggunakan pola arsitektur **Clean MVVM (Model-View-ViewModel/Repository)**:

```
[UI Layer / Jetpack Compose]
   │  Screens: Splash, Login, Smart Home Preview, Cameras, LiveView, Playback, Alerts, SOS, Profile
   │  Theme: Colors, Typography, Shapes
   ▼
[Navigation Layer]
   │  AppNavHost: AuthFlow vs MainTabsFlow
   ▼
[Domain / Repository Layer]
   │  NayakaRepository: Mengelola aliran data bisnis & penanganan error
   ▼
[Data Layer]
   ├── Network: Retrofit2 + ApiService + AuthInterceptor + TokenAuthenticator
   └── Local: TokenManager (EncryptedSharedPreferences)
```

---

## ⚡ Performa & Strategi Caching

**Caching** menyimpan hasil data yang baru diambil agar halaman dapat menampilkannya kembali tanpa selalu meminta data yang sama ke server. Aplikasi kini menggunakan **cache in-memory dengan masa berlaku (TTL)** untuk mengurangi request dan loading berulang saat pengguna berpindah halaman.

### Data yang di-cache

| Data | TTL |
| :--- | :--- |
| Ringkasan Dashboard | 15 detik |
| Daftar kamera dan detail kamera | 30 detik |
| Daftar rekaman | 30 detik |
| Alert | 15 detik |

Cache berada di `NayakaRepository`, yang hidup selama proses aplikasi. Karena itu, hasilnya tetap tersedia saat composable tab dibuat ulang. Request serentak untuk cache key yang sama digabungkan. Jika cache kedaluwarsa, data dimuat kembali dari server.

Tombol **Muat Ulang** pada Dashboard dan Kamera melewati cache. Cache terkait juga dibersihkan setelah perubahan kamera, perubahan status Alert, perubahan rekaman lokal, atau logout; sesi login baru memulai dengan cache kosong.

Cache hanya berlaku selama aplikasi berjalan; cache tidak disimpan setelah proses aplikasi dihentikan. Cache yang kedaluwarsa dimuat ulang ketika halaman meminta data, bukan diperbarui di latar belakang. **Room/database lokal** dapat dipertimbangkan kemudian jika data harus tetap tersedia setelah aplikasi ditutup atau ketika offline.

### Optimasi lanjutan

- Ukur waktu tunggu dan request berulang dengan Android Studio Network Inspector/Profiler sebelum menentukan optimasi berikutnya.
- Gunakan pagination untuk daftar besar jika endpoint backend mendukungnya.
- Pastikan live stream berhenti saat pengguna meninggalkan layar dan hanya stream yang sedang ditonton yang aktif.
- Pertimbangkan cache persisten hanya jika hasil pengukuran atau kebutuhan offline membenarkannya.

**Load balancing** menangani beban banyak request/pengguna pada sisi server; fitur itu tidak mencegah satu aplikasi mengulang request ketika halaman dibuka. Pertimbangkan load balancing jika metrik backend menunjukkan server kewalahan oleh akses bersamaan, bukan sebagai langkah pertama untuk mengatasi loading ulang di aplikasi.

---

## 🔄 Pemetaan Migrasi (React Native ke Kotlin)

| Modul React Native | Padanan di Proyek Kotlin |
| :--- | :--- |
| `App.tsx` & `RootNavigator.tsx` | `MainActivity.kt` & `AppNavHost.kt` |
| `src/context/AuthContext.tsx` | `TokenManager.kt` & `AuthRepository` |
| `src/context/DataContext.tsx` | `NayakaRepository.kt` |
| `src/api.ts` | `ApiService.kt`, `AuthInterceptor.kt`, `TokenAuthenticator.kt` |
| `src/types.ts` | `Models.kt` & `Requests.kt` |
| `src/components/MjpegView.tsx` | `CameraStreamPlayer.kt` & `MjpegStreamView.kt` |
| `src/components/CameraThumbnail.tsx` | `CameraCard.kt` (dengan Coil async image) |
| `src/utils/biometric.ts` | `BiometricHelper.kt` (`BiometricPrompt`) |
| `src/utils/format.ts` | `Formatters.kt` |
| `src/screens/auth/*` | `com.nayakacctv.ui.screens.auth.*` |
| `src/screens/home/DashboardScreen.tsx` | `DashboardScreen.kt` |
| `src/screens/home/CamerasScreen.tsx` | `CamerasScreen.kt` |
| `src/screens/home/CameraDetailScreen.tsx` | `CameraDetailScreen.kt` |
| `src/screens/home/LiveViewScreen.tsx` | `LiveViewScreen.kt` |
| `src/screens/home/PlaybackScreen.tsx` | `PlaybackScreen.kt` & `PlaybackDetailScreen.kt` |
| `src/screens/home/AlertsScreen.tsx` | `AlertsScreen.kt` |
| `src/screens/home/EmergencyScreen.tsx` | `EmergencyScreen.kt` |
| `src/screens/home/ProfileScreen.tsx` | `ProfileScreen.kt` |
| `src/screens/home/SubscriptionScreen.tsx` | `SubscriptionScreen.kt` |
| `src/screens/home/HelpScreen.tsx` | `HelpScreen.kt` & `TermsScreen.kt` |

---

## 📁 Struktur Direktori

```text
nayaka-mobile-kotlin/
├── gradle/
│   ├── libs.versions.toml     # Dependensi versi terpusat (Version Catalog)
│   └── wrapper/               # Gradle wrapper executable
├── app/
│   ├── build.gradle.kts       # Konfigurasi modul aplikasi Android
│   ├── proguard-rules.pro     # Aturan obfuscation r8/proguard
│   └── src/main/
│       ├── AndroidManifest.xml # Izin internet, biometrik, telepon, dll.
│       ├── res/               # Resource XML (strings, colors, network config)
│       └── java/com/nayakacctv/
│           ├── NayakaApp.kt   # Inisialisasi Application class
│           ├── MainActivity.kt # Entry point Activity dengan NavHost
│           ├── data/
│           │   ├── local/
│           │   │   └── TokenManager.kt # Sesi & token terenkripsi
│           │   ├── model/
│           │   │   ├── Models.kt      # Customer, Camera, Alert, Recording, dll.
│           │   │   └── Requests.kt    # Payload request login, update, sos, dll.
│           │   ├── network/
│           │   │   ├── ApiService.kt  # Endpoint Retrofit /api/v1/mobile/*
│           │   │   ├── AuthInterceptor.kt # Bearer token injection
│           │   │   ├── TokenAuthenticator.kt # Mutex auto-refresh token
│           │   │   └── NetworkClient.kt # Retrofit builder
│           │   └── repository/
│           │       └── NayakaRepository.kt # Abstraksi pemanggilan API
│           └── ui/
│               ├── components/
│               │   ├── CommonComponents.kt # TopAppBar, Badges, Button, Card
│               │   └── MjpegStreamView.kt  # Pemutar live view CCTV
│               ├── navigation/
│               │   ├── Screen.kt      # Rute navigasi
│               │   └── AppNavHost.kt  # Compose Navigation graph
│               ├── screens/           # Halaman UI
│               │   ├── auth/          # Login, Register, Forgot, Reset
│               │   ├── dashboard/     # Ringkasan rumah & status CCTV
│               │   ├── cameras/       # List, Detail, LiveView
│               │   ├── smarthome/     # Pratinjau perangkat, otomasi, dan menu lainnya
│               │   ├── playback/      # List rekaman & Video timeline player
│               │   ├── alerts/        # Notifikasi bahaya & filter
│               │   ├── emergency/     # Panic SOS & Telepon darurat
│               │   ├── profile/       # Pengaturan akun & biometrik
│               │   ├── subscription/  # Paket cloud storage
│               │   └── help/          # Bantuan & Syarat Ketentuan
│               ├── theme/
│               │   ├── Color.kt       # Palet warna Nayaka
│               │   ├── Type.kt        # Tipografi modern
│               │   └── Theme.kt       # Material 3 Theme wrapper
│               └── utils/
│                   ├── BiometricHelper.kt # Dialog pemindai sidik jari
│                   └── Formatters.kt      # Format mata uang IDR & waktu
├── build.gradle.kts           # Root build gradle
├── settings.gradle.kts        # Root settings gradle
└── gradle.properties          # JVM args & AndroidX flags
```

---

## 🌐 Konfigurasi API & Lingkungan

Secara default, URL backend mengarah ke server development:
- `BASE_URL = "http://dev.nayakacloud.com:701/api/v1/"` (pada `NetworkClient.kt`)

Jika Anda ingin menguji ke backend Next.js lokal yang berjalan di komputer:
1. Buka berkas `app/src/main/java/com/nayakacctv/data/network/NetworkClient.kt`
2. Ubah `BASE_URL`:
   - **Android Emulator**: `http://10.0.2.2:3000/api/v1/`
   - **HP Fisik (USB Debugging)**: `http://<IP_KOMPUTER_LOKAL>:3000/api/v1/`

---

## 🚀 Cara Membuka & Menjalankan

### Cara 1: Menggunakan Android Studio (Direkomendasikan)
1. Buka **Android Studio**.
2. Pilih **Open...** dan arahkan ke folder:
   `D:\Jaga Rumah\nayaka-mobile-master\nayaka-mobile-master\nayaka-mobile-kotlin`
3. Tunggu hingga proses **Gradle Sync** selesai secara otomatis.
4. Pilih target perangkat (Emulator Android atau HP Fisik).
5. Klik tombol hijau **Run 'app'** (atau tekan `Shift + F10`).

### Cara 2: Menggunakan Terminal / Command Line
Masuk ke folder proyek Kotlin:
```bash
cd "D:\Jaga Rumah\nayaka-mobile-master\nayaka-mobile-master\nayaka-mobile-kotlin"

# Build APK Debug
./gradlew assembleDebug

# Instal langsung ke perangkat yang terhubung
./gradlew installDebug
```
Berkas APK yang dihasilkan akan berada di:
`app/build/outputs/apk/debug/app-debug.apk`

---

## ✨ Fitur Utama

1. **Autentikasi Modern**:
   - Login / Pendaftaran Akun email & kata sandi
   - Otentikasi Biometrik instan (*Fingerprint / Face ID*)
   - Pemulihan sandi via kode OTP
   - Sesi aman dengan token auto-refresh di latar belakang
2. **Dashboard Cerdas & Snapshot Dinamis**:
   - Ringkasan total kamera, status online, merekam, dan offline.
   - **Snapshot Terkini Dinamis**: Polling otomatis snapshot terbaru tiap 6 detik dengan indikator live aktif tanpa membebani bandwidth video kontinu.
   - Akses darurat cepat & notifikasi ancaman kendaraan/orang terkini.
3. **Kamera & Siaran Langsung (Live View)**:
   - Pencarian kamera dan filter status.
   - Pemutar live streaming multi-protokol (WebRTC WHEP, HLS fMP4 berlatensi rendah, RTSP, MJPEG).
   - **Mode Layar Penuh (Landscape)**: Tampilan layar penuh dengan rotasi otomatis sensor dan penyembunyian bilah navigasi sistem.
   - **Kontrol PTZ**: Pengaturan arah kamera (Atas, Bawah, Kiri, Kanan, Zoom In/Out) dengan lencana status respons server.
   - **Bunyikan Sirine**: Tombol picu sirine alarm darurat terintegrasi langsung dari layar live view dengan dialog konfirmasi.
   - **Push-to-Talk**: Komunikasi audio dua arah ke speaker kamera.
   - **Snapshot Frame Instan**: Pengambilan gambar frame video real-time dan tersimpan langsung ke galeri foto (`Pictures/NayakaCCTV`).
   - **Perekaman Video CCTV**: Perekaman fMP4 instan langsung ke penyimpanan publik (`Movies/NayakaCCTV`) dengan normalisasi timestamp (tanpa freeze frame awal) dan indikator durasi REC.
4. **Histori Rekaman & Timeline Player (Playback)**:
   - Filter histori rekaman per kamera hingga 30 hari.
   - Scrubbing timeline slider dan kontrol kecepatan (0.5x, 1x, 2x, 4x).
   - Mode layar penuh untuk pemutaran rekaman.
   - Dukungan unduh rekaman lokal dan server.
   - **Hapus Rekaman**: Fitur hapus berkas rekaman langsung dari aplikasi beserta sinkronisasi cache.
5. **Peringatan Bahaya (Alerts)**:
   - Filter level ancaman (*Kritis, Belum Dibaca*)
   - Tandai semua notifikasi sudah dibaca
6. **Layanan Darurat (SOS)**:
   - Tombol Panic SOS merah mencolok dengan konfirmasi keamanan
   - Panggilan cepat telepon darurat (Polisi 110, Damkar 113, Medis 119, Satpam Lingkungan)
7. **Langganan Cloud & Bantuan**:
   - Manajemen paket penyimpanan cloud & histori tagihan
   - Artikel bantuan dan Syarat & Ketentuan lengkap
8. **Pratinjau Smart Home**:
   - Dashboard rumah dengan pintasan Perangkat dan Otomasi
   - UI pratinjau daftar perangkat per ruangan dan contoh rutinitas
   - Belum mengendalikan perangkat sungguhan; sakelar pratinjau dinonaktifkan

## 🏠 Arah Pengembangan Smart Home

JAGA RUMAH berkembang dari aplikasi CCTV menjadi pusat kendali rumah. Dashboard, daftar perangkat, dan otomasi kini memiliki UI pratinjau; data perangkat masih contoh dan belum ada perintah yang dikirim ke perangkat.

### Navigasi utama

- **Beranda** — ringkasan status rumah, akses cepat ke adegan favorit, ruangan, kamera, dan aktivitas terbaru.
- **Perangkat** — daftar perangkat yang dikelompokkan berdasarkan ruangan, dengan filter kategori seperti lampu, kunci pintu, TV, dan AC.
- **Kamera** — live view, playback, dan peringatan kamera dalam area khusus.
- **Otomasi** — jadwal dan aturan perangkat, misalnya menyalakan lampu teras pada waktu tertentu.
- **Lainnya** — akses ke playback, peringatan CCTV, profil, dan pratinjau aktivitas rumah.

Aktivitas perangkat dan otomatisasi masih berupa pratinjau sampai tersedia integrasi backend dan perangkat.

### Prinsip tampilan dan kontrol

- Beranda menampilkan informasi penting tanpa memenuhi layar dengan seluruh kontrol perangkat.
- Kartu perangkat memperlihatkan nama, ruangan, status, dan waktu pembaruan; kontrol rinci dibuka pada halaman detail perangkat.
- Kontrol disesuaikan dengan perangkat, seperti sakelar dan tingkat kecerahan lampu, suhu AC, atau status kunci pintu.
- Aksi keamanan seperti membuka kunci pintu perlu label yang jelas dan konfirmasi tambahan.
- Status offline, gagal merespons, dan waktu pembaruan terakhir harus dibedakan dari status berhasil.
- Adegan seperti **Pulang**, **Mode Tidur**, atau **Matikan Semua** ditampilkan hanya bila perangkat dan aturan adegannya telah dikonfigurasi.

Penambahan dukungan perangkat memerlukan API/backend dan jalur komunikasi yang sesuai dengan ekosistem perangkat (misalnya Wi-Fi, Zigbee, atau Matter); perubahan tampilan saja tidak cukup untuk mengendalikan perangkat fisik.
