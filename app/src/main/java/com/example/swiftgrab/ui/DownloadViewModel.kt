package com.example.swiftgrab.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.swiftgrab.SwiftGrabApp
import com.example.swiftgrab.data.cookies.CookieStorageManager
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.data.prefs.SwiftGrabSettings
import com.example.swiftgrab.data.repository.DownloadRepository
import com.example.swiftgrab.service.DownloadForegroundService
import com.example.swiftgrab.update.ApkDownloadState
import com.example.swiftgrab.update.AppUpdateCheckResult
import com.example.swiftgrab.update.AppUpdateInfo
import com.example.swiftgrab.update.AppUpdateManager
import com.example.swiftgrab.util.StorageHelper
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter(val title: String) {
    ALL("Semua"),
    ACTIVE("Aktif"),
    COMPLETED("Selesai"),
    FAILED("Gagal")
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

class DownloadViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app = application as SwiftGrabApp
    private val repository: DownloadRepository = app.repository
    private val preferences = app.userPreferences

    private val _activeFilter = MutableStateFlow(TaskFilter.ALL)
    private val _clipboardSuggestionUrl = MutableStateFlow<String?>(null)
    private val _ytdlUpdateStatus = MutableStateFlow<YtDlpUpdateState>(YtDlpUpdateState.Idle)
    private val _isXLoggedIn = MutableStateFlow(CookieStorageManager.hasValidXCookies(application))
    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    private val _apkDownloadState = MutableStateFlow<ApkDownloadState>(ApkDownloadState.Idle)
    private val _isUpdateDialogVisible = MutableStateFlow(false)

    private var hasAutoCheckedAppUpdate = false
    private var apkDownloadObserverJob: Job? = null

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
        app.isEngineReady,
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
        preferences.setLastHandledClipboardUrl(url)
        _clipboardSuggestionUrl.value = null
        DownloadForegroundService.startDownload(
            context = getApplication(),
            url = url,
            preset = preset
        )
    }

    fun startBatchDownloads(urls: List<String>, preset: DownloadPreset) {
        val context: Context = getApplication()
        for (url in urls) {
            DownloadForegroundService.startDownload(context, url.trim(), preset)
        }
    }

    fun pauseTask(taskId: String) {
        DownloadForegroundService.pause(getApplication(), taskId)
    }

    fun resumeTask(taskId: String) {
        DownloadForegroundService.resume(getApplication(), taskId)
    }

    fun cancelTask(taskId: String) {
        DownloadForegroundService.cancel(getApplication(), taskId)
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTaskById(taskId)
        }
    }

    fun retryTask(task: DownloadTaskEntity) {
        val preset = DownloadPreset.fromTask(task.formatLabel, task.audioOnly)
        DownloadForegroundService.startDownload(
            context = getApplication(),
            url = task.url,
            preset = preset,
            taskId = task.id
        )
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
        return try {
            YoutubeDL.getInstance().versionName(getApplication()) ?: "Siap"
        } catch (_: Throwable) {
            "Siap"
        }
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
        val context: Context = getApplication()
        if (!StorageHelper.takePersistablePermission(context, uri)) {
            return null
        }
        val path = StorageHelper.resolvePathFromTreeUri(context, uri)
        if (path != null) {
            preferences.setCustomDownloadLocation(path, uri.toString())
            return path
        }
        val displayLabel = StorageHelper.getDisplayLabelFromTreeUri(uri)
        preferences.setCustomDownloadLocation(null, uri.toString())
        return displayLabel
    }

    /**
     * Mengembalikan folder penyimpanan unduhan ke folder publik default.
     */
    fun resetStorageDir() {
        preferences.clearCustomDownloadDir()
    }

    /**
     * Memeriksa kesiapan mesin unduhan terlebih dahulu lalu memperbarui biner yt-dlp ke kanal stabil.
     */
    fun updateYoutubeDL() {
        if (_ytdlUpdateStatus.value is YtDlpUpdateState.Updating) return
        _ytdlUpdateStatus.value = YtDlpUpdateState.Updating

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("DownloadViewModel", "Starting yt-dlp binary update check...")
                val engineResult = app.awaitEngineReady()
                if (engineResult.isFailure) {
                    val cause = engineResult.exceptionOrNull()
                    throw cause ?: IllegalStateException("Engine init failed")
                }
                val context: Context = getApplication()
                val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                val currentVersion = YoutubeDL.getInstance().versionName(context) ?: "terbaru"
                Log.i("DownloadViewModel", "yt-dlp update status: $status, current version: $currentVersion")
                _ytdlUpdateStatus.value = YtDlpUpdateState.Success(currentVersion)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e("DownloadViewModel", "Failed to update yt-dlp", t)
                _ytdlUpdateStatus.value = YtDlpUpdateState.Error(t.message ?: "Gagal memperbarui")
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
            when (val result = AppUpdateManager.checkForUpdate()) {
                is AppUpdateCheckResult.UpdateAvailable -> {
                    _appUpdateState.value = AppUpdateState.UpdateAvailable(result.info)
                    _isUpdateDialogVisible.value = true
                    if (!manual) {
                        AppUpdateManager.showUpdateNotification(getApplication(), result.info)
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
     * Memulai unduhan APK pembaruan via [android.app.DownloadManager] dan meluncurkan
     * pemasang sistem segera setelah selesai apabila izin pemasangan paket tersedia.
     */
    fun startAppUpdateDownload(updateInfo: AppUpdateInfo) {
        if (_apkDownloadState.value is ApkDownloadState.Downloading) return

        val context: Context = getApplication()
        val enqueueResult = AppUpdateManager.enqueueApkDownload(context, updateInfo)
        enqueueResult.onFailure { error ->
            _apkDownloadState.value = ApkDownloadState.Failed(
                error.message ?: "Gagal memulai pengunduhan APK."
            )
            return
        }

        val downloadId = enqueueResult.getOrNull() ?: return
        apkDownloadObserverJob?.cancel()
        apkDownloadObserverJob = viewModelScope.launch {
            AppUpdateManager.observeApkDownload(context, downloadId).collect { state ->
                _apkDownloadState.value = state
                if (state is ApkDownloadState.ReadyToInstall) {
                    AppUpdateManager.launchApkInstaller(context, state.apkUri)
                }
            }
        }
    }

    /**
     * Meluncurkan pemasang APK hasil unduhan, atau membuka pengaturan izin sumber tidak dikenal
     * jika sistem Android belum mengizinkan instalasi paket dari SwiftGrab.
     */
    fun installDownloadedApk() {
        val context: Context = getApplication()
        when (val state = _apkDownloadState.value) {
            is ApkDownloadState.ReadyToInstall -> {
                AppUpdateManager.launchApkInstaller(context, state.apkUri)
            }
            is ApkDownloadState.PermissionRequired -> {
                if (AppUpdateManager.canInstallPackages(context)) {
                    _apkDownloadState.value = ApkDownloadState.ReadyToInstall(
                        downloadId = state.downloadId,
                        apkUri = state.apkUri,
                        localFilePath = state.localFilePath
                    )
                    AppUpdateManager.launchApkInstaller(context, state.apkUri)
                } else {
                    AppUpdateManager.launchUnknownSourcesSettings(context)
                }
            }
            else -> Unit
        }
    }

    fun openInstallPermissionSettings() {
        AppUpdateManager.launchUnknownSourcesSettings(getApplication())
    }

    fun openReleaseOnGitHub(url: String) {
        AppUpdateManager.openReleasePage(getApplication(), url)
    }

    /**
     * Dipanggil saat [MainActivity] kembali aktif untuk otomatis melanjutkan instalasi APK
     * apabila pengguna baru saja menyalakan izin "Install aplikasi tidak dikenal".
     */
    fun recheckInstallPermissionOnResume() {
        val state = _apkDownloadState.value as? ApkDownloadState.PermissionRequired ?: return
        val context: Context = getApplication()
        if (AppUpdateManager.canInstallPackages(context)) {
            _apkDownloadState.value = ApkDownloadState.ReadyToInstall(
                downloadId = state.downloadId,
                apkUri = state.apkUri,
                localFilePath = state.localFilePath
            )
            AppUpdateManager.launchApkInstaller(context, state.apkUri)
        }
    }

    fun refreshXLoginState() {
        _isXLoggedIn.value = CookieStorageManager.hasValidXCookies(getApplication())
    }

    fun logoutXAccount() {
        CookieStorageManager.clearXCookies(getApplication())
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        } catch (_: Exception) {}
        _isXLoggedIn.value = false
    }
}
