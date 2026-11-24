# Panduan Integrasi Notifikasi Motion & Person Detection (Android / Kotlin)
---

## 1. Ringkasan Endpoint Backend yang Tersedia

Semua endpoint mobile berada di bawah prefix `/api/v1/mobile` dan wajib menyertakan header autentikasi:
```http
Authorization: Bearer <access_token>
```

| Fitur | Method & Endpoint | Keterangan |
| :--- | :--- | :--- |
| Daftar Notifikasi | `GET /api/v1/mobile/alerts` | Mendukung query `limit`, `cursor`, `camera_id`, `type` (`motion`/`person`), `severity`, `is_read`. |
| Detail Notifikasi | `GET /api/v1/mobile/alerts/{alert_id}` | Mengembalikan detail alert beserta `thumbnail_url` dan `clip_url`. |
| Tandai Dibaca | `POST /api/v1/mobile/alerts/{alert_id}` | Menandai satu alert sebagai `is_read = true`. |
| Tandai Semua Dibaca | `POST /api/v1/mobile/alerts/read-all` | Menandai seluruh alert customer sebagai dibaca. |
| **Snapshot (JPG)** | `GET /api/v1/mobile/alerts/{alert_id}/snapshot` | Mengembalikan binary `image/jpeg` (butuh header `Authorization`). |
| **Video Klip (MP4)** | `GET /api/v1/mobile/alerts/{alert_id}/clip` | Mengembalikan `video/mp4` dengan dukungan HTTP `Range` (`206 Partial Content`) untuk ExoPlayer. Tambahkan `?download=1` untuk unduhan. |
| **Ambil Pengaturan Alert** | `GET /api/v1/mobile/alerts/settings` | Mengembalikan pengaturan per kamera & jadwal Sleep Mode. |
| **Simpan Pengaturan Alert** | `PUT /api/v1/mobile/alerts/settings` | Menyimpan toggle deteksi per kamera & jadwal Sleep Mode ke backend. |
| **Registrasi FCM Token** | `POST /api/v1/mobile/me/push-tokens` | Mendaftarkan token FCM perangkat Android. |
| **Hapus FCM Token** | `DELETE /api/v1/mobile/me/push-tokens/{installation_id}` | Menghapus token saat logout. |

---

## 2. Integrasi Firebase Cloud Messaging (FCM)

### A. Gradle & Manifest
1. Letakkan `google-services.json` di `app/google-services.json`.
2. Di `app/build.gradle.kts`, aktifkan plugin Google Services dan tambahkan dependency:
   ```kotlin
   implementation("com.google.firebase:firebase-messaging-ktx:24.1.0")
   ```
3. Di `app/src/main/AndroidManifest.xml`:
   - Tambahkan permission untuk Android 13+ (API 33+):
     ```xml
     <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
     ```
   - Daftarkan service FCM di dalam `<application>`:
     ```xml
     <service
         android:name=".service.NayakaFirebaseMessagingService"
         android:exported="false">
         <intent-filter>
             <action android:name="com.google.firebase.MESSAGING_EVENT" />
         </intent-filter>
     </service>
     ```

### B. Registrasi Token ke Backend
Saat aplikasi login berhasil atau saat `onNewToken(token)` dipanggil oleh FCM, kirim request ke `POST /api/v1/mobile/me/push-tokens`:
```json
{
  "installation_id": "<uuid_unik_per_install_aplikasi>",
  "platform": "android",
  "token": "<fcm_registration_token>",
  "permission": "granted",
  "app_version": "1.0.0"
}
```
> **Penting:** Backend hanya mengirim push ke baris `customer_push_tokens` yang memiliki `permission = "granted"`.

### C. Format Payload Push Notification dari Backend
Backend mengirim pesan FCM HTTP v1 dengan struktur:
- **`notification`**:
  - `title`: `"Orang Terdeteksi • <Nama Kamera>"` atau `"Gerakan Terdeteksi • <Nama Kamera>"`
  - `body`: `"Seseorang terdeteksi di area kamera ..."` / `"Gerakan terdeteksi di area kamera ..."`
- **`android.notification`**:
  - `channel_id`: `"cctv_alerts"` (pastikan aplikasi membuat `NotificationChannel` dengan ID `"cctv_alerts"` dan `IMPORTANCE_HIGH` saat startup di `NayakaApp.kt`).
- **`data`**:
  ```json
  {
    "alert_id": "550e8400-e29b-41d4-a716-446655440000",
    "camera_id": "11111111-2222-3333-4444-555555555555",
    "camera_name": "Kamera Teras",
    "type": "person",
    "severity": "critical",
    "occurred_at": "2026-11-24T06:15:00.000Z"
  }
  ```
- **Deep-link saat notifikasi diklik:** Ambil `alert_id` dari `Intent.extras` pada `MainActivity`, lalu arahkan `NavController` ke `AlertDetailScreen(alertId)`.

---

## 3. Update Data Model (`Models.kt`)

Tambahkan field baru berikut pada `AlertItem` di `data/model/Models.kt` agar cocok dengan response `GET /api/v1/mobile/alerts` dan `GET /api/v1/mobile/alerts/{alert_id}`:

