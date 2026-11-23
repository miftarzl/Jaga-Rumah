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

---

## 3. Status Implementasi di Android

- **Ukuran file**: `Recording` memetakan `file_size_bytes`; ukuran diteruskan ke daftar dan detail rekaman.
- **Filter Playback**: tersedia pilihan kamera dan rentang tanggal/jam lokal, dibatasi 24 jam terakhir. Timestamp dikirim sebagai UTC ISO-8601. Daftar bisa dimuat ulang tanpa memakai cache lama; rekaman lokal juga difilter oleh rentang yang sama.
- **Paginasi**: daftar mengambil halaman berukuran 20 menggunakan `cursor` sampai semua segmen dalam periode diterima.
- **Tarik klip**: dialog konfirmasi memakai kamera dan rentang yang dipilih, membatasi durasi hingga 30 menit, lalu meminta `video/mp4` secara terautentikasi, menyimpan stream ke `Movies/NayakaCCTV`, menambahkan rekaman lokal ke Playback, dan langsung membuka klip di pemutar.
- **Unduh / bagikan rekaman server**: file diunduh dari endpoint stream dan disimpan ke Galeri sebelum dibagikan.
- **Retensi**: UI menjelaskan penyimpanan maksimal 24 jam dan menampilkan pesan khusus bila rekaman sudah tidak tersedia (HTTP 404).
- Shortcut langsung dari `LiveViewScreen` ke Playback terfilter per kamera belum ditambahkan.

---

## 4. Catatan Teknis Penting
1. **Timezone**: Semua timestamp API (`started_at`, `ended_at`, `from`, `to`) berformat **UTC (`...Z`)**. Konversikan ke waktu lokal HP saat ditampilkan, dan konversikan kembali ke UTC saat mengirim query `from`/`to`.
2. **Auth Header**: Endpoint `/api/v1/mobile/cameras/{camera_id}/clip` wajib membawa `Authorization: Bearer <access_token>`, sedangkan `/api/v1/recordings/{recording_id}/stream` bisa langsung diputar ExoPlayer tanpa header tambahan.
