package com.example.swiftgrab.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.swiftgrab.util.StorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SwiftGrabSettings(
    val instantShareMode: Boolean = true,
    val defaultPreset: DownloadPreset = DownloadPreset.BEST_VIDEO,
    val autoStartClipboard: Boolean = false,
    val customDownloadPath: String? = null,
    val customDownloadUri: String? = null
)

/**
 * Abstraksi preferensi aplikasi agar komponen UI/ViewModel mudah diuji tanpa SharedPreferences nyata.
 */
interface DownloadPreferences {
    val settings: StateFlow<SwiftGrabSettings>
    fun isInstantShareMode(): Boolean
    fun setInstantShareMode(enabled: Boolean)
    fun getDefaultPreset(): DownloadPreset
    fun setDefaultPreset(preset: DownloadPreset)
    fun isAutoStartClipboard(): Boolean
    fun setAutoStartClipboard(enabled: Boolean)
    fun getLastHandledClipboardUrl(): String?
    fun setLastHandledClipboardUrl(url: String?)
    fun getCustomDownloadPath(): String?
    fun getCustomDownloadUri(): String?
    fun clearCustomDownloadDir(): Boolean
    fun saveActiveApkDownload(downloadId: Long, releaseTag: String): Boolean
    fun getActiveApkDownload(): Pair<Long, String>?
    fun clearActiveApkDownload()
}

/**
 * SharedPreferences-backed settings store exposing reactive [StateFlow] and synchronous reads.
 */
class UserPreferences(context: Context) : DownloadPreferences {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    override val settings: StateFlow<SwiftGrabSettings> = _settings.asStateFlow()

    override fun isInstantShareMode(): Boolean =
        prefs.getBoolean(KEY_INSTANT_SHARE_MODE, true)