```kotlin
data class AlertItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("camera_id") val cameraId: String = "",
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("camera_name") val cameraName: String = "",
    @SerializedName("type") val type: String = "motion", // "motion" | "person"
    @SerializedName("severity") val severity: String = "info", // "info" | "warning" | "critical"
    @SerializedName("title") val title: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("clip_url") val clipUrl: String? = null,
    @SerializedName("clip_duration_seconds") val clipDurationSeconds: Int? = null,
    @SerializedName("evidence_status") val evidenceStatus: String? = null, // "none" | "processing" | "ready" | "failed"
    @SerializedName("occurred_at") val occurredAt: String? = null,
    @SerializedName("time") val time: String = "",
    @SerializedName("is_read") val isRead: Boolean? = null,
    @SerializedName("read") val read: Boolean = false
)
```

> **Catatan URL Relatif:** Field `thumbnail_url` dan `clip_url` dari backend berbentuk path relatif seperti `/api/v1/mobile/alerts/{id}/snapshot` dan `/api/v1/mobile/alerts/{id}/clip`. Gabungkan dengan `BuildConfig.BASE_URL` (atau base URL `NetworkClient`) sebelum dimuat oleh Coil / ExoPlayer.

---

## 4. Menampilkan Snapshot & Memutar Video 10 Detik (Auth Header)

Karena endpoint `/snapshot` dan `/clip` dilindungi oleh JWT customer (`Authorization: Bearer <token>`), baik **Coil** maupun **Media3 ExoPlayer** harus menggunakan `OkHttpClient` yang sudah memiliki `AuthInterceptor` dari `NetworkClient.kt`:

1. **Snapshot dengan Coil (`AsyncImage`)**:
   - Konfigurasikan `ImageLoader` singleton menggunakan `OkHttpClient` ber-auth, lalu render `thumbnailUrl` di `AlertItemCard` (`CommonComponents.kt`) dan `AlertDetailScreen`.
2. **Video Klip 10 Detik dengan Media3 ExoPlayer**:
   - Gunakan `OkHttpDataSource.Factory(authenticatedOkHttpClient)` (atau `DefaultHttpDataSource.Factory().setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))`) pada `ExoPlayer`.
   - Backend sudah mendukung HTTP `Range` (`206 Partial Content`), sehingga seekbar dan replay di `PlayerView` berjalan mulus.
   - Untuk tombol **Unduh Video**, panggil URL `${baseUrl}${clipUrl}?download=1` dengan header `Authorization` dan simpan ke `MediaStore.Downloads` / `MediaStore.Video`.

---

## 5. Menghubungkan `AlertSettingsScreen.kt` ke Backend

Layar `AlertSettingsScreen.kt` sudah ada di aplikasi mobile (saat ini berstatus *Pratinjau UI*). Hubungkan ke API berikut:

### A. Muat Pengaturan (`GET /api/v1/mobile/alerts/settings`)
Contoh response `data`:
```json
{
  "cameras": [
    {
      "camera_id": "11111111-2222-3333-4444-555555555555",
      "camera_name": "Kamera Teras",
      "motion_detection_enabled": true,
      "person_detection_enabled": true,
      "notifications_enabled": true
    }
  ],
  "sleep_mode": {
    "enabled": false,
    "start_time": "22:00",
    "end_time": "06:00",
    "active_days": ["Sen", "Sel", "Rab", "Kam", "Jum"],
    "camera_ids": []
  }
}
```

### B. Simpan Pengaturan (`PUT /api/v1/mobile/alerts/settings`)
Body request yang dikirim saat tombol **"Simpan pengaturan"** ditekan:
```json
{
  "cameras": [
    {
      "camera_id": "11111111-2222-3333-4444-555555555555",
      "motion_detection_enabled": true,
      "person_detection_enabled": true,
      "notifications_enabled": true
    }
  ],
  "sleep_mode": {
    "enabled": true,
    "start_time": "22:00",
    "end_time": "06:00",
    "active_days": ["Sen", "Sel", "Rab", "Kam", "Jum"],
    "camera_ids": ["11111111-2222-3333-4444-555555555555"]
  }
}
```
- Nilai `active_days` yang valid: `"Sen"`, `"Sel"`, `"Rab"`, `"Kam"`, `"Jum"`, `"Sab"`, `"Min"`.
- Setelah terhubung ke endpoint ini, hapus komponen `PreviewAlertNotice()` di `AlertSettingsScreen.kt`.

---

## 6. Checklist Singkat untuk Programmer Mobile

1. [ ] Tambah `firebase-messaging-ktx`, `google-services.json`, dan `POST_NOTIFICATIONS` permission.
2. [ ] Buat `NayakaFirebaseMessagingService` + Notification Channel `"cctv_alerts"` + kirim token ke `POST /api/v1/mobile/me/push-tokens`.
3. [ ] Update `AlertItem` di `Models.kt` (`thumbnailUrl`, `clipUrl`, `clipDurationSeconds`, `evidenceStatus`).
4. [ ] Update `AlertItemCard` (`CommonComponents.kt`) & `AlertsScreen.kt` (tampilkan thumbnail snapshot + filter `Person` / `Motion`).
5. [ ] Buat `AlertDetailScreen.kt` + route di `Screen.kt` & `AppNavHost.kt` (tampilkan snapshot + ExoPlayer video 10 detik + tombol unduh + deep-link dari notif).
6. [ ] Hubungkan `AlertSettingsScreen.kt` ke `GET` & `PUT /api/v1/mobile/alerts/settings`.
