package com.example.swiftgrab.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
 * SharedPreferences-backed settings store exposing reactive [StateFlow] and synchronous reads.
 */
class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<SwiftGrabSettings> = _settings.asStateFlow()

    fun isInstantShareMode(): Boolean =
        prefs.getBoolean(KEY_INSTANT_SHARE_MODE, true)

    fun setInstantShareMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_INSTANT_SHARE_MODE, enabled) }
        _settings.update { it.copy(instantShareMode = enabled) }
    }

    fun getDefaultPreset(): DownloadPreset =
        DownloadPreset.fromName(prefs.getString(KEY_DEFAULT_PRESET, DownloadPreset.BEST_VIDEO.name))

    fun setDefaultPreset(preset: DownloadPreset) {
        prefs.edit { putString(KEY_DEFAULT_PRESET, preset.name) }
        _settings.update { it.copy(defaultPreset = preset) }
    }

    fun isAutoStartClipboard(): Boolean =
        prefs.getBoolean(KEY_AUTO_START_CLIPBOARD, false)

    fun setAutoStartClipboard(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_START_CLIPBOARD, enabled) }
        _settings.update { it.copy(autoStartClipboard = enabled) }
    }

    fun getLastHandledClipboardUrl(): String? =
        prefs.getString(KEY_LAST_CLIPBOARD_URL, null)

    fun setLastHandledClipboardUrl(url: String?) {
        prefs.edit {
            if (url.isNullOrBlank()) remove(KEY_LAST_CLIPBOARD_URL)
            else putString(KEY_LAST_CLIPBOARD_URL, url)
        }
    }

    fun getCustomDownloadPath(): String? =
        prefs.getString(KEY_CUSTOM_DOWNLOAD_PATH, null)

    fun setCustomDownloadPath(path: String?) {
        prefs.edit {
            if (path.isNullOrBlank()) remove(KEY_CUSTOM_DOWNLOAD_PATH)
            else putString(KEY_CUSTOM_DOWNLOAD_PATH, path)
        }
        _settings.update { it.copy(customDownloadPath = path?.takeIf { p -> p.isNotBlank() }) }
    }

    fun getCustomDownloadUri(): String? =
        prefs.getString(KEY_CUSTOM_DOWNLOAD_URI, null)

    fun setCustomDownloadUri(uri: String?) {
        prefs.edit {
            if (uri.isNullOrBlank()) remove(KEY_CUSTOM_DOWNLOAD_URI)
            else putString(KEY_CUSTOM_DOWNLOAD_URI, uri)
        }
        _settings.update { it.copy(customDownloadUri = uri?.takeIf { u -> u.isNotBlank() }) }
    }

    fun setCustomDownloadLocation(path: String?, uri: String?) {
        val cleanPath = path?.takeIf { it.isNotBlank() }
        val cleanUri = uri?.takeIf { it.isNotBlank() }
        prefs.edit {
            if (cleanPath == null) remove(KEY_CUSTOM_DOWNLOAD_PATH)
            else putString(KEY_CUSTOM_DOWNLOAD_PATH, cleanPath)

            if (cleanUri == null) remove(KEY_CUSTOM_DOWNLOAD_URI)
            else putString(KEY_CUSTOM_DOWNLOAD_URI, cleanUri)
        }
        _settings.update {
            it.copy(
                customDownloadPath = cleanPath,
                customDownloadUri = cleanUri
            )
        }
    }

    fun clearCustomDownloadDir() {
        prefs.edit {
            remove(KEY_CUSTOM_DOWNLOAD_PATH)
            remove(KEY_CUSTOM_DOWNLOAD_URI)
        }
        _settings.update {
            it.copy(
                customDownloadPath = null,
                customDownloadUri = null
            )
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
    }
}
