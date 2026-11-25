# Panduan Integrasi Mobile (Android / Kotlin): Fitur Deteksi Kendaraan AI & Zona Parkir

Dokumen ini adalah panduan implementasi untuk **Programmer Mobile (`nayaka-mobile-master`)** agar dapat mengintegrasikan fitur **Deteksi Kendaraan (AI)**, **Mode Jaga Parkir (Anti-Maling)**, dan **Editor Gambar Zona Parkir** yang sudah selesai dibangun di sisi Backend & Worker VPS (`nayaka-web-main`).

---

## 1. Ringkasan Fitur di Backend

Backend & Worker VPS sekarang mendukung:
1. **Dual-Toggle Deteksi Kendaraan per Kamera**:
   - `vehicle_detection_enabled` (`Boolean`, default `true`): Mengaktifkan deteksi kendaraan AI (`motorcycle`, `car`, `bicycle`, `bus`, `truck`).
   - `vehicle_guard_mode` (`Boolean`, default `false`): Mengaktifkan **Mode Jaga Parkir (Anti-Maling)**. Saat aktif, AI mengunci koordinat motor/mobil yang sedang parkir dan mengirim alert `severity = "critical"` jika kendaraan bergeser atau berkurang dari zona.
2. **Zona Parkir Kustom (`parking_zone`)**:
   - Berupa 1 kotak persegi dengan koordinat **ternormalisasi `0.0` sampai `1.0`** terhadap lebar & tinggi gambar kamera:
     `{ "x1": 0.15, "y1": 0.20, "x2": 0.75, "y2": 0.95 }`
   - Jika `parking_zone == null`, AI otomatis memantau **seluruh layar**.
   - Ukuran minimal kotak yang diterima backend adalah **5% lebar dan 5% tinggi** (`x2 - x1 >= 0.05` dan `y2 - y1 >= 0.05`).
3. **Endpoint Snapshot Kamera Statis**:
   - `GET /api/v1/mobile/cameras/{camera_id}/snapshot` mengembalikan `image/jpeg` 1 frame terbaru kamera untuk dijadikan latar belakang saat user menggambar Zona Parkir.
4. **Alert Baru Bertipe `"vehicle"` + Snapshot Ber-Bounding-Box**:
   - Snapshot bukti (`/api/v1/mobile/alerts/{alert_id}/snapshot`) sudah otomatis digambari kotak warna oleh server (Cyan = Zona Parkir, Hijau = Posisi Awal, Merah = Motor Bergerak/Masuk, Kuning = Orang).
   - Video bukti 10 detik tetap tersedia di `/api/v1/mobile/alerts/{alert_id}/clip`.
5. **Auto-Kalibrasi PTZ**:
   - Tidak ada perubahan payload pada endpoint `POST /api/v1/mobile/cameras/{camera_id}/ptz`. Backend otomatis mencatat `last_ptz_at` sehingga AI me-reset baseline parkir selama 12 detik pasca PTZ digerakkan.

---

## 2. Kontrak API Backend (Ready to Use)

Semua endpoint di bawah menggunakan header autentikasi mobile standar:
```http
Authorization: Bearer <access_token>
```

### A. Ambil Pengaturan Alert & Zona Parkir
`GET /api/v1/mobile/alerts/settings`

**Response `200 OK`:**
```json
{
  "data": {
    "cameras": [
      {
        "camera_id": "550e8400-e29b-41d4-a716-446655440000",
        "camera_name": "Kamera Garasi",
        "motion_detection_enabled": true,
        "person_detection_enabled": true,
        "vehicle_detection_enabled": true,
        "vehicle_guard_mode": true,
        "parking_zone": {
          "x1": 0.18,
          "y1": 0.22,
          "x2": 0.65,
          "y2": 0.92
        },
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
  },
  "meta": { "request_id": "..." }
}
```
> **Catatan**: `parking_zone` bernilai `null` jika user belum mengatur zona kustom (artinya seluruh layar dipantau).

---

### B. Simpan Pengaturan Alert & Zona Parkir
`PUT /api/v1/mobile/alerts/settings`

