# Panduan Troubleshooting Live Stream CCTV Android

Dokumen ini berisi langkah pemeriksaan ketika aplikasi Android menampilkan
**Koneksi Terputus** atau gagal memuat video CCTV.

## Kondisi yang ditemukan

Pada pengujian terakhir, aplikasi mencoba membuka HLS di
`dev.nayakacloud.com:8888`, tetapi koneksi TCP gagal. Dari mesin pengembangan,
port API `701` dapat dijangkau, sedangkan port stream `8888`, `8554`, dan `8889`
tidak dapat dijangkau.

Build Android saat ini mengambil URL dari endpoint stream kamera dan hanya
memutar `hls_url`. `cam.streamUrl`, RTSP, serta WebRTC tidak digunakan pada layar
live Android. Karena itu, kegagalan koneksi ke `:8888` perlu diperiksa pada
MediaMTX, firewall, Docker, atau reverse proxy.

## 1. Periksa respons endpoint backend

Saat layar kamera dibuka, aplikasi memanggil:

```http
GET /api/v1/mobile/cameras/{camera_id}/stream
Authorization: Bearer <access_token>
```

Pastikan respons berisi `data.hls_url` sebagai URL absolut yang dapat diakses
HP melalui internet, misalnya:

```json
{
  "data": {
    "hls_url": "https://dev.nayakacloud.com/hls/<stream_key>/index.m3u8",
    "rtsp_url": "rtsp://dev.nayakacloud.com:8554/<stream_key>",
    "webrtc_url": "https://dev.nayakacloud.com/<whep-path>"
  }
}
```

Contoh tersebut hanya format; sesuaikan host, path, dan konfigurasi token dengan
server yang benar. Jangan mengirim kredensial kamera atau token akses ke log
publik.

Periksa bahwa:

- `hls_url` memakai host publik yang benar, bukan hostname/IP yang keliru.
- URL menunjuk ke playlist HLS (`.m3u8`) yang sesuai dengan path MediaMTX.
- URL tidak mengarah ke `localhost`, alamat VPN/internal, atau port yang hanya
  terbuka dari VPS.
- Jika akses langsung ke port `8888` tidak diinginkan, arahkan `hls_url` ke
  reverse proxy HTTPS.

## 2. Pastikan MediaMTX melayani HLS

Jalankan pemeriksaan berikut pada VPS:

```bash
docker compose ps
docker compose logs --tail=100 mediamtx
sudo ss -lntp | grep ':8888'
```

Pastikan layanan MediaMTX aktif dan mendengarkan pada alamat yang dapat
dijangkau oleh reverse proxy atau jaringan publik. Periksa `mediamtx.yml` dan
pastikan HLS aktif, misalnya:

```yaml
hls: yes
hlsAddress: :8888
```

Jika path kamera memakai `sourceOnDemand`, permintaan playlist HLS akan memulai
koneksi ke kamera. Pastikan alamat sumber RTSP kamera hanya digunakan dari
server melalui VPN dan dapat dijangkau dari VPS.

Uji playlist langsung dari VPS:

```bash
curl -v "http://127.0.0.1:8888/<stream_key>/index.m3u8"
```

Jika permintaan lokal gagal, perbaiki layanan/path MediaMTX sebelum menguji dari
HP. Jika berhasil secara lokal tetapi gagal dari luar VPS, lanjutkan ke langkah
firewall atau reverse proxy.

## 3. Periksa Docker dan firewall

Pilih konfigurasi jaringan Docker yang memang digunakan:

- Dengan `network_mode: host`, MediaMTX memakai network namespace host. Pastikan
  MediaMTX mendengarkan pada port `8888` dan firewall VPS/cloud mengizinkan akses.
- Dengan bridge network, publikasikan port HLS, misalnya `8888:8888`, lalu
  pastikan konfigurasi firewall mengizinkan port tersebut.

Periksa firewall OS dan firewall/security group penyedia VPS. Jika akses HLS
langsung memang diizinkan:

```bash
sudo ufw status
sudo ufw allow 8888/tcp
```

Aturan di atas hanya mengubah firewall UFW; jika VPS memakai firewall lain atau
firewall cloud, atur aturan di tempat tersebut juga. Jangan membuka RTSP,
WebRTC, atau port lain tanpa kebutuhan. Hindari mengekspos MediaMTX tanpa
perlindungan ke internet publik.

## 4. Pilihan yang disarankan: reverse proxy HTTPS

Jika port `443` sudah terbuka, MediaMTX dapat tetap berada di localhost/private
network dan HLS diteruskan melalui reverse proxy. Contoh lokasi Nginx:

```nginx
location /hls/ {
    proxy_pass http://127.0.0.1:8888/;
    proxy_http_version 1.1;
    proxy_buffering off;
    proxy_read_timeout 3600s;
}
```

Dengan konfigurasi tersebut, backend perlu menghasilkan URL seperti:

```text
https://dev.nayakacloud.com/hls/<stream_key>/index.m3u8
```

Pastikan juga permintaan segmen video yang dirujuk playlist ikut melewati
reverse proxy. Jika playlist berhasil tetapi segmen gagal, video tetap tidak
akan tampil.

Sesuaikan konfigurasi dengan struktur Nginx yang sudah ada, aktifkan konfigurasi,
validasi, lalu reload Nginx:

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## 5. Uji dari jaringan luar VPS

Jangan hanya menguji dari VPS sendiri. Uji dari PC atau HP pada jaringan publik
yang sama seperti pengguna:

