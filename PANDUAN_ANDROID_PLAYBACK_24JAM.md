# Panduan Integrasi Playback & Tarik Rekaman Kejadian (Always-On 24 Jam) — Android Developer

Dokumen ini disesuaikan dengan kondisi terbaru repo **`nayaka-mobile-master`** (commit `7dabb442`) dan pembaruan backend **`nayaka-web-main`** yang sekarang sudah **Always Streaming & Continuous Recording 24/7** dengan **retensi maksimal 24 jam**.

---

## 1. Status Kesiapan Android Saat Ini (Hasil Telaah Repo)

Setelah menelaah commit terbaru (`64b53d06`, `90919d63`, `7dabb442`), beberapa bagian utama **sudah selesai** Anda kerjakan dengan baik:
- **`PlaybackDetailScreen.kt`**: Sudah menggunakan **Media3 ExoPlayer** untuk memutar `recording.playbackUrl` (`http://`, `https://`, `content://`, maupun file lokal) lengkap dengan kontrol Play/Pause, Seekbar, dan Speed.
- **`ApiService.kt` & `NayakaRepository.kt`**: Endpoint `GET mobile/recordings` (termasuk parameter `camera_id`, `from`, `to`) dan `GET mobile/recordings/{id}` sudah terpasang.

Sehingga Anda **tidak perlu** membangun ulang player di `PlaybackDetailScreen.kt` dari nol — cukup lengkapi bagian yang belum tersambung ke fitur **tarik rekaman 24 jam dari VPS** di bawah ini.

---

## 2. Daftar Endpoint Backend (Port `701`)

**Base URL:** `http://dev.nayakacloud.com:701/api/v1/`

### A. Daftar Segmen Rekaman (Segmen 5 Menit, Maksimal 24 Jam Terakhir)
```http
GET /api/v1/mobile/recordings?camera_id={uuid}&from={iso8601}&to={iso8601}&limit=20&cursor={cursor}
Authorization: Bearer <access_token>
```

**Contoh Response:**
```json
{
  "data": {
    "recordings": [
      {
        "id": "8e1c2b3a-...",
        "camera_id": "a1b2c3d4-...",
        "camera_name": "Kamera Contoh 2",
        "status": "available",
        "started_at": "2026-11-23T02:00:00.000Z",
        "ended_at": "2026-11-23T02:05:00.000Z",
        "duration_seconds": 300,
        "file_size_bytes": 42150400,
        "playback_url": "http://dev.nayakacloud.com:701/api/v1/recordings/8e1c2b3a-.../stream",
        "expires_at": "2026-11-24T02:00:00.000Z",
        "created_at": "2026-11-23T02:05:16.000Z"
      }
    ],
    "pagination": { "next_cursor": null, "has_more": false }
  }
}
```

### B. Streaming Video Playback Segmen (Langsung di `playback_url`)
```http
GET /api/v1/recordings/{recording_id}/stream
```
- Mendukung HTTP `Range: bytes=...` (`206 Partial Content`) sehingga ExoPlayer bisa *seek* maju-mundur secara mulus.
- Tambahkan `?download=1` jika ingin mengunduh file `.mp4` segmen tersebut.

### C. Tarik Klip Kejadian Berdasarkan Rentang Jam (Endpoint Baru)
Gunakan endpoint ini untuk menarik/memotong kejadian pada jam tertentu (bisa lintas beberapa segmen 5 menit, maksimal 30 menit per permintaan):
```http
GET /api/v1/mobile/cameras/{camera_id}/clip?from={iso8601}&to={iso8601}&download=1
Authorization: Bearer <access_token>
```
- `from` & `to` *(wajib)*: ISO-8601 UTC (contoh: `2026-11-23T01:12:00Z` s/d `2026-11-23T01:18:00Z`).
- Mengembalikan stream `video/mp4` hasil remux cepat dari server (`ffmpeg -c copy`).

### D. Kontrol Arah Kamera PTZ (Endpoint Baru — ONVIF via VPS)
Gunakan endpoint ini pada tombol arah PTZ di `LiveViewScreen.kt`:
```http
POST /api/v1/mobile/cameras/{camera_id}/ptz
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "direction": "left",
  "speed": 0.5,
  "duration_ms": 400
}
```
- `direction` *(wajib)*: `"up"`, `"down"`, `"left"`, `"right"`, `"zoom_in"`, `"zoom_out"`, atau `"stop"`.
- `speed` *(opsional, default `0.5`)*: `0.1` s/d `1.0`.
- `duration_ms` *(opsional, default `400`)*: durasi gerak dalam milidetik sebelum server otomatis mengirim `stop` (`0` = gerak terus sampai client mengirim `direction: "stop"`).

### E. Audio 2 Arah / Speaker Kamera — Push-to-Talk (Endpoint Baru)
Gunakan endpoint ini untuk mengirim suara dari mic HP ke speaker kamera Tapo C500:
```http
POST /api/v1/mobile/cameras/{camera_id}/talk
Authorization: Bearer <access_token>
Content-Type: multipart/form-data

audio=@rekaman_mic.m4a (atau .wav / .aac, maksimal 5 MB)
```
- Backend otomatis mengonversi audio ke format G.711 (8kHz mono) dan memutarnya di speaker kamera lewat worker `pytapo`.

---

## 3. Perubahan yang Masih Perlu Dilakukan di Android

