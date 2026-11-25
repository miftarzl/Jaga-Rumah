package com.nayakacctv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nayakacctv.data.model.AlertItem
import com.nayakacctv.data.model.Camera
import com.nayakacctv.data.network.NetworkClient
import com.nayakacctv.ui.theme.*
import com.nayakacctv.ui.utils.Formatters
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NayakaTopAppBar(
    title: String,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Kembali",
                        tint = TextPrimary
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SurfaceWhite,
            titleContentColor = TextPrimary
        )
    )
}

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor, label) = when (status.lowercase()) {
        "online", "active", "streaming" -> Triple(Color(0xFFDCFCE7), StatusOnline, "ONLINE")
        "recording", "merekam" -> Triple(Color(0xFFFEF9C3), StatusRecording, "MEREKAM")
        else -> Triple(Color(0xFFF1F5F9), StatusOffline, "OFFLINE")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SeverityBadge(severity: String) {
    val (bgColor, textColor, label) = when (severity.lowercase()) {
        "critical", "kritis" -> Triple(Color(0xFFFEE2E2), StatusDanger, "KRITIS")
        "warning", "peringatan" -> Triple(Color(0xFFFEF3C7), StatusWarning, "PERINGATAN")
        else -> Triple(Color(0xFFE0F2FE), StatusInfo, "INFO")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NayakaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color = PrimaryBlue,
    contentColor: Color = SurfaceWhite
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun NayakaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextMuted) },
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, tint = TextSecondary) }
            },
            trailingIcon = trailingIcon,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            isError = isError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = BorderSubtle,
                focusedContainerColor = SurfaceWhite,
                unfocusedContainerColor = SurfaceWhite,
                errorBorderColor = StatusDanger
            )
        )
        if (isError && !errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                color = StatusDanger,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun CameraCard(
    camera: Camera,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    snapshotFile: File? = null,
    loadSnapshotFromServer: Boolean = true,
    onSettingsClick: (() -> Unit)? = null
) {
    val isOnline = camera.status.equals("online", ignoreCase = true)
    var snapshotTick by remember(camera.id) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(camera.id, isOnline, loadSnapshotFromServer, snapshotFile) {
        if (loadSnapshotFromServer && snapshotFile == null && isOnline) {
            while (true) {
                delay(6000)
                snapshotTick = System.currentTimeMillis()
            }
        }
    }

    val snapshotUrl = remember(
        camera.thumbnailUrl,
        camera.id,
        snapshotTick,
        loadSnapshotFromServer,
        snapshotFile,
        isOnline
    ) {
        if (!loadSnapshotFromServer || snapshotFile != null) {
            null
        } else {
            val base = if (!camera.thumbnailUrl.isNullOrBlank()) {
                NetworkClient.resolveUrl(camera.thumbnailUrl)
            } else {
                NetworkClient.resolveUrl("mobile/cameras/${camera.id}/snapshot")
            }
            if (isOnline) {
                if (base.contains("?")) "$base&t=$snapshotTick" else "$base?t=$snapshotTick"
            } else {
                base
            }
        }
    }
    val snapshotModel: Any? = snapshotFile ?: snapshotUrl

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Thumbnail / Snapshot preview (clickable to open live stream)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFF1E293B))
                    .clickable(onClick = onClick)
            ) {
                if (snapshotModel != null) {
                    AsyncImage(
                        model = snapshotModel,
                        contentDescription = camera.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Snapshot akan diambil setelah stream dibuka.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }

                // Tombol Play di tengah agar intuitif bisa langsung ditonton
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Buka Siaran Langsung",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Tag Status Snapshot Aktif (Top-Start)
                if (isOnline && snapshotModel != null) {
                    Box(
                        modifier = Modifier
                            .padding(10.dp)
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "SNAPSHOT AKTIF",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Status Badge Overlay (Top-End)
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.TopEnd)
                ) {
                    StatusBadge(camera.status)
                }

                // Resolusi (Bottom-Start)
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = camera.resolution.ifBlank { "HD 1080P" },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Info & Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onClick)
                ) {
                    Text(
                        text = camera.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (camera.location.isNotBlank()) camera.location else "Tanpa lokasi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                if (onSettingsClick != null) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Pengaturan Kamera",
                            tint = PrimaryBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlertItemCard(
    alert: AlertItem,
    onClick: () -> Unit
) {
    val snapshotPath = alert.thumbnailUrl
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: alert.id.takeIf(String::isNotBlank)
            ?.let { "/api/v1/mobile/alerts/$it/snapshot" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.read) SurfaceWhite else Color(0xFFF0FDF4)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            snapshotPath
                ?.let(NetworkClient::resolveUrl)
                ?.let { thumbnailUrl ->
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = "Snapshot ${alert.cameraName}",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when (alert.severity.lowercase()) {
                            "critical" -> Color(0xFFFEE2E2)
                            "warning" -> Color(0xFFFEF3C7)
                            else -> Color(0xFFE0F2FE)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (alert.type.lowercase()) {
                        "motion" -> Icons.Default.DirectionsRun
                        "person" -> Icons.Default.Person
                        "vehicle" -> Icons.Default.DirectionsCar
                        "sound" -> Icons.Default.VolumeUp
                        else -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = when (alert.severity.lowercase()) {
                        "critical" -> StatusDanger
                        "warning" -> StatusWarning
                        else -> StatusInfo
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alert.cameraName.ifBlank { "Kamera CCTV" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    SeverityBadge(alert.severity)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Formatters.formatIsoDate(alert.time),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderSubtle)
        Text(
            text = "ATAU",
            modifier = Modifier.padding(horizontal = 14.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderSubtle)
    }
}

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Lanjutkan dengan Google",
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceWhite,
            contentColor = TextPrimary
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = PrimaryBlue,
                strokeWidth = 2.dp
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.nayakacctv.R.drawable.ic_google_logo),
                    contentDescription = "Google Logo",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        }
    }
}
