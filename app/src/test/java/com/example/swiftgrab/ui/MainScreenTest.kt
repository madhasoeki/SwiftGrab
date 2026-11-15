package com.example.swiftgrab.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderMainScreen(
        uiState: DownloadUiState,
        onSetFilter: (TaskFilter) -> Unit = {},
        onDownloadVideoFromBanner: (String) -> Unit = {},
        onDownloadAudioFromBanner: (String) -> Unit = {},
        onDismissBanner: () -> Unit = {},
        onPauseTask: (String) -> Unit = {},
        onResumeTask: (String) -> Unit = {},
        onCancelTask: (String) -> Unit = {},
        onDeleteTask: (String) -> Unit = {},
        onRetryTask: (DownloadTaskEntity) -> Unit = {},
        onClearCompletedTasks: () -> Unit = {},
        onOpenFile: (String) -> Unit = {},
        onShareFile: (String) -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenManualDialog: () -> Unit = {},
        onShowErrorTask: (DownloadTaskEntity) -> Unit = {}
    ) {
        composeTestRule.setContent {
            SwiftGrabTheme {
                MainScreen(
                    uiState = uiState,
                    ytDlpVersion = "2026.03.01",
                    onSetFilter = onSetFilter,
                    onDownloadVideoFromBanner = onDownloadVideoFromBanner,
                    onDownloadAudioFromBanner = onDownloadAudioFromBanner,
                    onDismissBanner = onDismissBanner,
                    onPauseTask = onPauseTask,
                    onResumeTask = onResumeTask,
                    onCancelTask = onCancelTask,
                    onDeleteTask = onDeleteTask,
                    onRetryTask = onRetryTask,
                    onClearCompletedTasks = onClearCompletedTasks,
                    onOpenFile = onOpenFile,
                    onShareFile = onShareFile,
                    onOpenSettings = onOpenSettings,
                    onOpenManualDialog = onOpenManualDialog,
                    onShowErrorTask = onShowErrorTask
                )
            }
        }
    }

    private fun sampleTask(
        id: String,
        title: String,
        status: DownloadStatus,
        outputPath: String? = null
    ) = DownloadTaskEntity(
        id = id,
        url = "https://example.com/$id",
        title = title,
        formatLabel = "MP4 1080p",
        status = status,
        outputPath = outputPath
    )

    @Test
    fun `engine badge reflects isEngineReady and empty state shows default copy for ALL filter`() {
        var settingsClicked = false
        var manualClicked = false

        renderMainScreen(
            uiState = DownloadUiState(
                isEngineReady = false,
                activeFilter = TaskFilter.ALL,
                tasks = emptyList(),
                filteredTasks = emptyList()
            ),
            onOpenSettings = { settingsClicked = true },
            onOpenManualDialog = { manualClicked = true }
        )

        composeTestRule.onNodeWithText("SwiftGrab").assertIsDisplayed()
        composeTestRule.onNodeWithText("Menyiapkan mesin…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Belum Ada Unduhan").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Bersihkan Riwayat Selesai").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("Pengaturan").performClick()
        composeTestRule.onNodeWithText("+ Tautan Manual", useUnmergedTree = true).performClick()

        assertTrue(settingsClicked)
        assertTrue(manualClicked)
    }

    @Test
    fun `filtered empty state shows category message and hides default description`() {
        renderMainScreen(
            uiState = DownloadUiState(
                isEngineReady = true,
                activeFilter = TaskFilter.COMPLETED,
                tasks = listOf(sampleTask("1", "Active Video", DownloadStatus.DOWNLOADING)),
                filteredTasks = emptyList()
            )
        )

        composeTestRule.onNodeWithText("yt-dlp siap").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tidak ada unduhan di kategori Selesai").assertIsDisplayed()
        composeTestRule.onNodeWithText("Belum Ada Unduhan").assertDoesNotExist()
    }

    @Test
    fun `filter chips display accurate counts, selection state, and invoke onSetFilter`() {
        val tasks = listOf(
            sampleTask("1", "Downloading", DownloadStatus.DOWNLOADING),
            sampleTask("2", "Paused", DownloadStatus.PAUSED),
            sampleTask("3", "Queued", DownloadStatus.QUEUED),
            sampleTask("4", "Completed", DownloadStatus.COMPLETED, outputPath = "/tmp/done.mp4"),
            sampleTask("5", "Failed", DownloadStatus.FAILED),
            sampleTask("6", "Canceled", DownloadStatus.CANCELED)
        )

        var selectedFilter: TaskFilter? = null
        var clearCompletedClicked = false

        renderMainScreen(
            uiState = DownloadUiState(
                isEngineReady = true,
                activeFilter = TaskFilter.ACTIVE,
                tasks = tasks,
                filteredTasks = tasks.take(3)
            ),
            onSetFilter = { selectedFilter = it },
            onClearCompletedTasks = { clearCompletedClicked = true }
        )

        composeTestRule.onNodeWithText("Semua (6)").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onNodeWithText("Aktif (3)").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithText("Selesai (1)").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onNodeWithText("Gagal (2)").assertIsDisplayed().assertIsNotSelected()

        composeTestRule.onNodeWithText("Selesai (1)").performClick()
        assertEquals(TaskFilter.COMPLETED, selectedFilter)

        composeTestRule.onNodeWithContentDescription("Bersihkan Riwayat Selesai")
            .assertIsDisplayed()
            .performClick()
        assertTrue(clearCompletedClicked)
    }

    @Test
    fun `clipboard suggestion banner renders domain and wires video, audio, and dismiss callbacks`() {
        val clipboardUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        var videoUrl: String? = null
        var audioUrl: String? = null
        var dismissed = false

        renderMainScreen(
            uiState = DownloadUiState(
                isEngineReady = true,
                tasks = emptyList(),
                filteredTasks = emptyList(),
                clipboardSuggestionUrl = clipboardUrl
            ),
            onDownloadVideoFromBanner = { videoUrl = it },
            onDownloadAudioFromBanner = { audioUrl = it },
            onDismissBanner = { dismissed = true }
        )

        composeTestRule.onNodeWithText("Belum Ada Unduhan").assertDoesNotExist()
        composeTestRule.onNodeWithText("youtube.com").assertIsDisplayed()

        composeTestRule.onNodeWithText("Unduh Video").performClick()
        composeTestRule.onNodeWithText("Unduh Audio").performClick()
        composeTestRule.onNodeWithContentDescription("Tutup banner").performClick()

        assertEquals(clipboardUrl, videoUrl)
        assertEquals(clipboardUrl, audioUrl)
        assertTrue(dismissed)
    }

    @Test
    fun `task list renders DownloadTaskCard items and propagates card actions`() {
        val activeTask = sampleTask("dl-1", "Active Video Title", DownloadStatus.DOWNLOADING)
        val completedTask = sampleTask(
            id = "done-1",
            title = "Finished Video Title",
            status = DownloadStatus.COMPLETED,
            outputPath = "/sdcard/Download/finished.mp4"
        )
        val failedTask = sampleTask("fail-1", "Failed Video Title", DownloadStatus.FAILED)

        var pausedId: String? = null
        var openedPath: String? = null
        var retriedTask: DownloadTaskEntity? = null

        renderMainScreen(
            uiState = DownloadUiState(
                isEngineReady = true,
                tasks = listOf(activeTask, completedTask, failedTask),
                filteredTasks = listOf(activeTask, completedTask, failedTask)
            ),
            onPauseTask = { pausedId = it },
            onOpenFile = { openedPath = it },
            onRetryTask = { retriedTask = it }
        )

        composeTestRule.onNodeWithText("Active Video Title").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Jeda unduhan").performClick()

        composeTestRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Finished Video Title"))
        composeTestRule.onNodeWithText("Finished Video Title").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Buka berkas").performClick()

        composeTestRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Failed Video Title"))
        composeTestRule.onNodeWithText("Failed Video Title").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Coba lagi unduhan").performClick()

        assertEquals("dl-1", pausedId)
        assertEquals("/sdcard/Download/finished.mp4", openedPath)
        assertEquals(failedTask, retriedTask)
    }
}
