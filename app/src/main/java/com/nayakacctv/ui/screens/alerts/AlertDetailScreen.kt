package com.nayakacctv.ui.screens.alerts

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nayakacctv.data.model.AlertItem
import com.nayakacctv.data.network.NetworkClient
import com.nayakacctv.data.repository.NayakaRepository
import com.nayakacctv.ui.components.NayakaTopAppBar
import com.nayakacctv.ui.components.SeverityBadge
import com.nayakacctv.ui.theme.BackgroundSlate
import com.nayakacctv.ui.theme.TextPrimary
import com.nayakacctv.ui.theme.TextSecondary
import com.nayakacctv.ui.utils.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AlertDetailScreen(
    alertId: String,
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var alert by remember(alertId) { mutableStateOf<AlertItem?>(null) }
    var error by remember(alertId) { mutableStateOf<String?>(null) }
    var snapshotError by remember(alertId) { mutableStateOf<String?>(null) }
    var clipError by remember(alertId) { mutableStateOf<String?>(null) }
    var isLoadingSnapshot by remember(alertId) { mutableStateOf(false) }
    var isLoadingClip by remember(alertId) { mutableStateOf(false) }
    var isPlayingClip by remember(alertId) { mutableStateOf(false) }
    var snapshotFile by remember(alertId) { mutableStateOf<File?>(null) }
    var clipFile by remember(alertId) { mutableStateOf<File?>(null) }
    var pendingImageSave by remember(alertId) { mutableStateOf<File?>(null) }
    var pendingVideoSave by remember(alertId) { mutableStateOf<File?>(null) }

    val saveImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        val source = pendingImageSave
        pendingImageSave = null
        if (uri != null && source != null) {
            coroutineScope.launch {
                copyEvidenceToUri(context, source, uri)
                    .onSuccess {
                        Toast.makeText(context, "Foto bukti berhasil disimpan.", Toast.LENGTH_LONG).show()
                    }
                    .onFailure {
                        Toast.makeText(context, it.localizedMessage ?: "Gagal menyimpan foto bukti.", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    val saveVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        val source = pendingVideoSave
        pendingVideoSave = null
        if (uri != null && source != null) {
            coroutineScope.launch {
                copyEvidenceToUri(context, source, uri)
                    .onSuccess {
                        Toast.makeText(context, "Klip bukti berhasil disimpan.", Toast.LENGTH_LONG).show()
                    }
                    .onFailure {
                        Toast.makeText(context, it.localizedMessage ?: "Gagal menyimpan klip bukti.", Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    suspend fun ensureSnapshotFile(): File {
        snapshotFile?.takeIf(File::exists)?.let { return it }
        val safeId = alertId.filter(Char::isLetterOrDigit).ifBlank { "alert" }
        val destination = File(context.cacheDir, "alert_${safeId}_snapshot.jpg")
        val file = repository.downloadAlertSnapshot(alertId, destination).getOrThrow()
        snapshotFile = file
        return file
    }

    suspend fun ensureClipFile(): File {
        clipFile?.takeIf(File::exists)?.let { return it }
        val safeId = alertId.filter(Char::isLetterOrDigit).ifBlank { "alert" }
        val destination = File(context.cacheDir, "alert_$safeId.mp4")
        val file = repository.downloadAlertClip(alertId, destination).getOrThrow()
        clipFile = file
        return file
    }

    fun launchSnapshotAction(action: suspend () -> Unit) {
        coroutineScope.launch {
            isLoadingSnapshot = true
            snapshotError = null
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snapshotError = e.localizedMessage ?: "Gagal memproses foto bukti."
            } finally {
                isLoadingSnapshot = false
            }
        }
    }

    fun launchClipAction(action: suspend () -> Unit) {
        coroutineScope.launch {
            isLoadingClip = true
            clipError = null
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                clipError = e.localizedMessage ?: "Gagal memproses klip bukti."
            } finally {
                isLoadingClip = false
            }
        }
    }

    LaunchedEffect(alertId) {
        repository.markAlertRead(alertId)
        repository.getAlertDetail(alertId)
            .onSuccess { alert = it }
            .onFailure { error = it.localizedMessage ?: "Gagal memuat detail notifikasi" }
    }

    Scaffold(
        topBar = {
            NayakaTopAppBar(
                title = "Detail Notifikasi",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = BackgroundSlate
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                alert == null && error == null -> CircularProgressIndicator()
                error != null -> Text(error.orEmpty(), color = TextSecondary)
                alert != null -> {
                    val item = alert!!
                    val snapshotPath = item.thumbnailUrl
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?: "/api/v1/mobile/alerts/$alertId/snapshot"

                    AsyncImage(
                        model = NetworkClient.resolveUrl(snapshotPath),
                        contentDescription = "Bukti snapshot notifikasi",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentScale = ContentScale.Crop,
                        onError = {
                            snapshotError = it.result.throwable.localizedMessage
                                ?: "Snapshot bukti tidak dapat dimuat."
                        }
                    )
                    snapshotError?.let {
                        Text("Snapshot: $it", color = TextSecondary)
                    }
                    AlertEvidenceActionRow(
                        isLoading = isLoadingSnapshot,
                        downloadLabel = "Unduh foto",
                        onDownload = {
                            launchSnapshotAction {
                                val file = ensureSnapshotFile()
                                val safeId = alertId.filter(Char::isLetterOrDigit).ifBlank { "alert" }
                                pendingImageSave = file
                                saveImageLauncher.launch("NAYAKA_ALERT_$safeId.jpg")
                            }
                        },
                        onShare = {
                            launchSnapshotAction {
                                val file = ensureSnapshotFile()
                                shareAlertEvidence(context, file, "image/jpeg", "Bagikan Foto Bukti")
                            }
                        }
                    )
                    SeverityBadge(item.severity)
                    Text(
                        text = item.title.ifBlank { item.cameraName.ifBlank { "Peringatan CCTV" } },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(item.message, color = TextSecondary)
                    Text(
                        text = Formatters.formatIsoDate(item.time.ifBlank { item.occurredAt.orEmpty() }),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Button(
                        onClick = {
                            launchClipAction {
                                ensureClipFile()
                                isPlayingClip = true
                            }
                        },
                        enabled = !isLoadingClip && !isPlayingClip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoadingClip) {
                            CircularProgressIndicator()
                        } else {
                            Text(
                                if (item.clipDurationSeconds != null) {
                                    "Putar klip bukti (${item.clipDurationSeconds} detik)"
                                } else {
                                    "Putar video bukti"
                                }
                            )
                        }
                    }
                    AlertEvidenceActionRow(
                        isLoading = isLoadingClip,
                        downloadLabel = "Unduh klip",
                        onDownload = {
                            launchClipAction {
                                val file = ensureClipFile()
                                val safeId = alertId.filter(Char::isLetterOrDigit).ifBlank { "alert" }
                                pendingVideoSave = file
                                saveVideoLauncher.launch("NAYAKA_ALERT_$safeId.mp4")
                            }
                        },
                        onShare = {
                            launchClipAction {
                                val file = ensureClipFile()
                                shareAlertEvidence(context, file, "video/mp4", "Bagikan Klip Bukti")
                            }
                        }
                    )
                    clipError?.let {
                        Text("Video: $it", color = TextSecondary)
                    }
                    if (isPlayingClip) {
                        clipFile?.let { AlertClipPlayer(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertEvidenceActionRow(
    isLoading: Boolean,
    downloadLabel: String,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            enabled = !isLoading,
            modifier = Modifier.weight(1f),
            onClick = onDownload
        ) {
            Text(if (isLoading) "Menyiapkan..." else downloadLabel)
        }
        OutlinedButton(
            enabled = !isLoading,
            modifier = Modifier.weight(1f),
            onClick = onShare
        ) {
            Text("Bagikan")
        }
    }
}

@Composable
private fun AlertClipPlayer(file: File) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }

    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { it.player = player }
    )
}

private suspend fun copyEvidenceToUri(
    context: android.content.Context,
    source: File,
    destination: Uri
): Result<Unit> = withContext(Dispatchers.IO) {
    try {
        val output = context.contentResolver.openOutputStream(destination)
            ?: throw IllegalStateException("Lokasi penyimpanan tidak dapat dibuka.")
        output.use { target ->
            source.inputStream().use { input -> input.copyTo(target) }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun shareAlertEvidence(
    context: android.content.Context,
    file: File,
    mimeType: String,
    chooserTitle: String
) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
}
