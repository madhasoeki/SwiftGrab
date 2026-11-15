package com.example.swiftgrab.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.swiftgrab.SwiftGrabApp
import com.example.swiftgrab.data.cookies.CookieStorageManager
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.data.prefs.UserPreferences
import com.example.swiftgrab.service.DownloadFileManager
import com.example.swiftgrab.service.DownloadForegroundService
import com.example.swiftgrab.update.ApkDownloadState
import com.example.swiftgrab.update.AppUpdateCheckResult
import com.example.swiftgrab.update.AppUpdateInfo
import com.example.swiftgrab.update.AppUpdateManager
import com.example.swiftgrab.util.StorageHelper
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

internal interface DownloadEngineGateway {
    val isEngineReady: StateFlow<Boolean>
    fun getYtDlpVersion(): String
    suspend fun updateYtDlp(): Result<String>
}

internal interface DownloadServiceGateway {
    fun startDownload(url: String, preset: DownloadPreset, taskId: String? = null)
    fun pause(taskId: String)
    fun resume(taskId: String)
    fun cancel(taskId: String)
    fun cleanupTemporaryFiles(taskId: String)
}

internal interface StorageGateway {
    fun needsLegacyWritePermission(customDownloadUri: String?): Boolean
    fun saveCustomTreeUri(uri: Uri): String?
}

internal interface XCookieGateway {
    fun hasValidXCookies(): Boolean
    fun clearXCookies()
}

internal interface AppUpdateGateway {
    suspend fun checkForUpdate(): AppUpdateCheckResult
    fun showUpdateNotification(info: AppUpdateInfo)
    fun restorePersistedApkDownloadState(): ApkDownloadState?
    fun enqueueApkDownload(updateInfo: AppUpdateInfo): Result<Long>
    fun observeApkDownload(downloadId: Long, releaseTag: String): Flow<ApkDownloadState>
    fun canInstallPackages(): Boolean
    fun launchApkInstaller(apkUri: Uri): Boolean
    fun launchUnknownSourcesSettings(): Boolean
    fun openReleasePage(url: String)
}

internal class AndroidDownloadEngineGateway(
    private val app: SwiftGrabApp
) : DownloadEngineGateway {

    override val isEngineReady: StateFlow<Boolean> = app.isEngineReady

    override fun getYtDlpVersion(): String {
        return try {
            YoutubeDL.getInstance().versionName(app) ?: "Siap"
        } catch (_: Throwable) {
            "Siap"
        }
    }

    override suspend fun updateYtDlp(): Result<String> {
        return try {
            Log.d("DownloadViewModel", "Starting yt-dlp binary update check...")
            val engineResult = app.awaitEngineReady()
            if (engineResult.isFailure) {
                val cause = engineResult.exceptionOrNull()
                throw cause ?: IllegalStateException("Engine init failed")
            }
            val context: Context = app
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            val currentVersion = YoutubeDL.getInstance().versionName(context) ?: "terbaru"
            Log.i("DownloadViewModel", "yt-dlp update status: $status, current version: $currentVersion")
            Result.success(currentVersion)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e("DownloadViewModel", "Failed to update yt-dlp", t)
            Result.failure(t)
        }
    }
}

internal class AndroidDownloadServiceGateway(
    private val context: Context
) : DownloadServiceGateway {

    override fun startDownload(url: String, preset: DownloadPreset, taskId: String?) {
        DownloadForegroundService.startDownload(
            context = context,
            url = url,
            preset = preset,
            taskId = taskId
        )
    }

    override fun pause(taskId: String) {
        DownloadForegroundService.pause(context, taskId)
    }

    override fun resume(taskId: String) {
        DownloadForegroundService.resume(context, taskId)
    }

    override fun cancel(taskId: String) {
        DownloadForegroundService.cancel(context, taskId)
    }

    override fun cleanupTemporaryFiles(taskId: String) {
        DownloadFileManager.cleanupTemporaryFiles(context, taskId)
    }
}

internal class AndroidStorageGateway(
    private val context: Context,
    private val preferences: UserPreferences
) : StorageGateway {

    override fun needsLegacyWritePermission(customDownloadUri: String?): Boolean {
        return StorageHelper.needsLegacyWritePermission(context, customDownloadUri)
    }

    override fun saveCustomTreeUri(uri: Uri): String? {
        return StorageHelper.saveCustomTreeUri(context, uri, preferences)
    }
}

internal class AndroidXCookieGateway(
    private val context: Context
) : XCookieGateway {

    override fun hasValidXCookies(): Boolean {
        return CookieStorageManager.hasValidXCookies(context)
    }

    override fun clearXCookies() {
        CookieStorageManager.clearXCookies(context)
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
    }
}

internal class AndroidAppUpdateGateway(
    private val context: Context,
    private val preferences: UserPreferences
) : AppUpdateGateway {

    override suspend fun checkForUpdate(): AppUpdateCheckResult {
        return AppUpdateManager.checkForUpdate()
    }

    override fun showUpdateNotification(info: AppUpdateInfo) {
        AppUpdateManager.showUpdateNotification(context, info)
    }

    override fun restorePersistedApkDownloadState(): ApkDownloadState? {
        return AppUpdateManager.restorePersistedApkDownloadState(context, preferences)
    }

    override fun enqueueApkDownload(updateInfo: AppUpdateInfo): Result<Long> {
        return AppUpdateManager.enqueueApkDownload(context, updateInfo, preferences)
    }

    override fun observeApkDownload(downloadId: Long, releaseTag: String): Flow<ApkDownloadState> {
        return AppUpdateManager.observeApkDownload(context, downloadId, releaseTag, preferences)
    }

    override fun canInstallPackages(): Boolean {
        return AppUpdateManager.canInstallPackages(context)
    }

    override fun launchApkInstaller(apkUri: Uri): Boolean {
        return AppUpdateManager.launchApkInstaller(context, apkUri)
    }

    override fun launchUnknownSourcesSettings(): Boolean {
        return AppUpdateManager.launchUnknownSourcesSettings(context)
    }

    override fun openReleasePage(url: String) {
        AppUpdateManager.openReleasePage(context, url)
    }
}
