package com.example.swiftgrab.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.swiftgrab.R
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.ui.components.ClipboardSuggestionBanner
import com.example.swiftgrab.ui.components.DownloadTaskCard
import com.example.swiftgrab.ui.components.SettingsBottomSheet
import com.example.swiftgrab.ui.components.XLoginDialog
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import com.example.swiftgrab.util.UrlValidator

@Composable
fun MainRoute(
    viewModel: DownloadViewModel,
    onOpenFile: (String) -> Unit,
    onShareFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isXLoginDialogOpen by rememberSaveable { mutableStateOf(false) }
    var isManualDialogOpen by rememberSaveable { mutableStateOf(false) }
    var selectedErrorTask by remember { mutableStateOf<DownloadTaskEntity?>(null) }

    MainScreen(
        uiState = uiState,
        ytDlpVersion = viewModel.getYtDlpVersion(),
        onSetFilter = viewModel::setFilter,
        onDownloadVideoFromBanner = { url -> viewModel.startDownload(url, DownloadPreset.BEST_VIDEO) },
        onDownloadAudioFromBanner = { url -> viewModel.startDownload(url, DownloadPreset.AUDIO_ONLY) },
        onDismissBanner = viewModel::dismissClipboardBanner,
        onPauseTask = viewModel::pauseTask,
        onResumeTask = viewModel::resumeTask,
        onCancelTask = viewModel::cancelTask,
        onDeleteTask = viewModel::deleteTask,
        onRetryTask = viewModel::retryTask,
        onClearCompletedTasks = viewModel::clearCompletedTasks,
        onOpenFile = onOpenFile,
        onShareFile = onShareFile,
        onOpenSettings = { isSettingsOpen = true },
        onOpenManualDialog = { isManualDialogOpen = true },
        onShowErrorTask = { task -> selectedErrorTask = task },
        modifier = modifier
    )

    if (isSettingsOpen) {
        SettingsBottomSheet(
            settings = uiState.settings,
            ytDlpVersion = viewModel.getYtDlpVersion(),
            updateState = uiState.ytdlUpdateStatus,
            isXLoggedIn = uiState.isXLoggedIn,
            onToggleInstantShare = viewModel::setInstantShareMode,
            onSelectDefaultPreset = viewModel::setDefaultPreset,
            onToggleAutoStartClipboard = viewModel::setAutoStartClipboard,
            onCheckYtDlpUpdate = viewModel::updateYoutubeDL,
            onOpenXLogin = { isXLoginDialogOpen = true },
            onLogoutX = {
                viewModel.logoutXAccount()
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_x_logout_success),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                viewModel.resetUpdateStatus()
                isSettingsOpen = false
            }
        )
    }

    if (isXLoginDialogOpen) {
        XLoginDialog(
            onDismiss = { isXLoginDialogOpen = false },
            onLoginSuccess = {
                viewModel.refreshXLoginState()
                isXLoginDialogOpen = false
            }
        )
    }

    if (isManualDialogOpen) {
        AddManualUrlDialog(
            defaultPreset = uiState.settings.defaultPreset,
            onDismiss = { isManualDialogOpen = false },
            onStartDownload = { url, preset ->
                viewModel.startDownload(url, preset)
                isManualDialogOpen = false
            }
        )
    }

    selectedErrorTask?.let { task ->
        TaskErrorDialog(
            task = task,
            onDismiss = { selectedErrorTask = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    uiState: DownloadUiState,
    ytDlpVersion: String,
    onSetFilter: (TaskFilter) -> Unit,
    onDownloadVideoFromBanner: (String) -> Unit,
    onDownloadAudioFromBanner: (String) -> Unit,
    onDismissBanner: () -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onRetryTask: (DownloadTaskEntity) -> Unit,
    onClearCompletedTasks: () -> Unit,
    onOpenFile: (String) -> Unit,
    onShareFile: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenManualDialog: () -> Unit,
    onShowErrorTask: (DownloadTaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        EngineStatusBadge(isReady = uiState.isEngineReady)
                    }
                },
                actions = {
                    val hasCompleted = uiState.tasks.any { it.status == DownloadStatus.COMPLETED }
                    if (hasCompleted) {
                        IconButton(
                            onClick = onClearCompletedTasks,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.btn_clear_completed),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenManualDialog,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.AddLink,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.btn_manual_link),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            // Filter Chips Bar
            FilterChipRow(
                activeFilter = uiState.activeFilter,
                allCount = uiState.tasks.size,
                activeCount = uiState.tasks.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.QUEUED },
                completedCount = uiState.tasks.count { it.status == DownloadStatus.COMPLETED },
                failedCount = uiState.tasks.count { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED },
                onSelectFilter = onSetFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Content List or Empty State
            if (uiState.filteredTasks.isEmpty() && uiState.clipboardSuggestionUrl.isNullOrBlank()) {
                EmptyStateView(
                    filter = uiState.activeFilter,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 88.dp // Space for Extended FAB
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // Clipboard Suggestion Banner at the top of list
                    item(key = "clipboard_banner", contentType = "banner") {
                        ClipboardSuggestionBanner(
                            url = uiState.clipboardSuggestionUrl,
                            onDownloadVideo = onDownloadVideoFromBanner,
                            onDownloadAudio = onDownloadAudioFromBanner,
                            onDismiss = onDismissBanner
                        )
                    }

                    // Task Cards with stable keys
                    items(
                        items = uiState.filteredTasks,
                        key = { it.id },
                        contentType = { "download_task_card" }
                    ) { task ->
                        DownloadTaskCard(
                            task = task,
                            onPause = onPauseTask,
                            onResume = onResumeTask,
                            onCancel = onCancelTask,
                            onDelete = onDeleteTask,
                            onOpenFile = onOpenFile,
                            onShareFile = onShareFile,
                            onRetry = onRetryTask,
                            onShowError = onShowErrorTask
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineStatusBadge(
    isReady: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(
                if (isReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
            )
    )
}

@Composable
private fun FilterChipRow(
    activeFilter: TaskFilter,
    allCount: Int,
    activeCount: Int,
    completedCount: Int,
    failedCount: Int,
    onSelectFilter: (TaskFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        TaskFilter.entries.forEach { filter ->
            val count = when (filter) {
                TaskFilter.ALL -> allCount
                TaskFilter.ACTIVE -> activeCount
                TaskFilter.COMPLETED -> completedCount
                TaskFilter.FAILED -> failedCount
            }

            val isSelected = filter == activeFilter

            FilterChip(
                selected = isSelected,
                onClick = { onSelectFilter(filter) },
                label = {
                    Text(
                        text = "${filter.title} ($count)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateView(
    filter: TaskFilter,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (filter == TaskFilter.ALL) {
                    stringResource(R.string.empty_title)
                } else {
                    "Tidak ada unduhan di kategori ${filter.title}"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AddManualUrlDialog(
    defaultPreset: DownloadPreset,
    onDismiss: () -> Unit,
    onStartDownload: (String, DownloadPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var urlText by rememberSaveable { mutableStateOf("") }
    var selectedPreset by rememberSaveable { mutableStateOf(defaultPreset) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.manual_dialog_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        errorMessage = null
                    },
                    label = { Text("Tautan URL") },
                    placeholder = { Text(stringResource(R.string.manual_dialog_hint)) },
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val item = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!item.isNullOrBlank()) {
                                    urlText = item.trim()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Tempel dari clipboard",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    DownloadPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = preset == selectedPreset,
                            onClick = { selectedPreset = preset },
                            label = { Text(preset.formatLabel) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeUrl = UrlValidator.extractUrl(urlText)
                    if (safeUrl == null) {
                        errorMessage = context.getString(R.string.manual_dialog_error)
                    } else {
                        onStartDownload(safeUrl, selectedPreset)
                    }
                },
                shape = RoundedCornerShape(10.dp),
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
                Text(stringResource(R.string.btn_start_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun TaskErrorDialog(
    task: DownloadTaskEntity,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fullError = task.errorMessage ?: "Tidak ada log kesalahan yang tercatat."

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.error_dialog_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Tugas: ${task.title}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = fullError,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("SwiftGrab Error", fullError))
                    Toast.makeText(context, "Log kesalahan disalin ke papan klip", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    SwiftGrabTheme(darkTheme = true) {
        MainScreen(
            uiState = DownloadUiState(
                isEngineReady = true,
                tasks = listOf(
                    DownloadTaskEntity(
                        id = "1",
                        url = "https://youtube.com/watch?v=1",
                        title = "Sample Active Download Video",
                        status = DownloadStatus.DOWNLOADING,
                        progress = 55.4f,
                        downloadedBytes = 35_000_000L,
                        totalBytes = 63_000_000L,
                        speed = "6.2 MB/s",
                        eta = "00:05"
                    )
                ),
                filteredTasks = listOf(
                    DownloadTaskEntity(
                        id = "1",
                        url = "https://youtube.com/watch?v=1",
                        title = "Sample Active Download Video",
                        status = DownloadStatus.DOWNLOADING,
                        progress = 55.4f,
                        downloadedBytes = 35_000_000L,
                        totalBytes = 63_000_000L,
                        speed = "6.2 MB/s",
                        eta = "00:05"
                    )
                ),
                clipboardSuggestionUrl = "https://www.youtube.com/watch?v=xyz"
            ),
            ytDlpVersion = "2026.03.01",
            onSetFilter = {},
            onDownloadVideoFromBanner = {},
            onDownloadAudioFromBanner = {},
            onDismissBanner = {},
            onPauseTask = {},
            onResumeTask = {},
            onCancelTask = {},
            onDeleteTask = {},
            onRetryTask = {},
            onClearCompletedTasks = {},
            onOpenFile = {},
            onShareFile = {},
            onOpenSettings = {},
            onOpenManualDialog = {},
            onShowErrorTask = {}
        )
    }
}
