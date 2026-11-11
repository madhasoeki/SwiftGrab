package com.example.swiftgrab.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import com.example.swiftgrab.R
import com.example.swiftgrab.SwiftGrabApp
import com.example.swiftgrab.ui.ShareInterceptActivity

/**
 * Android Quick Settings Tile allowing 1-tap video/audio download from the system clipboard.
 * Complies with Android 10–15 privacy policies by collapsing the notification shade and delegating
 * clipboard reading to the foreground-focused [ShareInterceptActivity].
 */
class QuickClipboardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Quick Settings tile clicked")

        if (isLocked) {
            unlockAndRun {
                launchClipboardDownload()
            }
        } else {
            launchClipboardDownload()
        }
    }

    private fun launchClipboardDownload() {
        val intent = Intent(this, ShareInterceptActivity::class.java).apply {
            action = ShareInterceptActivity.ACTION_CLIPBOARD_DOWNLOAD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ShareInterceptActivity.EXTRA_INTERNAL_TOKEN, SwiftGrabApp.internalTileToken)
        }

        try {
            val pendingWrapper = PendingIntentActivityWrapper(
                this,
                REQUEST_CODE_TILE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT,
                false
            )
            TileServiceCompat.startActivityAndCollapse(this, pendingWrapper)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ShareInterceptActivity from Quick Settings tile", e)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = getString(R.string.tile_quick_download)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(R.string.tile_quick_download_desc)
        }
        tile.updateTile()
    }

    companion object {
        private const val TAG = "QuickClipboardTileSvc"
        private const val REQUEST_CODE_TILE = 2001
    }
}
