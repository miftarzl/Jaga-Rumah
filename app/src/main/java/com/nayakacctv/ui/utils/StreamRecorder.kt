package com.nayakacctv.ui.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.nayakacctv.data.model.Recording
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

object StreamRecorder {

    private const val TAG = "StreamRecorder"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private var recordJob: Job? = null
    private var tempFile: File? = null
    private var fileOutputStream: FileOutputStream? = null

    private var recordingStartTime = 0L
    private var activeCameraId: String = ""
    private var activeCameraName: String = ""
    private var activeStreamUrl: String = ""

    @Volatile
    var isRecording: Boolean = false
        private set

    /**
     * Memulai perekaman siaran langsung CCTV dari URL stream (HLS atau HTTP).
     */
    fun startRecording(
        context: Context,
        streamUrl: String,
        cameraId: String,
        cameraName: String,
        scope: CoroutineScope
    ) {
        if (isRecording) {
            Log.w(TAG, "Recording already in progress.")
            return
        }

        try {
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            activeCameraId = cameraId
            activeCameraName = cameraName
            activeStreamUrl = streamUrl

            val cacheDir = context.cacheDir
            tempFile = File(cacheDir, "rec_temp_${System.currentTimeMillis()}.mp4")
            fileOutputStream = FileOutputStream(tempFile!!)

            recordJob = scope.launch(Dispatchers.IO) {
                try {
                    val trimmed = streamUrl.trim()
                    if (trimmed.contains(".m3u8", ignoreCase = true)) {
                        recordHlsStream(trimmed)
                    } else {
                        recordGenericStream(trimmed)
                    }
                } catch (e: CancellationException) {
                    Log.i(TAG, "Recording coroutine cancelled gracefully.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error while recording stream: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize recording", e)
            isRecording = false
        }
    }

    /**
     * Perekaman format HLS: mengunduh init fMP4 dan segmen-segmen video secara kontinu
     * dan menggabungkannya menjadi satu berkas MP4 utuh yang langsung playable.
     */
    private suspend fun recordHlsStream(streamUrl: String) = coroutineScope {
        var baseDir = streamUrl.substringBeforeLast('/') + "/"
        var mediaPlaylistUrl: String = streamUrl

        // Ambil master playlist jika URL adalah master index.m3u8
        val reqMaster = Request.Builder().url(streamUrl).build()
        try {
            httpClient.newCall(reqMaster).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val lines = body.lines().map { it.trim() }.filter { it.isNotBlank() }
                    val subline = lines.firstOrNull { !it.startsWith("#") && it.contains(".m3u8") }
                    if (subline != null) {
                        mediaPlaylistUrl = resolveUrl(baseDir, subline)
                        baseDir = mediaPlaylistUrl.substringBeforeLast('/') + "/"
                        Log.i(TAG, "Resolved HLS media playlist: $mediaPlaylistUrl")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve master playlist, fallback to direct url: ${e.message}")
        }

        var initDownloaded = false
        val downloadedSegments = mutableSetOf<String>()
        var isFirstPoll = true

        while (isActive && isRecording) {
            try {
                val req = Request.Builder().url(mediaPlaylistUrl).build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val playlistBody = resp.body?.string() ?: ""
                        val lines = playlistBody.lines().map { it.trim() }.filter { it.isNotBlank() }

                        // 1. Download init fMP4
                        if (!initDownloaded) {
                            for (line in lines) {
                                if (line.contains("EXT-X-MAP:URI=")) {
                                    val start = line.indexOf("URI=\"") + 5
                                    val end = line.indexOf("\"", start)
                                    if (start in 5..end) {
                                        val initUri = line.substring(start, end)
                                        val fullInitUrl = resolveUrl(baseDir, initUri)
                                        downloadAndAppend(fullInitUrl)
                                        initDownloaded = true
                                        Log.i(TAG, "HLS init fMP4 downloaded: $fullInitUrl")
                                        break
                                    }
                                }
                            }
                        }

                        // Filter semua baris segmen video (.mp4 atau .ts)
                        val segmentLines = lines.filter { !it.startsWith("#") }

                        // Pada poll pertama saat mulai rekam:
                        // Tandai semua segmen historis lama di buffer playlist sebagai "sudah terunduh"
                        // kecuali paling banyak 1 segmen live paling terakhir yang sedang berjalan
                        // Hal ini mencegah rekaman membengkak durasinya dan mencegah freeze pada segmen lama
                        if (isFirstPoll) {
                            isFirstPoll = false
                            if (segmentLines.size > 1) {
                                val oldHistorySegments = segmentLines.dropLast(1)
                                for (oldSeg in oldHistorySegments) {
                                    val oldKey = oldSeg.substringBefore('?').substringAfterLast('/')
                                    downloadedSegments.add(oldKey)
                                }
                                Log.i(TAG, "Skipped ${oldHistorySegments.size} historical buffer segments from past")
                            }
                        }

                        // Unduh hanya segmen aktif saat rekaman berjalan
                        for (line in segmentLines) {
                            val segKey = line.substringBefore('?').substringAfterLast('/')
                            if (!downloadedSegments.contains(segKey)) {
                                val segUrl = resolveUrl(baseDir, line)
                                val ok = downloadAndAppend(segUrl)
                                if (ok) {
                                    downloadedSegments.add(segKey)
                                    Log.i(TAG, "Downloaded fresh segment: $segKey")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Segment poll error: ${e.message}")
            }

            delay(1000)
        }
    }

    /**
     * Unduh berkas chunk dan tulis langsung ke output stream sementara.
     */
    private fun downloadAndAppend(url: String): Boolean {
        return try {
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        synchronized(StreamRecorder) {
                            fileOutputStream?.write(bytes)
                            fileOutputStream?.flush()
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download chunk failed ($url): ${e.message}")
            false
        }
    }

    /**
     * Perekaman generic stream (HTTP direct).
     */
    private suspend fun recordGenericStream(streamUrl: String) = coroutineScope {
        try {
            val req = Request.Builder().url(streamUrl).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val inputStream = resp.body?.byteStream()
                    if (inputStream != null) {
                        val buffer = ByteArray(16384)
                        while (isActive && isRecording) {
                            val read = inputStream.read(buffer)
                            if (read <= 0) break
                            synchronized(StreamRecorder) {
                                fileOutputStream?.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Generic stream download ended: ${e.message}")
        }
    }

    private fun resolveUrl(baseUrl: String, uri: String): String {
        return if (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) {
            uri
        } else if (uri.startsWith("/")) {
            val schemeEnd = baseUrl.indexOf("://")
            val hostEnd = baseUrl.indexOf('/', schemeEnd + 3)
            val hostPart = if (hostEnd != -1) baseUrl.substring(0, hostEnd) else baseUrl
            hostPart + uri
        } else {
            baseUrl + uri
        }
    }

    /**
     * Menghentikan perekaman dan menyimpan berkas video ke Galeri / Penyimpanan HP (Movies/NayakaCCTV)
     * serta mengembalikan objek [Recording] yang siap disimpan di arsip Playback.
     */
    suspend fun stopRecording(
        context: Context,
        frameBitmap: Bitmap? = null
    ): Result<Recording> = withContext(Dispatchers.IO) {
        if (!isRecording && tempFile == null) {
            return@withContext Result.failure(Exception("Tidak ada proses perekaman yang aktif."))
        }

        isRecording = false
        try {
            recordJob?.cancelAndJoin()
        } catch (_: Exception) {}
        recordJob = null

        val currentTemp = tempFile
        try {
            synchronized(StreamRecorder) {
                fileOutputStream?.flush()
                fileOutputStream?.close()
                fileOutputStream = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error closing fileOutputStream: ${e.message}")
        }

        if (currentTemp == null || !currentTemp.exists() || currentTemp.length() < 1024) {
            currentTemp?.delete()
            tempFile = null
            return@withContext Result.failure(Exception("Hasil rekaman kosong atau durasi terlalu singkat."))
        }

        // 0. Normalkan decode timestamp pada kotak tfdt fMP4 agar video mulai di detik 0.000 (menghilangkan freeze dan sync durasi)
        val fmp4DurationSec = normalizeFmp4Timestamps(currentTemp)

        val elapsedSec = ((System.currentTimeMillis() - recordingStartTime) / 1000L).coerceAtLeast(1L)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "NAYAKA_REC_${timeStamp}.mp4"

        try {
            // 1. Simpan salinan permanen ke app external storage (Movies/NayakaCCTV)
            val extMoviesDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "NayakaCCTV")
            if (!extMoviesDir.exists()) extMoviesDir.mkdirs()
            val persistentFile = File(extMoviesDir, fileName)
            currentTemp.copyTo(persistentFile, overwrite = true)

            // 2. Ambil durasi aktual langsung dari berkas video menggunakan MediaMetadataRetriever
            val retriever = MediaMetadataRetriever()
            val actualDurMs = try {
                retriever.setDataSource(persistentFile.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } catch (_: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
            val durationSec = if (actualDurMs != null && actualDurMs > 0) {
                (actualDurMs / 1000L).coerceAtLeast(1L)
            } else if (fmp4DurationSec != null && fmp4DurationSec > 0) {
                fmp4DurationSec
            } else {
                elapsedSec
            }

            // 3. Simpan ke Public MediaStore (Movies/NayakaCCTV) agar muncul di Galeri & Album HP
            val mediaStoreUri = saveVideoToMediaStore(context, currentTemp, fileName)

            // 4. Simpan thumbnail rekaman
            val thumbFile = saveRecordingThumbnail(context, persistentFile, frameBitmap, timeStamp)

            // 5. Bangun metadata Recording untuk Playback
            val isoDateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val startedAtStr = isoDateFmt.format(Date(recordingStartTime))
            val endedAtStr = isoDateFmt.format(Date())

            val rec = Recording(
                id = "local-" + UUID.randomUUID().toString().take(8),
                cameraId = activeCameraId,
                cameraName = activeCameraName.ifBlank { "Kamera CCTV" },
                title = "Rekaman Manual - $activeCameraName",
                startedAt = startedAtStr,
                endedAt = endedAtStr,
                duration = durationSec,
                durationSeconds = durationSec,
                sizeBytes = persistentFile.length(),
                status = "available",
                hasMotion = false,
                playbackUrl = persistentFile.absolutePath,
                thumbnailUrl = thumbFile?.absolutePath ?: mediaStoreUri?.toString()
            )

            currentTemp.delete()
            tempFile = null

            Log.i(TAG, "Recording saved successfully to ${persistentFile.absolutePath} and $mediaStoreUri")
            Result.success(rec)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to finalize recorded video: ${e.message}", e)
            currentTemp.delete()
            tempFile = null
            Result.failure(Exception("Gagal menyimpan rekaman video: ${e.localizedMessage}"))
        }
    }

    /**
     * Menyimpan berkas video ke MediaStore.Video.Media (Movies/NayakaCCTV).
     */
    private fun saveVideoToMediaStore(context: Context, sourceFile: File, fileName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.TITLE, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/NayakaCCTV")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                MediaScannerConnection.scanFile(context, arrayOf(sourceFile.absolutePath), arrayOf("video/mp4"), null)
                return uri
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to MediaStore uri: ${e.message}")
            }
        }
        return null
    }

    /**
     * Menyimpan thumbnail rekaman CCTV.
     */
    private fun saveRecordingThumbnail(
        context: Context,
        videoFile: File,
        frameBitmap: Bitmap?,
        timeStamp: String
    ): File? {
        return try {
            val thumbBitmap = frameBitmap ?: extractVideoFrame(videoFile) ?: return null
            val thumbDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "NayakaCCTV/Thumbs")
            if (!thumbDir.exists()) thumbDir.mkdirs()
            val thumbFile = File(thumbDir, "THUMB_${timeStamp}.jpg")
            FileOutputStream(thumbFile).use { out ->
                thumbBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            thumbFile
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create recording thumbnail: ${e.message}")
            null
        }
    }

    private fun extractVideoFrame(videoFile: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            retriever.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Menyimpan foto snapshot frame CCTV ke Galeri (Pictures/NayakaCCTV).
     */
    fun saveSnapshotToGallery(context: Context, bitmap: Bitmap): Result<Uri> {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "NAYAKA_IMG_${timeStamp}.jpg"
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.TITLE, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NayakaCCTV")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        return try {
            val uri = resolver.insert(collection, contentValues)
                ?: return Result.failure(Exception("Gagal membuat entri galeri foto"))

            resolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            // Juga simpan salinan lokal ke app storage
            val picDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir, "NayakaCCTV")
            if (!picDir.exists()) picDir.mkdirs()
            val localPic = File(picDir, fileName)
            FileOutputStream(localPic).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            MediaScannerConnection.scanFile(context, arrayOf(localPic.absolutePath), arrayOf("image/jpeg"), null)
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Menormalkan baseMediaDecodeTime pada kotak 'tfdt' fMP4
     * agar video dimulai tepat di detik 0.000 (menghilangkan freeze/delay 10 detik di awal)
     * dan durasi video sesuai dengan panjang rekaman sebenarnya.
     */
    private fun normalizeFmp4Timestamps(file: File): Long? {
        if (!file.exists() || file.length() < 16) return null

        var initialTime: Long? = null
        var lastTime: Long = 0L

        try {
            RandomAccessFile(file, "rw").use { raf ->
                val buffer = ByteArray(8192)
                var filePos = 0L
                val fileLen = raf.length()

                while (filePos < fileLen - 16) {
                    raf.seek(filePos)
                    val readLen = raf.read(buffer)
                    if (readLen < 8) break

                    var idx = 0
                    while (idx < readLen - 12) {
                        // Cari signature 't', 'f', 'd', 't' (0x74, 0x66, 0x64, 0x74)
                        if (buffer[idx] == 0x74.toByte() &&
                            buffer[idx + 1] == 0x66.toByte() &&
                            buffer[idx + 2] == 0x64.toByte() &&
                            buffer[idx + 3] == 0x74.toByte()
                        ) {
                            val tfdtOffset = filePos + idx
                            val version = buffer[idx + 4].toInt() and 0xFF

                            if (version == 1 && idx + 16 <= readLen) {
                                // 8-byte uint64 baseMediaDecodeTime
                                val timeOffset = tfdtOffset + 8
                                raf.seek(timeOffset)
                                val origTime = raf.readLong()

                                if (initialTime == null) {
                                    initialTime = origTime
                                }
                                val newTime = (origTime - initialTime!!).coerceAtLeast(0L)
                                lastTime = newTime

                                raf.seek(timeOffset)
                                raf.writeLong(newTime)
                                Log.i(TAG, "Normalized tfdt v1 at $timeOffset: $origTime -> $newTime")
                            } else if (version == 0 && idx + 12 <= readLen) {
                                // 4-byte uint32 baseMediaDecodeTime
                                val timeOffset = tfdtOffset + 8
                                raf.seek(timeOffset)
                                val origTime = raf.readInt().toLong() and 0xFFFFFFFFL

                                if (initialTime == null) {
                                    initialTime = origTime
                                }
                                val newTime = (origTime - initialTime!!).coerceAtLeast(0L)
                                lastTime = newTime

                                raf.seek(timeOffset)
                                raf.writeInt(newTime.toInt())
                                Log.i(TAG, "Normalized tfdt v0 at $timeOffset: $origTime -> $newTime")
                            }
                            idx += 12
                        } else {
                            idx++
                        }
                    }
                    filePos += (readLen - 16).coerceAtLeast(1)
                }
            }
            if (initialTime != null) {
                // Timescale video standar MediaMTX/H.264 adalah 90,000 Hz
                val durSec = (lastTime / 90000L) + 2L
                return durSec.coerceAtLeast(1L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to normalize fmp4 tfdt timestamps: ${e.message}")
        }
        return null
    }
}