### 1. Mapping `file_size_bytes` di `Models.kt` & `NayakaRepository.kt`
- Saat ini `Models.kt` hanya memetakan `@SerializedName("size_bytes") val sizeBytes: Long = 0`.
- Backend mengembalikan field **`file_size_bytes`**. Tambahkan `@SerializedName("file_size_bytes") val fileSizeBytes: Long? = null` di `Recording`, lalu di `NayakaRepository.kt` (`getRecordings` & `getRecordingDetail`) isi `sizeBytes = if (r.sizeBytes > 0) r.sizeBytes else (r.fileSizeBytes ?: 0L)` agar ukuran file tampil benar di `RecordingCard`.

### 2. Tambahkan Filter Kamera & Jam Kejadian di `PlaybackScreen.kt`
- Saat ini `PlaybackScreen.kt` masih memanggil `repository.getRecordings()` tanpa parameter dan tanpa filter UI.
- Tambahkan:
  - **Dropdown / Chip Pilih Kamera** (mengambil daftar kamera dari `repository.getCameras()`).
  - **Pemilih Tanggal & Rentang Jam (`Dari` – `Sampai`)** (dibatasi 24 jam terakhir) yang memanggil `repository.getRecordings(cameraId, fromIso, toIso, forceRefresh = true)`.
  - **Pull-to-refresh** / tombol Refresh dengan `forceRefresh = true` agar tidak tertahan `recordingsCache` (dari `TimedMemoryCache`).
  - Catatan di `NayakaRepository.getRecordings`: pastikan `localList` juga ikut difilter jika `from`/`to` diisi (atau hanya tampilkan `localList` bila tidak sedang memfilter rentang waktu server).

### 3. Fitur Tarik & Unduh Klip Kejadian ke Galeri
- Saat ini `StreamRecorder.kt` baru merekam live stream secara lokal di HP. Untuk menarik rekaman kejadian masa lalu dari VPS:
  - Tambahkan dialog **"Tarik Klip Kejadian"** (pilih kamera, jam mulai, jam selesai — maksimal 30 menit).
  - Panggil `GET /api/v1/mobile/cameras/{camera_id}/clip?from=...&to=...&download=1` dengan header `Authorization: Bearer <token>`, lalu simpan ke Galeri HP (`MediaStore.Video`, mirip alur simpan yang sudah ada di project).
- Periksa juga aksi **Share / Unduh** di `PlaybackDetailScreen.kt` agar untuk rekaman dari server (`playbackUrl` diawali `http://` atau `https://`), file diunduh dulu via HTTP (atau tambahkan `?download=1`) sebelum dibagikan lewat `FileProvider`.

### 4. PTZ & Audio 2 Arah di `LiveViewScreen.kt` — Sudah Diimplementasikan
- **PTZ**: Tombol arah mengirim gerak singkat 400 ms. Joystick di tengah memakai `duration_ms: 0`, menyesuaikan kecepatan dari jarak geser, dan mengirim `direction: "stop"` saat dilepas.
- **Batas arah joystick**: Kontrak endpoint saat ini hanya menerima `up`, `down`, `left`, dan `right`; joystick memilih arah terdekat dari empat arah tersebut. Sudut diagonal bebas memerlukan dukungan vektor pan/tilt di backend.
- **Stream saat PTZ**: Pemutar mencoba menyambungkan ulang secara otomatis jika stream kamera terputus sesaat selama kamera bergerak.
- **Audio Kamera → HP**: Tombol mute mengatur volume ExoPlayer.
- **Audio HP → Speaker Kamera**: Tombol push-to-talk meminta izin mikrofon, merekam `.m4a` AAC mono 16 kHz/32 kbps ketika ditekan, lalu mengunggah field `audio` ke endpoint `/api/v1/mobile/cameras/{camera_id}/talk` saat dilepas. Audio live kamera dimute selama proses rekam dan unggah.
- File harus berukuran maksimal 5 MB. Backend mengonversi audio ke G.711 (8 kHz mono) dan memutarnya lewat worker `pytapo`; pemutaran fisik tetap bergantung pada worker dan konfigurasi kamera di backend.
- Endpoint saat ini mengunggah satu file setelah tombol dilepas, jadi waktu tunggu juga mencakup upload, konversi, dan antrean pemutaran backend. Percakapan live tanpa jeda memerlukan transport audio streaming dua arah, bukan upload file.

### 5. Shortcut dari `LiveViewScreen.kt` & Penanganan Retensi 24 Jam
- *(Opsional)* Tambahkan tombol **"Riwayat Rekaman"** di `LiveViewScreen.kt` yang langsung membuka `PlaybackScreen` terfilter untuk kamera tersebut.
- Tampilkan keterangan di UI bahwa **"Rekaman otomatis tersimpan maksimal 24 jam"**, dan tampilkan pesan ramah jika user membuka detail rekaman yang sudah lewat 24 jam (`HTTP 404`).

---

## 4. Catatan Teknis Penting
1. **Timezone**: Semua timestamp API (`started_at`, `ended_at`, `from`, `to`) berformat **UTC (`...Z`)**. Konversikan ke waktu lokal HP saat ditampilkan, dan konversikan kembali ke UTC saat mengirim query `from`/`to`.
2. **Auth Header**: Endpoint `/api/v1/mobile/cameras/{camera_id}/clip` wajib membawa `Authorization: Bearer <access_token>`, sedangkan `/api/v1/recordings/{recording_id}/stream` bisa langsung diputar ExoPlayer tanpa header tambahan.