**Request Body:**
```json
{
  "cameras": [
    {
      "camera_id": "550e8400-e29b-41d4-a716-446655440000",
      "motion_detection_enabled": true,
      "person_detection_enabled": true,
      "vehicle_detection_enabled": true,
      "vehicle_guard_mode": true,
      "parking_zone": {
        "x1": 0.18,
        "y1": 0.22,
        "x2": 0.65,
        "y2": 0.92
      },
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
> **Catatan Penting**:
> - Untuk **Reset Zona ke Seluruh Layar**, kirim `"parking_zone": null`.
> - Field `vehicle_detection_enabled`, `vehicle_guard_mode`, dan `parking_zone` bersifat *backward-compatible* (jika tidak dikirim, nilai lama di DB dipertahankan).

---

### C. Ambil Snapshot Kamera untuk Background Editor Zona Parkir
`GET /api/v1/mobile/cameras/{camera_id}/snapshot`

- **Response `200 OK`**: Binary `Content-Type: image/jpeg`, `Cache-Control: no-store`.
- **Penggunaan di Android**: Muat dengan `Coil` (`AsyncImage`) menggunakan `OkHttpClient` yang sudah memasang `AuthInterceptor` (sama seperti loading snapshot alert), atau tambahkan query `?t=<timestamp>` saat user menekan tombol **"Segarkan Foto"**.

---

### D. Daftar & Detail Alert (`type: "vehicle"`)
`GET /api/v1/mobile/alerts` (mendukung filter `?type=vehicle`) dan `GET /api/v1/mobile/alerts/{alert_id}`

**Contoh item alert kendaraan:**
```json
{
  "id": "b12c34...",
  "camera_id": "550e8400-...",
  "camera_name": "Kamera Garasi",
  "type": "vehicle",
  "severity": "critical",
  "title": "Motor Parkir Bergerak!",
  "message": "Motor yang diparkir di area kamera Kamera Garasi terdeteksi bergeser atau dipindahkan.",
  "thumbnail_url": "/api/v1/mobile/alerts/b12c34.../snapshot",
  "clip_url": "/api/v1/mobile/alerts/b12c34.../clip",
  "clip_duration_seconds": 10,
  "evidence_status": "ready",
  "metadata": {
    "sub_type": "moved",
    "vehicle_class": "motorcycle",
    "confidence": 0.884,
    "count_before": 3,
    "count_after": 3,
    "person_nearby": true,
    "guard_mode": true
  },
  "occurred_at": "2026-11-25T03:15:10.000Z",
  "is_read": false
}
```

**Nilai `metadata.sub_type` yang mungkin muncul:**
| `sub_type` | `severity` | Arti |
| :--- | :--- | :--- |
| `"moved"` | `critical` | Kendaraan parkir bergeser signifikan dari posisi baseline |
| `"missing"` | `critical` | Jumlah kendaraan di zona parkir berkurang / hilang |
| `"entered"` | `warning` | Kendaraan baru masuk ke area/zona parkir |
| `"detected"` | `warning` | Aktivitas kendaraan terdeteksi (saat mode non-guard) |

---

### E. Payload FCM Push Notification
Sama dengan format existing (`NayakaFirebaseMessagingService.kt`):
```json
{
  "notification": {
    "title": "Motor Parkir Bergerak! • Kamera Garasi",
    "body": "Motor yang diparkir di area kamera Kamera Garasi terdeteksi bergeser atau dipindahkan."
  },
  "data": {
    "alert_id": "b12c34...",
    "camera_id": "550e8400-...",
    "camera_name": "Kamera Garasi",
    "type": "vehicle",
    "severity": "critical",
    "occurred_at": "2026-11-25T03:15:10.000Z"
  }
}
```

---

## 3. Implementasi di Codebase Android (`nayaka-mobile-master`)

**Status integrasi mobile:** model pengaturan kendaraan, switch Deteksi Kendaraan
dan Mode Jaga Parkir, editor zona parkir berbasis snapshot, penyimpanan pengaturan,
serta filter alert kendaraan sudah tersedia di aplikasi. Filter kendaraan saat ini
dijalankan pada daftar alert yang sudah dimuat oleh aplikasi.

Android hanya mengirim pengaturan dan menampilkan bukti/alert; inferensi AI tetap
dijalankan oleh backend/worker. Deteksi baru berfungsi jika migrasi database,
worker kendaraan, endpoint snapshot/settings, dan pengiriman alert sudah aktif
di VPS. `parking_zone: null` dikirim eksplisit saat zona di-reset agar backend
mengembalikan pemantauan ke seluruh layar. DTO request menggunakan wrapper
adapter JSON untuk memastikan nilai null tersebut tidak dihilangkan oleh
konfigurasi default Gson. Langkah-langkah berikut menjelaskan kontrak dan
checklist pengujian implementasi tersebut.

### Langkah 1: Tambah Model Kotlin (`data/model/Models.kt` & `Requests.kt`)

Tambahkan data class `ParkingZone`:
```kotlin
data class ParkingZone(
    @SerializedName("x1") val x1: Float,
    @SerializedName("y1") val y1: Float,
    @SerializedName("x2") val x2: Float,
    @SerializedName("y2") val y2: Float
)
```

Perbarui model response & request settings kamera:
```kotlin
data class AlertCameraSettings(
    @SerializedName("camera_id") val cameraId: String,
    @SerializedName("camera_name") val cameraName: String,
    @SerializedName("motion_detection_enabled") val motionDetectionEnabled: Boolean = true,
    @SerializedName("person_detection_enabled") val personDetectionEnabled: Boolean = true,
    @SerializedName("vehicle_detection_enabled") val vehicleDetectionEnabled: Boolean = true,
    @SerializedName("vehicle_guard_mode") val vehicleGuardMode: Boolean = false,
    @SerializedName("parking_zone") val parkingZone: ParkingZone? = null,
    @SerializedName("notifications_enabled") val notificationsEnabled: Boolean = true
)

