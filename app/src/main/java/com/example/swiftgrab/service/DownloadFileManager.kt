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
import com.example.swiftgrab.util.StorageHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

data class FinalizedDownload(
    val outputPath: String,
    val fileSize: Long,
    val file: File? = null,
    val displayName: String = ""
)

object DownloadFileManager {

    private const val TAG = "DownloadFileManager"
    private const val FOLDER_NAME = "SwiftGrab"
    private val exportLock = Any()

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
     * Disimpan di penyimpanan internal aplikasi agar yt-dlp dan aria2c selalu memiliki izin tulis penuh,
     * dengan fallback ke tata letak staging lama agar tugas yang dijeda sebelum pembaruan tetap bisa dilanjutkan.
     */
    fun getStagingDir(context: Context, taskId: String): File {
        val legacyDownloadStaging = safeStagingDir(getDownloadDir(context), taskId)
        if (legacyDownloadStaging.exists()) {
            return legacyDownloadStaging
        }

        val appDownloadsBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        val previousLayoutStaging = safeStagingDir(File(appDownloadsBase, FOLDER_NAME), taskId)
        if (previousLayoutStaging.exists()) {
            return previousLayoutStaging
        }

        val staging = safeStagingDir(File(appDownloadsBase, "staging"), taskId)
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
            val appDownloadsBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "downloads")
            val dirsToDelete = listOf(
                safeStagingDir(File(appDownloadsBase, "staging"), taskId),
                safeStagingDir(File(appDownloadsBase, FOLDER_NAME), taskId),
                safeStagingDir(getDownloadDir(context), taskId)
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

    internal fun safeStagingDir(baseDir: File, taskId: String): File {
        require(taskId.isNotBlank() && !taskId.contains('/') && !taskId.contains('\\') && !taskId.contains("..")) {
            "Invalid taskId for staging directory"
        }
        val child = File(baseDir, ".staging_$taskId")
        val baseCanonical = baseDir.canonicalFile
        val childCanonical = child.canonicalFile
        require(childCanonical.parentFile == baseCanonical) {
            "Staging directory escaped base directory"
        }
        return child
    }

    /**
     * Memindahkan berkas media selesai ke direktori unduhan akhir dan memicu pemindaian media.
     */
    fun finalizeCompletedFile(context: Context, taskId: String): File? {
        return try {
            finalizeCompletedMedia(context, taskId)?.file
        } catch (e: IOException) {
            Log.e(TAG, "Failed to finalize completed file for task: $taskId", e)
            null
        }
    }

    /**
     * Menemukan berkas media selesai di direktori staging, memindahkannya ke folder penyimpanan
     * publik atau folder SAF pilihan pengguna, serta memindainya ke MediaStore.
     *
     * Catatan: Ekspor ke SAF penyedia dokumen non-lokal bergantung pada pengindeksan penyedia,
     * sedangkan ekspor ke MediaStore.Downloads otomatis terindeks ketika IS_PENDING diubah ke 0.
     *
     * @throws IOException jika seluruh jalur ekspor gagal sehingga tugas tidak ditandai selesai palsu.
     */
    @Throws(IOException::class)
    fun finalizeCompletedMedia(context: Context, taskId: String): FinalizedDownload? {
        val appDownloadsBase = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        val stagingDir = safeStagingDir(File(appDownloadsBase, "staging"), taskId).takeIf { it.exists() }
            ?: safeStagingDir(File(appDownloadsBase, FOLDER_NAME), taskId).takeIf { it.exists() }
            ?: safeStagingDir(getDownloadDir(context), taskId).takeIf { it.exists() }
            ?: return null

        val completed = findCompletedMediaFile(stagingDir) ?: return null
        val fileSize = completed.length()
        val displayName = completed.name

        val prefs = try {
            (context.applicationContext as? SwiftGrabApp)?.userPreferences
                ?: UserPreferences(context)
        } catch (_: Exception) {
            null
        }
        val customPath = prefs?.getCustomDownloadPath()
        val customUriStr = prefs?.getCustomDownloadUri()
        val targetDir = getDownloadDir(context)

        // 1. Jika pengguna memilih folder SAF tanpa path berkas fisik langsung, tulis via SAF
        if (!customUriStr.isNullOrBlank() && customPath.isNullOrBlank()) {
            val treeUri = customUriStr.toUri()
            val docUri = copyToSafTreeUri(context, completed, treeUri)
            if (docUri != null) {
                stagingDir.deleteRecursively()
                return buildSafFinalizedResult(context, docUri, fileSize, displayName)
            }
        }

        // 2. Coba pindahkan langsung ke File target (Download/SwiftGrab atau folder path SAF) secara atomik
        val directMoved: File? = run {
            var allocatedTarget: File? = null
            try {
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val targetFile = reserveUniqueTargetFile(targetDir, completed.name)
                allocatedTarget = targetFile
                val moved = if (completed.renameTo(targetFile)) {
                    targetFile
                } else {
                    copyAndDelete(completed, targetFile)
                }
                if (moved.exists() && moved.length() > 0L && (fileSize <= 0L || moved.length() == fileSize)) {
                    moved
                } else {
                    if (completed.exists()) {
                        allocatedTarget.delete()
                    }
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct file move failed for ${targetDir.absolutePath}: ${e.message}")
                allocatedTarget?.let {
                    if (it.exists() && completed.exists()) {
                        it.delete()
                    }
                }
                null
            }
        }

        if (directMoved != null) {
            stagingDir.deleteRecursively()
            scanMediaFile(context, directMoved)
            return FinalizedDownload(
                outputPath = directMoved.absolutePath,
                fileSize = directMoved.length().takeIf { it > 0L } ?: fileSize,
                file = directMoved,
                displayName = displayName
            )
        }

        // 3. Jika gagal via File (mis. SD Card atau folder SAF terproteksi), tulis via SAF Tree URI
        if (!customUriStr.isNullOrBlank()) {
            val treeUri = customUriStr.toUri()
            val docUri = copyToSafTreeUri(context, completed, treeUri)
            if (docUri != null) {
                stagingDir.deleteRecursively()
                return buildSafFinalizedResult(context, docUri, fileSize, displayName)
            }
        }

        // 4. Fallback ke MediaStore.Downloads (Android 10+) jika penulisan File langsung dibatasi Scoped Storage
        if (customUriStr.isNullOrBlank()) {
            val mediaStoreUri = copyToMediaStoreDownloads(context, completed)
            if (mediaStoreUri != null) {
                stagingDir.deleteRecursively()
                val resolvedPath = resolveMediaStoreFilePath(context, mediaStoreUri)
                if (resolvedPath != null) {
                    val mediaFile = File(resolvedPath)
                    if (mediaFile.exists() && mediaFile.length() > 0L && (fileSize <= 0L || mediaFile.length() == fileSize)) {
                        scanMediaPath(context, mediaFile.absolutePath)
                        if (mediaFile.canRead()) {
                            return FinalizedDownload(
                                outputPath = mediaFile.absolutePath,
                                fileSize = mediaFile.length(),
                                file = mediaFile,
                                displayName = displayName
                            )
                        }
                    } else if (resolvedPath.startsWith("/")) {
                        scanMediaPath(context, resolvedPath)
                    }
                }
                return FinalizedDownload(
                    outputPath = mediaStoreUri.toString(),
                    fileSize = fileSize,
                    file = null,
                    displayName = displayName
                )
            }
        }

        // Seluruh ekspor gagal: pertahankan berkas di staging dan laporkan kegagalan agar dapat dicoba lagi
        Log.e(TAG, "All export attempts failed; preserving staged file at ${completed.absolutePath}")
        throw IOException("Gagal mengekspor berkas ke folder penyimpanan tujuan. Periksa izin penyimpanan atau pilih folder melalui Pengaturan.")
    }

    private fun buildSafFinalizedResult(
        context: Context,
        docUri: Uri,
        fallbackSize: Long,
        displayName: String
    ): FinalizedDownload {
        val resolvedPath = StorageHelper.resolvePathFromDocumentUri(context, docUri)
        if (resolvedPath != null) {
            // Pindai hanya jika URI SAF dipetakan ke path sistem berkas lokal (bukan content:// URI).
            scanMediaPath(context, resolvedPath)
            val resolvedFile = File(resolvedPath)
            if (resolvedFile.exists() && resolvedFile.canRead() && resolvedFile.length() > 0L && (fallbackSize <= 0L || resolvedFile.length() == fallbackSize)) {
                return FinalizedDownload(
                    outputPath = resolvedFile.absolutePath,
                    fileSize = resolvedFile.length(),
                    file = resolvedFile,
                    displayName = displayName
                )
            }
        }
        // Ekspor SAF ke penyedia non-lokal tanpa path fisik tidak dipindai dengan MediaScannerConnection
        // karena MediaScanner hanya menerima path lokal; berkas mengandalkan pengindeksan penyedia SAF.
        return FinalizedDownload(
            outputPath = docUri.toString(),
            fileSize = fallbackSize,
            file = null,
            displayName = displayName
        )
    }

    private fun resolveMediaStoreFilePath(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (colIdx >= 0) cursor.getString(colIdx) else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
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
        scanMediaPath(context, file.absolutePath, onScanCompleted)
    }

    /**
     * Memindai path sistem berkas lokal (bukan URI `content://`) menggunakan [MediaScannerConnection.scanFile].
     */
    fun scanMediaPath(
        context: Context,
        localPath: String,
        onScanCompleted: ((path: String, uri: Uri?) -> Unit)? = null
    ) {
        if (!localPath.startsWith("/")) return
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(localPath),
                null
            ) { path, uri ->
                Log.i(TAG, "MediaScannerConnection scanned completed file: $path -> $uri")
                onScanCompleted?.invoke(path, uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling MediaScannerConnection.scanFile for $localPath", e)
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

    /**
     * Memilih nama berkas target unik secara non-destruktif tanpa menimpa berkas yang sudah ada.
     */
    internal fun buildUniqueTargetFile(
        targetDir: File,
        fileName: String,
        timestamp: Long = System.currentTimeMillis()
    ): File {
        val rawTarget = File(targetDir, fileName)
        if (!rawTarget.exists()) return rawTarget
        val dotIndex = fileName.lastIndexOf('.')
        val nameWithoutExt = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extSuffix = if (dotIndex > 0) ".${fileName.substring(dotIndex + 1)}" else ""

        var counter = 0
        while (true) {
            val suffix = if (counter == 0) "_$timestamp" else "_${timestamp}_$counter"
            val candidate = File(targetDir, "$nameWithoutExt$suffix$extSuffix")
            if (!candidate.exists()) {
                return candidate
            }
            counter++
        }
    }

    /**
     * Mengalokasikan dan memesan berkas target unik secara atomik (`createNewFile`) agar
     * ekspor paralel dengan nama berkas sama tidak saling menimpa.
     */
    @Throws(IOException::class)
    internal fun reserveUniqueTargetFile(
        targetDir: File,
        fileName: String,
        timestampProvider: () -> Long = { System.currentTimeMillis() }
    ): File {
        synchronized(exportLock) {
            val rawTarget = File(targetDir, fileName)
            if (rawTarget.createNewFile()) {
                return rawTarget
            }

            val dotIndex = fileName.lastIndexOf('.')
            val nameWithoutExt = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
            val extSuffix = if (dotIndex > 0) ".${fileName.substring(dotIndex + 1)}" else ""
            val timestamp = timestampProvider()

            var counter = 0
            while (true) {
                val suffix = if (counter == 0) "_$timestamp" else "_${timestamp}_$counter"
                val candidate = File(targetDir, "$nameWithoutExt$suffix$extSuffix")
                if (candidate.createNewFile()) {
                    return candidate
                }
                counter++
            }
        }
    }

    private fun copyAndDelete(source: File, destination: File): File {
        try {
            val expectedBytes = source.length()
            val copiedBytes = FileInputStream(source).use { input ->
                FileOutputStream(destination).use { output ->
                    val copied = input.copyTo(output)
                    output.flush()
                    copied
                }
            }
            if (copiedBytes != expectedBytes || destination.length() != expectedBytes) {
                throw IOException("Incomplete copy to ${destination.absolutePath}")
            }
            source.delete()
            return destination
        } catch (e: Exception) {
            if (destination.exists() && source.exists()) {
                destination.delete()
            }
            throw e
        }
    }

    private fun copyToSafTreeUri(
        context: Context,
        source: File,
        treeUri: Uri
    ): Uri? {
        var createdDocUri: Uri? = null
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
            createdDocUri = docUri

            val expectedBytes = source.length()
            val stream = context.contentResolver.openOutputStream(docUri)
                ?: throw IOException("Unable to open SAF output stream for $docUri")
            val copiedBytes = stream.use { output ->
                FileInputStream(source).use { input ->
                    val copied = input.copyTo(output)
                    output.flush()
                    copied
                }
            }
            if (copiedBytes != expectedBytes) {
                throw IOException("Incomplete SAF copy: expected $expectedBytes, copied $copiedBytes")
            }

            source.delete()
            docUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file to SAF tree URI: $treeUri", e)
            createdDocUri?.let { uri ->
                try {
                    if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                        context.contentResolver.delete(uri, null, null)
                    }
                } catch (_: Exception) {
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (_: Exception) {}
                }
            }
            null
        }
    }

    private fun copyToMediaStoreDownloads(context: Context, source: File): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        var pendingUri: Uri? = null
        return try {
            val mimeType = resolveMimeType(source)
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER_NAME")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            pendingUri = itemUri

            val expectedBytes = source.length()
            val stream = resolver.openOutputStream(itemUri)
                ?: throw IOException("Unable to open MediaStore output stream for $itemUri")
            val copiedBytes = stream.use { output ->
                FileInputStream(source).use { input ->
                    val copied = input.copyTo(output)
                    output.flush()
                    copied
                }
            }
            if (copiedBytes != expectedBytes) {
                throw IOException("Incomplete MediaStore copy: expected $expectedBytes, copied $copiedBytes")
            }

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            val updatedRows = resolver.update(itemUri, values, null, null)
            if (updatedRows <= 0) {
                throw IOException("MediaStore failed to publish item: $itemUri")
            }

            source.delete()
            itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export file to MediaStore.Downloads", e)
            pendingUri?.let { uri ->
                try {
                    resolver.delete(uri, null, null)
                } catch (_: Exception) {}
            }
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
