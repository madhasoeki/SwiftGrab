package com.example.swiftgrab.service

import com.example.swiftgrab.data.prefs.DownloadPreset
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

object DownloadRequestBuilder {

    /**
     * Builds a standard YoutubeDLRequest configured with aria2c multi-connection acceleration,
     * safe resume support (--continue), and format selection.
     */
    fun build(
        url: String,
        outputDir: File,
        audioOnly: Boolean = false,
        cookiesFile: File? = null,
        extraOptions: Map<String, String> = emptyMap()
    ): YoutubeDLRequest {
        val preset = if (audioOnly) DownloadPreset.AUDIO_ONLY else DownloadPreset.BEST_VIDEO
        return build(
            url = url,
            outputDir = outputDir,
            preset = preset,
            cookiesFile = cookiesFile,
            extraOptions = extraOptions
        )
    }

    /**
     * Builds a YoutubeDLRequest using the specified [DownloadPreset].
     */
    fun build(
        url: String,
        outputDir: File,
        preset: DownloadPreset,
        cookiesFile: File? = null,
        extraOptions: Map<String, String> = emptyMap()
    ): YoutubeDLRequest {
        return YoutubeDLRequest(url).apply {
            addOption("--no-mtime")
            addOption("-v")
            addOption("--newline")
            addOption("--continue")
            addOption("--no-update")

            // Aria2c multi-connection acceleration & robust chunk resumption
            addOption("--downloader", "libaria2c.so")
            addOption("--downloader-args", "aria2c:\"-c -x 8 -s 8 -k 1M --file-allocation=none\"")

            // Attach user session cookies (e.g. for age-restricted / sensitive content on X/Twitter)
            if (cookiesFile != null && cookiesFile.exists() && cookiesFile.length() > 0L) {
                addOption("--cookies", cookiesFile.absolutePath)
            }

            if (preset.audioOnly) {
                addOption("-x")
                addOption("--audio-format", "mp3")
                addOption("--embed-thumbnail")
                addOption("--embed-metadata")
            } else {
                val selector = preset.formatSelector
                    ?: "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
                addOption("-f", selector)
                addOption("--merge-output-format", "mp4")
            }

            // Output template: title truncated to 150 bytes to avoid filesystem max path issues, with video ID and extension
            addOption("-o", File(outputDir, "%(title).150B [%(id)s].%(ext)s").absolutePath)

            // Any extra options specified by the caller
            for ((key, value) in extraOptions) {
                if (value.isEmpty()) {
                    addOption(key)
                } else {
                    addOption(key, value)
                }
            }
        }
    }
}
