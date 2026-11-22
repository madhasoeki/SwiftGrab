package com.example.swiftgrab.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.swiftgrab.BuildConfig
import com.example.swiftgrab.R
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.data.repository.LiveProgress
import com.example.swiftgrab.ui.components.ClipboardSuggestionBanner
import com.example.swiftgrab.ui.components.DownloadTaskCard
import com.example.swiftgrab.ui.components.SettingsBottomSheet
import com.example.swiftgrab.ui.components.SwiftGrabAppIconTile
import com.example.swiftgrab.ui.components.SwiftGrabHorizontalLockup
import com.example.swiftgrab.ui.components.UpdateAvailableDialog
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
    val liveProgressState = viewModel.liveProgress.collectAsStateWithLifecycle()
    val liveProgressProvider: (String) -> LiveProgress? = remember(liveProgressState) {
        { taskId -> liveProgressState.value[taskId] }
    }
    val pendingLegacyDownload by viewModel.pendingLegacyStorageDownload.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var isXLoginDialogOpen by rememberSaveable { mutableStateOf(false) }
    var isManualDialogOpen by rememberSaveable { mutableStateOf(false) }
    var selectedErrorTask by remember { mutableStateOf<DownloadTaskEntity?>(null) }

    val legacySafPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val display = viewModel.setStorageDirFromUri(uri)
            if (display != null) {
                Toast.makeText(
                    context,
                    resources.getString(R.string.toast_storage_dir_updated, display),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.executePendingLegacyStorageDownload()
            } else {
                Toast.makeText(
                    context,
                    resources.getString(R.string.toast_storage_dir_error),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.clearPendingLegacyStorageDownload()
            }
        } else {
            Toast.makeText(
                context,
                resources.getString(R.string.toast_legacy_storage_denied),
                Toast.LENGTH_SHORT
            ).show()
            viewModel.clearPendingLegacyStorageDownload()
        }
    }

    val legacyStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.executePendingLegacyStorageDownload()
        } else {
            Toast.makeText(
                context,
                resources.getString(R.string.toast_legacy_storage_denied),
                Toast.LENGTH_SHORT
            ).show()
            legacySafPickerLauncher.launch(null)
        }
    }

    LaunchedEffect(pendingLegacyDownload) {
        if (pendingLegacyDownload != null) {
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    MainScreen(
        uiState = uiState,
        liveProgressProvider = liveProgressProvider,
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
            appUpdateState = uiState.appUpdateState,
            currentAppVersion = BuildConfig.VERSION_NAME,
            onToggleInstantShare = viewModel::setInstantShareMode,
            onSelectDefaultPreset = viewModel::setDefaultPreset,
            onToggleAutoStartClipboard = viewModel::setAutoStartClipboard,
            onCheckYtDlpUpdate = viewModel::updateYoutubeDL,
            onCheckAppUpdate = { viewModel.checkForAppUpdate(manual = true) },
            onShowUpdateDialog = {
                isSettingsOpen = false
                viewModel.showUpdateDialogOrCheck()
            },
            onOpenXLogin = { isXLoginDialogOpen = true },
            onLogoutX = {
                viewModel.logoutXAccount()
                Toast.makeText(
                    context,
                    resources.getString(R.string.toast_x_logout_success),
                    Toast.LENGTH_SHORT
                ).show()
            },
            onSelectStorageDirUri = { uri ->
                val display = viewModel.setStorageDirFromUri(uri)
                if (display != null) {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.toast_storage_dir_updated, display),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.toast_storage_dir_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onResetStorageDir = {
                if (viewModel.resetStorageDir()) {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.toast_storage_dir_reset),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        resources.getString(R.string.toast_storage_dir_reset_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = {
                viewModel.resetUpdateStatus()
                isSettingsOpen = false
            }
        )
    }

    val availableUpdate = (uiState.appUpdateState as? AppUpdateState.UpdateAvailable)?.info
    if (uiState.isUpdateDialogVisible && availableUpdate != null) {
        UpdateAvailableDialog(
            updateInfo = availableUpdate,
            currentVersion = BuildConfig.VERSION_NAME,
            downloadState = uiState.apkDownloadState,
            onDownloadAndInstall = { viewModel.startAppUpdateDownload(availableUpdate) },
            onInstallNow = viewModel::installDownloadedApk,
            onOpenInstallPermissionSettings = viewModel::openInstallPermissionSettings,
            onOpenGitHub = { viewModel.openReleaseOnGitHub(availableUpdate.htmlUrl) },
            onDismiss = viewModel::dismissUpdateDialog
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
            onStartDownloads = { urls, preset ->
                if (urls.size == 1) {
                    viewModel.startDownload(urls.first(), preset)
                } else {
                    viewModel.startBatchDownloads(urls, preset)
                }
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

/**
 * Layar utama aplikasi yang menampilkan daftar unduhan, filter status, saran clipboard,
 * dan aksi cepat untuk menambah tautan atau membuka pengaturan.
 */
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
    modifier: Modifier = Modifier,
    liveProgressProvider: (String) -> LiveProgress? = { null }
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SwiftGrabHorizontalLockup(
                            symbolSize = 24.dp,
                            wordmarkHeight = 16.dp
                        )
                        EngineStatusBadge(isReady = uiState.isEngineReady)
                    }
                },
                actions = {
                    val hasCompleted = uiState.tasks.any { it.status == DownloadStatus.COMPLETED }
                    if (hasCompleted) {
                        TopBarActionButton(
                            icon = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.btn_clear_completed),
                            onClick = onClearCompletedTasks
                        )
                    }

                    TopBarActionButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.cd_settings),
                        onClick = onOpenSettings
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
                shape = MaterialTheme.shapes.medium,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 4.dp
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection)
                )
                .consumeWindowInsets(innerPadding)
        ) {
            // Filter Chips Strip
            FilterChipRow(
                activeFilter = uiState.activeFilter,
                allCount = uiState.tasks.size,
                activeCount = uiState.tasks.count {
                    it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.PAUSED ||
                        it.status == DownloadStatus.QUEUED
                },
                completedCount = uiState.tasks.count { it.status == DownloadStatus.COMPLETED },
                failedCount = uiState.tasks.count {
                    it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED
                },
                onSelectFilter = onSetFilter,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Content List or Empty State
            if (uiState.filteredTasks.isEmpty() && uiState.clipboardSuggestionUrl.isNullOrBlank()) {
                EmptyStateView(
                    filter = uiState.activeFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = innerPadding.calculateBottomPadding())
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 92.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
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

                    // Task Cards with stable keys and deferred progress reads
                    items(
                        items = uiState.filteredTasks,
                        key = { it.id },
                        contentType = { "download_task_card" }
                    ) { task ->
                        val taskProgressProvider = remember(task.id, liveProgressProvider) {
                            { liveProgressProvider(task.id) }
                        }
                        DownloadTaskCard(
                            task = task,
                            liveProgressProvider = taskProgressProvider,
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

/**
 * Tombol aksi app bar bertarget sentuh 48dp dengan kontainer squircle tonal 36dp dan border presisi.
 */
@Composable
private fun TopBarActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Lencana indikator kesiapan mesin yt-dlp dan aria2c pada app bar.
 */
@Composable
private fun EngineStatusBadge(
    isReady: Boolean,
    modifier: Modifier = Modifier
) {
    val statusText = if (isReady) {
        stringResource(R.string.engine_ready)
    } else {
        stringResource(R.string.engine_initializing)
    }
    val containerColor = if (isReady) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
    }
    val borderColor = if (isReady) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
    }
    val dotColor = if (isReady) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val textColor = if (isReady) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = statusText
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
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

            SwiftGrabFilterChip(
                label = "${filter.title} ($count)",
                selected = filter == activeFilter,
                onClick = { onSelectFilter(filter) }
            )
        }
    }
}

@Composable
private fun SwiftGrabFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            )
        },
        leadingIcon = null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    )
}

