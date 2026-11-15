package com.example.swiftgrab.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.swiftgrab.SwiftGrabApp
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import com.example.swiftgrab.data.prefs.DownloadPreferences
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.data.prefs.SwiftGrabSettings
import com.example.swiftgrab.data.repository.DownloadRepository
import com.example.swiftgrab.data.repository.LiveProgress
import com.example.swiftgrab.update.ApkDownloadState
import com.example.swiftgrab.update.AppUpdateCheckResult
import com.example.swiftgrab.update.AppUpdateInfo
import com.example.swiftgrab.util.UrlValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter(val title: String) {
    ALL("Semua"),
    ACTIVE("Aktif"),
    COMPLETED("Selesai"),
    FAILED("Gagal")
}

sealed interface PendingDownloadRequest {
    data class Single(val url: String, val preset: DownloadPreset) : PendingDownloadRequest
    data class Batch(val urls: List<String>, val preset: DownloadPreset) : PendingDownloadRequest
    data class Retry(val task: DownloadTaskEntity) : PendingDownloadRequest
}

sealed interface YtDlpUpdateState {
    data object Idle : YtDlpUpdateState
    data object Updating : YtDlpUpdateState
    data class Success(val version: String) : YtDlpUpdateState
    data class Error(val message: String) : YtDlpUpdateState
}

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateState
    data class UpToDate(val version: String) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

data class DownloadUiState(
    val tasks: List<DownloadTaskEntity> = emptyList(),
    val filteredTasks: List<DownloadTaskEntity> = emptyList(),
    val activeFilter: TaskFilter = TaskFilter.ALL,
    val clipboardSuggestionUrl: String? = null,
    val isEngineReady: Boolean = false,
    val ytdlUpdateStatus: YtDlpUpdateState = YtDlpUpdateState.Idle,
    val settings: SwiftGrabSettings = SwiftGrabSettings(),
    val isXLoggedIn: Boolean = false,
    val appUpdateState: AppUpdateState = AppUpdateState.Idle,
    val apkDownloadState: ApkDownloadState = ApkDownloadState.Idle,
    val isUpdateDialogVisible: Boolean = false
)

