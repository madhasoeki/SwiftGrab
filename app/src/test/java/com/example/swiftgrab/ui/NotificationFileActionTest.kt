package com.example.swiftgrab.ui

import com.example.swiftgrab.service.DownloadActionReceiver
import com.example.swiftgrab.service.DownloadForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFileActionTest {

    @Test
    fun `isSupportedAction accepts only open and share actions`() {
        assertTrue(NotificationFileActionActivity.isSupportedAction(NotificationFileActionActivity.ACTION_OPEN_FILE))
        assertTrue(NotificationFileActionActivity.isSupportedAction(NotificationFileActionActivity.ACTION_SHARE_FILE))

        assertFalse(NotificationFileActionActivity.isSupportedAction(null))
        assertFalse(NotificationFileActionActivity.isSupportedAction(""))
        assertFalse(NotificationFileActionActivity.isSupportedAction(DownloadActionReceiver.ACTION_PAUSE))
        assertFalse(NotificationFileActionActivity.isSupportedAction("com.example.other.ACTION"))
    }

    @Test
    fun `buildActionRequestCode produces distinct codes per task and action`() {
        val taskA = "task-uuid-1"
        val taskB = "task-uuid-2"

        val openA = DownloadForegroundService.buildActionRequestCode(taskA, NotificationFileActionActivity.ACTION_OPEN_FILE)
        val shareA = DownloadForegroundService.buildActionRequestCode(taskA, NotificationFileActionActivity.ACTION_SHARE_FILE)
        val openB = DownloadForegroundService.buildActionRequestCode(taskB, NotificationFileActionActivity.ACTION_OPEN_FILE)
        val pauseA = DownloadForegroundService.buildActionRequestCode(taskA, DownloadActionReceiver.ACTION_PAUSE)

        assertNotEquals(openA, shareA)
        assertNotEquals(openA, openB)
        assertNotEquals(openA, pauseA)
    }

    @Test
    fun `buildActionDataUriString prevents collisions even when taskId hashCodes match`() {
        val collidingIdA = "Aa"
        val collidingIdB = "BB"
        assertEquals(collidingIdA.hashCode(), collidingIdB.hashCode())

        val uriOpenA = NotificationFileActionActivity.buildActionDataUriString(
            taskId = collidingIdA,
            action = NotificationFileActionActivity.ACTION_OPEN_FILE
        )
        val uriOpenB = NotificationFileActionActivity.buildActionDataUriString(
            taskId = collidingIdB,
            action = NotificationFileActionActivity.ACTION_OPEN_FILE
        )
        val uriShareA = NotificationFileActionActivity.buildActionDataUriString(
            taskId = collidingIdA,
            action = NotificationFileActionActivity.ACTION_SHARE_FILE
        )

        assertNotEquals(uriOpenA, uriOpenB)
        assertNotEquals(uriOpenA, uriShareA)
    }
}
