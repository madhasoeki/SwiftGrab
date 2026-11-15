package com.example.swiftgrab.service

import android.util.Log
import com.example.swiftgrab.SwiftGrabApp
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class MediaMetadata(
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long = 0L
)

/**
 * Ekstraktor metadata ringan berbasis yt-dlp (--dump-single-json --no-download)
 * untuk mengisi judul asli, URL thumbnail, dan durasi media tanpa mengunduh berkas.
 */
object MediaMetadataExtractor {

    private const val TAG = "MediaMetadataExtractor"

    fun buildMetadataRequest(url: String, cookiesFile: File? = null): YoutubeDLRequest {
        return YoutubeDLRequest(url).apply {
            addOption("--dump-single-json")
            addOption("--no-download")
            addOption("--flat-playlist")
            addOption("--no-warnings")
            addOption("--no-update")
            if (cookiesFile != null && cookiesFile.exists() && cookiesFile.length() > 0L) {
                addOption("--cookies", cookiesFile.absolutePath)
            }
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun fetch(
        app: SwiftGrabApp,
        url: String,
        cookiesFile: File?,
        processId: String
    ): MediaMetadata? {
        val engineResult = app.awaitEngineReady()
        if (engineResult.isFailure) {
            return null
        }

        val request = buildMetadataRequest(url = url, cookiesFile = cookiesFile)
        return withContext(Dispatchers.IO) {
            val job = currentCoroutineContext()[Job]
            val cancelHandle = job?.invokeOnCompletion(onCancelling = true) { cause ->
                if (cause != null) {
                    app.destroyProcessAsync(processId)
                }
            }
            try {
                val response = YoutubeDL.getInstance().execute(request, processId, null)
                currentCoroutineContext().ensureActive()
                parseJson(response.out)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "Metadata fetch failed for $url (processId=$processId): ${t.message}")
                null
            } finally {
                cancelHandle?.dispose()
            }
        }
    }

    fun parseJson(rawOutput: String?): MediaMetadata? {
        if (rawOutput.isNullOrBlank()) return null

        val jsonText = extractJsonPayload(rawOutput) ?: return null
        val json = try {
            JSONObject(jsonText)
        } catch (_: Throwable) {
            return null
        }

        val firstEntry = json.optJSONArray("entries")?.optJSONObject(0)

        val title = json.optNonBlankString("title")
            ?: json.optNonBlankString("fulltitle")
            ?: firstEntry?.optNonBlankString("title")
            ?: firstEntry?.optNonBlankString("fulltitle")

        val thumbnailUrl = extractThumbnailUrl(json)
            ?: firstEntry?.let(::extractThumbnailUrl)

        val durationSeconds = extractDurationSeconds(json)
            .takeIf { it > 0L }
            ?: firstEntry?.let(::extractDurationSeconds)
            ?: 0L

        if (title == null && thumbnailUrl == null && durationSeconds <= 0L) {
            return null
        }

        return MediaMetadata(
            title = title,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds
        )
    }

    fun formatDuration(durationSeconds: Long): String {
        if (durationSeconds <= 0L) return ""
        val hours = durationSeconds / 3600L
        val minutes = (durationSeconds % 3600L) / 60L
        val seconds = durationSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun extractJsonPayload(rawOutput: String): String? {
        val trimmed = rawOutput.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        return trimmed.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.startsWith("{") && it.endsWith("}") }
    }

    private fun extractThumbnailUrl(json: JSONObject): String? {
        val direct = json.optNonBlankString("thumbnail")?.takeIf(::isValidHttpUrl)
        if (direct != null) return direct

        val thumbnails = json.optJSONArray("thumbnails") ?: return null
        for (i in thumbnails.length() - 1 downTo 0) {
            val candidate = thumbnails.optJSONObject(i)
                ?.optNonBlankString("url")
                ?.takeIf(::isValidHttpUrl)
            if (candidate != null) {
                return candidate
            }
        }
        return null
    }

    private fun extractDurationSeconds(json: JSONObject): Long {
        if (json.isNull("duration")) return 0L
        val raw = json.optDouble("duration", -1.0)
        if (raw.isNaN() || raw.isInfinite() || raw <= 0.0) return 0L
        return raw.toLong().coerceAtLeast(0L)
    }

    private fun JSONObject.optNonBlankString(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key, "").trim()
        if (value.isEmpty() || value.equals("null", ignoreCase = true)) return null
        return value
    }

    private fun isValidHttpUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.startsWith("https://") || lower.startsWith("http://")
    }
}
