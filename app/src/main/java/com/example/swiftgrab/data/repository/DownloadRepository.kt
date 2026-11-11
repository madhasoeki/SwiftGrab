package com.example.swiftgrab.data.repository

import com.example.swiftgrab.data.db.DownloadDao
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Single Source of Truth (SSOT) repository for download tasks.
 * Exposes suspend and Flow APIs without internal scopes.
 */
class DownloadRepository(
    private val downloadDao: DownloadDao
) {

    val allTasks: Flow<List<DownloadTaskEntity>> = downloadDao.observeAllTasks()

    fun observeTasksByStatuses(statuses: List<DownloadStatus>): Flow<List<DownloadTaskEntity>> =
        downloadDao.observeTasksByStatuses(statuses)

    fun observeTask(id: String): Flow<DownloadTaskEntity?> =
        downloadDao.observeTaskById(id)

    suspend fun getTask(id: String): DownloadTaskEntity? =
        downloadDao.getTaskById(id)

    suspend fun getTasksByStatus(status: DownloadStatus): List<DownloadTaskEntity> =
        downloadDao.getTasksByStatus(status)

    suspend fun upsertTask(task: DownloadTaskEntity) {
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
        downloadDao.updateStatus(id, status, errorMessage)
    }

    suspend fun markCompleted(id: String, outputPath: String, totalBytes: Long) {
        downloadDao.markCompleted(id = id, outputPath = outputPath, totalBytes = totalBytes)
    }

    suspend fun deleteTask(task: DownloadTaskEntity) {
        downloadDao.deleteTask(task)
    }

    suspend fun deleteTaskById(id: String) {
        downloadDao.deleteTaskById(id)
    }

    suspend fun deleteCompletedTasks() {
        downloadDao.deleteCompletedTasks()
    }
}
