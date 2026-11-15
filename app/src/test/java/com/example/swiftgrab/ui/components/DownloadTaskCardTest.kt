package com.example.swiftgrab.ui.components

import android.app.Application
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DownloadTaskCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderCard(
        task: DownloadTaskEntity,
        onPause: (String) -> Unit = {},
        onResume: (String) -> Unit = {},
        onCancel: (String) -> Unit = {},
        onDelete: (String) -> Unit = {},
        onOpenFile: (String) -> Unit = {},
        onShareFile: (String) -> Unit = {},
        onRetry: (DownloadTaskEntity) -> Unit = {},
        onShowError: (DownloadTaskEntity) -> Unit = {}
    ) {
        composeTestRule.setContent {
            SwiftGrabTheme {
                DownloadTaskCard(
                    task = task,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onDelete = onDelete,
                    onOpenFile = onOpenFile,
                    onShareFile = onShareFile,
                    onRetry = onRetry,
                    onShowError = onShowError
                )
            }
        }
    }

    @Test
    fun `downloading task displays metrics, determinate progress, and triggers pause and cancel`() {
        var pausedId: String? = null
        var canceledId: String? = null

        val task = DownloadTaskEntity(
            id = "task-dl",
            url = "https://youtube.com/watch?v=1",
            title = "Nature Documentary 4K",
            formatLabel = "MP4 1080p",
            status = DownloadStatus.DOWNLOADING,
            progress = 50f,
            downloadedBytes = 50L * 1024L * 1024L,
            totalBytes = 100L * 1024L * 1024L,
            speed = "5.0 MB/s",
            eta = "00:11"
        )

        renderCard(
            task = task,
            onPause = { pausedId = it },
            onCancel = { canceledId = it }
        )

        composeTestRule.onNodeWithText("Nature Documentary 4K").assertIsDisplayed()
        composeTestRule.onNodeWithText("MP4 1080p").assertIsDisplayed()
        composeTestRule.onNodeWithText("50.0 MB / 100.0 MB • 50% • 5.0 MB/s • ETA 00:11").assertIsDisplayed()
        composeTestRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f))
        ).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Jeda unduhan").performClick()
        composeTestRule.onNodeWithContentDescription("Batalkan unduhan").performClick()

        assertEquals("task-dl", pausedId)
        assertEquals("task-dl", canceledId)
        composeTestRule.onNodeWithContentDescription("Lanjutkan unduhan").assertDoesNotExist()
    }

    @Test
    fun `paused task displays paused percentage and triggers resume and cancel`() {
        var resumedId: String? = null
        var canceledId: String? = null

        val task = DownloadTaskEntity(
            id = "task-paused",
            url = "https://youtube.com/watch?v=2",
            title = "Synthwave Mix",
            formatLabel = "MP3",
            audioOnly = true,
            status = DownloadStatus.PAUSED,
            progress = 42.9f
        )

        renderCard(
            task = task,
            onResume = { resumedId = it },
            onCancel = { canceledId = it }
        )

        composeTestRule.onNodeWithText("Synthwave Mix").assertIsDisplayed()
        composeTestRule.onNodeWithText("MP3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dijeda • 42%").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Lanjutkan unduhan").performClick()
        composeTestRule.onNodeWithContentDescription("Batalkan unduhan").performClick()

        assertEquals("task-paused", resumedId)
        assertEquals("task-paused", canceledId)
        composeTestRule.onNodeWithContentDescription("Jeda unduhan").assertDoesNotExist()
    }

    @Test
    fun `queued task displays indeterminate progress and cancel action`() {
        var canceledId: String? = null

        val task = DownloadTaskEntity(
            id = "task-queued",
            url = "https://youtube.com/watch?v=3",
            title = "Queued Clip",
            formatLabel = "MP4 Terbaik",
            status = DownloadStatus.QUEUED
        )

        renderCard(
            task = task,
            onCancel = { canceledId = it }
        )

        composeTestRule.onNodeWithText("Menunggu antrean…").assertIsDisplayed()
        composeTestRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        ).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Batalkan unduhan").performClick()
        assertEquals("task-queued", canceledId)
        composeTestRule.onNodeWithContentDescription("Jeda unduhan").assertDoesNotExist()
    }

    @Test
    fun `completed task displays formatted GB size and triggers open, share, and delete`() {
        var openedPath: String? = null
        var sharedPath: String? = null
        var deletedId: String? = null

        val task = DownloadTaskEntity(
            id = "task-done",
            url = "https://youtube.com/watch?v=4",
            title = "Completed Feature Film",
            formatLabel = "MP4 1080p",
            status = DownloadStatus.COMPLETED,
            progress = 100f,
            totalBytes = 2L * 1024L * 1024L * 1024L,
            outputPath = "/storage/emulated/0/Download/SwiftGrab/film.mp4"
        )

        renderCard(
            task = task,
            onOpenFile = { openedPath = it },
            onShareFile = { sharedPath = it },
            onDelete = { deletedId = it }
        )

        composeTestRule.onNodeWithText("Selesai • 2.0 GB").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Buka berkas").performClick()
        composeTestRule.onNodeWithContentDescription("Bagikan berkas").performClick()
        composeTestRule.onNodeWithContentDescription("Hapus tugas").performClick()

        assertEquals("/storage/emulated/0/Download/SwiftGrab/film.mp4", openedPath)
        assertEquals("/storage/emulated/0/Download/SwiftGrab/film.mp4", sharedPath)
        assertEquals("task-done", deletedId)
    }

    @Test
    fun `completed task without outputPath ignores open and share clicks`() {
        var openCalled = false
        var shareCalled = false

        val task = DownloadTaskEntity(
            id = "task-done-no-path",
            url = "https://youtube.com/watch?v=4b",
            title = "Completed Without Path",
            status = DownloadStatus.COMPLETED,
            progress = 100f,
            totalBytes = 0L,
            outputPath = null
        )

        renderCard(
            task = task,
            onOpenFile = { openCalled = true },
            onShareFile = { shareCalled = true }
        )

        composeTestRule.onNodeWithText("Selesai").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Buka berkas").performClick()
        composeTestRule.onNodeWithContentDescription("Bagikan berkas").performClick()

        assertTrue(!openCalled)
        assertTrue(!shareCalled)
    }

    @Test
    fun `failed task maps friendly error and triggers retry, showError, and delete`() {
        var retriedTask: DownloadTaskEntity? = null
        var errorTask: DownloadTaskEntity? = null
        var deletedId: String? = null

        val task = DownloadTaskEntity(
            id = "task-failed",
            url = "https://youtube.com/watch?v=5",
            title = "Broken Stream",
            status = DownloadStatus.FAILED,
            errorMessage = "ERROR: Video unavailable in your country"
        )

        renderCard(
            task = task,
            onRetry = { retriedTask = it },
            onShowError = { errorTask = it },
            onDelete = { deletedId = it }
        )

        composeTestRule.onNodeWithText("Gagal • Video tidak tersedia / dihapus").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Coba lagi unduhan").performClick()
        composeTestRule.onNodeWithContentDescription("Lihat alasan kegagalan").performClick()
        composeTestRule.onNodeWithContentDescription("Hapus tugas").performClick()

        assertEquals(task, retriedTask)
        assertEquals(task, errorTask)
        assertEquals("task-failed", deletedId)
    }

    @Test
    fun `canceled task displays canceled label and retry, error info, and delete actions`() {
        var retriedTask: DownloadTaskEntity? = null

        val task = DownloadTaskEntity(
            id = "task-canceled",
            url = "https://youtube.com/watch?v=6",
            title = "Canceled Clip",
            status = DownloadStatus.CANCELED
        )

        renderCard(
            task = task,
            onRetry = { retriedTask = it }
        )

        composeTestRule.onNodeWithText("Dibatalkan").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Coba lagi unduhan").performClick()
        composeTestRule.onNodeWithContentDescription("Lihat alasan kegagalan").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Hapus tugas").assertIsDisplayed()

        assertEquals(task, retriedTask)
    }
}
