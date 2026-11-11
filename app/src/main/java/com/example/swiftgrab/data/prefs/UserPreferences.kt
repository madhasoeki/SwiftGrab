package com.example.swiftgrab.data.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SwiftGrabSettings(
    val instantShareMode: Boolean = true,
    val defaultPreset: DownloadPreset = DownloadPreset.BEST_VIDEO,
    val autoStartClipboard: Boolean = false
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
        prefs.edit().putBoolean(KEY_INSTANT_SHARE_MODE, enabled).apply()
        _settings.update { it.copy(instantShareMode = enabled) }
    }

    fun getDefaultPreset(): DownloadPreset =
        DownloadPreset.fromName(prefs.getString(KEY_DEFAULT_PRESET, DownloadPreset.BEST_VIDEO.name))

    fun setDefaultPreset(preset: DownloadPreset) {
        prefs.edit().putString(KEY_DEFAULT_PRESET, preset.name).apply()
        _settings.update { it.copy(defaultPreset = preset) }
    }

    fun isAutoStartClipboard(): Boolean =
        prefs.getBoolean(KEY_AUTO_START_CLIPBOARD, false)

    fun setAutoStartClipboard(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_CLIPBOARD, enabled).apply()
        _settings.update { it.copy(autoStartClipboard = enabled) }
    }

    fun getLastHandledClipboardUrl(): String? =
        prefs.getString(KEY_LAST_CLIPBOARD_URL, null)

    fun setLastHandledClipboardUrl(url: String?) {
        prefs.edit().putString(KEY_LAST_CLIPBOARD_URL, url).apply()
    }

    private fun readSettings(): SwiftGrabSettings {
        return SwiftGrabSettings(
            instantShareMode = isInstantShareMode(),
            defaultPreset = getDefaultPreset(),
            autoStartClipboard = isAutoStartClipboard()
        )
    }

    companion object {
        private const val PREFS_NAME = "swiftgrab_prefs"
        private const val KEY_INSTANT_SHARE_MODE = "instant_share_mode"
        private const val KEY_DEFAULT_PRESET = "default_preset"
        private const val KEY_AUTO_START_CLIPBOARD = "auto_start_clipboard"
        private const val KEY_LAST_CLIPBOARD_URL = "last_clipboard_url"
    }
}