class DownloadViewModel internal constructor(
    application: Application,
    private val repository: DownloadRepository,
    private val preferences: DownloadPreferences,
    private val engineGateway: DownloadEngineGateway,
    private val taskDispatcher: DownloadServiceGateway,
    private val storageGateway: StorageGateway,
    private val cookieGateway: XCookieGateway,
    private val updateGateway: AppUpdateGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        swiftGrabApp = application as SwiftGrabApp
    )

    private constructor(
        application: Application,
        swiftGrabApp: SwiftGrabApp
    ) : this(
        application = application,
        repository = swiftGrabApp.repository,
        preferences = swiftGrabApp.userPreferences,
        engineGateway = AndroidDownloadEngineGateway(swiftGrabApp),
        taskDispatcher = AndroidDownloadServiceGateway(application),
        storageGateway = AndroidStorageGateway(application, swiftGrabApp.userPreferences),
        cookieGateway = AndroidXCookieGateway(application),
        updateGateway = AndroidAppUpdateGateway(application, swiftGrabApp.userPreferences),
        ioDispatcher = Dispatchers.IO
    )

    private val _activeFilter = MutableStateFlow(TaskFilter.ALL)
    private val _clipboardSuggestionUrl = MutableStateFlow<String?>(null)
    private val _ytdlUpdateStatus = MutableStateFlow<YtDlpUpdateState>(YtDlpUpdateState.Idle)
    private val _isXLoggedIn = MutableStateFlow(cookieGateway.hasValidXCookies())
    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    private val _apkDownloadState = MutableStateFlow<ApkDownloadState>(ApkDownloadState.Idle)
    private val _isUpdateDialogVisible = MutableStateFlow(false)
    private val _pendingLegacyStorageDownload = MutableStateFlow<PendingDownloadRequest?>(null)
    val pendingLegacyStorageDownload: StateFlow<PendingDownloadRequest?> = _pendingLegacyStorageDownload.asStateFlow()

    val liveProgress: StateFlow<Map<String, LiveProgress>> = repository.liveProgress

    private var hasAutoCheckedAppUpdate = false
    private var apkDownloadObserverJob: Job? = null
    private var activeApkDownloadId: Long? = null

    init {
        restorePersistedApkDownload()
    }

    private val tasksAndFilter = combine(repository.allTasks, _activeFilter) { tasks, filter ->
        val filtered = when (filter) {
            TaskFilter.ALL -> tasks
            TaskFilter.ACTIVE -> tasks.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.QUEUED }
            TaskFilter.COMPLETED -> tasks.filter { it.status == DownloadStatus.COMPLETED }
            TaskFilter.FAILED -> tasks.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED }
        }
        Pair(tasks, Pair(filter, filtered))
    }

    private val stateGroup1 = combine(tasksAndFilter, _clipboardSuggestionUrl) { (tasks, filterAndFiltered), clipboardUrl ->
        Triple(tasks, filterAndFiltered, clipboardUrl)
    }

    private val stateGroup2 = combine(
        engineGateway.isEngineReady,
        _ytdlUpdateStatus,
        preferences.settings,
        _isXLoggedIn
    ) { isReady, updateStatus, settings, isXLoggedIn ->
        Pair(isReady, Triple(updateStatus, settings, isXLoggedIn))
    }

    private val stateGroup3 = combine(
        _appUpdateState,
        _apkDownloadState,
        _isUpdateDialogVisible
    ) { appUpdateState, apkDownloadState, isUpdateDialogVisible ->
        Triple(appUpdateState, apkDownloadState, isUpdateDialogVisible)
    }

    val uiState: StateFlow<DownloadUiState> = combine(stateGroup1, stateGroup2, stateGroup3) { g1, g2, g3 ->
        val (tasks, filterAndFiltered, clipboardUrl) = g1
        val (filter, filtered) = filterAndFiltered
        val (isReady, updateSettingsX) = g2
        val (updateStatus, settings, isXLoggedIn) = updateSettingsX
        val (appUpdateState, apkDownloadState, isUpdateDialogVisible) = g3

        DownloadUiState(
            tasks = tasks,
            filteredTasks = filtered,
            activeFilter = filter,
            clipboardSuggestionUrl = clipboardUrl,
            isEngineReady = isReady,
            ytdlUpdateStatus = updateStatus,
            settings = settings,
            isXLoggedIn = isXLoggedIn,
            appUpdateState = appUpdateState,
            apkDownloadState = apkDownloadState,
            isUpdateDialogVisible = isUpdateDialogVisible
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadUiState()
    )

    fun setFilter(filter: TaskFilter) {
        _activeFilter.value = filter
    }

    fun onClipboardUrlDetected(url: String) {
        val lastUrl = preferences.getLastHandledClipboardUrl()
        if (url != lastUrl) {
            if (preferences.isAutoStartClipboard()) {
                preferences.setLastHandledClipboardUrl(url)
                startDownload(url, preferences.getDefaultPreset())
            } else {
                _clipboardSuggestionUrl.value = url
            }
        }
    }

    fun dismissClipboardBanner() {
        val current = _clipboardSuggestionUrl.value
        if (current != null) {
            preferences.setLastHandledClipboardUrl(current)
        }
        _clipboardSuggestionUrl.value = null
    }

    fun startDownload(url: String, preset: DownloadPreset) {
        if (storageGateway.needsLegacyWritePermission(preferences.getCustomDownloadUri())) {
            _pendingLegacyStorageDownload.value = PendingDownloadRequest.Single(url, preset)
            return
        }
        preferences.setLastHandledClipboardUrl(url)
        _clipboardSuggestionUrl.value = null
        taskDispatcher.startDownload(
            url = url,
            preset = preset
        )
    }

    fun startBatchDownloads(urls: List<String>, preset: DownloadPreset) {
        val validUrls = urls.mapNotNull { UrlValidator.extractUrl(it) }.distinct()
        if (validUrls.isEmpty()) return

        if (storageGateway.needsLegacyWritePermission(preferences.getCustomDownloadUri())) {
            _pendingLegacyStorageDownload.value = PendingDownloadRequest.Batch(validUrls, preset)
            return
        }
        val currentBannerUrl = _clipboardSuggestionUrl.value
        if (currentBannerUrl != null && validUrls.contains(currentBannerUrl)) {
            preferences.setLastHandledClipboardUrl(currentBannerUrl)
            _clipboardSuggestionUrl.value = null
        }
        for (url in validUrls) {
            taskDispatcher.startDownload(url = url, preset = preset)
        }
    }

    fun pauseTask(taskId: String) {
        taskDispatcher.pause(taskId)
    }

    fun resumeTask(taskId: String) {
        taskDispatcher.resume(taskId)
    }

    fun cancelTask(taskId: String) {
        taskDispatcher.cancel(taskId)
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch(ioDispatcher) {
            taskDispatcher.cleanupTemporaryFiles(taskId)
            repository.deleteTaskById(taskId)
        }
    }

    fun retryTask(task: DownloadTaskEntity) {
        if (storageGateway.needsLegacyWritePermission(preferences.getCustomDownloadUri())) {
            _pendingLegacyStorageDownload.value = PendingDownloadRequest.Retry(task)
            return
        }
        val preset = DownloadPreset.fromTask(task.formatLabel, task.audioOnly)
        taskDispatcher.startDownload(
            url = task.url,
            preset = preset,
            taskId = task.id
        )
    }

    /**
     * Melanjutkan permintaan unduhan tertunda setelah izin penyimpanan legacy atau folder SAF tersedia.
     */
    fun executePendingLegacyStorageDownload() {
        val pending = _pendingLegacyStorageDownload.value ?: return
        _pendingLegacyStorageDownload.value = null
        when (pending) {
            is PendingDownloadRequest.Single -> startDownload(pending.url, pending.preset)
            is PendingDownloadRequest.Batch -> startBatchDownloads(pending.urls, pending.preset)
            is PendingDownloadRequest.Retry -> retryTask(pending.task)
        }
    }

    fun clearPendingLegacyStorageDownload() {
        _pendingLegacyStorageDownload.value = null
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            repository.deleteCompletedTasks()
        }
    }

    /**
     * Mengembalikan versi biner yt-dlp yang terpasang, atau fallback aman jika mesin belum siap.
     */
    fun getYtDlpVersion(): String {
        return engineGateway.getYtDlpVersion()
    }

    fun setInstantShareMode(enabled: Boolean) {
        preferences.setInstantShareMode(enabled)
    }

    fun setDefaultPreset(preset: DownloadPreset) {
        preferences.setDefaultPreset(preset)
    }

    fun setAutoStartClipboard(enabled: Boolean) {
        preferences.setAutoStartClipboard(enabled)
    }

    /**
     * Mengatur folder unduhan kustom dari URI Storage Access Framework yang dipilih pengguna.
     * Mengembalikan path fisik atau label tampilan folder jika izin berhasil dipertahankan dan disimpan.
     */
    fun setStorageDirFromUri(uri: Uri): String? {
        return storageGateway.saveCustomTreeUri(uri)
    }

    /**
     * Mengembalikan folder penyimpanan unduhan ke folder publik default.
     * Mengembalikan `true` jika preferensi berhasil dihapus.
     */
    fun resetStorageDir(): Boolean {
        return preferences.clearCustomDownloadDir()
    }

    /**
     * Memeriksa kesiapan mesin unduhan terlebih dahulu lalu memperbarui biner yt-dlp ke kanal stabil.
     */
    fun updateYoutubeDL() {
        if (_ytdlUpdateStatus.value is YtDlpUpdateState.Updating) return
        _ytdlUpdateStatus.value = YtDlpUpdateState.Updating

        viewModelScope.launch(ioDispatcher) {
            engineGateway.updateYtDlp()
                .onSuccess { currentVersion ->
                    _ytdlUpdateStatus.value = YtDlpUpdateState.Success(currentVersion)
                }
                .onFailure { throwable ->
                    _ytdlUpdateStatus.value = YtDlpUpdateState.Error(throwable.message ?: "Gagal memperbarui")
                }
        }
    }

    fun resetUpdateStatus() {
        _ytdlUpdateStatus.value = YtDlpUpdateState.Idle
        if (_appUpdateState.value is AppUpdateState.UpToDate || _appUpdateState.value is AppUpdateState.Error) {
            _appUpdateState.value = AppUpdateState.Idle
        }
    }

    /**
     * Memeriksa pembaruan aplikasi SwiftGrab melalui GitHub Releases API.
     * Jika [manual] bernilai false, pengecekan otomatis hanya berjalan sekali per sesi aplikasi.
     */
    fun checkForAppUpdate(manual: Boolean = false) {
        if (!manual && hasAutoCheckedAppUpdate) return
        if (_appUpdateState.value is AppUpdateState.Checking) return

        hasAutoCheckedAppUpdate = true
        _appUpdateState.value = AppUpdateState.Checking

        viewModelScope.launch {
            when (val result = updateGateway.checkForUpdate()) {
                is AppUpdateCheckResult.UpdateAvailable -> {
                    val currentApkState = _apkDownloadState.value
                    if (currentApkState !is ApkDownloadState.Idle && !currentApkState.matchesRelease(result.info)) {
                        if (currentApkState !is ApkDownloadState.Downloading) {
                            _apkDownloadState.value = ApkDownloadState.Idle
                            preferences.clearActiveApkDownload()
                        }
                    }
                    _appUpdateState.value = AppUpdateState.UpdateAvailable(result.info)
                    _isUpdateDialogVisible.value = true
                    if (!manual) {
                        updateGateway.showUpdateNotification(result.info)
                    }
                }
                is AppUpdateCheckResult.UpToDate -> {
                    _appUpdateState.value = if (manual) {
                        AppUpdateState.UpToDate(result.currentVersion)
                    } else {
                        AppUpdateState.Idle
                    }
                }
                is AppUpdateCheckResult.Error -> {
                    _appUpdateState.value = if (manual) {
                        AppUpdateState.Error(result.message)
                    } else {
                        AppUpdateState.Idle
                    }
                }
            }
        }
    }

    /**
     * Menampilkan modal pembaruan aplikasi jika informasi rilis terbaru sudah tersedia,
     * atau memulai pemeriksaan ulang secara manual jika belum tersedia.
     */
    fun showUpdateDialogOrCheck() {
        if (_appUpdateState.value is AppUpdateState.UpdateAvailable) {
            _isUpdateDialogVisible.value = true
        } else {
            checkForAppUpdate(manual = true)
        }
    }

    fun dismissUpdateDialog() {
        _isUpdateDialogVisible.value = false
    }

    /**
     * Memulihkan pengamatan atau status APK siap-pasang dari [android.app.DownloadManager]
     * berdasarkan ID unduhan yang tersimpan sebelum proses aplikasi dihentikan sistem.
     */
    private fun restorePersistedApkDownload() {
        viewModelScope.launch(ioDispatcher) {
            val restored = updateGateway.restorePersistedApkDownloadState()
                ?: return@launch
            when (restored) {
                is ApkDownloadState.Downloading -> {
                    activeApkDownloadId = restored.downloadId
                    _apkDownloadState.value = restored
                    observeApkDownloadById(
                        downloadId = restored.downloadId,
                        releaseTag = restored.releaseTag,
                        autoLaunchInstaller = false
                    )
                }
                is ApkDownloadState.ReadyToInstall,
                is ApkDownloadState.PermissionRequired -> {
                    _apkDownloadState.value = restored
                }
                else -> Unit
            }
        }
    }

    /**
     * Memulai unduhan APK pembaruan via [android.app.DownloadManager] dan meluncurkan
     * pemasang sistem segera setelah selesai apabila izin pemasangan paket tersedia.
     */
    fun startAppUpdateDownload(updateInfo: AppUpdateInfo) {
        val currentState = _apkDownloadState.value
        if (currentState is ApkDownloadState.Downloading && currentState.matchesRelease(updateInfo)) {
            return
        }

        val releaseTag = updateInfo.tagName
        // Tandai status Downloading secara sinkron sebelum enqueue agar tap ganda tidak memicu duplikat
        _apkDownloadState.value = ApkDownloadState.Downloading(
            downloadId = -1L,
            releaseTag = releaseTag,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = updateInfo.apkSizeBytes
        )

        val enqueueResult = updateGateway.enqueueApkDownload(updateInfo)
        enqueueResult.onFailure { error ->
            activeApkDownloadId = null
            _apkDownloadState.value = ApkDownloadState.Failed(
                releaseTag = releaseTag,
                message = error.message ?: "Gagal memulai pengunduhan APK."
            )
            return
        }

        val downloadId = enqueueResult.getOrNull() ?: return
        activeApkDownloadId = downloadId
        observeApkDownloadById(
            downloadId = downloadId,
            releaseTag = releaseTag,
            autoLaunchInstaller = true
        )
    }

    private fun observeApkDownloadById(
        downloadId: Long,
        releaseTag: String,
        autoLaunchInstaller: Boolean
    ) {
        apkDownloadObserverJob?.cancel()
        apkDownloadObserverJob = viewModelScope.launch {
            updateGateway.observeApkDownload(downloadId, releaseTag).collect { state ->
                if (activeApkDownloadId != downloadId) return@collect
                _apkDownloadState.value = state
                if (autoLaunchInstaller && state is ApkDownloadState.ReadyToInstall) {
                    updateGateway.launchApkInstaller(state.apkUri)
                }
            }
        }
    }

    /**
     * Meluncurkan pemasang APK hasil unduhan, atau membuka pengaturan izin sumber tidak dikenal
     * jika sistem Android belum mengizinkan instalasi paket dari SwiftGrab.
     */
    fun installDownloadedApk() {
        val availableInfo = (_appUpdateState.value as? AppUpdateState.UpdateAvailable)?.info
        when (val state = _apkDownloadState.value) {
            is ApkDownloadState.ReadyToInstall -> {
                if (availableInfo != null && !state.matchesRelease(availableInfo)) return
                updateGateway.launchApkInstaller(state.apkUri)
            }
            is ApkDownloadState.PermissionRequired -> {
                if (availableInfo != null && !state.matchesRelease(availableInfo)) return
                if (updateGateway.canInstallPackages()) {
                    _apkDownloadState.value = ApkDownloadState.ReadyToInstall(
                        downloadId = state.downloadId,
                        releaseTag = state.releaseTag,
                        apkUri = state.apkUri,
                        localFilePath = state.localFilePath
                    )
                    updateGateway.launchApkInstaller(state.apkUri)
                } else {
                    updateGateway.launchUnknownSourcesSettings()
                }
            }
            else -> Unit
        }
    }

    fun openInstallPermissionSettings() {
        updateGateway.launchUnknownSourcesSettings()
    }

    fun openReleaseOnGitHub(url: String) {
        updateGateway.openReleasePage(url)
    }

    /**
     * Dipanggil saat [MainActivity] kembali aktif untuk otomatis melanjutkan instalasi APK
     * apabila pengguna baru saja menyalakan izin "Install aplikasi tidak dikenal".
     */
    fun recheckInstallPermissionOnResume() {
        val state = _apkDownloadState.value as? ApkDownloadState.PermissionRequired ?: return
        val availableInfo = (_appUpdateState.value as? AppUpdateState.UpdateAvailable)?.info
        if (availableInfo != null && !state.matchesRelease(availableInfo)) return

        if (updateGateway.canInstallPackages()) {
            _apkDownloadState.value = ApkDownloadState.ReadyToInstall(
                downloadId = state.downloadId,
                releaseTag = state.releaseTag,
                apkUri = state.apkUri,
                localFilePath = state.localFilePath
            )
            updateGateway.launchApkInstaller(state.apkUri)
        }
    }

    fun refreshXLoginState() {
        _isXLoggedIn.value = cookieGateway.hasValidXCookies()
    }

    fun logoutXAccount() {
        cookieGateway.clearXCookies()
        _isXLoggedIn.value = false
    }
}
