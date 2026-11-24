package com.nayakacctv.ui.screens.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
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

@Composable
fun AlertDetailScreen(
    alertId: String,
    repository: NayakaRepository,
    onNavigateBack: () -> Unit
) {
    var alert by remember(alertId) { mutableStateOf<AlertItem?>(null) }
    var error by remember(alertId) { mutableStateOf<String?>(null) }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                alert == null && error == null -> CircularProgressIndicator()
                error != null -> Text(error.orEmpty(), color = TextSecondary)
                alert != null -> {
                    val item = alert!!
                    val snapshotUrl = item.thumbnailUrl
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let { resolveEvidenceUrl(it) }

                    snapshotUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = "Snapshot notifikasi",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
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
                }
            }
        }
    }
}

private fun resolveEvidenceUrl(url: String): String =
    if (url.startsWith("http://") || url.startsWith("https://")) {
        url
    } else {
        NetworkClient.BASE_URL.trimEnd('/') + "/" + url.trimStart('/')
    }
