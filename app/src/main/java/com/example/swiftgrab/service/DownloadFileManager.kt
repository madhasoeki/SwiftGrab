package com.example.swiftgrab.service

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.example.swiftgrab.SwiftGrabApp
import com.example.swiftgrab.data.prefs.UserPreferences
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class FinalizedDownload(
    val outputPath: String,
    val fileSize: Long,
    val file: File? = null
)

object DownloadFileManager {

    private const val TAG = "DownloadFileManager"
    private const val FOLDER_NAME = "SwiftGrab"

    /**
     * Direktori publik bawaan untuk unduhan SwiftGrab:
     * File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SwiftGrab")
     */
    fun getDefaultDownloadDir(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER_NAME
        )
    }

    /**
     * Direktori tujuan akhir unduhan. Menggunakan folder kustom yang dipilih pengguna melalui SAF
     * jika tersedia, atau fallback ke folder publik Download/SwiftGrab.
     */
    fun getDownloadDir(context: Context): File {
        val customPath = try {
            val prefs = (context.applicationContext as? SwiftGrabApp)?.userPreferences
                ?: UserPreferences(context)
            prefs.getCustomDownloadPath()
        } catch (_: Exception) {
            null
        }

        val defaultPublicDir = try {
            getDefaultDownloadDir()
        } catch (_: Exception) {
            val fallbackBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            File(fallbackBase, FOLDER_NAME)
        }

        return resolveDownloadDir(customPath, defaultPublicDir)
    }

    internal fun resolveDownloadDir(customPath: String?, defaultPublicDir: File): File {
        if (!customPath.isNullOrBlank()) {
            val customDir = File(customPath)
            if (customDir.exists() || customDir.mkdirs()) {
                return customDir
            }
        }
        if (!defaultPublicDir.exists()) {
            defaultPublicDir.mkdirs()
        }
        return defaultPublicDir
    }

    /**
     * Direktori staging per tugas untuk mengisolasi berkas sementara (.part, .aria2, .ytdl).
     * Disimpan di penyimpanan internal aplikasi agar yt-dlp dan aria2c selalu memiliki izin tulis penuh.
     */
    fun getStagingDir(context: Context, taskId: String): File {
        val legacyDownloadStaging = File(getDownloadDir(context), ".staging_$taskId")
        if (legacyDownloadStaging.exists()) {
            return legacyDownloadStaging
        }

        val stagingBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        val staging = File(stagingBase, "staging/.staging_$taskId")
        if (!staging.exists()) {
            staging.mkdirs()
        }
        return staging
    }

    /**
     * Menghapus semua berkas unduhan sementara (.part, .aria2, .ytdl, dll.) untuk tugas yang dibatalkan.
     */
    fun cleanupTemporaryFiles(context: Context, taskId: String) {
        try {
            val stagingBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            val dirsToDelete = listOf(
                File(stagingBase, "staging/.staging_$taskId"),
                File(getDownloadDir(context), ".staging_$taskId")
            )
            for (stagingDir in dirsToDelete) {
                if (stagingDir.exists()) {
                    stagingDir.deleteRecursively()
                    Log.d(TAG, "Deleted staging directory for task: $taskId (${stagingDir.absolutePath})")
                }
            }

            // Bersihkan berkas sisa di direktori unduhan jika ada
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
     * Memindahkan berkas media selesai ke direktori unduhan akhir dan memicu pemindaian media.
     */
    fun finalizeCompletedFile(context: Context, taskId: String): File? {
        return finalizeCompletedMedia(context, taskId)?.file
    }

    /**
     * Menemukan berkas media selesai di direktori staging, memindahkannya ke folder penyimpanan
     * publik atau folder SAF pilihan pengguna, serta memindainya ke MediaStore.
     */
    fun finalizeCompletedMedia(context: Context, taskId: String): FinalizedDownload? {
        val stagingBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        val stagingDir = File(stagingBase, "staging/.staging_$taskId").takeIf { it.exists() }
            ?: File(getDownloadDir(context), ".staging_$taskId").takeIf { it.exists() }
            ?: return null

        val completed = findCompletedMediaFile(stagingDir) ?: return null
        val fileSize = completed.length()

        val prefs = try {
            (context.applicationContext as? SwiftGrabApp)?.userPreferences
                ?: UserPreferences(context)
        } catch (_: Exception) {
            null
        }
        val customPath = prefs?.getCustomDownloadPath()
        val customUriStr = prefs?.getCustomDownloadUri()
        val targetDir = getDownloadDir(context)
        val targetFile = buildUniqueTargetFile(targetDir, completed.name)

        // 1. Jika pengguna memilih folder SAF tanpa path berkas fisik langsung, tulis via SAF
        if (!customUriStr.isNullOrBlank() && customPath.isNullOrBlank()) {
            val treeUri = customUriStr.toUri()
            val docUri = copyToSafTreeUri(context, completed, treeUri)
            if (docUri != null) {
                stagingDir.deleteRecursively()
                return FinalizedDownload(
                    outputPath = docUri.toString(),
                    fileSize = fileSize,
                    file = null
                )
            }
        }

        // 2. Coba pindahkan langsung ke File target (Download/SwiftGrab atau folder path SAF)
        val directMoved: File? = try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val moved = if (completed.renameTo(targetFile)) {
                targetFile
            } else {
                copyAndDelete(completed, targetFile)
            }
            if (moved.exists() && moved.length() > 0L) moved else null
        } catch (e: Exception) {
            Log.w(TAG, "Direct file move failed for ${targetFile.absolutePath}: ${e.message}")
            null
        }

        if (directMoved != null) {
            stagingDir.deleteRecursively()
            scanMediaFile(context, directMoved)
            return FinalizedDownload(
                outputPath = directMoved.absolutePath,
                fileSize = directMoved.length().takeIf { it > 0L } ?: fileSize,
                file = directMoved
            )
        }

        // 3. Jika gagal via File (mis. SD Card atau folder SAF terproteksi), tulis via SAF Tree URI
        if (!customUriStr.isNullOrBlank()) {
            val treeUri = customUriStr.toUri()
            val docUri = copyToSafTreeUri(context, completed, treeUri)
            if (docUri != null) {
                stagingDir.deleteRecursively()
                val possiblePhysicalFile = File(targetDir, completed.name)
                if (possiblePhysicalFile.exists()) {
                    scanMediaFile(context, possiblePhysicalFile)
                    return FinalizedDownload(
                        outputPath = possiblePhysicalFile.absolutePath,
                        fileSize = possiblePhysicalFile.length().takeIf { it > 0L } ?: fileSize,
                        file = possiblePhysicalFile
                    )
                }
                return FinalizedDownload(
                    outputPath = docUri.toString(),
                    fileSize = fileSize,
                    file = null
                )
            }
        }

        // 4. Fallback ke MediaStore.Downloads (Android 10+) jika penulisan File langsung dibatasi Scoped Storage
        val mediaStoreUri = copyToMediaStoreDownloads(context, completed)
        if (mediaStoreUri != null) {
            stagingDir.deleteRecursively()
            val expectedPublicFile = File(getDefaultDownloadDir(), completed.name)
            if (expectedPublicFile.exists()) {
                scanMediaFile(context, expectedPublicFile)
                return FinalizedDownload(
                    outputPath = expectedPublicFile.absolutePath,
                    fileSize = expectedPublicFile.length().takeIf { it > 0L } ?: fileSize,
                    file = expectedPublicFile
                )
            }
            return FinalizedDownload(
                outputPath = mediaStoreUri.toString(),
                fileSize = fileSize,
                file = null
            )
        }

        // Fallback terakhir: pertahankan di lokasi staging jika seluruh ekspor gagal
        Log.e(TAG, "All export attempts failed; keeping file at ${completed.absolutePath}")
        scanMediaFile(context, completed)
        return FinalizedDownload(
            outputPath = completed.absolutePath,
            fileSize = fileSize,
            file = completed
        )
    }

    /**
     * Memindai berkas media dengan [MediaScannerConnection.scanFile] agar video/audio yang selesai
     * diunduh langsung otomatis muncul di Galeri HP dan aplikasi Pemutar Musik/Video.
     */
    fun scanMediaFile(
        context: Context,
        file: File,
        onScanCompleted: ((path: String, uri: Uri?) -> Unit)? = null
    ) {
        if (!file.exists()) return
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                null
            ) { path, uri ->
                Log.i(TAG, "MediaScannerConnection scanned completed file: $path -> $uri")
                onScanCompleted?.invoke(path, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling MediaScannerConnection.scanFile for ${file.absolutePath}", e)
        }
    }

    internal fun findCompletedMediaFile(stagingDir: File): File? {
        if (!stagingDir.exists()) return null
        val mediaFiles = stagingDir.listFiles { file ->
            file.isFile &&
                !file.name.endsWith(".part") &&
                !file.name.endsWith(".aria2") &&
                !file.name.endsWith(".ytdl") &&
                !file.name.endsWith(".temp")
        } ?: return null
        return mediaFiles.maxByOrNull { it.lastModified() }
    }

    internal fun buildUniqueTargetFile(
        targetDir: File,
        fileName: String,
        timestamp: Long = System.currentTimeMillis()
    ): File {
        val rawTarget = File(targetDir, fileName)
        if (!rawTarget.exists()) return rawTarget
        val dotIndex = fileName.lastIndexOf('.')
        val uniqueName = if (dotIndex > 0) {
            val nameWithoutExt = fileName.substring(0, dotIndex)
            val ext = fileName.substring(dotIndex + 1)
            "${nameWithoutExt}_$timestamp.$ext"
        } else {
            "${fileName}_$timestamp"
        }
        return File(targetDir, uniqueName)
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

    private fun copyToSafTreeUri(
        context: Context,
        source: File,
        treeUri: Uri
    ): Uri? {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            val mimeType = resolveMimeType(source)
            val docUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                mimeType,
                source.name
            ) ?: return null

            context.contentResolver.openOutputStream(docUri)?.use { output ->
                FileInputStream(source).use { input ->
                    input.copyTo(output)
                }
            } ?: return null

            source.delete()
            docUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file to SAF tree URI: $treeUri", e)
            null
        }
    }

    private fun copyToMediaStoreDownloads(context: Context, source: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val mimeType = resolveMimeType(source)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null

            resolver.openOutputStream(itemUri)?.use { output ->
                FileInputStream(source).use { input ->
                    input.copyTo(output)
                }
            } ?: return null

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            source.delete()
            itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export file to MediaStore.Downloads", e)
            null
        }
    }

    private fun resolveMimeType(file: File): String {
        val ext = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return when {
            mime != null -> mime
            ext == "mp4" -> "video/mp4"
            ext == "mkv" -> "video/x-matroska"
            ext == "webm" -> "video/webm"
            ext == "mp3" -> "audio/mpeg"
            ext == "m4a" -> "audio/mp4"
            ext == "opus" -> "audio/opus"
            else -> "application/octet-stream"
        }
    }
}
