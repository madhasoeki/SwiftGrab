package com.example.swiftgrab.service

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DownloadFileManager {

    private const val TAG = "DownloadFileManager"
    private const val FOLDER_NAME = "SwiftGrab"

    /**
     * Public or app-specific destination directory for completed downloads.
     */
    fun getDownloadDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        val dir = File(baseDir, FOLDER_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Staging directory for a specific task to isolate temporary .part and .aria2 files.
     */
    fun getStagingDir(context: Context, taskId: String): File {
        val staging = File(getDownloadDir(context), ".staging_$taskId")
        if (!staging.exists()) {
            staging.mkdirs()
        }
        return staging
    }

    /**
     * Removes all temporary download files (.part, .aria2, .ytdl, etc.) for a canceled task.
     */
    fun cleanupTemporaryFiles(context: Context, taskId: String) {
        try {
            val stagingDir = File(getDownloadDir(context), ".staging_$taskId")
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
                Log.d(TAG, "Deleted staging directory for task: $taskId")
            }

            // Also clean up any lingering files in the root download directory matching taskId
            val downloadDir = getDownloadDir(context)
            downloadDir.listFiles { file ->
                file.name.contains(taskId) && (
                    file.name.endsWith(".part") ||
                        file.name.endsWith(".aria2") ||
                        file.name.endsWith(".ytdl")
                    )
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temporary files for task: $taskId", e)
        }
    }

    /**
     * Locates the finished media file inside the task staging directory and promotes it to the
     * final download directory.
     */
    fun finalizeCompletedFile(context: Context, taskId: String): File? {
        val stagingDir = File(getDownloadDir(context), ".staging_$taskId")
        if (!stagingDir.exists()) return null

        val mediaFiles = stagingDir.listFiles { file ->
            !file.name.endsWith(".part") &&
                !file.name.endsWith(".aria2") &&
                !file.name.endsWith(".ytdl") &&
                !file.name.endsWith(".temp")
        } ?: return null

        val completed = mediaFiles.maxByOrNull { it.lastModified() } ?: return null
        val targetDir = getDownloadDir(context)
        val rawTarget = File(targetDir, completed.name)
        val targetFile = if (rawTarget.exists()) {
            val timestamp = System.currentTimeMillis()
            val uniqueName = if (completed.extension.isNotEmpty()) {
                "${completed.nameWithoutExtension}_$timestamp.${completed.extension}"
            } else {
                "${completed.name}_$timestamp"
            }
            File(targetDir, uniqueName)
        } else {
            rawTarget
        }

        return try {
            val moved = if (completed.renameTo(targetFile)) {
                targetFile
            } else {
                copyAndDelete(completed, targetFile)
            }
            stagingDir.deleteRecursively()
            moved
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move file to final directory: ${completed.absolutePath}", e)
            completed
        }
    }

    private fun copyAndDelete(source: File, destination: File): File {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
        source.delete()
        return destination
    }
}
