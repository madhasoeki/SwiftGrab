package com.example.swiftgrab.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Status of a download task.
 */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELED;

    val isActive: Boolean
        get() = this == QUEUED || this == DOWNLOADING

    val isTerminal: Boolean
        get() = this == COMPLETED || this == FAILED || this == CANCELED
}

/**
 * Room entity representing a single media download task.
 */
@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val formatLabel: String = "Auto",
    val audioOnly: Boolean = false,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: String = "",
    val eta: String = "",
    val outputPath: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
