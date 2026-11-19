package com.example.swiftgrab.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class DownloadForegroundServiceTest {

    @Test
    fun `foreground notification id is 1001`() {
        assertEquals(1001, DownloadForegroundService.NOTIFICATION_FOREGROUND_ID)
    }

    @Test
    fun `paused notification id uses separate namespace and positive integer`() {
        val taskId = "test-task-123"
        val expected = 200_000 + (taskId.hashCode() and 0x0FFF_FFFF)
        val actual = DownloadForegroundService.pausedNotificationId(taskId)

        assertEquals(expected, actual)
        assertTrue("Paused notification ID must be >= 200,000", actual >= 200_000)
        assertNotEquals(DownloadForegroundService.NOTIFICATION_FOREGROUND_ID, actual)
    }

    @Test
    fun `paused notification id handles negative hash codes safely`() {
        // Find or synthesize strings that yield negative hashCodes
        val negativeHashIds = listOf(
            "task_negative_hash_candidate_1",
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "special_hash_!@#$"
        )

        for (id in negativeHashIds) {
            val notifId = DownloadForegroundService.pausedNotificationId(id)
            assertTrue("ID must be non-negative and >= 200_000 for $id", notifId >= 200_000)
            assertNotEquals(DownloadForegroundService.NOTIFICATION_FOREGROUND_ID, notifId)
        }
    }

    @Test
    fun `different task IDs produce distinct paused notification IDs`() {
        val id1 = "task-alpha"
        val id2 = "task-beta"

        val notifId1 = DownloadForegroundService.pausedNotificationId(id1)
        val notifId2 = DownloadForegroundService.pausedNotificationId(id2)

        assertNotEquals(notifId1, notifId2)
    }
}
