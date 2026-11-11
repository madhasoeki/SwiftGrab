package com.example.swiftgrab.data

import com.example.swiftgrab.data.db.DownloadDao
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import com.example.swiftgrab.data.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRepositoryTest {

    private class FakeDownloadDao : DownloadDao {
        private val tasks = MutableStateFlow<Map<String, DownloadTaskEntity>>(emptyMap())

        override fun observeAllTasks(): Flow<List<DownloadTaskEntity>> =
            tasks.map { map -> map.values.sortedByDescending { it.createdAt } }

        override fun observeTasksByStatuses(statuses: List<DownloadStatus>): Flow<List<DownloadTaskEntity>> =
            tasks.map { map ->
                map.values.filter { it.status in statuses }.sortedByDescending { it.createdAt }
            }

        override fun observeTaskById(id: String): Flow<DownloadTaskEntity?> =
            tasks.map { it[id] }

        override suspend fun getTaskById(id: String): DownloadTaskEntity? =
            tasks.value[id]

        override suspend fun getTasksByStatus(status: DownloadStatus): List<DownloadTaskEntity> =
            tasks.value.values.filter { it.status == status }.sortedBy { it.createdAt }

        override suspend fun upsertTask(task: DownloadTaskEntity) {
            tasks.value = tasks.value + (task.id to task)
        }

        override suspend fun updateTask(task: DownloadTaskEntity) {
            tasks.value = tasks.value + (task.id to task)
        }

        override suspend fun updateProgress(
            id: String,
            progress: Float,
            downloadedBytes: Long,
            totalBytes: Long,
            speed: String,
            eta: String,
            activeStatus: DownloadStatus,
            updatedAt: Long
        ) {
            val current = tasks.value[id] ?: return
            if (current.status != activeStatus) return
            tasks.value = tasks.value + (
                id to current.copy(
                    progress = progress,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speed = speed,
                    eta = eta,
                    updatedAt = updatedAt
                )
            )
        }

        override suspend fun updateMetadata(
            id: String,
            title: String,
            thumbnailUrl: String?,
            formatLabel: String,
            updatedAt: Long
        ) {
            val current = tasks.value[id] ?: return
            tasks.value = tasks.value + (
                id to current.copy(
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    formatLabel = formatLabel,
                    updatedAt = updatedAt
                )
            )
        }

        override suspend fun updateStatus(
            id: String,
            status: DownloadStatus,
            errorMessage: String?,
            updatedAt: Long
        ) {
            val current = tasks.value[id] ?: return
            tasks.value = tasks.value + (
                id to current.copy(
                    status = status,
                    errorMessage = errorMessage,
                    updatedAt = updatedAt
                )
            )
        }

        override suspend fun markCompleted(
            id: String,
            outputPath: String,
            totalBytes: Long,
            completedStatus: DownloadStatus,
            updatedAt: Long
        ) {
            val current = tasks.value[id] ?: return
            tasks.value = tasks.value + (
                id to current.copy(
                    status = completedStatus,
                    progress = 100f,
                    outputPath = outputPath,
                    totalBytes = if (totalBytes > 0) totalBytes else current.totalBytes,
                    downloadedBytes = if (totalBytes > 0) totalBytes else current.downloadedBytes,
                    speed = "",
                    eta = "",
                    errorMessage = null,
                    updatedAt = updatedAt
                )
            )
        }

        override suspend fun deleteTask(task: DownloadTaskEntity) {
            tasks.value = tasks.value - task.id
        }

        override suspend fun deleteTaskById(id: String) {
            tasks.value = tasks.value - id
        }

        override suspend fun deleteCompletedTasks(completedStatus: DownloadStatus) {
            tasks.value = tasks.value.filterValues { it.status != completedStatus }
        }
    }

    @Test
    fun `createTask inserts queued task and updates progress and completion`() = runTest {
        val repo = DownloadRepository(FakeDownloadDao())

        val task = repo.createTask(
            id = "task-1",
            url = "https://example.com/video",
            title = "Sample Video",
            formatLabel = "MP4 1080p",
            audioOnly = false
        )

        assertEquals(DownloadStatus.QUEUED, task.status)
        assertEquals(1, repo.allTasks.first().size)

        repo.updateStatus("task-1", DownloadStatus.DOWNLOADING)
        repo.updateProgress(
            id = "task-1",
            progress = 55.5f,
            downloadedBytes = 555_000L,
            totalBytes = 1_000_000L,
            speed = "8.4 MB/s",
            eta = "00:09"
        )

        val inProgress = repo.getTask("task-1")
        assertNotNull(inProgress)
        assertEquals(DownloadStatus.DOWNLOADING, inProgress?.status)
        assertEquals(55.5f, inProgress?.progress)
        assertEquals("8.4 MB/s", inProgress?.speed)

        repo.markCompleted("task-1", "/storage/emulated/0/Download/video.mp4", 1_000_000L)
        val completed = repo.getTask("task-1")
        assertEquals(DownloadStatus.COMPLETED, completed?.status)
        assertEquals(100f, completed?.progress)
        assertEquals("/storage/emulated/0/Download/video.mp4", completed?.outputPath)

        repo.deleteCompletedTasks()
        assertNull(repo.getTask("task-1"))
    }
}
