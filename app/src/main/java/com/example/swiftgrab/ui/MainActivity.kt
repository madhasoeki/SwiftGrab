package com.example.swiftgrab.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import com.example.swiftgrab.util.FileSharingHelper
import com.example.swiftgrab.util.UrlValidator

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            Log.d(TAG, "POST_NOTIFICATIONS permission granted: $isGranted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

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

    companion object {
        private const val TAG = "MainActivity"
    }
}

