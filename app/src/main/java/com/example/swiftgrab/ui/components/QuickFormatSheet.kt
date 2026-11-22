package com.example.swiftgrab.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.swiftgrab.R
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.service.MediaMetadata
import com.example.swiftgrab.service.MediaMetadataExtractor
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import com.example.swiftgrab.util.UrlValidator

sealed interface QuickFormatMetadataState {
    data object Loading : QuickFormatMetadataState
    data class Loaded(val metadata: MediaMetadata) : QuickFormatMetadataState
    data object Unavailable : QuickFormatMetadataState
}

/**
 * Compact bottom sheet displayed over external apps in Quick Format Sheet mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickFormatSheet(
    url: String,
    selectedPreset: DownloadPreset,
    metadataState: QuickFormatMetadataState = QuickFormatMetadataState.Unavailable,
    onSelectPreset: (DownloadPreset) -> Unit,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline)
        },
        modifier = modifier
    ) {
        QuickFormatSheetContent(
            url = url,
            selectedPreset = selectedPreset,
            metadataState = metadataState,
            onSelectPreset = onSelectPreset,
            onStartDownload = onStartDownload,
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun QuickFormatSheetContent(
    url: String,
    selectedPreset: DownloadPreset,
    metadataState: QuickFormatMetadataState,
    onSelectPreset: (DownloadPreset) -> Unit,
    onStartDownload: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val domain = UrlValidator.extractDomain(url)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        // Header: Brand icon tile, title & detected link domain
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            SwiftGrabAppIconTile(size = 42.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.format_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = domain.ifBlank { url },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        MediaPreviewCard(
            url = url,
            domain = domain,
            metadataState = metadataState
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Presets List
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            DownloadPreset.entries.forEach { preset ->
                SwiftGrabPresetOptionCard(
                    preset = preset,
                    isSelected = preset == selectedPreset,
                    onClick = { onSelectPreset(preset) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_cancel),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onStartDownload,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1.5f)
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_start_download),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun MediaPreviewCard(
    url: String,
    domain: String,
    metadataState: QuickFormatMetadataState,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            when (metadataState) {
                is QuickFormatMetadataState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(width = 96.dp, height = 54.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.format_sheet_loading_metadata),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                is QuickFormatMetadataState.Loaded -> {
                    val metadata = metadataState.metadata
                    StatefulPreviewThumbnail(thumbnailUrl = metadata.thumbnailUrl)

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = metadata.title?.takeIf { it.isNotBlank() } ?: url,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (domain.isNotBlank()) {
                                Text(
                                    text = domain,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            val durationLabel = MediaMetadataExtractor.formatDuration(metadata.durationSeconds)
                            if (durationLabel.isNotEmpty()) {
                                DurationBadge(durationLabel = durationLabel)
                            }
                        }
                    }
                }
                is QuickFormatMetadataState.Unavailable -> {
                    StatefulPreviewThumbnail(thumbnailUrl = null)

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.format_sheet_metadata_unavailable),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationBadge(
    durationLabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
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
                text = durationLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatefulPreviewThumbnail(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
) {
    var imageLoaded by remember(thumbnailUrl) { mutableStateOf(false) }
    var imageLoadFailed by remember(thumbnailUrl) { mutableStateOf(false) }

    PreviewThumbnail(
        thumbnailUrl = thumbnailUrl,
        imageLoaded = imageLoaded,
        imageLoadFailed = imageLoadFailed,
        onImageSuccess = {
            imageLoaded = true
            imageLoadFailed = false
        },
        onImageError = {
            imageLoaded = false
            imageLoadFailed = true
        },
        modifier = modifier
    )
}

@Composable
internal fun PreviewThumbnail(
    thumbnailUrl: String?,
    imageLoaded: Boolean,
    imageLoadFailed: Boolean,
    onImageSuccess: () -> Unit,
    onImageError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.size(width = 96.dp, height = 54.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (!thumbnailUrl.isNullOrBlank() && !imageLoadFailed) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = { onImageSuccess() },
                    onError = { onImageError() },
                    modifier = Modifier.matchParentSize()
                )
            }
            if (thumbnailUrl.isNullOrBlank() || imageLoadFailed || !imageLoaded) {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickFormatSheetPreviewContainer(
    darkTheme: Boolean,
    url: String,
    selectedPreset: DownloadPreset,
    metadataState: QuickFormatMetadataState
) {
    SwiftGrabTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            QuickFormatSheetContent(
                url = url,
                selectedPreset = selectedPreset,
                metadataState = metadataState,
                onSelectPreset = {},
                onStartDownload = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "QuickFormatSheet Loaded Dark", showBackground = true)
@Composable
private fun QuickFormatSheetLoadedPreview() {
    QuickFormatSheetPreviewContainer(
        darkTheme = true,
        url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        selectedPreset = DownloadPreset.BEST_VIDEO,
        metadataState = QuickFormatMetadataState.Loaded(
            MediaMetadata(
                title = "Jetpack Compose Material 3 Adaptive Layout Tutorial",
                thumbnailUrl = null,
                durationSeconds = 754L
            )
        )
    )
}

@Preview(name = "QuickFormatSheet Loaded Light", showBackground = true)
@Composable
private fun QuickFormatSheetLoadedLightPreview() {
    QuickFormatSheetPreviewContainer(
        darkTheme = false,
        url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        selectedPreset = DownloadPreset.BEST_VIDEO,
        metadataState = QuickFormatMetadataState.Loaded(
            MediaMetadata(
                title = "Jetpack Compose Material 3 Adaptive Layout Tutorial",
                thumbnailUrl = null,
                durationSeconds = 754L
            )
        )
    )
}

@Preview(name = "QuickFormatSheet Loading Dark", showBackground = true)
@Composable
private fun QuickFormatSheetLoadingPreview() {
    QuickFormatSheetPreviewContainer(
        darkTheme = true,
        url = "https://x.com/example/status/1234567890",
        selectedPreset = DownloadPreset.DATA_SAVER,
        metadataState = QuickFormatMetadataState.Loading
    )
}

@Preview(name = "QuickFormatSheet Loading Light", showBackground = true)
@Composable
private fun QuickFormatSheetLoadingLightPreview() {
    QuickFormatSheetPreviewContainer(
        darkTheme = false,
        url = "https://x.com/example/status/1234567890",
        selectedPreset = DownloadPreset.DATA_SAVER,
        metadataState = QuickFormatMetadataState.Loading
    )
}

@Preview(name = "QuickFormatSheet Unavailable Dark", showBackground = true)
@Composable
private fun QuickFormatSheetUnavailablePreview() {
    QuickFormatSheetPreviewContainer(
        darkTheme = true,
        url = "https://example.com/media/clip.mp4",
        selectedPreset = DownloadPreset.AUDIO_ONLY,
        metadataState = QuickFormatMetadataState.Unavailable
    )
}

@Preview(name = "QuickFormatSheet Unavailable Light", showBackground = true)
@Composable
private fun QuickFormatSheetUnavailableLightPreview() {
    QuickFormatSheetPreviewContainer(
        darkTheme = false,
        url = "https://example.com/media/clip.mp4",
        selectedPreset = DownloadPreset.AUDIO_ONLY,
        metadataState = QuickFormatMetadataState.Unavailable
    )
}
