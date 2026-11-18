# 📱 Nayaka CCTV Mobile (Native Android - Kotlin)

> **Aplikasi Pemantauan CCTV & Keamanan Hunian Pintar — Native Android (Kotlin & Jetpack Compose)**  
> Hasil migrasi lengkap dari versi React Native ke Native Kotlin modern dengan arsitektur MVVM, Material 3 Design, dan Jetpack Navigation.

---

## 📑 Daftar Isi

- [Gambaran Umum](#-gambaran-umum)
- [Teknologi & Dependensi](#-teknologi--dependensi)
- [Arsitektur Proyek](#-arsitektur-proyek)
- [Pemetaan Migrasi (React Native ke Kotlin)](#-pemetaan-migrasi-react-native-ke-kotlin)
- [Struktur Direktori](#-struktur-direktori)
- [Konfigurasi API & Lingkungan](#-konfigurasi-api--lingkungan)
- [Cara Membuka & Menjalankan](#-cara-membuka--menjalankan)
- [Fitur Utama](#-fitur-utama)

---

## 📖 Gambaran Umum

**Nayaka CCTV Android Kotlin** adalah aplikasi mobile klien native yang dibangun dari awal menggunakan standar terbaru ekosistem Android:
- **UI Murni Deklaratif**: Dibangun 100% menggunakan **Jetpack Compose** dan **Material 3**.
- **Live MJPEG Streamer**: Komponen stream native berlatensi rendah dengan snapshot frame, timer perekaman, dan kontrol arah kamera (PTZ).
- **Keamanan Perangkat Keras**: Menggunakan `BiometricPrompt` & `EncryptedSharedPreferences` yang terenkripsi langsung via Android Keystore.
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
| **Image Loading** | Coil Compose | `2.7.0` | Pemuat thumbnail kamera asynchronous |
| **Video Stream** | AndroidX WebKit | `1.12.1` | Native WebEngine decoding MJPEG multipart stream |

---

## 🏗 Arsitektur Proyek

Aplikasi menggunakan pola arsitektur **Clean MVVM (Model-View-ViewModel/Repository)**:

```
[UI Layer / Jetpack Compose]
   │  Screens: Login, Dashboard, Cameras, LiveView, Playback, Alerts, SOS, Profile
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

## 🔄 Pemetaan Migrasi (React Native ke Kotlin)

| Modul React Native | Padanan di Proyek Kotlin |
| :--- | :--- |
| `App.tsx` & `RootNavigator.tsx` | `MainActivity.kt` & `AppNavHost.kt` |
| `src/context/AuthContext.tsx` | `TokenManager.kt` & `AuthRepository` |
| `src/context/DataContext.tsx` | `NayakaRepository.kt` |
| `src/api.ts` | `ApiService.kt`, `AuthInterceptor.kt`, `TokenAuthenticator.kt` |
| `src/types.ts` | `Models.kt` & `Requests.kt` |
| `src/components/MjpegView.tsx` | `MjpegStreamView.kt` |
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
│               │   ├── dashboard/     # Ringkasan status CCTV
│               │   ├── cameras/       # List, Detail, LiveView
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
2. **Dashboard Cerdas**:
   - Ringkasan total kamera, online, sedang merekam, dan offline
   - Akses darurat cepat & notifikasi terkini
3. **Kamera & Siaran Langsung (Live View)**:
   - Pencarian kamera dan filter status
   - Pemutar live streaming MJPEG latensi rendah
   - Kontrol PTZ (Atas, Bawah, Kiri, Kanan)
   - Snapshot foto seketika ke memori perangkat
   - Perekaman video lokal dengan indikator REC
4. **Histori Rekaman & Timeline Player**:
   - Filter histori rekaman per kamera
   - Scrubbing timeline video slider
   - Pengatur kecepatan putar (0.5x, 1x, 2x, 4x)
5. **Peringatan Bahaya (Alerts)**:
   - Filter level ancaman (*Kritis, Belum Dibaca*)
   - Tandai semua notifikasi sudah dibaca
6. **Layanan Darurat (SOS)**:
   - Tombol Panic SOS merah mencolok dengan konfirmasi keamanan
   - Panggilan cepat telepon darurat (Polisi 110, Damkar 113, Medis 119, Satpam Lingkungan)
7. **Langganan Cloud & Bantuan**:
   - Manajemen paket penyimpanan cloud & histori tagihan
   - Artikel bantuan dan Syarat & Ketentuan lengkap