/**
 * Tampilan kosong "Meteor Catch Brand Showcase" ketika tidak ada tugas unduhan yang sesuai.
 */
@Composable
private fun EmptyStateView(
    filter: TaskFilter,
    modifier: Modifier = Modifier
) {
    val outerArcColor = MaterialTheme.colorScheme.outlineVariant
    val innerArcColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(140.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val outerInset = 2.dp.toPx()
                val innerInset = 15.dp.toPx()
                val outerSize = Size(size.width - outerInset * 2f, size.height - outerInset * 2f)
                val innerSize = Size(size.width - innerInset * 2f, size.height - innerInset * 2f)

                drawArc(
                    color = outerArcColor,
                    startAngle = 350f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(outerInset, outerInset),
                    size = outerSize,
                    style = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = outerArcColor,
                    startAngle = 246f,
                    sweepAngle = 46f,
                    useCenter = false,
                    topLeft = Offset(outerInset, outerInset),
                    size = outerSize,
                    style = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = innerArcColor,
                    startAngle = 342f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = Offset(innerInset, innerInset),
                    size = innerSize,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            SwiftGrabAppIconTile(size = 76.dp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (filter == TaskFilter.ALL) {
                stringResource(R.string.empty_title)
            } else {
                "Tidak ada unduhan di kategori ${filter.title}"
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (filter == TaskFilter.ALL) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max)
            ) {
                EmptyCapabilityPill(
                    icon = Icons.Rounded.Share,
                    label = stringResource(R.string.empty_feature_one_tap),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                EmptyCapabilityPill(
                    icon = Icons.Rounded.ContentPaste,
                    label = stringResource(R.string.empty_feature_clipboard),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun EmptyCapabilityPill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Kerangka dialog Material 3 seragam dengan kontainer extraLarge dan border presisi 1dp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwiftGrabAlertDialog(
    onDismissRequest: () -> Unit,
    headerIcon: @Composable () -> Unit,
    title: String,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    headerIcon()
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    content()
                }

                Spacer(modifier = Modifier.height(20.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    actions()
                }
            }
        }
    }
}

/**
 * Dialog untuk memasukkan satu atau beberapa tautan URL unduhan secara manual beserta pemilihan preset format.
 */
@Composable
private fun AddManualUrlDialog(
    defaultPreset: DownloadPreset,
    onDismiss: () -> Unit,
    onStartDownloads: (List<String>, DownloadPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var urlText by rememberSaveable { mutableStateOf("") }
    var selectedPreset by rememberSaveable { mutableStateOf(defaultPreset) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val detectedUrls = remember(urlText) { UrlValidator.extractUrls(urlText) }
    val context = LocalContext.current
    val resources = LocalResources.current

    SwiftGrabAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        headerIcon = { SwiftGrabAppIconTile(size = 32.dp) },
        title = stringResource(R.string.manual_dialog_title),
        actions = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.btn_cancel))
            }

            Button(
                onClick = {
                    val safeUrls = UrlValidator.extractUrls(urlText)
                    if (safeUrls.isEmpty()) {
                        errorMessage = resources.getString(R.string.manual_dialog_error)
                    } else {
                        onStartDownloads(safeUrls, selectedPreset)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (detectedUrls.size > 1) {
                        stringResource(R.string.btn_start_batch_download, detectedUrls.size)
                    } else {
                        stringResource(R.string.btn_start_download)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    errorMessage = null
                },
                label = { Text("Tautan URL") },
                placeholder = { Text(stringResource(R.string.manual_dialog_hint)) },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                isError = errorMessage != null,
                supportingText = {
                    when {
                        errorMessage != null -> {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        detectedUrls.size > 1 -> {
                            Text(
                                text = stringResource(R.string.manual_dialog_batch_count, detectedUrls.size),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.manual_dialog_batch_helper),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val item = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!item.isNullOrBlank()) {
                                val trimmed = item.trim()
                                val existingLines = urlText.lineSequence().map { it.trim() }.toSet()
                                urlText = when {
                                    urlText.isBlank() -> trimmed
                                    existingLines.contains(trimmed) -> urlText
                                    else -> "${urlText.trimEnd()}\n$trimmed"
                                }
                                errorMessage = null
                            }
                        },
                        modifier = Modifier.size(48.dp)
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                DownloadPreset.entries.forEach { preset ->
                    SwiftGrabFilterChip(
                        label = preset.formatLabel,
                        selected = preset == selectedPreset,
                        onClick = { selectedPreset = preset }
                    )
                }
            }
        }
    }
}

/**
 * Dialog untuk menampilkan log detail kesalahan unduhan dan menyalinnya ke papan klip.
 */
@Composable
private fun TaskErrorDialog(
    task: DownloadTaskEntity,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fullError = task.errorMessage ?: "Tidak ada log kesalahan yang tercatat."

    SwiftGrabAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        headerIcon = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        title = stringResource(R.string.error_dialog_title),
        actions = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.btn_close))
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("SwiftGrab Error", fullError))
                    Toast.makeText(context, "Log kesalahan disalin ke papan klip", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.btn_copy),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Tugas: ${task.title}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = fullError,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

private fun previewSampleTasks(): List<DownloadTaskEntity> = listOf(
    DownloadTaskEntity(
        id = "1",
        url = "https://youtube.com/watch?v=1",
        title = "Sample Active Download Video",
        formatLabel = "MP4 1080p",
        status = DownloadStatus.DOWNLOADING,
        progress = 55.4f,
        downloadedBytes = 35_000_000L,
        totalBytes = 63_000_000L,
        speed = "6.2 MB/s",
        eta = "00:05"
    ),
    DownloadTaskEntity(
        id = "2",
        url = "https://youtube.com/watch?v=2",
        title = "Finished Podcast Audio Mix",
        formatLabel = "MP3",
        audioOnly = true,
        status = DownloadStatus.COMPLETED,
        progress = 100f,
        downloadedBytes = 18_400_000L,
        totalBytes = 18_400_000L,
        outputPath = "/sdcard/Download/SwiftGrab/podcast.mp3"
    )
)

@Composable
private fun MainScreenPreviewHost(
    darkTheme: Boolean,
    uiState: DownloadUiState
) {
    SwiftGrabTheme(darkTheme = darkTheme) {
        MainScreen(
            uiState = uiState,
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

@Preview(name = "MainScreen Populated Dark", showBackground = true)
@Composable
private fun MainScreenPopulatedDarkPreview() {
    val tasks = previewSampleTasks()
    MainScreenPreviewHost(
        darkTheme = true,
        uiState = DownloadUiState(
            isEngineReady = true,
            tasks = tasks,
            filteredTasks = tasks,
            clipboardSuggestionUrl = "https://www.youtube.com/watch?v=xyz"
        )
    )
}

@Preview(name = "MainScreen Populated Light", showBackground = true)
@Composable
private fun MainScreenPopulatedLightPreview() {
    val tasks = previewSampleTasks()
    MainScreenPreviewHost(
        darkTheme = false,
        uiState = DownloadUiState(
            isEngineReady = true,
            tasks = tasks,
            filteredTasks = tasks,
            clipboardSuggestionUrl = "https://www.youtube.com/watch?v=xyz"
        )
    )
}

@Preview(name = "MainScreen Empty Dark", showBackground = true)
@Composable
private fun MainScreenEmptyDarkPreview() {
    MainScreenPreviewHost(
        darkTheme = true,
        uiState = DownloadUiState(
            isEngineReady = true,
            activeFilter = TaskFilter.ALL
        )
    )
}

@Preview(name = "MainScreen Empty Light", showBackground = true)
@Composable
private fun MainScreenEmptyLightPreview() {
    MainScreenPreviewHost(
        darkTheme = false,
        uiState = DownloadUiState(
            isEngineReady = false,
            activeFilter = TaskFilter.ALL
        )
    )
}
