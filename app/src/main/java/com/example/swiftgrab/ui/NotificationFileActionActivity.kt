package com.example.swiftgrab.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.swiftgrab.util.FileSharingHelper

/**
 * Activity transparan internal (non-exported) untuk menangani tombol aksi "Buka" dan "Bagikan"
 * pada notifikasi unduhan selesai melalui PendingIntent.getActivity agar patuh terhadap
 * pembatasan notification trampoline Android 12+.
 */
class NotificationFileActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        finish()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        if (!isSupportedAction(action)) return

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath.isNullOrBlank()) return

        if (intent.hasExtra(EXTRA_NOTIFICATION_ID)) {
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)
        }

        when (action) {
            ACTION_OPEN_FILE -> FileSharingHelper.openFile(this, filePath)
            ACTION_SHARE_FILE -> FileSharingHelper.shareFile(this, filePath)
        }
    }

    companion object {
        const val ACTION_OPEN_FILE = "com.example.swiftgrab.action.OPEN_FILE"
        const val ACTION_SHARE_FILE = "com.example.swiftgrab.action.SHARE_FILE"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        internal fun isSupportedAction(action: String?): Boolean {
            return action == ACTION_OPEN_FILE || action == ACTION_SHARE_FILE
        }

        fun createIntent(
            context: Context,
            action: String,
            filePath: String,
            notificationId: Int
        ): Intent {
            require(isSupportedAction(action)) { "Unsupported file notification action: $action" }
            return Intent(context, NotificationFileActionActivity::class.java).apply {
                this.action = action
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
        }
    }
}
