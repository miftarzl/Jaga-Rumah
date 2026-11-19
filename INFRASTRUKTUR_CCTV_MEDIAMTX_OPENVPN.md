# 🛡️ Panduan Infrastruktur CCTV: Tapo C500 + OpenVPN + MediaMTX + Mobile App

Dokumen ini menjelaskan arsitektur dan langkah konfigurasi infrastruktur streaming CCTV berlatensi rendah (*low-latency*) menggunakan **TP-Link Tapo C500**, **OpenVPN**, dan **MediaMTX Gateway** untuk aplikasi Nayaka CCTV.

---

## 📐 Topologi & Alur Arsitektur

```
[ Tapo C500 di Lokasi ]
   │
   │ (Koneksi WiFi / Kabel LAN Rumah)
   ▼
[ Router Lokasi (OpenVPN Client) ]
   │
   │ 🔒 (Jalur Terowongan OpenVPN / tun0)
   ▼
[ VPS Nayaka ]
   │
   ├── 1. OpenVPN Server (Interface tun0: 10.8.0.1)
   │      └─ Melihat Tapo C500 pada IP VPN (contoh: 10.8.0.50:554)
   │
   ├── 2. MediaMTX Gateway (Docker: bluenviron/mediamtx)
   │      ├─ Menarik RTSP Tapo C500 via jalur OpenVPN secara hemat bandwidth (sourceOnDemand)
   │      ├─ Mengonversi menjadi WebRTC WHEP (:8889) ➔ Delay ~0.3 detik
   │      └─ Mengonversi menjadi RTSP Publik (:8554) & HLS (:8888)
   │
   └── 3. Backend Next.js (Port 701:3000)
          ├─ Menyediakan endpoint klaim perangkat: POST /api/v1/mobile/devices/claim
          └─ Menyediakan URL stream bertoken: GET /api/v1/mobile/cameras/{id}/stream
                  │
                  │ 🌐 Internet Publik (4G / 5G / WiFi Umum)
                  ▼
[ Aplikasi Android (Kotlin) di HP Customer ]
   └─ Memutar video WebRTC / RTSP tanpa perlu instalasi aplikasi VPN di HP pengguna!
```

---

## 1. Konfigurasi Tapo C500 (Akun Kamera)

1. Buka aplikasi resmi **TP-Link Tapo** di smartphone yang terhubung ke kamera.
2. Pilih kamera **Tapo C500** ➔ Masuk ke **Pengaturan (ikon gerigi)** di pojok kanan atas.
3. Gulir ke bawah, pilih **Pengaturan Lanjutan (Advanced Settings)** ➔ **Akun Kamera (Camera Account)**.
4. Buat Akun Kamera Lokal:
   * **Username**: `admin` (atau nama pilihan Anda)
   * **Password**: `RahasiaTapo123!` (buat kata sandi yang kuat)
5. Format URL RTSP Tapo C500:
   * **Kualitas Utama (1080p FHD)**:
     ```text
     rtsp://admin:RahasiaTapo123!@10.8.0.50:554/stream1
     ```
   * **Kualitas Ringan (360p Substream)**:
     ```text
     rtsp://admin:RahasiaTapo123!@10.8.0.50:554/stream2
     ```

---

## 2. Pengaturan di VPS (OpenVPN & MediaMTX)

### A. Verifikasi OpenVPN di VPS
Setelah OpenVPN terpasang di VPS dan router lokasi Tapo terhubung, pastikan VPS bisa menjangkau IP Tapo C500:
```bash
# Uji ping dari terminal VPS ke IP kamera di VPN
ping 10.8.0.50
```

### B. Konfigurasi MediaMTX (`mediamtx.yml`)
File `mediamtx.yml` telah disiapkan di root proyek web. Di bagian `paths`, daftarkan kamera Tapo C500:

