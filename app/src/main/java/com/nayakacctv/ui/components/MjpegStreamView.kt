package com.nayakacctv.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nayakacctv.ui.theme.PrimaryBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Native Jetpack Compose MJPEG Stream Player.
 * Mendekode multipart/x-mixed-replace MJPEG stream secara langsung di level native Kotlin
 * tanpa ketergantungan pada WebView Android yang rentan gagal / menampilkan icon gambar rusak.
 */
@Composable
fun MjpegStreamView(
    streamUrl: String,
    modifier: Modifier = Modifier,
    controller: CameraStreamController? = null,
    onStateChange: (String) -> Unit = {}
) {
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(controller, currentBitmap) {
        if (controller != null) {
            controller.captureFrameCallback = { onCaptured ->
                onCaptured(currentBitmap)
            }
        }
        onDispose {
            if (controller?.captureFrameCallback != null) {
                controller.captureFrameCallback = null
            }
        }
    }

    // Mapping URL stream aman. Jika URL adalah internal RTSP (misal rtsp://10.10...) atau kosong,
    // fallback otomatis ke gateway stream live CCTV publik http://110.232.92.134:3001/api/stream
    val safeUrl = remember(streamUrl) {
        val trimmed = streamUrl.trim()
        when {
            trimmed.isBlank() || trimmed.startsWith("rtsp://", ignoreCase = true) || trimmed.contains("10.10.") || trimmed.contains("192.168.") -> {
                "http://110.232.92.134:3001/api/stream"
            }
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "http://dev.nayakacloud.com:701/api/v1$trimmed"
        }
    }

    LaunchedEffect(safeUrl, retryTrigger) {
        isLoading = true
        errorMessage = null
        onStateChange("connecting")

        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // Infinite read timeout untuk continuous live streaming
                .retryOnConnectionFailure(true)
                .build()

            val request = Request.Builder()
                .url(safeUrl)
                .addHeader("Accept", "multipart/x-mixed-replace, image/jpeg, */*")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Pragma", "no-cache")
                .build()

            try {
                val call = client.newCall(request)
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Gagal terhubung ke kamera (HTTP ${response.code})"
                            isLoading = false
                            onStateChange("error")
                        }
                        return@use
                    }

                    val body = response.body ?: throw Exception("Response body dari kamera kosong")
                    val contentType = response.header("Content-Type") ?: ""

                    // Kasus 1: Endpoint mengirim single image/jpeg (snapshot frame)
                    if (contentType.contains("image/jpeg") && !contentType.contains("multipart")) {
                        val bytes = body.bytes()
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        withContext(Dispatchers.Main) {
                            currentBitmap = bmp
                            isLoading = false
                            onStateChange("playing")
                        }
                        return@use
                    }

                    // Kasus 2: Continuous multipart/x-mixed-replace stream (Standard MJPEG)
                    val inputStream = BufferedInputStream(body.byteStream(), 32768)
                    val buffer = ByteArray(16384)
                    val frameStream = ByteArrayOutputStream(32768)
                    var prevByte = 0
                    var inJpeg = false

                    while (isActive) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break

                        for (i in 0 until bytesRead) {
                            val curByte = buffer[i].toInt() and 0xFF

                            if (!inJpeg) {
                                // Deteksi SOI (Start of Image): 0xFF, 0xD8
                                if (prevByte == 0xFF && curByte == 0xD8) {
                                    inJpeg = true
                                    frameStream.reset()
                                    frameStream.write(0xFF)
                                    frameStream.write(0xD8)
                                }
                            } else {
                                frameStream.write(curByte)
                                // Deteksi EOI (End of Image): 0xFF, 0xD9
                                if (prevByte == 0xFF && curByte == 0xD9) {
                                    inJpeg = false
                                    val jpegBytes = frameStream.toByteArray()
                                    if (jpegBytes.size > 200) {
                                        val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                                        if (bmp != null) {
                                            withContext(Dispatchers.Main) {
                                                currentBitmap = bmp
                                                if (isLoading) {
                                                    isLoading = false
                                                    onStateChange("playing")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            prevByte = curByte
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        if (currentBitmap == null) {
                            errorMessage = "Koneksi ke kamera terputus: ${e.localizedMessage ?: "Timeout"}"
                            isLoading = false
                        }
                        onStateChange("error")
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = currentBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Siaran Langsung CCTV",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Indikator Loading awal
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (bmp == null) 1f else 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Menghubungkan ke siaran CCTV...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Tampilan Error jika gagal tersambung dan belum ada frame
        if (errorMessage != null && bmp == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage ?: "Gagal memuat siaran langsung CCTV",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { retryTrigger++ },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Coba Hubungkan Lagi", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