data class AlertCameraSettingsRequest(
    @SerializedName("camera_id") val cameraId: String,
    @SerializedName("motion_detection_enabled") val motionDetectionEnabled: Boolean,
    @SerializedName("person_detection_enabled") val personDetectionEnabled: Boolean,
    @SerializedName("vehicle_detection_enabled") val vehicleDetectionEnabled: Boolean,
    @SerializedName("vehicle_guard_mode") val vehicleGuardMode: Boolean,
    @SerializedName("parking_zone") val parkingZone: ParkingZone?,
    @SerializedName("notifications_enabled") val notificationsEnabled: Boolean
)
```
> **Tips**: Pastikan konfigurasi Gson pada `NetworkClient.kt` atau serialisasi request tetap mengirim `"parking_zone": null` saat user me-reset zona, atau tambahkan `serializeNulls()` khusus saat menyimpan settings agar reset zona ke seluruh layar tersimpan di server.

---

### Langkah 2: Tambah Helper URL Snapshot Kamera (`NayakaRepository.kt`)

```kotlin
fun getCameraSnapshotUrl(cameraId: String, nonce: Long = System.currentTimeMillis()): String {
    return "${NetworkClient.BASE_URL}api/v1/mobile/cameras/$cameraId/snapshot?t=$nonce"
}
```

---

### Langkah 3: Perbarui `AlertSettingsScreen.kt` (Dual-Toggle + Tombol Zona Parkir)

Pada `AlertSettingsScreen.kt`:
1. Tambahkan state map baru:
   ```kotlin
   val vehicleEnabled = remember { mutableStateMapOf<String, Boolean>() }
   val vehicleGuardMode = remember { mutableStateMapOf<String, Boolean>() }
   val parkingZones = remember { mutableStateMapOf<String, ParkingZone?>() }
   var editingZoneCamera by remember { mutableStateOf<Camera?>(null) }
   ```
2. Isi nilai awal dari `repository.getAlertSettings()` saat `LaunchedEffect` berjalan.
3. Pada `CameraAlertCard`, tambahkan 2 switch + 1 baris tombol Zona Parkir:
   - **Switch 1**: `"Deteksi kendaraan (AI)"` — subtitle: *"Mendeteksi motor/mobil masuk atau bergerak."*
   - **Switch 2**: `"Mode Jaga Parkir (Anti-Maling)"` — `enabled = vehicleEnabled`, subtitle: *"Kirim peringatan KRITIS saat kendaraan parkir bergeser atau hilang."*
   - **Baris Zona Parkir**:
     - Tampilkan status: `if (zone != null) "Zona Kustom Aktif" else "Seluruh Layar (Otomatis)"`
     - Tombol `OutlinedButton("Atur Zona")` -> membuka `ParkingZoneEditorDialog`.
4. Perbarui syarat `enabled` pada switch `"Notifikasi alert"` menjadi:
   `enabled = motionEnabled || personEnabled || vehicleEnabled` (agar user tetap bisa menerima notif kendaraan meski motion biasa dimatikan!).

---

### Langkah 4: Buat `ParkingZoneEditorDialog` (Jetpack Compose `Canvas` + Drag)

**Aturan Penting Akurasi Koordinat:**
- **WAJIB** menggunakan `ContentScale.FillBounds` pada `AsyncImage` di dalam container `aspectRatio(16f / 9f)` yang ukurannya sama persis dengan `Canvas` overlay! Dengan begitu, posisi sentuhan `(offset.x / width, offset.y / height)` memetakan **1:1 secara presisi** ke piksel frame yang dianalisis oleh AI di VPS.

Contoh kerangka komponen Compose yang bisa langsung dipakai:
```kotlin
@Composable
fun ParkingZoneEditorDialog(
    cameraName: String,
    snapshotUrl: String,
    initialZone: ParkingZone?,
    onDismiss: () -> Unit,
    onSave: (ParkingZone?) -> Unit
) {
    var zone by remember(initialZone) { mutableStateOf(initialZone) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zona Parkir • $cameraName", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Geser jari pada gambar untuk menggambar kotak area parkir yang ingin dijaga AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val nx = (pos.x / size.width).coerceIn(0f, 1f)
                                    val ny = (pos.y / size.height).coerceIn(0f, 1f)
                                    dragStart = Offset(nx, ny)
                                    zone = ParkingZone(nx, ny, nx, ny)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val start = dragStart ?: return@detectDragGestures
                                    val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                                    val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                                    zone = ParkingZone(
                                        x1 = minOf(start.x, nx),
                                        y1 = minOf(start.y, ny),
                                        x2 = maxOf(start.x, nx),
                                        y2 = maxOf(start.y, ny)
                                    )
                                },
                                onDragEnd = {
                                    dragStart = null
                                    val cur = zone
                                    if (cur != null && (cur.x2 - cur.x1 < 0.05f || cur.y2 - cur.y1 < 0.05f)) {
                                        zone = initialZone
                                    }
                                }
                            )
                        }
                ) {
                    AsyncImage(
                        model = snapshotUrl,
                        contentDescription = "Snapshot Kamera",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val z = zone ?: return@Canvas
                        val left = z.x1 * size.width
                        val top = z.y1 * size.height
                        val w = (z.x2 - z.x1) * size.width
                        val h = (z.y2 - z.y1) * size.height
                        drawRect(
                            color = Color(0x3322D3EE),
                            topLeft = Offset(left, top),
                            size = Size(w, h)
                        )
                        drawRect(
                            color = Color(0xFF22D3EE),
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
                TextButton(onClick = { zone = null }) {
                    Text("Reset ke Seluruh Layar")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(zone) }) { Text("Terapkan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
```

---

### Langkah 5: Perbarui `AlertsScreen.kt` & `AlertItemCard` (`CommonComponents.kt`)

1. Di `AlertsScreen.kt`:
   - Tambahkan filter chip `"Kendaraan"` ke `val filters = listOf("Semua", "Kendaraan", "Kritis", "Belum Dibaca")`.
   - Pada `filteredAlerts`, tambahkan kondisi:
     `"Kendaraan" -> item.type.equals("vehicle", ignoreCase = true)`.
2. Di `CommonComponents.kt` (`AlertItemCard`):
   - Tambahkan pemetaan ikon & warna untuk `type == "vehicle"`:
     - Ikon: `Icons.Default.TwoWheeler` atau `Icons.Default.DirectionsCar`
     - Warna badge: `StatusDanger` jika `severity == "critical"` (mis. *"Motor Parkir Bergerak!"*), atau `PrimaryBlue` jika `warning`.

---

## 4. Checklist Pengujian End-to-End (1 Kamera di VPS)

1. Pastikan migrasi database `0019_vehicle_detection.sql` sudah dijalankan di VPS (`pnpm db:migrate`) dan container `onvif-event-worker` sudah di-rebuild (`docker compose up -d --build onvif-event-worker app`).
2. Buka **Pengaturan Alert** di HP -> aktifkan **Deteksi Kendaraan (AI)** dan **Mode Jaga Parkir (Anti-Maling)**.
3. Klik **Atur Zona Parkir** -> pastikan snapshot garasi muncul -> gambar kotak menutupi area 3 motor parkir -> klik **Terapkan** lalu **Simpan pengaturan**.
4. Tunggu ~10 detik agar worker mengkalibrasi baseline awal motor parkir.
5. **Uji Anti-False-Alarm**: Nyalakan/matikan lampu garasi atau lewat di depan kamera tanpa menyentuh motor -> pastikan **tidak ada** alert `vehicle` kritis yang masuk.
6. **Uji Anti-Curanmor**: Geser/dorong 1 motor keluar posisi sejauh ~1 meter -> tunggu 3–5 detik -> verifikasi push notification **"Motor Parkir Bergerak!"** masuk, foto snapshot menampilkan kotak hijau (posisi awal) & kotak merah (motor bergerak), dan video bukti 10 detik dapat diputar di `AlertDetailScreen`.
