package com.example.swiftgrab.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.swiftgrab.R
import com.example.swiftgrab.SwiftGrabApp
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import com.example.swiftgrab.update.AppUpdateManager
import com.example.swiftgrab.util.FileSharingHelper
import com.example.swiftgrab.util.UrlValidator

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d(TAG, "POST_NOTIFICATIONS permission granted: $isGranted")
        }

    private val requestLegacyStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d(TAG, "WRITE_EXTERNAL_STORAGE permission granted: $isGranted")
            if (!isGranted) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_storage_permission_needed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        requestLegacyStoragePermissionIfNeeded()
        handleIntent(intent)
        viewModel.checkForAppUpdate(manual = false)

        setContent {
            SwiftGrabTheme {
                MainRoute(
                    viewModel = viewModel,
                    onOpenFile = { path -> FileSharingHelper.openFile(this@MainActivity, path) },
                    onShareFile = { path -> FileSharingHelper.shareFile(this@MainActivity, path) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.recheckInstallPermissionOnResume()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(AppUpdateManager.EXTRA_SHOW_UPDATE_DIALOG, false) == true) {
            viewModel.showUpdateDialogOrCheck()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            checkClipboardForMediaUrl()
        }
    }

    private fun checkClipboardForMediaUrl() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0)?.coerceToText(this)?.toString()
                val url = UrlValidator.extractUrl(text)
                if (url != null) {
                    viewModel.onClipboardUrlDetected(url)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to inspect clipboard on window focus", e)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!isGranted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val prefs = (application as SwiftGrabApp).userPreferences
            val hasCustomSafFolder = !prefs.getCustomDownloadUri().isNullOrBlank()
            if (hasCustomSafFolder) return

            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (!isGranted) {
                requestLegacyStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

