package com.example.swiftgrab.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.swiftgrab.R
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import com.example.swiftgrab.update.ApkDownloadState
import com.example.swiftgrab.update.AppUpdateInfo
import java.util.Locale

/**
 * Dialog Material 3 yang menampilkan informasi versi baru SwiftGrab dari GitHub Releases,
 * changelog yang dapat digulir, progres pengunduhan APK via DownloadManager, dan tombol aksi
 * untuk mengunduh & memasang langsung maupun membuka halaman GitHub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableDialog(
    updateInfo: AppUpdateInfo,
    currentVersion: String,
    downloadState: ApkDownloadState,
    onDownloadAndInstall: () -> Unit,
    onInstallNow: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onOpenGitHub: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveDownloadState = if (downloadState.matchesRelease(updateInfo)) {
        downloadState
    } else {
        ApkDownloadState.Idle
    }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        UpdateAvailableDialogContent(
            updateInfo = updateInfo,
            currentVersion = currentVersion,
            effectiveDownloadState = effectiveDownloadState,
            onDownloadAndInstall = onDownloadAndInstall,
            onInstallNow = onInstallNow,
            onOpenInstallPermissionSettings = onOpenInstallPermissionSettings,
            onOpenGitHub = onOpenGitHub,
            onDismiss = onDismiss
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UpdateAvailableDialogContent(
    updateInfo: AppUpdateInfo,
    currentVersion: String,
    effectiveDownloadState: ApkDownloadState,
    onDownloadAndInstall: () -> Unit,
    onInstallNow: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onOpenGitHub: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDownloading = effectiveDownloadState is ApkDownloadState.Downloading
    val hasApkAsset = !updateInfo.apkDownloadUrl.isNullOrBlank()
    val buttonShape = RoundedCornerShape(12.dp)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SwiftGrabAppIconTile(size = 52.dp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.update_dialog_title, updateInfo.versionName),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                        )
                    ) {
                        val sizeText = if (updateInfo.apkSizeBytes > 0L) {
                            " • ${formatMegabytes(updateInfo.apkSizeBytes)}"
                        } else {
                            ""
                        }
                        Text(
                            text = "v$currentVersion → v${updateInfo.versionName}$sizeText",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.update_dialog_changelog_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp, max = 190.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = updateInfo.changelog,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                when (effectiveDownloadState) {
                    is ApkDownloadState.Downloading -> {
                        Spacer(modifier = Modifier.height(14.dp))
                        val percent = (effectiveDownloadState.progress * 100f).toInt().coerceIn(0, 100)
                        val downloadedLabel = formatMegabytes(effectiveDownloadState.downloadedBytes)
                        val totalLabel = if (effectiveDownloadState.totalBytes > 0L) {
                            " / ${formatMegabytes(effectiveDownloadState.totalBytes)}"
                        } else {
                            ""
                        }

                        if (effectiveDownloadState.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { effectiveDownloadState.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                strokeCap = StrokeCap.Round
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.update_downloading_desc),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (effectiveDownloadState.totalBytes > 0L) {
                                    "$percent% ($downloadedLabel$totalLabel)"
                                } else {
                                    downloadedLabel
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    is ApkDownloadState.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusNoticeBanner(
                            icon = Icons.Rounded.CheckCircle,
                            text = stringResource(R.string.update_ready_to_install_desc),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    is ApkDownloadState.PermissionRequired -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusNoticeBanner(
                            icon = Icons.Rounded.Security,
                            text = stringResource(R.string.update_permission_required_desc),
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                            borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.30f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    is ApkDownloadState.Failed -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusNoticeBanner(
                            icon = Icons.Rounded.ErrorOutline,
                            text = effectiveDownloadState.message,
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.30f),
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    ApkDownloadState.Idle -> Unit
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when (effectiveDownloadState) {
                is ApkDownloadState.ReadyToInstall -> {
                    Button(
                        onClick = onInstallNow,
                        shape = buttonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.update_dialog_install_now),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                is ApkDownloadState.PermissionRequired -> {
                    Button(
                        onClick = onOpenInstallPermissionSettings,
                        shape = buttonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.update_dialog_allow_install),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                else -> {
                    if (hasApkAsset) {
                        Button(
                            onClick = onDownloadAndInstall,
                            enabled = !isDownloading,
                            shape = buttonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (effectiveDownloadState is ApkDownloadState.Failed) {
                                    stringResource(R.string.update_dialog_retry)
                                } else {
                                    stringResource(R.string.update_dialog_download_install)
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    } else {
                        Button(
                            onClick = onOpenGitHub,
                            shape = buttonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.update_dialog_open_github),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onDismiss,
                    shape = buttonShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_dialog_later),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                if (hasApkAsset) {
                    TextButton(
                        onClick = onOpenGitHub,
                        shape = buttonShape,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.update_dialog_open_github),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusNoticeBanner(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    borderColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
        }
    }
}

private fun formatMegabytes(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}

private val previewUpdateInfo = AppUpdateInfo(
    versionName = "0.0.3",
    tagName = "v0.0.3",
    changelog = "### Pembaruan Terbaru\n• Fitur In-App Auto-Updater langsung dari GitHub Releases\n• Perbaikan penyimpanan folder publik Download/SwiftGrab untuk Xiaomi HyperOS\n• Pemindaian MediaStore otomatis agar video/audio langsung muncul di Galeri",
    htmlUrl = "https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.3",
    apkDownloadUrl = "https://github.com/madhasoeki/SwiftGrab/releases/download/v0.0.3/SwiftGrab-v0.0.3-arm64-v8a.apk",
    apkFileName = "SwiftGrab-v0.0.3-arm64-v8a.apk",
    apkSizeBytes = 58_400_000L
)

@Composable
private fun PreviewUpdateDialogWrapper(
    darkTheme: Boolean,
    downloadState: ApkDownloadState
) {
    SwiftGrabTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                UpdateAvailableDialogContent(
                    updateInfo = previewUpdateInfo,
                    currentVersion = "0.0.2",
                    effectiveDownloadState = downloadState,
                    onDownloadAndInstall = {},
                    onInstallNow = {},
                    onOpenInstallPermissionSettings = {},
                    onOpenGitHub = {},
                    onDismiss = {}
                )
            }
        }
    }
}

@Preview(name = "Update Dialog Idle Dark", showBackground = true)
@Composable
private fun UpdateAvailableDialogIdleDarkPreview() {
    PreviewUpdateDialogWrapper(
        darkTheme = true,
        downloadState = ApkDownloadState.Idle
    )
}

@Preview(name = "Update Dialog Idle Light", showBackground = true)
@Composable
private fun UpdateAvailableDialogIdleLightPreview() {
    PreviewUpdateDialogWrapper(
        darkTheme = false,
        downloadState = ApkDownloadState.Idle
    )
}

@Preview(name = "Update Dialog Downloading Dark", showBackground = true)
@Composable
private fun UpdateAvailableDialogDownloadingDarkPreview() {
    PreviewUpdateDialogWrapper(
        darkTheme = true,
        downloadState = ApkDownloadState.Downloading(
            downloadId = 1L,
            releaseTag = "v0.0.3",
            progress = 0.62f,
            downloadedBytes = 36_200_000L,
            totalBytes = 58_400_000L
        )
    )
}

@Preview(name = "Update Dialog Downloading Light", showBackground = true)
@Composable
private fun UpdateAvailableDialogDownloadingLightPreview() {
    PreviewUpdateDialogWrapper(
        darkTheme = false,
        downloadState = ApkDownloadState.Downloading(
            downloadId = 1L,
            releaseTag = "v0.0.3",
            progress = 0.62f,
            downloadedBytes = 36_200_000L,
            totalBytes = 58_400_000L
        )
    )
}
