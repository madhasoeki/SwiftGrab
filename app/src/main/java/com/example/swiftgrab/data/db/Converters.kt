package com.example.swiftgrab.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus {
        return try {
            DownloadStatus.valueOf(value)
        } catch (_: IllegalArgumentException) {
            DownloadStatus.FAILED
        }
    }
}
