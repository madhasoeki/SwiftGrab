package com.example.swiftgrab.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.swiftgrab.R
import com.example.swiftgrab.SwiftGrabApp
import com.example.swiftgrab.data.cookies.CookieStorageManager
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.service.DownloadForegroundService
import com.example.swiftgrab.service.MediaMetadata
import com.example.swiftgrab.service.MediaMetadataExtractor
import com.example.swiftgrab.ui.components.QuickFormatMetadataState
import com.example.swiftgrab.ui.components.QuickFormatSheet
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import com.example.swiftgrab.util.StorageHelper
import com.example.swiftgrab.util.UrlValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

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
    private var metadataState by mutableStateOf<QuickFormatMetadataState>(QuickFormatMetadataState.Loading)
    private var contentInitialized = false

    private var metadataJob: Job? = null
    private var activePreviewProcessId: String? = null

    private var pendingLegacyUrl: String? = null
    private var pendingLegacyPreset: DownloadPreset? = null
    private var pendingLegacyMetadata: MediaMetadata? = null

    private val legacySafPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val url = pendingLegacyUrl
            val preset = pendingLegacyPreset ?: DownloadPreset.BEST_VIDEO
            val metadata = pendingLegacyMetadata
            pendingLegacyUrl = null
            pendingLegacyPreset = null
            pendingLegacyMetadata = null

            if (uri != null && url != null) {
                val prefs = (application as SwiftGrabApp).userPreferences
                val display = StorageHelper.saveCustomTreeUri(this, uri, prefs)
                if (display != null) {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_storage_dir_updated, display),
                        Toast.LENGTH_SHORT
                    ).show()
                    dispatchDownloadAndFinish(url, preset, metadata)
                    return@registerForActivityResult
                }
                Toast.makeText(
                    this,
                    getString(R.string.toast_storage_dir_error),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.toast_legacy_storage_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
            finish()
        }

    private val legacyStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            val url = pendingLegacyUrl
            val preset = pendingLegacyPreset ?: DownloadPreset.BEST_VIDEO
            val metadata = pendingLegacyMetadata
            if (url == null) {
                pendingLegacyMetadata = null
                finish()
                return@registerForActivityResult
            }
            if (isGranted) {
                pendingLegacyUrl = null
                pendingLegacyPreset = null
                pendingLegacyMetadata = null
                dispatchDownloadAndFinish(url, preset, metadata)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.toast_legacy_storage_denied),
                    Toast.LENGTH_SHORT
                ).show()
                legacySafPickerLauncher.launch(null)
            }
        }

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
        startPreviewMetadataFetch(url)

        if (!contentInitialized) {
            contentInitialized = true
            setContent {
                SwiftGrabTheme {
                    val currentUrl = activeUrl
                    if (currentUrl != null) {
                        QuickFormatSheet(
                            url = currentUrl,
                            selectedPreset = selectedPreset,
                            metadataState = metadataState,
                            onSelectPreset = { selectedPreset = it },
                            onStartDownload = {
                                val resolvedMetadata = (metadataState as? QuickFormatMetadataState.Loaded)?.metadata
                                cancelPreviewMetadataFetch()
                                startDownloadAndFinish(currentUrl, selectedPreset, resolvedMetadata)
                            },
                            onDismiss = {
                                cancelPreviewMetadataFetch()
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun startPreviewMetadataFetch(url: String) {
        cancelPreviewMetadataFetch()
        metadataState = QuickFormatMetadataState.Loading
        val processId = "preview_${UUID.randomUUID()}"
        activePreviewProcessId = processId

        metadataJob = lifecycleScope.launch {
            val app = application as SwiftGrabApp
            val cookiesFile = CookieStorageManager.getXCookiesFile(this@ShareInterceptActivity)
            val result = withTimeoutOrNull(PREVIEW_METADATA_TIMEOUT_MS) {
                MediaMetadataExtractor.fetch(
                    app = app,
                    url = url,
                    cookiesFile = cookiesFile,
                    processId = processId
                )
            }
            if (activePreviewProcessId == processId) {
                activePreviewProcessId = null
            }
            if (activeUrl == url) {
                metadataState = if (result != null) {
                    QuickFormatMetadataState.Loaded(result)
                } else {
                    QuickFormatMetadataState.Unavailable
                }
            }
        }
    }

    private fun cancelPreviewMetadataFetch() {
        metadataJob?.cancel()
        metadataJob = null
        val processId = activePreviewProcessId
        activePreviewProcessId = null
        if (processId != null) {
            (application as? SwiftGrabApp)?.destroyProcessAsync(processId)
        }
    }

    override fun onDestroy() {
        cancelPreviewMetadataFetch()
        super.onDestroy()
    }

    private fun startDownloadAndFinish(
        url: String,
        preset: DownloadPreset,
        metadata: MediaMetadata? = null
    ) {
        val prefs = (application as SwiftGrabApp).userPreferences
        if (StorageHelper.needsLegacyWritePermission(this, prefs.getCustomDownloadUri())) {
            activeUrl = null
            pendingLegacyUrl = url
            pendingLegacyPreset = preset
            pendingLegacyMetadata = metadata
            legacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        dispatchDownloadAndFinish(url, preset, metadata)
    }

    private fun dispatchDownloadAndFinish(
        url: String,
        preset: DownloadPreset,
        metadata: MediaMetadata? = null
    ) {
        DownloadForegroundService.startDownload(
            context = this,
            url = url,
            preset = preset,
            title = metadata?.title,
            thumbnailUrl = metadata?.thumbnailUrl,
            durationSeconds = metadata?.durationSeconds ?: 0L
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
        private const val PREVIEW_METADATA_TIMEOUT_MS = 12_000L
        const val ACTION_CLIPBOARD_DOWNLOAD = "com.example.swiftgrab.action.CLIPBOARD_DOWNLOAD"
        const val EXTRA_FORCE_FORMAT_SHEET = "extra_force_format_sheet"
        const val EXTRA_INTERNAL_TOKEN = "extra_internal_token"
    }
}
