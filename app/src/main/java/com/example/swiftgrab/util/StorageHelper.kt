package com.example.swiftgrab.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.net.URLDecoder

/**
 * Utilitas untuk mengelola akses folder penyimpanan, resolusi URI Storage Access Framework (SAF),
 * serta persistensi izin URI akses dokumen.
 */
object StorageHelper {

    private const val TAG = "StorageHelper"

    /**
     * Mempertahankan izin akses URI (persistable URI permission) yang dikembalikan oleh OpenDocumentTree.
     */
    fun takePersistablePermission(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "Persisted URI permission for: $uri")
        } catch (e: Exception) {
            Log.w(TAG, "Could not take persistable URI permission for $uri: ${e.message}")
        }
    }

    /**
     * Menyimpan izin akses dan mencoba mengonversi tree [Uri] menjadi path sistem berkas absolut.
     */
    fun resolvePathAndPersist(context: Context, treeUri: Uri): String? {
        takePersistablePermission(context, treeUri)
        val path = resolvePathFromTreeUri(context, treeUri)
        if (path != null) {
            val target = File(path)
            if (target.exists() || target.mkdirs()) {
                return target.absolutePath
            }
        }
        return path
    }

    /**
     * Mengambil path sistem berkas fisik dari tree [Uri] Storage Access Framework jika memungkinkan.
     */
    fun resolvePathFromTreeUri(context: Context, treeUri: Uri): String? {
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            treeUri.lastPathSegment
        } ?: return null

        val primaryRoot = try {
            Environment.getExternalStorageDirectory()
        } catch (_: Exception) {
            File("/storage/emulated/0")
        }

        val externalFiles = try {
            context.getExternalFilesDirs(null).filterNotNull()
        } catch (_: Exception) {
            emptyList()
        }

        return resolvePathFromDocumentId(
            docId = docId,
            primaryStorageRoot = primaryRoot,
            externalStorageDirs = externalFiles
        )
    }

    /**
     * Resolusi murni tanpa dependensi Android framework terhadap docId SAF ke path berkas.
     * Dapat diuji secara deterministik pada unit test.
     */
    fun resolvePathFromDocumentId(
        docId: String,
        primaryStorageRoot: File,
        externalStorageDirs: List<File> = emptyList()
    ): String? {
        val decoded = decodePercent(docId)

        if (decoded.startsWith("primary:", ignoreCase = true)) {
            val relative = decoded.substringAfter("primary:").trimStart('/')
            val target = if (relative.isNotEmpty()) File(primaryStorageRoot, relative) else primaryStorageRoot
            return target.absolutePath
        }

        if (decoded.contains(":")) {
            val parts = decoded.split(":", limit = 2)
            val volumeId = parts[0]
            val relative = parts.getOrNull(1)?.trimStart('/') ?: ""

            // Cek path standar SD Card di /storage/<volumeId>
            val volumeDir = File("/storage/$volumeId")
            if (volumeDir.exists()) {
                val target = if (relative.isNotEmpty()) File(volumeDir, relative) else volumeDir
                return target.absolutePath
            }

            // Cek externalFilesDirs untuk menemukan volume ID root
            for (dir in externalStorageDirs) {
                val abs = dir.absolutePath
                if (abs.contains(volumeId)) {
                    val rootPath = abs.substringBefore("/Android")
                    val target = if (relative.isNotEmpty()) File(rootPath, relative) else File(rootPath)
                    return target.absolutePath
                }
            }
        }

        if (decoded.startsWith("/")) {
            return File(decoded).absolutePath
        }

        return null
    }

    /**
     * Mengembalikan label nama yang ramah pengguna untuk ditampilkan di antarmuka jika path fisik tidak ada.
     */
    fun getDisplayLabelFromTreeUri(treeUri: Uri): String {
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            treeUri.lastPathSegment
        } ?: treeUri.toString()

        val decoded = decodePercent(docId)
        return when {
            decoded.startsWith("primary:", ignoreCase = true) -> {
                val rel = decoded.substringAfter("primary:").trimStart('/')
                if (rel.isEmpty()) "Penyimpanan Utama" else "Penyimpanan Utama / $rel"
            }
            decoded.contains(":") -> {
                val parts = decoded.split(":", limit = 2)
                val vol = parts[0]
                val rel = parts.getOrNull(1)?.trimStart('/') ?: ""
                if (rel.isEmpty()) "Kartu SD ($vol)" else "Kartu SD ($vol) / $rel"
            }
            else -> decoded
        }
    }

    /**
     * Mendekode karakter persen (seperti %2F untuk '/') dengan tetap mempertahankan tanda '+'.
     */
    internal fun decodePercent(input: String): String {
        return try {
            URLDecoder.decode(input.replace("+", "%2B"), Charsets.UTF_8.name())
        } catch (_: Exception) {
            input
        }
    }
}
