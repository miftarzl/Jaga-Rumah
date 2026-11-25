package com.nayakacctv.ui.screens.playback

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nayakacctv.data.model.Recording
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.AppLockManager
import com.nayakacctv.ui.utils.Formatters
import com.nayakacctv.ui.utils.StreamRecorder
import com.nayakacctv.ui.utils.findFragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PlaybackDetailScreen(
    recordingId: String,
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findFragmentActivity() }

    var recording by remember { mutableStateOf<Recording?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentSeconds by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    LaunchedEffect(isFullscreen) {
        val act = activity ?: return@LaunchedEffect
        val window = act.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (isFullscreen) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                act.window?.let { win ->
                    WindowCompat.getInsetsController(win, win.decorView).show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    LaunchedEffect(recordingId) {
        val result = repository.getRecordingDetail(recordingId)
        recording = result.getOrNull()
        loadError = result.exceptionOrNull()?.let { error ->
            if (error.message.orEmpty().contains("404")) {
                "Rekaman sudah tidak tersedia di server atau telah dihapus."
            } else {
                error.localizedMessage ?: "Gagal memuat rekaman."
            }
        }
        totalDuration = (recording?.duration ?: 0L).takeIf { it > 0 } ?: 1L
    }

    val playbackUrl = recording?.playbackUrl
    val mediaUri: Uri? = remember(playbackUrl) {
        if (playbackUrl.isNullOrBlank()) null
        else if (playbackUrl.startsWith("http://", ignoreCase = true) ||
            playbackUrl.startsWith("https://", ignoreCase = true) ||
            playbackUrl.startsWith("content://", ignoreCase = true)
        ) {
            Uri.parse(playbackUrl)
        } else {
            Uri.fromFile(File(playbackUrl))
        }
    }

    val exoPlayer = remember(mediaUri) {
        if (mediaUri != null) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(mediaUri))
                prepare()
                playWhenReady = true
            }
        } else null
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val dur = player.duration
                if (dur > 0 && dur != androidx.media3.common.C.TIME_UNSET) {
                    val sec = dur / 1000
                    if (sec > 0) totalDuration = sec
                }
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                val dur = player.duration
                if (dur > 0 && dur != androidx.media3.common.C.TIME_UNSET) {
                    val sec = dur / 1000
                    if (sec > 0) totalDuration = sec
                }
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Update position sync & durasi dinamis
    LaunchedEffect(exoPlayer, isPlaying, isScrubbing) {
        val player = exoPlayer
        if (player != null && !isScrubbing) {
            while (true) {
                val dur = player.duration
                if (dur > 0 && dur != androidx.media3.common.C.TIME_UNSET) {
                    val sec = dur / 1000
                    if (sec > 0 && sec != totalDuration) {
                        totalDuration = sec
                    }
                }
                if (player.isPlaying) {
                    currentSeconds = (player.currentPosition / 1000).coerceAtMost(totalDuration.coerceAtLeast(1L))
                }
                delay(250)
            }
        }
    }

    // Kecepatan putar
    LaunchedEffect(speed) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    if (isFullscreen) {
        // Tampilan Fullscreen Horizontal (Layar Penuh)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (exoPlayer != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            keepScreenOn = true
                            player = exoPlayer
                        }
                    },
                    update = { it.player = exoPlayer }
                )
            }

            // Top Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isFullscreen = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Keluar Fullscreen", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = recording?.title?.ifBlank { "Putar Rekaman" } ?: "Putar Rekaman",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("${speed}x", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { isFullscreen = false }) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Keluar Fullscreen", tint = Color.White)
                    }
                }
            }

            // Center Play/Pause Control
            IconButton(
                onClick = {
                    val player = exoPlayer
                    if (player != null) {
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.seekTo(0)
                            player.play()
                        } else if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    } else {
                        isPlaying = !isPlaying
                    }
                },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            // Bottom Scrubber Overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                val maxRange = totalDuration.coerceAtLeast(1L).toFloat()
                Slider(
                    value = currentSeconds.toFloat().coerceIn(0f, maxRange),
                    onValueChange = {
                        isScrubbing = true
                        currentSeconds = it.toLong()
                    },
                    onValueChangeFinished = {
                        exoPlayer?.seekTo(currentSeconds * 1000)
                        isScrubbing = false
                    },
                    valueRange = 0f..maxRange,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryBlue,
                        activeTrackColor = PrimaryBlue,
                        inactiveTrackColor = Color.DarkGray
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Formatters.formatDuration(currentSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                    Text(
                        text = Formatters.formatDuration(totalDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
        return
    }

    // Tampilan Normal Portrait
    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Putar Rekaman",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus Rekaman", tint = StatusDanger)
                    }
                }
            )
        },
        containerColor = BackgroundSlate
    ) { padding ->
        val rec = recording
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (loadError != null) {
                Text(
                    text = loadError.orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = StatusDanger
                )
            }

            // Player Video Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (exoPlayer != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                keepScreenOn = true
                                player = exoPlayer
                            }
                        },
                        update = { it.player = exoPlayer }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(70.dp)
                    )
                }

                // Overlay Play/Pause Control
                IconButton(
                    onClick = {
                        val player = exoPlayer
                        if (player != null) {
                            if (player.playbackState == Player.STATE_ENDED) {
                                player.seekTo(0)
                                player.play()
                            } else if (player.isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        } else {
                            isPlaying = !isPlaying
                        }
                    },
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Speed Pill (Top-Right)
                Box(
                    modifier = Modifier
                        .padding(12.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${speed}x",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tombol Fullscreen (Bottom-Right)
                IconButton(
                    onClick = { isFullscreen = true },
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.BottomEnd)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Mode Layar Penuh",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Timeline Scrubber
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val maxRange = totalDuration.coerceAtLeast(1L).toFloat()
                Slider(
                    value = currentSeconds.toFloat().coerceIn(0f, maxRange),
                    onValueChange = {
                        isScrubbing = true
                        currentSeconds = it.toLong()
                    },
                    onValueChangeFinished = {
                        exoPlayer?.seekTo(currentSeconds * 1000)
                        isScrubbing = false
                    },
                    valueRange = 0f..maxRange,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryBlue,
                        activeTrackColor = PrimaryBlue,
                        inactiveTrackColor = BorderSubtle
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = Formatters.formatDuration(currentSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = Formatters.formatDuration(totalDuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            // Metadata Detail Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = rec?.title?.ifBlank { "Rekaman CCTV" } ?: "Memuat...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${rec?.cameraName ?: ""} • ${Formatters.formatIsoDate(rec?.startedAt ?: "")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Durasi: ${Formatters.formatDuration(totalDuration)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = "Ukuran: ${Formatters.formatBytes(rec?.sizeBytes ?: 0L)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            // Controls & Speeds Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kecepatan Pemutaran",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(0.5f, 1f, 2f, 4f).forEach { s ->
                            val isSelected = speed == s
                            FilterChip(
                                selected = isSelected,
                                onClick = { speed = s },
                                label = { Text("${s}x", fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = BorderSubtle)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Aksi Berkas Rekaman",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            enabled = !isDownloading && !isDeleting,
                            onClick = {
                                if (playbackUrl.isNullOrBlank()) {
                                    Toast.makeText(context, "URL rekaman tidak tersedia.", Toast.LENGTH_SHORT).show()
                                } else if (playbackUrl.startsWith("/") ||
                                    playbackUrl.startsWith("file://") ||
                                    playbackUrl.startsWith("content://", ignoreCase = true)
                                ) {
                                    Toast.makeText(context, "Berkas video sudah tersimpan di Galeri & Movies/NayakaCCTV", Toast.LENGTH_LONG).show()
                                } else {
                                    isDownloading = true
                                    coroutineScope.launch {
                                        val result = repository.downloadRecording(recordingId)
                                        result.onSuccess { body ->
                                            val saved = withContext(Dispatchers.IO) {
                                                body.byteStream().use {
                                                    StreamRecorder.saveVideoStreamToGallery(
                                                        context,
                                                        it,
                                                        "NAYAKA_REC_${recordingId}.mp4"
                                                    )
                                                }
                                            }
                                            saved.onSuccess {
                                                Toast.makeText(context, "Video berhasil disimpan ke Galeri / Movies/NayakaCCTV.", Toast.LENGTH_LONG).show()
                                            }.onFailure {
                                                Toast.makeText(context, it.localizedMessage ?: "Gagal menyimpan video.", Toast.LENGTH_LONG).show()
                                            }
                                        }.onFailure {
                                            Toast.makeText(context, it.localizedMessage ?: "Gagal mengunduh rekaman.", Toast.LENGTH_LONG).show()
                                        }
                                        isDownloading = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isDownloading) "Mengunduh..." else "Unduh Video", fontSize = 13.sp)
                        }

                        Button(
                            enabled = !isDownloading && !isDeleting,
                            onClick = {
                                try {
                                    if (playbackUrl != null && playbackUrl.startsWith("content://", ignoreCase = true)) {
                                        val shareUri = Uri.parse(playbackUrl)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "video/mp4"
                                            putExtra(Intent.EXTRA_STREAM, shareUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        AppLockManager.isSuppressed = true
                                        context.startActivity(Intent.createChooser(shareIntent, "Bagikan Rekaman CCTV"))
                                        return@Button
                                    } else if (playbackUrl != null && (playbackUrl.startsWith("/") || playbackUrl.startsWith("file://"))) {
                                        val cleanPath = playbackUrl.removePrefix("file://")
                                        val f = File(cleanPath)
                                        if (f.exists()) {
                                            val shareUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                f
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "video/mp4"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            AppLockManager.isSuppressed = true
                                            context.startActivity(Intent.createChooser(shareIntent, "Bagikan Rekaman CCTV"))
                                            return@Button
                                        }
                                    } else if (!playbackUrl.isNullOrBlank()) {
                                        isDownloading = true
                                        coroutineScope.launch {
                                            val result = repository.downloadRecording(recordingId)
                                            result.onSuccess { body ->
                                                val saved = withContext(Dispatchers.IO) {
                                                    body.byteStream().use {
                                                        StreamRecorder.saveVideoStreamToGallery(
                                                            context,
                                                            it,
                                                            "NAYAKA_REC_${recordingId}.mp4"
                                                        )
                                                    }
                                                }
                                                saved.onSuccess { uri ->
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "video/mp4"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    AppLockManager.isSuppressed = true
                                                    context.startActivity(Intent.createChooser(shareIntent, "Bagikan Rekaman CCTV"))
                                                }.onFailure {
                                                    Toast.makeText(context, it.localizedMessage ?: "Gagal menyiapkan video.", Toast.LENGTH_SHORT).show()
                                                }
                                            }.onFailure {
                                                Toast.makeText(context, it.localizedMessage ?: "Gagal mengunduh rekaman.", Toast.LENGTH_SHORT).show()
                                            }
                                            isDownloading = false
                                        }
                                        return@Button
                                    }
                                    Toast.makeText(context, "URL rekaman tidak tersedia.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Gagal membagikan rekaman: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tombol Hapus Rekaman
                    OutlinedButton(
                        enabled = !isDownloading && !isDeleting,
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = StatusDanger)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hapus Rekaman Ini", fontSize = 13.sp, color = StatusDanger, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text("Hapus Rekaman?", fontWeight = FontWeight.Bold, color = StatusDanger) },
            text = { Text("Rekaman video ini akan dihapus dari daftar histori rekaman dan memori penyimpanan.") },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        coroutineScope.launch {
                            val url = playbackUrl
                            if (url != null) {
                                try {
                                    if (url.startsWith("content://", ignoreCase = true)) {
                                        context.contentResolver.delete(Uri.parse(url), null, null)
                                    } else if (url.startsWith("/") || url.startsWith("file://")) {
                                        File(url.removePrefix("file://")).delete()
                                    }
                                } catch (_: Exception) {}
                            }
                            repository.deleteLocalRecording(recordingId)
                            isDeleting = false
                            showDeleteDialog = false
                            Toast.makeText(context, "Rekaman berhasil dihapus.", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Hapus", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, enabled = !isDeleting) {
                    Text("Batal")
                }
            }
        )
    }
}
