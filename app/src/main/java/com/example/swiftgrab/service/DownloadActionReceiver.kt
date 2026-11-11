package com.example.swiftgrab.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BroadcastReceiver for handling notification action clicks (Pause, Resume, Cancel).
 * Marked with android:exported="false" to prevent external intent spoofing.
 */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val action = intent.action ?: return

        Log.d(TAG, "Received notification action: $action for task: $taskId")

        val serviceIntent = Intent(context, DownloadForegroundService::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    companion object {
        private const val TAG = "DownloadActionReceiver"
        const val ACTION_PAUSE = "com.example.swiftgrab.action.PAUSE"
        const val ACTION_RESUME = "com.example.swiftgrab.action.RESUME"
        const val ACTION_CANCEL = "com.example.swiftgrab.action.CANCEL"
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
