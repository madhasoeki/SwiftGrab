package com.example.swiftgrab.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.io.File

/**
 * Utilitas untuk mengelola akses folder penyimpanan, resolusi URI Storage Access Framework (SAF),
 * izin penyimpanan legacy (API < 29), serta persistensi izin URI akses dokumen.
 */
object StorageHelper {

    private const val TAG = "StorageHelper"
    internal const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * Mempertahankan izin akses URI (persistable URI permission) yang dikembalikan oleh OpenDocumentTree.
     * Mengembalikan true hanya jika izin baca dan tulis berhasil dipertahankan.
     */
    fun takePersistablePermission(context: Context, uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "Persisted URI permission for: $uri")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException taking persistable URI permission for $uri", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Could not take persistable URI permission for $uri: ${e.message}", e)
            false
        }
    }

    /**
     * Melepaskan izin akses URI persisten sebelumnya saat pengguna mengganti atau mereset folder penyimpanan.
     */
    fun releasePersistablePermission(context: Context, uriString: String?) {
        if (uriString.isNullOrBlank()) return
        try {
            val uri = uriString.toUri()
            val resolver = context.contentResolver
            val persisted = resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
            var flags = 0
            if (persisted?.isReadPermission == true) {
                flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            if (persisted?.isWritePermission == true) {
                flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            if (flags == 0) {
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            resolver.releasePersistableUriPermission(uri, flags)
            Log.d(TAG, "Released persisted URI permission for: $uriString")
        } catch (e: SecurityException) {
            Log.d(TAG, "Persistable URI permission already released for $uriString: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not release persistable URI permission for $uriString: ${e.message}")
        }
    }

    /**
     * Memeriksa apakah URI dokumen berasal dari penyedia penyimpanan eksternal perangkat.
     */
    fun isExternalStorageDocument(uri: Uri): Boolean =
        isExternalStorageAuthority(uri.authority)

    internal fun isExternalStorageAuthority(authority: String?): Boolean =
        authority == EXTERNAL_STORAGE_AUTHORITY

    /**
     * Mengonversi tree [Uri] menjadi path sistem berkas absolut jika berasal dari penyedia penyimpanan eksternal.
     * Untuk penyedia dokumen lain, mengembalikan null agar ekspor memakai aliran SAF.
     */
    fun resolvePathFromTreeUri(context: Context, treeUri: Uri): String? {
        if (!isExternalStorageDocument(treeUri)) {
            Log.d(TAG, "Skipping local path resolution for non-external-storage URI: ${treeUri.authority}")
            return null
        }

        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (_: Exception) {
            treeUri.lastPathSegment
        } ?: return null

        val path = resolveDocIdWithContext(context, docId)
        if (path != null) {
            val target = File(path)
            if (target.exists() || target.mkdirs()) {
                return target.absolutePath
            }
        }
        return path
    }

    /**
     * Mengonversi document [Uri] hasil pembuatan berkas SAF menjadi path berkas lokal jika memungkinkan.
     */
    fun resolvePathFromDocumentUri(context: Context, docUri: Uri): String? {
        if (!isExternalStorageDocument(docUri)) {
            return null
        }
        val docId = try {
            DocumentsContract.getDocumentId(docUri)
        } catch (_: Exception) {
            null
        } ?: return null

        return resolveDocIdWithContext(context, docId)
    }

    /**
     * Mempertahankan izin akses URI persisten dan memperbarui preferensi pengguna dengan path atau label folder.
     * Mengembalikan path atau label tampilan folder jika berhasil dipertahankan, atau null jika gagal.
     */
    fun saveCustomTreeUri(
        context: Context,
        treeUri: Uri,
        preferences: com.example.swiftgrab.data.prefs.UserPreferences
    ): String? {
        val previousUri = preferences.getCustomDownloadUri()
        if (!takePersistablePermission(context, treeUri)) {
            return null
        }
        val path = resolvePathFromTreeUri(context, treeUri)
        if (path != null) {
            val target = File(path)
            if (target.exists() || target.mkdirs()) {
                val saved = preferences.setCustomDownloadLocation(target.absolutePath, treeUri.toString())
                if (!saved) {
                    if (treeUri.toString() != previousUri) {
                        releasePersistablePermission(context, treeUri.toString())
                    }
                    return null
                }
                return target.absolutePath
            }
        }
        val displayLabel = getDisplayLabelFromTreeUri(treeUri)
        val saved = preferences.setCustomDownloadLocation(null, treeUri.toString())
        if (!saved) {
            if (treeUri.toString() != previousUri) {
                releasePersistablePermission(context, treeUri.toString())
            }
            return null
        }
        return displayLabel
    }

    /**
     * Memeriksa apakah aplikasi perlu meminta izin runtime [Manifest.permission.WRITE_EXTERNAL_STORAGE]
     * pada Android 8.0–9.0 (API < 29) sebelum menulis ke folder publik bawaan.
     */
    fun needsLegacyWritePermission(context: Context, customDownloadUri: String?): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        return needsLegacyWritePermissionForApi(
            sdkInt = Build.VERSION.SDK_INT,
            hasCustomSafUri = !customDownloadUri.isNullOrBlank(),
            hasWritePermission = hasPermission
        )
    }

    internal fun needsLegacyWritePermissionForApi(
        sdkInt: Int,
        hasCustomSafUri: Boolean,
        hasWritePermission: Boolean
    ): Boolean {
        return sdkInt < Build.VERSION_CODES.Q && !hasCustomSafUri && !hasWritePermission
    }

    private fun resolveDocIdWithContext(context: Context, docId: String): String? {
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
     * Resolusi murni tanpa dependensi Android framework terhadap docId SAF yang sudah didekodekan
     * oleh [DocumentsContract] ke path berkas fisik tanpa mendekode ulang karakter literal seperti `%2F`.
     */
    fun resolvePathFromDocumentId(
        docId: String,
        primaryStorageRoot: File,
        externalStorageDirs: List<File> = emptyList()
    ): String? {
        if (docId.startsWith("primary:", ignoreCase = true)) {
            val relative = docId.substringAfter("primary:").trimStart('/')
            val target = if (relative.isNotEmpty()) File(primaryStorageRoot, relative) else primaryStorageRoot
            return target.absolutePath
        }

        if (docId.contains(":")) {
            val parts = docId.split(":", limit = 2)
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

        if (docId.startsWith("/")) {
            return File(docId).absolutePath
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

        return when {
            docId.startsWith("primary:", ignoreCase = true) -> {
                val rel = docId.substringAfter("primary:").trimStart('/')
                if (rel.isEmpty()) "Penyimpanan Utama" else "Penyimpanan Utama / $rel"
            }
            docId.contains(":") -> {
                val parts = docId.split(":", limit = 2)
                val vol = parts[0]
                val rel = parts.getOrNull(1)?.trimStart('/') ?: ""
                if (rel.isEmpty()) "Kartu SD ($vol)" else "Kartu SD ($vol) / $rel"
            }
            else -> docId
        }
    }
}
