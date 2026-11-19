package com.nayakacctv.ui.components

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nayakacctv.ui.theme.PrimaryBlue

@Composable
fun CameraStreamPlayer(
    streamUrl: String,
    modifier: Modifier = Modifier,
    forceHls: Boolean = false,
    onStateChange: (String) -> Unit = {}
) {
    val uri = remember(streamUrl) { Uri.parse(streamUrl) }
    val isHls = forceHls || uri.path?.endsWith(".m3u8", ignoreCase = true) == true
    val isNativeVideo = isHls || uri.scheme.equals("rtsp", ignoreCase = true)

    if (isNativeVideo) {
        Media3CameraStreamPlayer(
            streamUrl = streamUrl,
            forceHls = isHls,
            modifier = modifier,
            onStateChange = onStateChange
        )
    } else {
        MjpegStreamView(streamUrl, modifier, onStateChange)
    }
}

@Composable
private fun Media3CameraStreamPlayer(
    streamUrl: String,
    forceHls: Boolean,
    modifier: Modifier,
    onStateChange: (String) -> Unit
) {
    val context = LocalContext.current
    val currentOnStateChange by rememberUpdatedState(onStateChange)
    var retryCount by remember(streamUrl) { mutableIntStateOf(0) }
    var hasError by remember(streamUrl) { mutableStateOf(false) }
    var isBuffering by remember(streamUrl) { mutableStateOf(true) }
    var errorMessage by remember(streamUrl) { mutableStateOf<String?>(null) }

    val player = remember(streamUrl, forceHls, retryCount) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .apply {
                    if (forceHls) setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                .build()
            setMediaItem(mediaItem)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    hasError = false
                    isBuffering = false
                    currentOnStateChange("playing")
                } else if (playbackState == Player.STATE_BUFFERING) {
                    isBuffering = true
                    currentOnStateChange("connecting")
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isBuffering = false
                val err = error.cause?.localizedMessage
                    ?: "${error.errorCodeName}: ${error.localizedMessage ?: "Gagal memuat stream"}"
                errorMessage = err
                Log.e("NAYAKA_STREAM", "ExoPlayer error on $streamUrl: $err", error)
                currentOnStateChange("error")
            }
        }
        player.addListener(listener)
        currentOnStateChange("connecting")

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    this.player = player
                }
            },
            update = { it.player = player }
        )

        if (isBuffering && !hasError) {
            CircularProgressIndicator(color = PrimaryBlue)
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
                Button(onClick = { retryCount++ }) {
                    Text("Coba lagi")
                }
            }
        }
    }
}
