package com.example.swiftgrab.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.swiftgrab.R
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.data.prefs.SwiftGrabSettings
import com.example.swiftgrab.service.DownloadFileManager
import com.example.swiftgrab.ui.AppUpdateState
import com.example.swiftgrab.ui.YtDlpUpdateState
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import com.example.swiftgrab.update.AppUpdateInfo
import com.example.swiftgrab.util.StorageHelper

/**
 * Lembar pengaturan aplikasi untuk mengonfigurasi preferensi unduhan, format default,
 * clipboard auto-start, pembaruan yt-dlp, dan autentikasi akun X.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    settings: SwiftGrabSettings,
    ytDlpVersion: String,
    updateState: YtDlpUpdateState,
    isXLoggedIn: Boolean,
    appUpdateState: AppUpdateState,
    currentAppVersion: String,
    onToggleInstantShare: (Boolean) -> Unit,
    onSelectDefaultPreset: (DownloadPreset) -> Unit,
    onToggleAutoStartClipboard: (Boolean) -> Unit,
    onCheckYtDlpUpdate: () -> Unit,
    onCheckAppUpdate: () -> Unit,
    onShowUpdateDialog: () -> Unit,
    onOpenXLogin: () -> Unit,
    onLogoutX: () -> Unit,
    onSelectStorageDirUri: (Uri) -> Unit,
    onResetStorageDir: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectStorageDirUri(uri)
        }
    }

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
        SettingsBottomSheetContent(
            settings = settings,
            ytDlpVersion = ytDlpVersion,
            updateState = updateState,
            isXLoggedIn = isXLoggedIn,
            appUpdateState = appUpdateState,
            currentAppVersion = currentAppVersion,
            onToggleInstantShare = onToggleInstantShare,
            onSelectDefaultPreset = onSelectDefaultPreset,
            onToggleAutoStartClipboard = onToggleAutoStartClipboard,
            onCheckYtDlpUpdate = onCheckYtDlpUpdate,
            onCheckAppUpdate = onCheckAppUpdate,
            onShowUpdateDialog = onShowUpdateDialog,
            onOpenXLogin = onOpenXLogin,
            onLogoutX = onLogoutX,
            onPickStorageDir = { folderPickerLauncher.launch(null) },
            onResetStorageDir = onResetStorageDir,
            onDismiss = onDismiss
        )
    }
}

@Composable
internal fun SettingsBottomSheetContent(
    settings: SwiftGrabSettings,
    ytDlpVersion: String,
    updateState: YtDlpUpdateState,
    isXLoggedIn: Boolean,
    appUpdateState: AppUpdateState,
    currentAppVersion: String,
    onToggleInstantShare: (Boolean) -> Unit,
    onSelectDefaultPreset: (DownloadPreset) -> Unit,
    onToggleAutoStartClipboard: (Boolean) -> Unit,
    onCheckYtDlpUpdate: () -> Unit,
    onCheckAppUpdate: () -> Unit,
    onShowUpdateDialog: () -> Unit,
    onOpenXLogin: () -> Unit,
    onLogoutX: () -> Unit,
    onPickStorageDir: () -> Unit,
    onResetStorageDir: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    defaultStorageDisplayPath: String = remember { DownloadFileManager.getDisplayDefaultDownloadPath() }
) {
    val storageDir = settings.customDownloadPath
        ?: settings.customDownloadUri?.let { StorageHelper.getDisplayLabelFromTreeUri(it.toUri()) }
        ?: defaultStorageDisplayPath
    val isCustomStorage = settings.customDownloadPath != null || settings.customDownloadUri != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        BrandIdentityHeaderCard(
            currentAppVersion = currentAppVersion,
            onDismiss = onDismiss
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Section 1: Share Mode (Instant 1-Tap vs Quick Format Sheet)
        SectionHeader(
            title = stringResource(R.string.settings_share_mode),
            icon = Icons.Rounded.FlashOn
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleCard(
            title = stringResource(R.string.settings_one_tap),
            description = if (settings.instantShareMode) {
                stringResource(R.string.settings_one_tap_desc)
            } else {
                stringResource(R.string.settings_format_sheet_desc)
            },
            checked = settings.instantShareMode,
            onCheckedChange = onToggleInstantShare
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section 2: Default Preset
        SectionHeader(
            title = stringResource(R.string.settings_default_preset),
            icon = Icons.Rounded.HighQuality
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
        ) {
            DownloadPreset.entries.forEach { preset ->
                SwiftGrabPresetOptionCard(
                    preset = preset,
                    isSelected = preset == settings.defaultPreset,
                    onClick = { onSelectDefaultPreset(preset) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Section 3: Auto-Clipboard
        SectionHeader(
            title = stringResource(R.string.settings_auto_clipboard),
            icon = Icons.Rounded.ContentPaste
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsToggleCard(
            title = stringResource(R.string.settings_auto_clipboard),
            description = stringResource(R.string.settings_auto_clipboard_desc),
            checked = settings.autoStartClipboard,
            onCheckedChange = onToggleAutoStartClipboard
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section 4: Akun X / Twitter
        SectionHeader(
            title = stringResource(R.string.x_account_section_title),
            icon = Icons.Rounded.Security
        )
        Spacer(modifier = Modifier.height(8.dp))
        XAccountCard(
            isXLoggedIn = isXLoggedIn,
            onOpenXLogin = onOpenXLogin,
            onLogoutX = onLogoutX
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section 5: Storage Directory
        SectionHeader(
            title = stringResource(R.string.settings_storage_dir),
            icon = Icons.Rounded.Folder
        )
        Spacer(modifier = Modifier.height(8.dp))
        StorageDirectoryCard(
            storageDir = storageDir,
            isCustomStorage = isCustomStorage,
            onPickStorageDir = onPickStorageDir,
            onResetStorageDir = onResetStorageDir
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section 6: yt-dlp Binary Update
        SectionHeader(
            title = stringResource(R.string.settings_ytdlp_update),
            icon = Icons.Rounded.SystemUpdate
        )
        Spacer(modifier = Modifier.height(8.dp))
        YtDlpUpdateCard(
            ytDlpVersion = ytDlpVersion,
            updateState = updateState,
            onCheckYtDlpUpdate = onCheckYtDlpUpdate
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Section 7: In-App Auto-Updater (SwiftGrab Application)
        SectionHeader(
            title = stringResource(R.string.settings_app_update),
            icon = Icons.Rounded.SystemUpdate
        )
        Spacer(modifier = Modifier.height(8.dp))
        AppUpdateCard(
            currentAppVersion = currentAppVersion,
            appUpdateState = appUpdateState,
            onCheckAppUpdate = onCheckAppUpdate,
            onShowUpdateDialog = onShowUpdateDialog
        )
    }
}

@Composable
private fun BrandIdentityHeaderCard(
    currentAppVersion: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appName = stringResource(R.string.app_name)

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
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp)
        ) {
            SwiftGrabAppIconTile(size = 44.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.weight(1f)
            ) {
                SwiftGrabWordmark(
                    height = 17.dp,
                    modifier = Modifier.semantics {
                        contentDescription = appName
                        heading()
                    }
                )
                Text(
                    text = "v$currentAppVersion • Zero-Paste Media Downloader",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.btn_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
private fun SettingsToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = swiftGrabSwitchColors()
            )
        }
    }
}

@Composable
private fun swiftGrabSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline
)

@Composable
private fun XAccountCard(
    isXLoggedIn: Boolean,
    onOpenXLogin: () -> Unit,
    onLogoutX: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isXLoggedIn) {
                        stringResource(R.string.x_account_status_logged_in)
                    } else {
                        stringResource(R.string.x_account_status_not_logged_in)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isXLoggedIn) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = CircleShape,
                    color = if (isXLoggedIn) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    border = if (isXLoggedIn) {
                        null
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    }
                ) {
                    Text(
                        text = if (isXLoggedIn) "Aktif" else "Tamu",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isXLoggedIn) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isXLoggedIn) {
                    stringResource(R.string.x_account_desc_logged_in)
                } else {
                    stringResource(R.string.x_account_desc_not_logged_in)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (!isXLoggedIn) {
                Button(
                    onClick = onOpenXLogin,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_x_login),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = onOpenXLogin,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Login Ulang",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Button(
                        onClick = onLogoutX,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_x_logout),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageDirectoryCard(
    storageDir: String,
    isCustomStorage: Boolean,
    onPickStorageDir: () -> Unit,
    onResetStorageDir: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onPickStorageDir,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (isCustomStorage) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = storageDir,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isCustomStorage) {
                        stringResource(R.string.settings_storage_dir_custom)
                    } else {
                        stringResource(R.string.settings_storage_dir_hint)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCustomStorage) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onResetStorageDir,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = stringResource(R.string.settings_storage_dir_reset),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun YtDlpUpdateCard(
    ytDlpVersion: String,
    updateState: YtDlpUpdateState,
    onCheckYtDlpUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_ytdlp_version, ytDlpVersion),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (updateState) {
                is YtDlpUpdateState.Updating -> {
                    CheckingProgressRow(message = stringResource(R.string.ytdlp_updating))
                }
                is YtDlpUpdateState.Success -> {
                    StatusFeedbackRow(
                        icon = Icons.Rounded.CheckCircle,
                        message = stringResource(R.string.ytdlp_update_success, updateState.version),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                is YtDlpUpdateState.Error -> {
                    StatusFeedbackRow(
                        icon = Icons.Rounded.ErrorOutline,
                        message = stringResource(R.string.ytdlp_update_error, updateState.message),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                YtDlpUpdateState.Idle -> Unit
            }

            if (updateState !is YtDlpUpdateState.Updating) {
                MaintenanceCheckButton(
                    text = stringResource(R.string.btn_check_ytdlp_update),
                    onClick = onCheckYtDlpUpdate,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AppUpdateCard(
    currentAppVersion: String,
    appUpdateState: AppUpdateState,
    onCheckAppUpdate: () -> Unit,
    onShowUpdateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_app_version, currentAppVersion),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            when (appUpdateState) {
                is AppUpdateState.Checking -> {
                    CheckingProgressRow(message = stringResource(R.string.app_update_checking))
                }
                is AppUpdateState.UpdateAvailable -> {
                    Button(
                        onClick = onShowUpdateDialog,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Update tersedia: v${appUpdateState.info.versionName} (Ketuk untuk melihat)",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                is AppUpdateState.UpToDate -> {
                    StatusFeedbackRow(
                        icon = Icons.Rounded.CheckCircle,
                        message = stringResource(R.string.app_update_up_to_date, appUpdateState.version),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                is AppUpdateState.Error -> {
                    StatusFeedbackRow(
                        icon = Icons.Rounded.ErrorOutline,
                        message = stringResource(R.string.app_update_error, appUpdateState.message),
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                AppUpdateState.Idle -> Unit
            }

            if (appUpdateState !is AppUpdateState.Checking) {
                MaintenanceCheckButton(
                    text = stringResource(R.string.btn_check_app_update),
                    onClick = onCheckAppUpdate,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun MaintenanceCheckButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun CheckingProgressRow(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatusFeedbackRow(
    icon: ImageVector,
    message: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsBottomSheetPreviewContainer(
    darkTheme: Boolean,
    settings: SwiftGrabSettings,
    updateState: YtDlpUpdateState,
    isXLoggedIn: Boolean,
    appUpdateState: AppUpdateState
) {
    SwiftGrabTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            SettingsBottomSheetContent(
                settings = settings,
                ytDlpVersion = "2026.03.17",
                updateState = updateState,
                isXLoggedIn = isXLoggedIn,
                appUpdateState = appUpdateState,
                currentAppVersion = "0.0.5",
                onToggleInstantShare = {},
                onSelectDefaultPreset = {},
                onToggleAutoStartClipboard = {},
                onCheckYtDlpUpdate = {},
                onCheckAppUpdate = {},
                onShowUpdateDialog = {},
                onOpenXLogin = {},
                onLogoutX = {},
                onPickStorageDir = {},
                onResetStorageDir = {},
                onDismiss = {},
                defaultStorageDisplayPath = "/storage/emulated/0/Download/SwiftGrab"
            )
        }
    }
}

@Preview(name = "SettingsBottomSheet Default Dark", showBackground = true, heightDp = 1500)
@Composable
private fun SettingsBottomSheetDefaultDarkPreview() {
    SettingsBottomSheetPreviewContainer(
        darkTheme = true,
        settings = SwiftGrabSettings(),
        updateState = YtDlpUpdateState.Idle,
        isXLoggedIn = false,
        appUpdateState = AppUpdateState.Idle
    )
}

@Preview(name = "SettingsBottomSheet Default Light", showBackground = true, heightDp = 1500)
@Composable
private fun SettingsBottomSheetDefaultLightPreview() {
    SettingsBottomSheetPreviewContainer(
        darkTheme = false,
        settings = SwiftGrabSettings(),
        updateState = YtDlpUpdateState.Idle,
        isXLoggedIn = false,
        appUpdateState = AppUpdateState.Idle
    )
}

@Preview(name = "SettingsBottomSheet Active Dark", showBackground = true, heightDp = 1620)
@Composable
private fun SettingsBottomSheetActiveDarkPreview() {
    SettingsBottomSheetPreviewContainer(
        darkTheme = true,
        settings = SwiftGrabSettings(
            instantShareMode = false,
            defaultPreset = DownloadPreset.DATA_SAVER,
            autoStartClipboard = true,
            customDownloadPath = "/storage/emulated/0/Movies/SwiftGrab"
        ),
        updateState = YtDlpUpdateState.Success("2026.03.18"),
        isXLoggedIn = true,
        appUpdateState = AppUpdateState.UpdateAvailable(
            AppUpdateInfo(
                versionName = "0.0.6",
                tagName = "v0.0.6",
                changelog = "Pembaruan desain Meteor Catch",
                htmlUrl = "https://github.com/example/SwiftGrab/releases/tag/v0.0.6",
                apkDownloadUrl = null,
                apkFileName = null
            )
        )
    )
}

@Preview(name = "SettingsBottomSheet Active Light", showBackground = true, heightDp = 1620)
@Composable
private fun SettingsBottomSheetActiveLightPreview() {
    SettingsBottomSheetPreviewContainer(
        darkTheme = false,
        settings = SwiftGrabSettings(
            instantShareMode = false,
            defaultPreset = DownloadPreset.AUDIO_ONLY,
            autoStartClipboard = true,
            customDownloadPath = "/storage/emulated/0/Music/SwiftGrab"
        ),
        updateState = YtDlpUpdateState.Success("2026.03.18"),
        isXLoggedIn = true,
        appUpdateState = AppUpdateState.UpdateAvailable(
            AppUpdateInfo(
                versionName = "0.0.6",
                tagName = "v0.0.6",
                changelog = "Pembaruan desain Meteor Catch",
                htmlUrl = "https://github.com/example/SwiftGrab/releases/tag/v0.0.6",
                apkDownloadUrl = null,
                apkFileName = null
            )
        )
    )
}