```powershell
Test-NetConnection dev.nayakacloud.com -Port 8888
```

Untuk konfigurasi reverse proxy HTTPS, uji port `443` dan playlist:

```powershell
Test-NetConnection dev.nayakacloud.com -Port 443
curl.exe -I "https://dev.nayakacloud.com/hls/<stream_key>/index.m3u8"
```

Hasil yang diharapkan adalah koneksi TCP berhasil dan playlist mengembalikan
respons HTTP sukses. Pastikan path playlist serta segmen video bisa diakses,
bukan hanya halaman API.

## 6. Arti umum pesan error

| Pesan / hasil | Yang perlu diperiksa |
| --- | --- |
| `Unable to resolve host` | Host dalam `hls_url` salah atau DNS belum tersedia. |
| `Connection refused` / gagal terhubung ke `:8888` | Tidak ada listener pada port tersebut, atau firewall menolak koneksi. |
| HTTP `404` | Path/stream key playlist tidak cocok dengan path MediaMTX. |
| HTTP `401` atau `403` | Aturan autentikasi/token pada backend atau reverse proxy. |
| Playlist berhasil tetapi video tetap kosong | Periksa URL dan akses segmen HLS serta log sumber kamera MediaMTX. |

### Diagnosis untuk `Response code: 404`

Error terbaru di aplikasi adalah `Response code: 404`. Ini berbeda dari error
koneksi sebelumnya: server HLS sekarang dapat dijangkau dan mengembalikan respons
HTTP, tetapi resource pada path yang diminta tidak ditemukan. Aplikasi memakai
`hls_url` dari API secara langsung; aplikasi tidak membentuk stream key atau
mengganti path URL.

Minta tim backend/infra memeriksa:

1. Nilai `hls_url` pada respons `GET /api/v1/mobile/cameras/{camera_id}/stream`.
   Ambil dan periksa URL secara internal; jangan membagikan query/token ke chat
   atau log publik.
2. Stream key pada path playlist, misalnya
   `/<stream_key>/index.m3u8`, harus sama persis dengan nama path yang
   didefinisikan di konfigurasi `paths` MediaMTX.
3. Uji URL playlist yang sama dari VPS dan dari jaringan luar VPS:

   ```bash
   curl -i "http://127.0.0.1:8888/<stream_key>/index.m3u8"
   ```

   Untuk URL publik atau reverse proxy, jalankan `curl -i` dengan URL publik
   yang sama. Jangan menaruh token rahasia di riwayat shell bersama.
4. Periksa log MediaMTX saat permintaan dilakukan. Pastikan path ditemukan,
   sumber kamera berhasil dibuka, dan reverse proxy tidak menambah atau
   menghapus segmen path yang salah.
5. Jika playlist memberikan `200 OK`, pastikan URL segmen `.ts` atau `.m4s`
   yang dirujuk playlist juga berhasil diakses.

Pesan singkat yang dapat diteruskan ke tim backend:

> Aplikasi Android sudah mengambil `hls_url` dari
> `GET /api/v1/mobile/cameras/{camera_id}/stream` dan mengirim permintaan HLS.
> Server membalas HTTP 404. Mohon cocokkan path/stream key di `hls_url` dengan
> konfigurasi `paths` MediaMTX, uji playlist dari VPS dan jaringan publik, lalu
> periksa log MediaMTX/reverse proxy. Jangan kirim token URL di log atau chat.

## 7. Setelah perbaikan server

1. Pastikan endpoint stream mengembalikan `hls_url` yang baru dan valid.
2. Uji URL playlist serta segmen dari jaringan luar VPS.
3. Buka ulang halaman kamera di aplikasi agar aplikasi mengambil URL terbaru.
4. Jika masih gagal, simpan pesan error terbaru dan periksa log MediaMTX pada
   waktu yang sama.

Saat ini Android hanya memakai `hls_url`; membuka port RTSP `8554` atau WebRTC
`8889` saja tidak akan memperbaiki pemutaran HLS di layar live.

## 8. Gambar berhenti tetapi status tetap “LIVE HD”

Status “LIVE HD” sebelumnya hanya menunjukkan bahwa ExoPlayer berada pada status
`READY`; status itu sendiri tidak memastikan bahwa frame video masih diterima.
Android sekarang memantau progres setiap frame yang akan ditampilkan. ExoPlayer
diberi kesempatan menangani buffering normal; aplikasi baru mencoba memulai
ulang jika tidak ada frame baru selama 15 detik saat pemutar aktif. Retry
dibatasi hingga tiga kali dengan jeda bertambah (2, 4, lalu 8 detik), dan hitungan
baru direset setelah 30 detik pemutaran stabil. Jika frame tetap tidak masuk,
aplikasi menampilkan kegagalan koneksi.

Jika masalah terus berulang, periksa kestabilan sumber kamera dan MediaMTX serta
akses playlist dan segmen HLS dari jaringan HP. Pemulihan di aplikasi tidak
dapat memperbaiki sumber stream yang terus mengirim frame beku atau terhenti.

Untuk mengurangi buffering berulang, pemutar memakai buffer minimum 10 detik
dan maksimum 30 detik, mulai setelah 3 detik buffer, serta menargetkan jeda live
10 detik (rentang 8-20 detik). Dampaknya, tayangan dapat tertinggal beberapa
detik dari kejadian langsung, tetapi lebih tahan terhadap jitter jaringan.
