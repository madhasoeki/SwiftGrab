package com.example.swiftgrab.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileSharingHelper {

    private const val SHARED_MEDIA_DIR = "shared_media"

    fun openFile(context: Context, filePath: String) {
        val toastContext = context.applicationContext ?: context
        if (filePath.startsWith("content://")) {
            try {
                val uri = filePath.toUri()
                val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(toastContext, "Tidak ada aplikasi untuk memutar berkas ini", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(toastContext, "Gagal membuka berkas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(toastContext, "Berkas tidak ditemukan di penyimpanan", Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = resolveShareableUri(context, file) ?: run {
            Toast.makeText(toastContext, "Gagal membuka berkas", Toast.LENGTH_SHORT).show()
            return
        }

        val mimeType = getMimeType(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(toastContext, "Tidak ada aplikasi untuk memutar berkas ini", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareFile(context: Context, filePath: String) {
        val toastContext = context.applicationContext ?: context
        if (filePath.startsWith("content://")) {
            try {
                val uri = filePath.toUri()
                val mimeType = context.contentResolver.getType(uri) ?: "*/*"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Bagikan berkas via").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(toastContext, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(toastContext, "Berkas tidak ditemukan", Toast.LENGTH_SHORT).show()
            return
        }

        val uri: Uri = resolveShareableUri(context, file) ?: run {
            Toast.makeText(toastContext, "Gagal membagikan berkas", Toast.LENGTH_SHORT).show()
            return
        }

        val mimeType = getMimeType(file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Bagikan berkas via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(toastContext, "Gagal membagikan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Menghasilkan URI [FileProvider] apabila [file] berada di dalam root yang dikonfigurasi
     * (`Download/` atau penyimpanan aplikasi), atau membuat salinan sementara khusus berbagi di
     * `cache/shared_media/` untuk berkas dari folder kustom di luar cakupan FileProvider.
     */
    private fun resolveShareableUri(context: Context, file: File): Uri? {
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: IllegalArgumentException) {
            try {
                val shareCacheDir = File(context.cacheDir, SHARED_MEDIA_DIR).apply {
                    if (exists()) {
                        listFiles()?.forEach { it.delete() }
                    } else {
                        mkdirs()
                    }
                }
                val cacheCopy = File(shareCacheDir, file.name)
                FileInputStream(file).use { input ->
                    FileOutputStream(cacheCopy).use { output ->
                        input.copyTo(output)
                    }
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheCopy)
            } catch (_: Exception) {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getMimeType(file: File): String {
        val extension = file.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        return mime ?: resolveFallbackMimeType(extension)
    }

    internal fun resolveFallbackMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "opus" -> "audio/opus"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> "*/*"
        }
    }
}
