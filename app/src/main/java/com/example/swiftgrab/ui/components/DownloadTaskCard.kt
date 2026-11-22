package com.example.swiftgrab.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.swiftgrab.R
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import com.example.swiftgrab.data.repository.LiveProgress
import com.example.swiftgrab.service.MediaMetadataExtractor
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import java.util.Locale

@Composable
fun DownloadTaskCard(
    task: DownloadTaskEntity,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onShareFile: (String) -> Unit,
    onRetry: (DownloadTaskEntity) -> Unit,
    onShowError: (DownloadTaskEntity) -> Unit,
    modifier: Modifier = Modifier,
    liveProgressProvider: () -> LiveProgress? = { null }
) {
    val borderColor = when (task.status) {
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Row 1: Thumbnail, Title, Format & Duration Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                MediaThumbnail(
                    thumbnailUrl = task.thumbnailUrl,
                    audioOnly = task.audioOnly
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FormatBadge(label = task.formatLabel, audioOnly = task.audioOnly)
                        if (task.durationSeconds > 0L) {
                            DurationBadge(durationSeconds = task.durationSeconds)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2: Progress Indicator (deferred read to isolate recomposition)
            val progressColor = when (task.status) {
                DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                DownloadStatus.FAILED, DownloadStatus.CANCELED -> MaterialTheme.colorScheme.error
                DownloadStatus.QUEUED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            }

            val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

            if (task.status == DownloadStatus.QUEUED) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = trackColor,
                    strokeCap = StrokeCap.Round
                )
            } else {
                val progressProvider: () -> Float = remember(task.id, task.status, task.progress, liveProgressProvider) {
                    {
                        val currentProgress = if (task.status == DownloadStatus.DOWNLOADING) {
                            liveProgressProvider()?.progress ?: task.progress
                        } else {
                            task.progress
                        }
                        (currentProgress / 100f).coerceIn(0f, 1f)
                    }
                }
                LinearProgressIndicator(
                    progress = progressProvider,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = progressColor,
                    trackColor = trackColor,
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Non-color Status Icon + Tabular Metrics (Left) & Touch Action Controls (Right)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricsText(
                    taskId = task.id,
                    status = task.status,
                    fallbackProgress = task.progress,
                    fallbackDownloadedBytes = task.downloadedBytes,
                    fallbackTotalBytes = task.totalBytes,
                    fallbackSpeed = task.speed,
                    fallbackEta = task.eta,
                    errorMessage = task.errorMessage,
                    liveProgressProvider = liveProgressProvider,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                ActionButtons(
                    task = task,
                    onPause = { onPause(task.id) },
                    onResume = { onResume(task.id) },
                    onCancel = { onCancel(task.id) },
                    onDelete = { onDelete(task.id) },
                    onOpenFile = { task.outputPath?.let { onOpenFile(it) } },
                    onShareFile = { task.outputPath?.let { onShareFile(it) } },
                    onRetry = { onRetry(task) },
                    onShowError = { onShowError(task) }
                )
            }
        }
    }
}

@Composable
private fun MediaThumbnail(
    thumbnailUrl: String?,
    audioOnly: Boolean,
    modifier: Modifier = Modifier
) {
    val thumbShape = RoundedCornerShape(10.dp)
    val fallbackPainter = rememberCenteredIconPainter(
        imageVector = if (audioOnly) Icons.Rounded.Audiotrack else Icons.Rounded.VideoLibrary,
        iconSize = 22.dp,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Box(
        modifier = modifier
            .size(width = 80.dp, height = 46.dp)
            .clip(thumbShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnailUrl?.takeIf { it.isNotBlank() })
                .crossfade(true)
                .build(),
            contentDescription = null,
            placeholder = fallbackPainter,
            error = fallbackPainter,
            fallback = fallbackPainter,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, thumbShape)
        )
    }
}