    override fun setInstantShareMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_INSTANT_SHARE_MODE, enabled) }
        _settings.update { it.copy(instantShareMode = enabled) }
    }

    override fun getDefaultPreset(): DownloadPreset =
        DownloadPreset.fromName(prefs.getString(KEY_DEFAULT_PRESET, DownloadPreset.BEST_VIDEO.name))

    override fun setDefaultPreset(preset: DownloadPreset) {
        prefs.edit { putString(KEY_DEFAULT_PRESET, preset.name) }
        _settings.update { it.copy(defaultPreset = preset) }
    }

    override fun isAutoStartClipboard(): Boolean =
        prefs.getBoolean(KEY_AUTO_START_CLIPBOARD, false)

    override fun setAutoStartClipboard(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_START_CLIPBOARD, enabled) }
        _settings.update { it.copy(autoStartClipboard = enabled) }
    }

    override fun getLastHandledClipboardUrl(): String? =
        prefs.getString(KEY_LAST_CLIPBOARD_URL, null)

    override fun setLastHandledClipboardUrl(url: String?) {
        prefs.edit {
            if (url.isNullOrBlank()) remove(KEY_LAST_CLIPBOARD_URL)
            else putString(KEY_LAST_CLIPBOARD_URL, url)
        }
    }

    override fun getCustomDownloadPath(): String? =
        prefs.getString(KEY_CUSTOM_DOWNLOAD_PATH, null)

    fun setCustomDownloadPath(path: String?) {
        prefs.edit {
            if (path.isNullOrBlank()) remove(KEY_CUSTOM_DOWNLOAD_PATH)
            else putString(KEY_CUSTOM_DOWNLOAD_PATH, path)
        }
        _settings.update { it.copy(customDownloadPath = path?.takeIf { p -> p.isNotBlank() }) }
    }

    override fun getCustomDownloadUri(): String? =
        prefs.getString(KEY_CUSTOM_DOWNLOAD_URI, null)

    fun setCustomDownloadUri(uri: String?): Boolean {
        val previousUri = getCustomDownloadUri()
        val cleanUri = uri?.takeIf { it.isNotBlank() }
        val editor = prefs.edit()
        if (cleanUri == null) editor.remove(KEY_CUSTOM_DOWNLOAD_URI)
        else editor.putString(KEY_CUSTOM_DOWNLOAD_URI, cleanUri)
        val committed = editor.commit()
        if (committed) {
            _settings.update { it.copy(customDownloadUri = cleanUri) }
            if (shouldReleasePreviousUri(previousUri, cleanUri)) {
                StorageHelper.releasePersistablePermission(appContext, previousUri)
            }
        }
        return committed
    }

    fun setCustomDownloadLocation(path: String?, uri: String?): Boolean {
        val previousUri = getCustomDownloadUri()
        val cleanPath = path?.takeIf { it.isNotBlank() }
        val cleanUri = uri?.takeIf { it.isNotBlank() }
        val editor = prefs.edit()
        if (cleanPath == null) editor.remove(KEY_CUSTOM_DOWNLOAD_PATH)
        else editor.putString(KEY_CUSTOM_DOWNLOAD_PATH, cleanPath)

        if (cleanUri == null) editor.remove(KEY_CUSTOM_DOWNLOAD_URI)
        else editor.putString(KEY_CUSTOM_DOWNLOAD_URI, cleanUri)

        val committed = editor.commit()
        if (committed) {
            _settings.update {
                it.copy(
                    customDownloadPath = cleanPath,
                    customDownloadUri = cleanUri
                )
            }
            if (shouldReleasePreviousUri(previousUri, cleanUri)) {
                StorageHelper.releasePersistablePermission(appContext, previousUri)
            }
        }
        return committed
    }

    override fun clearCustomDownloadDir(): Boolean {
        val previousUri = getCustomDownloadUri()
        val editor = prefs.edit()
        editor.remove(KEY_CUSTOM_DOWNLOAD_PATH)
        editor.remove(KEY_CUSTOM_DOWNLOAD_URI)
        val cleared = editor.commit()
        if (cleared) {
            _settings.update {
                it.copy(
                    customDownloadPath = null,
                    customDownloadUri = null
                )
            }
            if (shouldReleasePreviousUri(previousUri, null)) {
                StorageHelper.releasePersistablePermission(appContext, previousUri)
            }
        }
        return cleared
    }

    @android.annotation.SuppressLint("UseKtx")
    override fun saveActiveApkDownload(downloadId: Long, releaseTag: String): Boolean {
        if (downloadId <= 0L || releaseTag.isBlank()) return false
        val editor = prefs.edit()
        editor.putLong(KEY_ACTIVE_APK_DOWNLOAD_ID, downloadId)
        editor.putString(KEY_ACTIVE_APK_RELEASE_TAG, releaseTag)
        return editor.commit()
    }

    override fun getActiveApkDownload(): Pair<Long, String>? {
        val id = prefs.getLong(KEY_ACTIVE_APK_DOWNLOAD_ID, -1L)
        val tag = prefs.getString(KEY_ACTIVE_APK_RELEASE_TAG, null)
        return if (id > 0L && !tag.isNullOrBlank()) id to tag else null
    }

    override fun clearActiveApkDownload() {
        prefs.edit {
            remove(KEY_ACTIVE_APK_DOWNLOAD_ID)
            remove(KEY_ACTIVE_APK_RELEASE_TAG)
        }
    }

    private fun readSettings(): SwiftGrabSettings {
        return SwiftGrabSettings(
            instantShareMode = isInstantShareMode(),
            defaultPreset = getDefaultPreset(),
            autoStartClipboard = isAutoStartClipboard(),
            customDownloadPath = getCustomDownloadPath(),
            customDownloadUri = getCustomDownloadUri()
        )
    }

    companion object {
        private const val PREFS_NAME = "swiftgrab_prefs"
        private const val KEY_INSTANT_SHARE_MODE = "instant_share_mode"
        private const val KEY_DEFAULT_PRESET = "default_preset"
        private const val KEY_AUTO_START_CLIPBOARD = "auto_start_clipboard"
        private const val KEY_LAST_CLIPBOARD_URL = "last_clipboard_url"
        private const val KEY_CUSTOM_DOWNLOAD_PATH = "custom_download_path"
        private const val KEY_CUSTOM_DOWNLOAD_URI = "custom_download_uri"
        private const val KEY_ACTIVE_APK_DOWNLOAD_ID = "active_apk_download_id"
        private const val KEY_ACTIVE_APK_RELEASE_TAG = "active_apk_release_tag"

        internal fun shouldReleasePreviousUri(previousUri: String?, newUri: String?): Boolean {
            return !previousUri.isNullOrBlank() && previousUri != newUri
        }
    }
}
