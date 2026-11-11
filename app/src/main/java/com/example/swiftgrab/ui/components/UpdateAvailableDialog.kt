package com.example.swiftgrab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    val isDownloading = downloadState is ApkDownloadState.Downloading
    val hasApkAsset = !updateInfo.apkDownloadUrl.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.update_dialog_title, updateInfo.versionName),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
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
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.update_dialog_changelog_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp, max = 210.dp)
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

                when (downloadState) {
                    is ApkDownloadState.Downloading -> {
                        Spacer(modifier = Modifier.height(14.dp))
                        val percent = (downloadState.progress * 100f).toInt().coerceIn(0, 100)
                        val downloadedLabel = formatMegabytes(downloadState.downloadedBytes)
                        val totalLabel = if (downloadState.totalBytes > 0L) {
                            " / ${formatMegabytes(downloadState.totalBytes)}"
                        } else {
                            ""
                        }

                        if (downloadState.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { downloadState.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainer,
                                strokeCap = StrokeCap.Round
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainer,
                                strokeCap = StrokeCap.Round
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.update_downloading_desc),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (downloadState.totalBytes > 0L) {
                                    "$percent% ($downloadedLabel$totalLabel)"
                                } else {
                                    downloadedLabel
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is ApkDownloadState.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusNoticeBanner(
                            icon = Icons.Rounded.CheckCircle,
                            text = stringResource(R.string.update_ready_to_install_desc),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    is ApkDownloadState.PermissionRequired -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusNoticeBanner(
                            icon = Icons.Rounded.Security,
                            text = stringResource(R.string.update_permission_required_desc),
                            containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    is ApkDownloadState.Failed -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusNoticeBanner(
                            icon = Icons.Rounded.ErrorOutline,
                            text = downloadState.message,
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    }
                    ApkDownloadState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is ApkDownloadState.ReadyToInstall -> {
                    Button(
                        onClick = onInstallNow,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.InstallMobile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.update_dialog_install_now),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is ApkDownloadState.PermissionRequired -> {
                    Button(
                        onClick = onOpenInstallPermissionSettings,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.update_dialog_allow_install),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    if (hasApkAsset) {
                        Button(
                            onClick = onDownloadAndInstall,
                            enabled = !isDownloading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (downloadState is ApkDownloadState.Failed) {
                                    stringResource(R.string.update_dialog_retry)
                                } else {
                                    stringResource(R.string.update_dialog_download_install)
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = onOpenGitHub,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.update_dialog_open_github),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_dialog_later))
                }
                if (hasApkAsset) {
                    TextButton(onClick = onOpenGitHub) {
                        Text(stringResource(R.string.update_dialog_open_github))
                    }
                }
            }
        }
    )
}

@Composable
private fun StatusNoticeBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
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

@Preview
@Composable
private fun UpdateAvailableDialogPreview() {
    SwiftGrabTheme(darkTheme = true) {
        UpdateAvailableDialog(
            updateInfo = AppUpdateInfo(
                versionName = "0.0.3",
                tagName = "v0.0.3",
                changelog = "### Pembaruan Terbaru\n• Fitur In-App Auto-Updater langsung dari GitHub Releases\n• Perbaikan penyimpanan folder publik Download/SwiftGrab untuk Xiaomi HyperOS\n• Pemindaian MediaStore otomatis agar video/audio langsung muncul di Galeri",
                htmlUrl = "https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.3",
                apkDownloadUrl = "https://github.com/madhasoeki/SwiftGrab/releases/download/v0.0.3/SwiftGrab-v0.0.3-arm64-v8a.apk",
                apkFileName = "SwiftGrab-v0.0.3-arm64-v8a.apk",
                apkSizeBytes = 58_400_000L
            ),
            currentVersion = "0.0.2",
            downloadState = ApkDownloadState.Idle,
            onDownloadAndInstall = {},
            onInstallNow = {},
            onOpenInstallPermissionSettings = {},
            onOpenGitHub = {},
            onDismiss = {}
        )
    }
}
