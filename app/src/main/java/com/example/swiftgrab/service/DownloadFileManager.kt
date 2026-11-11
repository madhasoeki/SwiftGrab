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
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

data class FinalizedDownload(
    val outputPath: String,
    val fileSize: Long,
    val file: File? = null,
    val displayName: String = ""
)

object DownloadFileManager {

    private const val TAG = "DownloadFileManager"
    private const val FOLDER_NAME = "SwiftGrab"
    private const val COPY_BUFFER_SIZE = 32 * 1024
    private val exportLock = Any()
    private val taskExportLocks = ConcurrentHashMap<String, Any>()
    private val canceledExportTasks = ConcurrentHashMap.newKeySet<String>()

    internal fun getTaskLock(taskId: String): Any =
        taskExportLocks.getOrPut(taskId) { Any() }

    fun markTaskCanceled(taskId: String) {
        canceledExportTasks.add(taskId)
    }

    fun clearTaskCanceled(taskId: String) {
        canceledExportTasks.remove(taskId)
    }

    fun isTaskCanceled(taskId: String): Boolean =
        canceledExportTasks.contains(taskId)

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
     * Mengembalikan teks path folder default untuk ditampilkan di UI tanpa membuat direktori di disk.
     */
    fun getDisplayDefaultDownloadPath(): String {
        return try {
            getDefaultDownloadDir().absolutePath
        } catch (_: Exception) {
            "Download/$FOLDER_NAME"
        }
    }

