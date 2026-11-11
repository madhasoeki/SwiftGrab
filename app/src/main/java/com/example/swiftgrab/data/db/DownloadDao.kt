package com.example.swiftgrab.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN (:statuses) ORDER BY createdAt DESC")
    fun observeTasksByStatuses(statuses: List<DownloadStatus>): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    fun observeTaskById(id: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getTasksByStatus(status: DownloadStatus): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: DownloadTaskEntity)

    @Update
    suspend fun updateTask(task: DownloadTaskEntity)

    @Query(
        """
        UPDATE download_tasks
        SET progress = :progress,
            downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes,
            speed = :speed,
            eta = :eta,
            updatedAt = :updatedAt
        WHERE id = :id AND status = :activeStatus
        """
    )
    suspend fun updateProgress(
        id: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: String,
        eta: String,
        activeStatus: DownloadStatus = DownloadStatus.DOWNLOADING,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE download_tasks
        SET title = :title,
            thumbnailUrl = :thumbnailUrl,
            formatLabel = :formatLabel,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateMetadata(
        id: String,
        title: String,
        thumbnailUrl: String?,
        formatLabel: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = :status,
            errorMessage = :errorMessage,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: DownloadStatus,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = :completedStatus,
            progress = 100.0,
            outputPath = :outputPath,
            totalBytes = CASE WHEN :totalBytes > 0 THEN :totalBytes ELSE totalBytes END,
            downloadedBytes = CASE WHEN :totalBytes > 0 THEN :totalBytes ELSE downloadedBytes END,
            speed = '',
            eta = '',
            errorMessage = NULL,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun markCompleted(
        id: String,
        outputPath: String,
        totalBytes: Long,
        completedStatus: DownloadStatus = DownloadStatus.COMPLETED,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteTask(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM download_tasks WHERE status = :completedStatus")
    suspend fun deleteCompletedTasks(completedStatus: DownloadStatus = DownloadStatus.COMPLETED)
}
