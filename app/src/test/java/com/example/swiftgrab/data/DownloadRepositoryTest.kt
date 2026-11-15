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

        override suspend fun reconcileDownloadingTasksToPaused(
            downloadingStatus: DownloadStatus,
            pausedStatus: DownloadStatus,
            updatedAt: Long
        ): Int {
            var updated = 0
            val nextMap = tasks.value.mapValues { (_, task) ->
                if (task.status == downloadingStatus) {
                    updated++
                    task.copy(
                        status = pausedStatus,
                        speed = "",
                        eta = "",
                        errorMessage = null,
                        updatedAt = updatedAt
                    )
                } else {
                    task
                }
            }
            tasks.value = nextMap
            return updated
        }

        override suspend fun reconcileDownloadingTaskToPaused(
            id: String,
            downloadingStatus: DownloadStatus,
            pausedStatus: DownloadStatus,
            updatedAt: Long
        ): Int {
            val current = tasks.value[id] ?: return 0
            if (current.status != downloadingStatus) return 0
            tasks.value = tasks.value + (
                id to current.copy(
                    status = pausedStatus,
                    speed = "",
                    eta = "",
                    errorMessage = null,
                    updatedAt = updatedAt
                )
            )
            return 1
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

    @Test
    fun `reconcileInterruptedDownloads converts stuck DOWNLOADING tasks to PAUSED while preserving progress`() = runTest {
        val dao = FakeDownloadDao()
        val repo = DownloadRepository(dao)

        dao.upsertTask(
            DownloadTaskEntity(
                id = "stuck-1",
                url = "https://example.com/1",
                title = "Video 1",
                status = DownloadStatus.DOWNLOADING,
                progress = 42.5f,
                downloadedBytes = 425_000L,
                totalBytes = 1_000_000L,
                speed = "3.2 MB/s",
                eta = "00:12"
            )
        )
        dao.upsertTask(
            DownloadTaskEntity(
                id = "queued-1",
                url = "https://example.com/2",
                title = "Video 2",
                status = DownloadStatus.QUEUED
            )
        )
        dao.upsertTask(
            DownloadTaskEntity(
                id = "completed-1",
                url = "https://example.com/3",
                title = "Video 3",
                status = DownloadStatus.COMPLETED,
                progress = 100f
            )
        )
        dao.upsertTask(
            DownloadTaskEntity(
                id = "failed-1",
                url = "https://example.com/4",
                title = "Video 4",
                status = DownloadStatus.FAILED,
                errorMessage = "Network error"
            )
        )

        val recovered = repo.reconcileInterruptedDownloads()
        assertEquals(1, recovered)

        val stuck = repo.getTask("stuck-1")
        assertEquals(DownloadStatus.PAUSED, stuck?.status)
        assertEquals(42.5f, stuck?.progress)
        assertEquals(425_000L, stuck?.downloadedBytes)
        assertEquals(1_000_000L, stuck?.totalBytes)
        assertEquals("", stuck?.speed)
        assertEquals("", stuck?.eta)
        assertNull(stuck?.errorMessage)

        assertEquals(DownloadStatus.QUEUED, repo.getTask("queued-1")?.status)
        assertEquals(DownloadStatus.COMPLETED, repo.getTask("completed-1")?.status)
        assertEquals(DownloadStatus.FAILED, repo.getTask("failed-1")?.status)
        assertEquals("Network error", repo.getTask("failed-1")?.errorMessage)
    }

    @Test
    fun `reconcileInterruptedDownloads excludes active task ids and leaves non-downloading tasks untouched`() = runTest {
        val dao = FakeDownloadDao()
        val repo = DownloadRepository(dao)

        dao.upsertTask(
            DownloadTaskEntity(
                id = "active-1",
                url = "https://example.com/active",
                title = "Active",
                status = DownloadStatus.DOWNLOADING,
                progress = 60f,
                speed = "5.0 MB/s",
                eta = "00:05"
            )
        )
        dao.upsertTask(
            DownloadTaskEntity(
                id = "orphaned-1",
                url = "https://example.com/orphaned",
                title = "Orphaned",
                status = DownloadStatus.DOWNLOADING,
                progress = 20f,
                speed = "1.0 MB/s",
                eta = "00:40"
            )
        )

        val recovered = repo.reconcileInterruptedDownloads(activeTaskIds = setOf("active-1"))
        assertEquals(1, recovered)

        val active = repo.getTask("active-1")
        assertEquals(DownloadStatus.DOWNLOADING, active?.status)
        assertEquals("5.0 MB/s", active?.speed)

        val orphaned = repo.getTask("orphaned-1")
        assertEquals(DownloadStatus.PAUSED, orphaned?.status)
        assertEquals(20f, orphaned?.progress)
        assertEquals("", orphaned?.speed)
        assertEquals("", orphaned?.eta)

        // Calling reconcileInterruptedDownload on a non-DOWNLOADING task must be a no-op
        assertEquals(0, repo.reconcileInterruptedDownload("orphaned-1"))
    }
}