```yaml
paths:
  # Ganti cam-c500-01 dengan identifier kamera Anda
  cam-c500-01:
    source: rtsp://admin:RahasiaTapo123!@10.8.0.50:554/stream1
    # sourceOnDemand: yes memastikan RTSP ke Tapo HANYA ditarik saat user HP sedang menonton!
    sourceOnDemand: yes
    sourceOnDemandCloseAfter: 10s
```

### C. Menjalankan MediaMTX via Docker Compose
File `docker-compose.yml` telah diperbarui dengan layanan `mediamtx` yang menggunakan mode `network_mode: "host"`, sehingga container langsung bisa membaca interface `tun0` OpenVPN tanpa konfigurasi routing rumit:

```bash
cd nayaka-web-main
docker compose up -d
```

### D. Port Firewall VPS yang Perlu Dibuka:
* `701/tcp` : Web Admin & Backend API
* `8554/tcp`: RTSP MediaMTX
* `8889/tcp`: WebRTC API MediaMTX (WHEP)
* `8888/tcp`: HLS MediaMTX
* `8189/udp`: WebRTC ICE UDP data transfer
* `1194/udp`: OpenVPN (sesuai port OpenVPN Anda)

---

## 3. Alur Registrasi & Kode Klaim (*Claim Code*)

### Langkah 1: Admin Daftarkan Perangkat di Web
1. Admin membuka Web Admin ➔ Menu **Perangkat (Devices)** ➔ **Tambah Perangkat**.
2. Masukkan data:
   * **Serial Number**: Misal `TAPO-C500-ABC123`
   * **Model**: `TP-Link Tapo C500`
   * **Stream URL**: `rtsp://admin:RahasiaTapo123!@10.8.0.50:554/stream1`
3. Backend secara otomatis:
   * Menyimpan perangkat dengan status `in_stock`.
   * Menghasilkan **Kode Klaim Acak** (misal: `NYK-7842-9901`).
4. Admin menyerahkan kode klaim ini ke customer (melalui kartu garansi/dus/chat).

---

### Langkah 2: Customer Klaim Kamera di Aplikasi Mobile
Aplikasi mobile memanggil endpoint:

```http
POST /api/v1/mobile/devices/claim
Authorization: Bearer <access_token_customer>
Content-Type: application/json

{
  "code": "NYK-7842-9901"
}
```

**Respon Sukses (`200 OK`)**:
```json
{
  "data": {
    "deviceId": "550e8400-e29b-41d4-a716-446655440000",
    "deviceName": "Tapo C500 Outdoor",
    "serialNumber": "TAPO-C500-ABC123",
    "model": "TP-Link Tapo C500"
  },
  "meta": { "request_id": "req-uuid-1234" }
}
```
*Kamera langsung terikat ke akun customer dan muncul di daftar kamera dashboard mobile.*

---

### Langkah 3: Customer Menonton Live Stream
Saat customer menekan tombol kamera di aplikasi mobile, aplikasi meminta URL stream:

```http
GET /api/v1/mobile/cameras/{camera_id}/stream
Authorization: Bearer <access_token_customer>
```

**Respon (`200 OK`)**:
```json
{
  "data": {
    "camera_id": "550e8400-e29b-41d4-a716-446655440000",
    "camera_name": "Tapo C500 Outdoor",
    "stream_key": "cam-tapo-c500-abc123",
    "webrtc_url": "http://stream.nayakacloud.com:8889/cam-tapo-c500-abc123/whep",
    "rtsp_url": "rtsp://stream.nayakacloud.com:8554/cam-tapo-c500-abc123",
    "hls_url": "http://stream.nayakacloud.com:8888/cam-tapo-c500-abc123/index.m3u8"
  },
  "meta": { "request_id": "req-uuid-5678" }
}
```

Aplikasi mobile dapat memutar video secara langsung menggunakan:
* **WebRTC**: Latensi super rendah (< 0.5 detik).
* **RTSP (ExoPlayer)**: Native Android player menggunakan modul `media3-exoplayer-rtsp`.
