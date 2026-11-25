package com.nayakacctv.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nayakacctv.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicLong

private const val MAX_AUTOMATIC_RETRIES = 5
private const val FRAME_PROGRESS_CHECK_INTERVAL_MS = 1_000L
private const val FRAME_STALL_TIMEOUT_MS = 10_000L
private const val STABLE_PLAYBACK_TO_RESET_RETRIES_MS = 30_000L
private const val RETRY_BASE_DELAY_MS = 2_000L

class CameraStreamController {
    var captureFrameCallback: ((onCaptured: (Bitmap?) -> Unit) -> Unit)? = null

    fun captureFrame(onCaptured: (Bitmap?) -> Unit) {
        val callback = captureFrameCallback
        if (callback != null) {
            callback(onCaptured)
        } else {
            onCaptured(null)
        }
    }
}

@Composable
fun rememberCameraStreamController(): CameraStreamController = remember { CameraStreamController() }

@Composable
fun CameraStreamPlayer(
    streamUrl: String,
    modifier: Modifier = Modifier,
    forceHls: Boolean = false,
    isMuted: Boolean = false,
    fallbackStreamUrl: String? = null,
    controller: CameraStreamController? = null,
    onStateChange: (String) -> Unit = {}
) {
    var useFallback by remember(streamUrl, fallbackStreamUrl) { mutableStateOf(false) }
    val fallbackUrl = fallbackStreamUrl?.takeIf { it.isNotBlank() && it != streamUrl }
    val activeStreamUrl = if (useFallback) fallbackUrl ?: streamUrl else streamUrl
    val uri = remember(activeStreamUrl) { Uri.parse(activeStreamUrl) }
    val isWhep = !useFallback && uri.path?.trimEnd('/')?.endsWith("/whep", ignoreCase = true) == true
    val isHls = forceHls || uri.path?.endsWith(".m3u8", ignoreCase = true) == true
    val isNativeVideo = isHls || uri.scheme.equals("rtsp", ignoreCase = true)

    if (isWhep) {
        WebRtcCameraStreamPlayer(
            streamUrl = activeStreamUrl,
            modifier = modifier,
            isMuted = isMuted,
            controller = controller,
            onStateChange = onStateChange,
            onError = {
                if (fallbackUrl != null) {
                    useFallback = true
                    onStateChange("connecting")
                } else {
                    onStateChange("error")
                }
            }
        )
    } else if (isNativeVideo) {
        Media3CameraStreamPlayer(
            streamUrl = activeStreamUrl,
            forceHls = isHls,
            isMuted = isMuted,
            modifier = modifier,
            controller = controller,
            onStateChange = onStateChange
        )
    } else {
        MjpegStreamView(
            streamUrl = activeStreamUrl,
            modifier = modifier,
            controller = controller,
            onStateChange = onStateChange
        )
    }
}

