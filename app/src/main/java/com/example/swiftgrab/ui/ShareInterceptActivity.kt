package com.example.swiftgrab.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.swiftgrab.R
import com.example.swiftgrab.SwiftGrabApp
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.service.DownloadForegroundService
import com.example.swiftgrab.ui.components.QuickFormatSheet
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import com.example.swiftgrab.util.UrlValidator

/**
 * Transparent activity that captures ACTION_SEND intents and Quick Settings clipboard triggers.
 * Supports two user-configurable modes:
 * 1. Instant / One-Tap Mode (default): Dispatches download directly, shows a short toast, and finishes.
 * 2. Quick Format Sheet Mode: Renders a compact ModalBottomSheet for selecting video/audio format presets.
 */
class ShareInterceptActivity : ComponentActivity() {

    private var pendingClipboardRead = false
    private var activeUrl by mutableStateOf<String?>(null)
    private var selectedPreset by mutableStateOf(DownloadPreset.BEST_VIDEO)
    private var contentInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        processIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pendingClipboardRead) {
            pendingClipboardRead = false
            readClipboardAndDispatch()
        }
    }

    private fun processIntent(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                val url = UrlValidator.extractUrl(sharedText)
                if (url == null) {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_invalid_url),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return
                }
                handleExtractedUrl(url)
            }
            ACTION_CLIPBOARD_DOWNLOAD -> {
                val token = intent.getStringExtra(EXTRA_INTERNAL_TOKEN)
                if (token != SwiftGrabApp.internalTileToken) {
                    Log.w(TAG, "Rejected unauthorized ACTION_CLIPBOARD_DOWNLOAD from external caller")
                    finish()
                    return
                }

                if (hasWindowFocus()) {
                    readClipboardAndDispatch()
                } else {
                    pendingClipboardRead = true
                }
            }
            else -> {
                finish()
            }
        }
    }

    private fun readClipboardAndDispatch() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipData = clipboard?.primaryClip
        val clipText = if (clipData != null && clipData.itemCount > 0) {
            clipData.getItemAt(0)?.coerceToText(this)?.toString()
        } else {
            null
        }

        val url = UrlValidator.extractUrl(clipText)
        if (url == null) {
            Toast.makeText(
                this,
                getString(R.string.toast_clipboard_empty),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        val prefs = (application as SwiftGrabApp).userPreferences
        prefs.setLastHandledClipboardUrl(url)
        handleExtractedUrl(url)
    }

    private fun handleExtractedUrl(url: String) {
        val prefs = (application as SwiftGrabApp).userPreferences
        val defaultPreset = prefs.getDefaultPreset()
        val forceSheet = intent?.getBooleanExtra(EXTRA_FORCE_FORMAT_SHEET, false) == true

        if (prefs.isInstantShareMode() && !forceSheet) {
            startDownloadAndFinish(url, defaultPreset)
        } else {
            showFormatSheet(url, defaultPreset)
        }
    }

    private fun showFormatSheet(url: String, defaultPreset: DownloadPreset) {
        activeUrl = url
        selectedPreset = defaultPreset

        if (!contentInitialized) {
            contentInitialized = true
            setContent {
                SwiftGrabTheme {
                    val currentUrl = activeUrl
                    if (currentUrl != null) {
                        QuickFormatSheet(
                            url = currentUrl,
                            selectedPreset = selectedPreset,
                            onSelectPreset = { selectedPreset = it },
                            onStartDownload = {
                                startDownloadAndFinish(currentUrl, selectedPreset)
                            },
                            onDismiss = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun startDownloadAndFinish(url: String, preset: DownloadPreset) {
        DownloadForegroundService.startDownload(
            context = this,
            url = url,
            preset = preset
        )
        val domain = UrlValidator.extractDomain(url)
        Toast.makeText(
            this,
            getString(R.string.toast_download_started, domain.ifBlank { url }),
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }

    companion object {
        private const val TAG = "ShareInterceptActivity"
        const val ACTION_CLIPBOARD_DOWNLOAD = "com.example.swiftgrab.action.CLIPBOARD_DOWNLOAD"
        const val EXTRA_FORCE_FORMAT_SHEET = "extra_force_format_sheet"
        const val EXTRA_INTERNAL_TOKEN = "extra_internal_token"
    }
}
