package com.example.swiftgrab.data.repository

import com.example.swiftgrab.data.db.DownloadDao
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * In-memory snapshot of fast-changing progress metrics for an active download task.
 * Kept separate from [DownloadTaskEntity] so Room [DownloadRepository.allTasks] does not
 * re-emit the entire task list on every 250ms progress tick.
 */
data class LiveProgress(
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: String = "",
    val eta: String = ""
)

/**
 * Single Source of Truth (SSOT) repository for download tasks.
 * Exposes suspend and Flow APIs without internal scopes.
 */
class DownloadRepository(
    private val downloadDao: DownloadDao
) {

    val allTasks: Flow<List<DownloadTaskEntity>> = downloadDao.observeAllTasks()

    private val _liveProgress = MutableStateFlow<Map<String, LiveProgress>>(emptyMap())
    val liveProgress: StateFlow<Map<String, LiveProgress>> = _liveProgress.asStateFlow()

    fun observeTasksByStatuses(statuses: List<DownloadStatus>): Flow<List<DownloadTaskEntity>> =
        downloadDao.observeTasksByStatuses(statuses)

    fun observeTask(id: String): Flow<DownloadTaskEntity?> =
        downloadDao.observeTaskById(id)

    suspend fun getTask(id: String): DownloadTaskEntity? =
        downloadDao.getTaskById(id)

    suspend fun getTasksByStatus(status: DownloadStatus): List<DownloadTaskEntity> =
        downloadDao.getTasksByStatus(status)

    suspend fun upsertTask(task: DownloadTaskEntity) {
        if (task.status != DownloadStatus.DOWNLOADING) {
            clearLiveProgress(task.id)
        }
        downloadDao.upsertTask(task)
    }

    suspend fun createTask(
        url: String,
        title: String = url,
        thumbnailUrl: String? = null,
        formatLabel: String = "Auto",
        audioOnly: Boolean = false,
        id: String = UUID.randomUUID().toString()
    ): DownloadTaskEntity {
        val task = DownloadTaskEntity(
            id = id,
            url = url,
            title = title,
            thumbnailUrl = thumbnailUrl,
            formatLabel = formatLabel,
            audioOnly = audioOnly,
            status = DownloadStatus.QUEUED
        )
        downloadDao.upsertTask(task)
        return task
    }

    fun updateLiveProgress(
        id: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: String,
        eta: String
    ) {
        val next = LiveProgress(
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speed = speed,
            eta = eta
        )
        _liveProgress.update { current ->
            if (current[id] == next) current else current + (id to next)
        }
    }

    fun clearLiveProgress(id: String) {
        _liveProgress.update { current ->
            if (id in current) current - id else current
        }
    }

    private fun consumeLiveProgress(id: String): LiveProgress? {
        val previous = _liveProgress.getAndUpdate { current ->
            if (id in current) current - id else current
        }
        return previous[id]
    }

    suspend fun updateProgress(
        id: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: String,
        eta: String
    ) {
        downloadDao.updateProgress(
            id = id,
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speed = speed,
            eta = eta
        )
    }

    suspend fun updateMetadata(
        id: String,
        title: String,
        thumbnailUrl: String?,
        formatLabel: String
    ) {
        downloadDao.updateMetadata(
            id = id,
            title = title,
            thumbnailUrl = thumbnailUrl,
            formatLabel = formatLabel
        )
    }

    suspend fun updateStatus(id: String, status: DownloadStatus, errorMessage: String? = null) {
        if (status != DownloadStatus.DOWNLOADING) {
            val pendingLive = consumeLiveProgress(id)
            if (pendingLive != null && (status == DownloadStatus.PAUSED || status == DownloadStatus.FAILED)) {
                downloadDao.updateProgress(
                    id = id,
                    progress = pendingLive.progress,
                    downloadedBytes = pendingLive.downloadedBytes,
                    totalBytes = pendingLive.totalBytes,
                    speed = "",
                    eta = ""
                )
            }
        }
        downloadDao.updateStatus(id, status, errorMessage)
    }

    /**
     * Memulihkan tugas berstatus [DownloadStatus.DOWNLOADING] yang terhenti akibat crash atau force-kill
     * menjadi [DownloadStatus.PAUSED] agar berkas sementara (.part/.aria2) dapat langsung dilanjutkan.
     */
    suspend fun reconcileInterruptedDownloads(activeTaskIds: Set<String> = emptySet()): Int {
        val liveSnapshot = _liveProgress.value
        if (liveSnapshot.isNotEmpty()) {
            val idsToFlush = liveSnapshot.keys.filter { it !in activeTaskIds }
            for (id in idsToFlush) {
                val pending = consumeLiveProgress(id) ?: continue
                downloadDao.updateProgress(
                    id = id,
                    progress = pending.progress,
                    downloadedBytes = pending.downloadedBytes,
                    totalBytes = pending.totalBytes,
                    speed = "",
                    eta = ""
                )
            }
        }
        if (activeTaskIds.isEmpty()) {
            return downloadDao.reconcileDownloadingTasksToPaused()
        }
        val stuckTasks = downloadDao.getTasksByStatus(DownloadStatus.DOWNLOADING)
            .filter { it.id !in activeTaskIds }
        var recoveredCount = 0
        for (task in stuckTasks) {
            recoveredCount += downloadDao.reconcileDownloadingTaskToPaused(task.id)
        }
        return recoveredCount
    }

    suspend fun reconcileInterruptedDownload(id: String): Int {
        val pending = consumeLiveProgress(id)
        if (pending != null) {
            downloadDao.updateProgress(
                id = id,
                progress = pending.progress,
                downloadedBytes = pending.downloadedBytes,
                totalBytes = pending.totalBytes,
                speed = "",
                eta = ""
            )
        }
        return downloadDao.reconcileDownloadingTaskToPaused(id)
    }

    suspend fun markCompleted(id: String, outputPath: String, totalBytes: Long) {
        clearLiveProgress(id)
        downloadDao.markCompleted(id = id, outputPath = outputPath, totalBytes = totalBytes)
    }

    suspend fun deleteTask(task: DownloadTaskEntity) {
        clearLiveProgress(task.id)
        downloadDao.deleteTask(task)
    }

    suspend fun deleteTaskById(id: String) {
        clearLiveProgress(id)
        downloadDao.deleteTaskById(id)
    }

    suspend fun deleteCompletedTasks() {
        downloadDao.deleteCompletedTasks()
    }
}