@Composable
private fun Media3CameraStreamPlayer(
    streamUrl: String,
    forceHls: Boolean,
    isMuted: Boolean,
    modifier: Modifier,
    controller: CameraStreamController? = null,
    onStateChange: (String) -> Unit
) {
    val context = LocalContext.current
    val currentOnStateChange by rememberUpdatedState(onStateChange)
    var retryCount by remember(streamUrl) { mutableIntStateOf(0) }
    var automaticRetryAttempts by remember(streamUrl) { mutableIntStateOf(0) }
    var retryTrigger by remember(streamUrl) { mutableIntStateOf(0) }
    var hasError by remember(streamUrl, retryCount) { mutableStateOf(false) }
    var isBuffering by remember(streamUrl, retryCount) { mutableStateOf(true) }
    var errorMessage by remember(streamUrl, retryCount) { mutableStateOf<String?>(null) }
    var playerViewInstance by remember { mutableStateOf<PlayerView?>(null) }

    val player = remember(streamUrl, forceHls, retryCount) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000, // minBufferMs: tahan jitter jaringan yang singkat
                30_000, // maxBufferMs: ruang buffer lebih besar untuk live stream
                3_000,  // bufferForPlaybackMs: mulai setelah buffer awal terkumpul
                5_000   // bufferForPlaybackAfterRebufferMs: hindari buffer berulang
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .apply {
                if (forceHls) setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(10_000L)
                    .setMinOffsetMs(8_000L)
                    .setMaxOffsetMs(20_000L)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.05f)
                    .build()
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaItem(mediaItem)
                playWhenReady = true
                prepare()
            }
    }
    val renderedVideoFrames = remember(player) { AtomicLong(0L) }
    var hasRenderedFirstFrame by remember(player) { mutableStateOf(false) }

    LaunchedEffect(player, isMuted) {
        player.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(retryTrigger) {
        if (retryTrigger > 0) {
            val retryDelayMs = RETRY_BASE_DELAY_MS * (1 shl (retryTrigger - 1))
            delay(retryDelayMs.coerceAtMost(30_000L))
            retryCount++
        }
    }

    LaunchedEffect(player) {
        var observedFrameCount = renderedVideoFrames.get()
        var lastFrameProgressAt = 0L
        var stablePlaybackSince = 0L

        while (isActive) {
            delay(FRAME_PROGRESS_CHECK_INTERVAL_MS)
            val now = SystemClock.elapsedRealtime()
            val currentFrameCount = renderedVideoFrames.get()
            if (currentFrameCount > observedFrameCount) {
                observedFrameCount = currentFrameCount
                lastFrameProgressAt = now
                if (player.playbackState == Player.STATE_READY && player.playWhenReady && player.isPlaying) {
                    if (stablePlaybackSince == 0L) {
                        stablePlaybackSince = now
                    } else if (now - stablePlaybackSince >= STABLE_PLAYBACK_TO_RESET_RETRIES_MS) {
                        automaticRetryAttempts = 0
                        stablePlaybackSince = now
                    }
                } else {
                    stablePlaybackSince = 0L
                }
                continue
            }

            if (lastFrameProgressAt == 0L) continue

            if (player.playbackState != Player.STATE_READY || !player.playWhenReady || !player.isPlaying) {
                stablePlaybackSince = 0L
            }

            if (now - lastFrameProgressAt >= FRAME_STALL_TIMEOUT_MS) {
                val message = "Tidak ada frame video baru selama 10 detik."
                stablePlaybackSince = 0L
                if (automaticRetryAttempts < MAX_AUTOMATIC_RETRIES) {
                    automaticRetryAttempts++
                    isBuffering = true
                    hasError = false
                    errorMessage = message
                    retryTrigger = automaticRetryAttempts
                    currentOnStateChange("reconnecting")
                } else {
                    hasError = true
                    isBuffering = false
                    errorMessage = message
                    currentOnStateChange("error")
                }
                break
            }
        }
    }

    DisposableEffect(controller, playerViewInstance) {
        if (controller != null && playerViewInstance != null) {
            controller.captureFrameCallback = { onCaptured ->
                val pView = playerViewInstance
                val surfaceView = pView?.videoSurfaceView as? SurfaceView
                val textureView = pView?.videoSurfaceView as? TextureView

                if (textureView != null) {
                    onCaptured(textureView.bitmap)
                } else if (surfaceView != null && surfaceView.holder.surface.isValid && surfaceView.width > 0 && surfaceView.height > 0) {
                    val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
                    PixelCopy.request(
                        surfaceView,
                        bitmap,
                        { res ->
                            if (res == PixelCopy.SUCCESS) {
                                onCaptured(bitmap)
                            } else {
                                onCaptured(null)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } else {
                    onCaptured(null)
                }
            }
        }
        onDispose {
            if (controller?.captureFrameCallback != null) {
                controller.captureFrameCallback = null
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    hasError = false
                    if (hasRenderedFirstFrame) {
                        isBuffering = false
                        retryTrigger = 0
                        currentOnStateChange("playing")
                    } else {
                        isBuffering = true
                        currentOnStateChange("connecting")
                    }
                } else if (playbackState == Player.STATE_BUFFERING) {
                    isBuffering = true
                    currentOnStateChange("connecting")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val err = error.cause?.localizedMessage
                    ?: "${error.errorCodeName}: ${error.localizedMessage ?: "Gagal memuat stream"}"
                Log.e("NAYAKA_STREAM", "ExoPlayer error on $streamUrl: $err", error)
                if (automaticRetryAttempts < MAX_AUTOMATIC_RETRIES) {
                    automaticRetryAttempts++
                    isBuffering = true
                    hasError = false
                    errorMessage = err
                    retryTrigger = automaticRetryAttempts
                    currentOnStateChange("reconnecting")
                } else {
                    hasError = true
                    isBuffering = false
                    errorMessage = err
                    currentOnStateChange("error")
                }
            }
        }
        val analyticsListener = object : AnalyticsListener {
            override fun onRenderedFirstFrame(
                eventTime: AnalyticsListener.EventTime,
                output: Any,
                renderTimeMs: Long
            ) {
                hasRenderedFirstFrame = true
                isBuffering = false
                hasError = false
                retryTrigger = 0
                currentOnStateChange("playing")
            }
        }
        val videoFrameMetadataListener = VideoFrameMetadataListener { _, _, _, _ ->
            renderedVideoFrames.incrementAndGet()
        }
        player.setVideoFrameMetadataListener(videoFrameMetadataListener)
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        currentOnStateChange("connecting")

        onDispose {
            player.clearVideoFrameMetadataListener(videoFrameMetadataListener)
            player.removeListener(listener)
            player.removeAnalyticsListener(analyticsListener)
            player.release()
        }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    this.player = player
                    playerViewInstance = this
                }
            },
            update = {
                it.player = player
                playerViewInstance = it
            }
        )

        if (isBuffering && !hasError) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = PrimaryBlue)
                if (automaticRetryAttempts > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Menyambungkan kembali...",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (hasError) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                val displayError = when {
                    errorMessage?.contains("404") == true -> "Server MediaMTX belum memancarkan stream (HTTP 404)"
                    else -> errorMessage ?: "Gagal memuat stream"
                }
                Text(
                    text = displayError,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = streamUrl,
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    automaticRetryAttempts = 0
                    retryTrigger = 0
                    retryCount++
                }) {
                    Text("Coba lagi")
                }
            }
        }

    }
}