@Composable
private fun rememberCenteredIconPainter(
    imageVector: ImageVector,
    iconSize: Dp,
    tint: Color
): Painter {
    val vectorPainter = rememberVectorPainter(image = imageVector)
    val density = LocalDensity.current
    val iconSizePx = with(density) { iconSize.toPx() }
    val colorFilter = remember(tint) { ColorFilter.tint(tint) }

    return remember(vectorPainter, iconSizePx, colorFilter) {
        object : Painter() {
            override val intrinsicSize: Size = Size.Unspecified

            override fun DrawScope.onDraw() {
                val targetSize = minOf(iconSizePx, size.width, size.height)
                val dx = (size.width - targetSize) / 2f
                val dy = (size.height - targetSize) / 2f
                translate(left = dx, top = dy) {
                    with(vectorPainter) {
                        draw(
                            size = Size(targetSize, targetSize),
                            colorFilter = colorFilter
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationBadge(
    durationSeconds: Long,
    modifier: Modifier = Modifier
) {
    val formatted = MediaMetadataExtractor.formatDuration(durationSeconds)
    if (formatted.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FormatBadge(
    label: String,
    audioOnly: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (audioOnly) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    }
    val borderColor = if (audioOnly) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    }
    val contentColor = if (audioOnly) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetricsText(
    taskId: String,
    status: DownloadStatus,
    fallbackProgress: Float,
    fallbackDownloadedBytes: Long,
    fallbackTotalBytes: Long,
    fallbackSpeed: String,
    fallbackEta: String,
    errorMessage: String?,
    liveProgressProvider: () -> LiveProgress?,
    modifier: Modifier = Modifier
) {
    val liveProgress by remember(taskId, status, liveProgressProvider) {
        derivedStateOf {
            if (status == DownloadStatus.DOWNLOADING) liveProgressProvider() else null
        }
    }

    val text = when (status) {
        DownloadStatus.DOWNLOADING -> {
            val progressValue = liveProgress?.progress ?: fallbackProgress
            val downloadedBytes = liveProgress?.downloadedBytes ?: fallbackDownloadedBytes
            val totalBytes = liveProgress?.totalBytes ?: fallbackTotalBytes
            val speed = liveProgress?.speed ?: fallbackSpeed
            val eta = liveProgress?.eta ?: fallbackEta

            val progressInt = progressValue.toInt().coerceIn(0, 100)
            val downloaded = formatBytes(downloadedBytes)
            val total = if (totalBytes > 0L) formatBytes(totalBytes) else "--"
            val speedPart = if (speed.isNotBlank()) " • $speed" else ""
            val etaPart = if (eta.isNotBlank()) " • ETA $eta" else ""
            "$downloaded / $total • $progressInt%$speedPart$etaPart"
        }
        DownloadStatus.PAUSED -> {
            val progressInt = fallbackProgress.toInt().coerceIn(0, 100)
            "Dijeda • $progressInt%"
        }
        DownloadStatus.COMPLETED -> {
            val size = if (fallbackTotalBytes > 0L) formatBytes(fallbackTotalBytes) else ""
            if (size.isNotBlank()) "Selesai • $size" else "Selesai"
        }
        DownloadStatus.FAILED -> {
            val err = parseDisplayError(errorMessage)
            "Gagal • $err"
        }
        DownloadStatus.CANCELED -> "Dibatalkan"
        DownloadStatus.QUEUED -> "Menunggu antrean…"
    }

    val statusIcon = when (status) {
        DownloadStatus.DOWNLOADING -> Icons.Rounded.Downloading
        DownloadStatus.PAUSED -> Icons.Rounded.PauseCircleOutline
        DownloadStatus.COMPLETED -> Icons.Rounded.CheckCircleOutline
        DownloadStatus.FAILED -> Icons.Rounded.ErrorOutline
        DownloadStatus.CANCELED -> Icons.Rounded.Cancel
        DownloadStatus.QUEUED -> Icons.Rounded.Schedule
    }

    val statusColor = when (status) {
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.CANCELED, DownloadStatus.QUEUED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Tombol aksi kontekstual (jeda, lanjutkan, batal, hapus, putar, bagikan, ulangi)
 * sesuai status tugas unduhan.
 */
@Composable
private fun ActionButtons(
    task: DownloadTaskEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit,
    onRetry: () -> Unit,
    onShowError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = modifier
    ) {
        when (task.status) {
            DownloadStatus.DOWNLOADING -> {
                PrimaryTaskActionButton(
                    onClick = onPause,
                    icon = Icons.Rounded.Pause,
                    contentDescription = stringResource(R.string.cd_pause),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    iconSize = 20.dp
                )
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_cancel),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DownloadStatus.PAUSED -> {
                PrimaryTaskActionButton(
                    onClick = onResume,
                    icon = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_resume),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconSize = 22.dp
                )
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_cancel),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DownloadStatus.COMPLETED -> {
                PrimaryTaskActionButton(
                    onClick = onOpenFile,
                    icon = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    iconSize = 22.dp
                )
                IconButton(
                    onClick = onShareFile,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.cd_share),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                PrimaryTaskActionButton(
                    onClick = onRetry,
                    icon = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_retry),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    iconSize = 20.dp
                )
                IconButton(
                    onClick = onShowError,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = stringResource(R.string.cd_error_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DownloadStatus.QUEUED -> {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_cancel),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryTaskActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    iconSize: Dp
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(containerColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1000) {
        String.format(Locale.US, "%.1f GB", mb / 1024)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

private fun parseDisplayError(errorMessage: String?): String {
    if (errorMessage.isNullOrBlank()) return "Terjadi kesalahan"
    val lower = errorMessage.lowercase()
    return when {
        lower.contains("no video could be found") -> "Bukan video / tweet tanpa video"
        lower.contains("video unavailable") -> "Video tidak tersedia / dihapus"
        lower.contains("private video") || lower.contains("this tweet is from a private account") -> "Video bersifat privat"
        lower.contains("sign in") || lower.contains("login required") -> "Perlu login / dibatasi usia"
        lower.contains("404") -> "Konten tidak ditemukan (404)"
        lower.contains("403") -> "Akses ditolak (403)"
        lower.contains("network") || lower.contains("timed out") || lower.contains("timeout") -> "Koneksi internet bermasalah"
        else -> {
            val afterError = errorMessage.substringAfter("ERROR: ")
            val cleanLine = afterError.substringAfter(": ").substringBefore("\n").trim()
            if (cleanLine.isNotBlank()) cleanLine.take(36) else errorMessage.substringBefore("\n").take(36)
        }
    }
}

private val previewDownloadingTask = DownloadTaskEntity(
    id = "1",
    url = "https://youtube.com/watch?v=demo",
    title = "Amazing Nature 4K HDR Documentary Sample",
    durationSeconds = 185L,
    formatLabel = "MP4 1080p",
    status = DownloadStatus.DOWNLOADING,
    progress = 68.4f,
    downloadedBytes = 45_200_000L,
    totalBytes = 120_000_000L,
    speed = "8.4 MB/s",
    eta = "00:09"
)

private val previewPausedTask = DownloadTaskEntity(
    id = "2",
    url = "https://youtube.com/watch?v=demo2",
    title = "Cyberpunk Music Mix 2026 Synthwave",
    durationSeconds = 3725L,
    formatLabel = "MP3",
    audioOnly = true,
    status = DownloadStatus.PAUSED,
    progress = 42.0f,
    downloadedBytes = 14_000_000L,
    totalBytes = 35_000_000L
)

private val previewCompletedTask = DownloadTaskEntity(
    id = "3",
    url = "https://youtube.com/watch?v=demo3",
    title = "Kotlin Jetpack Compose Clean Architecture Tutorial",
    formatLabel = "MP4 720p",
    status = DownloadStatus.COMPLETED,
    progress = 100f,
    downloadedBytes = 85_000_000L,
    totalBytes = 85_000_000L,
    outputPath = "/storage/emulated/0/Download/SwiftGrab/sample.mp4"
)

private val previewFailedTask = DownloadTaskEntity(
    id = "4",
    url = "https://youtube.com/watch?v=demo4",
    title = "Restricted or Private Video Title",
    formatLabel = "Auto",
    status = DownloadStatus.FAILED,
    progress = 12f,
    errorMessage = "HTTP Error 403: Forbidden - Private video or login required"
)

@Composable
private fun PreviewTaskCardWrapper(
    darkTheme: Boolean,
    task: DownloadTaskEntity
) {
    SwiftGrabTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(12.dp)) {
                DownloadTaskCard(
                    task = task,
                    onPause = {},
                    onResume = {},
                    onCancel = {},
                    onDelete = {},
                    onOpenFile = {},
                    onShareFile = {},
                    onRetry = {},
                    onShowError = {}
                )
            }
        }
    }
}

@Preview(name = "Downloading Dark", showBackground = true)
@Composable
private fun DownloadTaskCardDownloadingPreview() {
    PreviewTaskCardWrapper(darkTheme = true, task = previewDownloadingTask)
}

@Preview(name = "Downloading Light", showBackground = true)
@Composable
private fun DownloadTaskCardDownloadingLightPreview() {
    PreviewTaskCardWrapper(darkTheme = false, task = previewDownloadingTask)
}

@Preview(name = "Paused Dark", showBackground = true)
@Composable
private fun DownloadTaskCardPausedPreview() {
    PreviewTaskCardWrapper(darkTheme = true, task = previewPausedTask)
}

@Preview(name = "Paused Light", showBackground = true)
@Composable
private fun DownloadTaskCardPausedLightPreview() {
    PreviewTaskCardWrapper(darkTheme = false, task = previewPausedTask)
}

@Preview(name = "Completed Dark", showBackground = true)
@Composable
private fun DownloadTaskCardCompletedPreview() {
    PreviewTaskCardWrapper(darkTheme = true, task = previewCompletedTask)
}

@Preview(name = "Completed Light", showBackground = true)
@Composable
private fun DownloadTaskCardCompletedLightPreview() {
    PreviewTaskCardWrapper(darkTheme = false, task = previewCompletedTask)
}

@Preview(name = "Failed Dark", showBackground = true)
@Composable
private fun DownloadTaskCardFailedPreview() {
    PreviewTaskCardWrapper(darkTheme = true, task = previewFailedTask)
}

@Preview(name = "Failed Light", showBackground = true)
@Composable
private fun DownloadTaskCardFailedLightPreview() {
    PreviewTaskCardWrapper(darkTheme = false, task = previewFailedTask)
}