    /**
     * Memeriksa apakah berkas berada di dalam root [FileProvider] yang dikonfigurasi (`Download/`
     * atau penyimpanan internal/eksternal aplikasi) sehingga aman dibagikan sebagai file path.
     */
    fun isPathShareableViaFileProvider(context: Context, file: File): Boolean {
        val canonicalPath = try {
            file.canonicalPath
        } catch (_: Exception) {
            return false
        }
        val allowedRoots = buildList {
            try {
                add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalPath)
            } catch (_: Exception) {}
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
                runCatching { add(it.canonicalPath) }
            }
            runCatching { add(File(context.filesDir, "downloads").canonicalPath) }
            runCatching { add(File(context.cacheDir, "shared_media").canonicalPath) }
        }
        return isPathWithinAllowedRoots(canonicalPath, allowedRoots)
    }

    internal fun isPathWithinAllowedRoots(canonicalPath: String, allowedRoots: List<String>): Boolean {
        return allowedRoots.any { root ->
            canonicalPath == root || canonicalPath.startsWith("$root${File.separator}")
        }
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
        synchronized(getTaskLock(taskId)) {
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
    }

    /**
     * Menghapus berkas atau URI hasil ekspor apabila pembatalan terjadi tepat setelah finalisasi.
     */
    fun rollbackExportedDownload(context: Context, finalized: FinalizedDownload) {
        try {
            if (finalized.outputPath.startsWith("content://")) {
                val uri = finalized.outputPath.toUri()
                val deletedByDoc = runCatching {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                }.getOrDefault(false)
                if (!deletedByDoc) {
                    context.contentResolver.delete(uri, null, null)
                }
            }
            finalized.file?.let { exportedFile ->
                if (exportedFile.exists()) {
                    exportedFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to rollback exported download ${finalized.outputPath}: ${e.message}")
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
    fun finalizeCompletedMedia(
        context: Context,
        taskId: String,
        isCanceled: () -> Boolean = { false }
    ): FinalizedDownload? {
        val checkCanceled = { isTaskCanceled(taskId) || isCanceled() || Thread.currentThread().isInterrupted }
        if (checkCanceled()) {
            throw CancellationException("Download canceled before export")
        }

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

        // 1. Jika pengguna memilih folder SAF, jangan pernah fallback ke folder publik Download/SwiftGrab
        if (!customUriStr.isNullOrBlank()) {
            val selectedDir = customPath?.takeIf { it.isNotBlank() }?.let { File(it) }
            if (selectedDir != null && isPathShareableViaFileProvider(context, selectedDir)) {
                val directSelected = moveDirectlyToTargetDir(
                    context = context,
                    taskId = taskId,
                    completed = completed,
                    targetDir = selectedDir,
                    fileSize = fileSize,
                    displayName = displayName,
                    stagingDir = stagingDir,
                    checkCanceled = checkCanceled
                )
                if (directSelected != null) {
                    return directSelected
                }
            }

            val treeUri = customUriStr.toUri()
            val docUri = copyToSafTreeUri(context, taskId, completed, treeUri, checkCanceled)
            if (docUri != null) {
                synchronized(getTaskLock(taskId)) {
                    if (checkCanceled()) {
                        deleteSafDocumentQuietly(context, docUri)
                        throw CancellationException("Download canceled during SAF export")
                    }
                    stagingDir.deleteRecursively()
                }
                return buildSafFinalizedResult(context, docUri, fileSize, displayName)
            }

            Log.e(TAG, "SAF export failed for $customUriStr; preserving staged file at ${completed.absolutePath}")
            throw IOException("Gagal mengekspor berkas ke folder SAF yang dipilih. Periksa izin akses folder di Pengaturan.")
        }

        // 2. Folder publik default Download/SwiftGrab (atau customPath langsung tanpa SAF URI)
        val targetDir = getDownloadDir(context)
        val directMoved = moveDirectlyToTargetDir(
            context = context,
            taskId = taskId,
            completed = completed,
            targetDir = targetDir,
            fileSize = fileSize,
            displayName = displayName,
            stagingDir = stagingDir,
            checkCanceled = checkCanceled
        )
        if (directMoved != null) {
            return directMoved
        }

        // 3. Fallback ke MediaStore.Downloads (Android 10+) jika penulisan File langsung ke Download/SwiftGrab dibatasi
        if (customPath.isNullOrBlank()) {
            val mediaStoreUri = copyToMediaStoreDownloads(context, taskId, completed, checkCanceled)
            if (mediaStoreUri != null) {
                synchronized(getTaskLock(taskId)) {
                    if (checkCanceled()) {
                        deleteMediaStoreItemQuietly(context, mediaStoreUri)
                        throw CancellationException("Download canceled during MediaStore export")
                    }
                    stagingDir.deleteRecursively()
                }
                val resolvedPath = resolveMediaStoreFilePath(context, mediaStoreUri)
                if (resolvedPath != null) {
                    val mediaFile = File(resolvedPath)
                    if (mediaFile.exists() && mediaFile.length() > 0L && (fileSize <= 0L || mediaFile.length() == fileSize)) {
                        scanMediaPath(context, mediaFile.absolutePath)
                        if (mediaFile.canRead() && isPathShareableViaFileProvider(context, mediaFile)) {
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

    private fun moveDirectlyToTargetDir(
        context: Context,
        taskId: String,
        completed: File,
        targetDir: File,
        fileSize: Long,
        displayName: String,
        stagingDir: File,
        checkCanceled: () -> Boolean
    ): FinalizedDownload? {
        var allocatedTarget: File? = null
        val moved: File? = try {
            if (checkCanceled()) {
                throw CancellationException("Download canceled before direct move")
            }
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = reserveUniqueTargetFile(targetDir, completed.name)
            allocatedTarget = targetFile
            val resultFile = if (completed.renameTo(targetFile)) {
                targetFile
            } else {
                copyAndDelete(completed, targetFile, checkCanceled)
            }
            synchronized(getTaskLock(taskId)) {
                if (checkCanceled()) {
                    if (resultFile.exists()) {
                        resultFile.delete()
                    }
                    throw CancellationException("Download canceled during direct move")
                }
                if (resultFile.exists() && resultFile.length() > 0L && (fileSize <= 0L || resultFile.length() == fileSize)) {
                    stagingDir.deleteRecursively()
                    resultFile
                } else {
                    if (completed.exists()) {
                        allocatedTarget.delete()
                    }
                    null
                }
            }
        } catch (ce: CancellationException) {
            allocatedTarget?.let { if (it.exists()) it.delete() }
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Direct file move failed for ${targetDir.absolutePath}: ${e.message}")
            allocatedTarget?.let {
                if (it.exists() && completed.exists()) {
                    it.delete()
                }
            }
            null
        }

        if (moved != null) {
            scanMediaFile(context, moved)
            return FinalizedDownload(
                outputPath = moved.absolutePath,
                fileSize = moved.length().takeIf { it > 0L } ?: fileSize,
                file = moved,
                displayName = displayName
            )
        }
        return null
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
            if (
                resolvedFile.exists() &&
                resolvedFile.canRead() &&
                resolvedFile.length() > 0L &&
                (fallbackSize <= 0L || resolvedFile.length() == fallbackSize) &&
                isPathShareableViaFileProvider(context, resolvedFile)
            ) {
                return FinalizedDownload(
                    outputPath = resolvedFile.absolutePath,
                    fileSize = resolvedFile.length(),
                    file = resolvedFile,
                    displayName = displayName
                )
            }
        }
        // Ekspor SAF di luar root FileProvider atau penyedia non-lokal tetap memakai docUri SAF
        // agar open/share bekerja tanpa perlu memperluas cakupan FileProvider ke seluruh storage.
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

    private fun copyStreamWithCancellation(
        input: InputStream,
        output: OutputStream,
        checkCanceled: () -> Boolean
    ): Long {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var totalCopied = 0L
        while (true) {
            if (checkCanceled()) {
                throw CancellationException("Download canceled during stream copy")
            }
            val bytesRead = input.read(buffer)
            if (bytesRead == -1) break
            output.write(buffer, 0, bytesRead)
            totalCopied += bytesRead
        }
        output.flush()
        return totalCopied
    }

    private fun copyAndDelete(
        source: File,
        destination: File,
        checkCanceled: () -> Boolean = { false }
    ): File {
        try {
            val expectedBytes = source.length()
            val copiedBytes = FileInputStream(source).use { input ->
                FileOutputStream(destination).use { output ->
                    copyStreamWithCancellation(input, output, checkCanceled)
                }
            }
            if (copiedBytes != expectedBytes || destination.length() != expectedBytes) {
                throw IOException("Incomplete copy to ${destination.absolutePath}")
            }
            source.delete()
            return destination
        } catch (t: Throwable) {
            if (destination.exists() && (source.exists() || t is CancellationException)) {
                destination.delete()
            }
            throw t
        }
    }

    private fun copyToSafTreeUri(
        context: Context,
        taskId: String,
        source: File,
        treeUri: Uri,
        checkCanceled: () -> Boolean
    ): Uri? {
        var createdDocUri: Uri? = null
        return try {
            if (checkCanceled()) {
                throw CancellationException("Download canceled before SAF export")
            }
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
                    copyStreamWithCancellation(input, output, checkCanceled)
                }
            }
            if (copiedBytes != expectedBytes) {
                throw IOException("Incomplete SAF copy: expected $expectedBytes, copied $copiedBytes")
            }

            synchronized(getTaskLock(taskId)) {
                if (checkCanceled()) {
                    throw CancellationException("Download canceled before finalizing SAF document")
                }
                source.delete()
            }
            docUri
        } catch (ce: CancellationException) {
            createdDocUri?.let { deleteSafDocumentQuietly(context, it) }
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file to SAF tree URI: $treeUri", e)
            createdDocUri?.let { deleteSafDocumentQuietly(context, it) }
            null
        }
    }

    private fun copyToMediaStoreDownloads(
        context: Context,
        taskId: String,
        source: File,
        checkCanceled: () -> Boolean
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        var pendingUri: Uri? = null
        return try {
            if (checkCanceled()) {
                throw CancellationException("Download canceled before MediaStore export")
            }
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
                    copyStreamWithCancellation(input, output, checkCanceled)
                }
            }
            if (copiedBytes != expectedBytes) {
                throw IOException("Incomplete MediaStore copy: expected $expectedBytes, copied $copiedBytes")
            }

            synchronized(getTaskLock(taskId)) {
                if (checkCanceled()) {
                    throw CancellationException("Download canceled before MediaStore publish")
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                val updatedRows = resolver.update(itemUri, values, null, null)
                if (updatedRows <= 0) {
                    throw IOException("MediaStore failed to publish item: $itemUri")
                }
                source.delete()
            }
            itemUri
        } catch (ce: CancellationException) {
            pendingUri?.let { deleteMediaStoreItemQuietly(context, it) }
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export file to MediaStore.Downloads", e)
            pendingUri?.let { deleteMediaStoreItemQuietly(context, it) }
            null
        }
    }

    private fun deleteSafDocumentQuietly(context: Context, uri: Uri) {
        try {
            if (!DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (_: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private fun deleteMediaStoreItemQuietly(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
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
