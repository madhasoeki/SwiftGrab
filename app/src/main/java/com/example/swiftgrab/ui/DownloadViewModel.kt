package com.example.swiftgrab.ui

import android.app.Application
import android.content.Context
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
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

data class DownloadUiState(
    val tasks: List<DownloadTaskEntity> = emptyList(),
    val filteredTasks: List<DownloadTaskEntity> = emptyList(),
    val activeFilter: TaskFilter = TaskFilter.ALL,
    val clipboardSuggestionUrl: String? = null,
    val isEngineReady: Boolean = false,
    val ytdlUpdateStatus: YtDlpUpdateState = YtDlpUpdateState.Idle,
    val settings: SwiftGrabSettings = SwiftGrabSettings(),
    val isXLoggedIn: Boolean = false
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

    val uiState: StateFlow<DownloadUiState> = combine(stateGroup1, stateGroup2) { g1, g2 ->
        val (tasks, filterAndFiltered, clipboardUrl) = g1
        val (filter, filtered) = filterAndFiltered
        val (isReady, updateSettingsX) = g2
        val (updateStatus, settings, isXLoggedIn) = updateSettingsX

        DownloadUiState(
            tasks = tasks,
            filteredTasks = filtered,
            activeFilter = filter,
            clipboardSuggestionUrl = clipboardUrl,
            isEngineReady = isReady,
            ytdlUpdateStatus = updateStatus,
            settings = settings,
            isXLoggedIn = isXLoggedIn
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
