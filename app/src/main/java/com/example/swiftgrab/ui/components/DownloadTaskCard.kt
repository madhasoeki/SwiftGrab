package com.example.swiftgrab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.swiftgrab.R
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
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
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Row 1: Thumbnail, Title, Format Badge
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

                    Spacer(modifier = Modifier.height(4.dp))

                    FormatBadge(label = task.formatLabel, audioOnly = task.audioOnly)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2: Progress Indicator
            val progressColor = when (task.status) {
                DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                DownloadStatus.FAILED, DownloadStatus.CANCELED -> MaterialTheme.colorScheme.error
                DownloadStatus.QUEUED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            }

            val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

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
                LinearProgressIndicator(
                    progress = { (task.progress / 100f).coerceIn(0f, 1f) },
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

            // Row 3: Tabular Metrics (Left) & Touch Action Controls (Right)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricsText(
                    task = task,
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
    Box(
        modifier = modifier
            .size(width = 64.dp, height = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                imageVector = if (audioOnly) Icons.Rounded.Audiotrack else Icons.Rounded.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
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
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (audioOnly) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        },
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (audioOnly) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MetricsText(
    task: DownloadTaskEntity,
    modifier: Modifier = Modifier
) {
    val text = when (task.status) {
        DownloadStatus.DOWNLOADING -> {
            val progressInt = task.progress.toInt().coerceIn(0, 100)
            val downloaded = formatBytes(task.downloadedBytes)
            val total = if (task.totalBytes > 0L) formatBytes(task.totalBytes) else "--"
            val speedPart = if (task.speed.isNotBlank()) " • ${task.speed}" else ""
            val etaPart = if (task.eta.isNotBlank()) " • ETA ${task.eta}" else ""
            "$downloaded / $total • $progressInt%$speedPart$etaPart"
        }
        DownloadStatus.PAUSED -> {
            val progressInt = task.progress.toInt().coerceIn(0, 100)
            "Dijeda • $progressInt%"
        }
        DownloadStatus.COMPLETED -> {
            val size = if (task.totalBytes > 0L) formatBytes(task.totalBytes) else ""
            if (size.isNotBlank()) "Selesai • $size" else "Selesai"
        }
        DownloadStatus.FAILED -> {
            val err = parseDisplayError(task.errorMessage)
            "Gagal • $err"
        }
        DownloadStatus.CANCELED -> "Dibatalkan"
        DownloadStatus.QUEUED -> "Menunggu antrean…"
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
        color = when (task.status) {
            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
            DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
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
                IconButton(
                    onClick = onPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = stringResource(R.string.cd_pause),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_cancel),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            DownloadStatus.PAUSED -> {
                IconButton(
                    onClick = onResume,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_resume),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_cancel),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            DownloadStatus.COMPLETED -> {
                IconButton(
                    onClick = onOpenFile,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
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
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DownloadStatus.FAILED, DownloadStatus.CANCELED -> {
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.cd_retry),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
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
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
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
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
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

// Previews for all 4 states per DESIGN.md checklist
@Preview(showBackground = true)
@Composable
private fun DownloadTaskCardDownloadingPreview() {
    SwiftGrabTheme(darkTheme = true) {
        DownloadTaskCard(
            task = DownloadTaskEntity(
                id = "1",
                url = "https://youtube.com/watch?v=demo",
                title = "Amazing Nature 4K HDR Documentary Sample",
                formatLabel = "MP4 1080p",
                status = DownloadStatus.DOWNLOADING,
                progress = 68.4f,
                downloadedBytes = 45_200_000L,
                totalBytes = 120_000_000L,
                speed = "8.4 MB/s",
                eta = "00:09"
            ),
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

@Preview(showBackground = true)
@Composable
private fun DownloadTaskCardPausedPreview() {
    SwiftGrabTheme(darkTheme = true) {
        DownloadTaskCard(
            task = DownloadTaskEntity(
                id = "2",
                url = "https://youtube.com/watch?v=demo2",
                title = "Cyberpunk Music Mix 2026 Synthwave",
                formatLabel = "MP3",
                audioOnly = true,
                status = DownloadStatus.PAUSED,
                progress = 42.0f,
                downloadedBytes = 14_000_000L,
                totalBytes = 35_000_000L
            ),
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

@Preview(showBackground = true)
@Composable
private fun DownloadTaskCardCompletedPreview() {
    SwiftGrabTheme(darkTheme = true) {
        DownloadTaskCard(
            task = DownloadTaskEntity(
                id = "3",
                url = "https://youtube.com/watch?v=demo3",
                title = "Kotlin Jetpack Compose Clean Architecture Tutorial",
                formatLabel = "MP4 720p",
                status = DownloadStatus.COMPLETED,
                progress = 100f,
                downloadedBytes = 85_000_000L,
                totalBytes = 85_000_000L,
                outputPath = "/storage/emulated/0/Download/SwiftGrab/sample.mp4"
            ),
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

@Preview(showBackground = true)
@Composable
private fun DownloadTaskCardFailedPreview() {
    SwiftGrabTheme(darkTheme = true) {
        DownloadTaskCard(
            task = DownloadTaskEntity(
                id = "4",
                url = "https://youtube.com/watch?v=demo4",
                title = "Restricted or Private Video Title",
                formatLabel = "Auto",
                status = DownloadStatus.FAILED,
                progress = 12f,
                errorMessage = "HTTP Error 403: Forbidden - Private video or login required"
            ),
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
