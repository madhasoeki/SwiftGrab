package com.example.swiftgrab.data.prefs

/**
 * Download format presets shown in Quick Format Sheet and used for One-Tap / Auto-Clipboard downloads.
 */
enum class DownloadPreset(
    val formatLabel: String,
    val displayTitle: String,
    val subtitle: String,
    val audioOnly: Boolean,
    val formatSelector: String?
) {
    BEST_VIDEO(
        formatLabel = "MP4 Terbaik",
        displayTitle = "Video (MP4 Terbaik)",
        subtitle = "Resolusi tertinggi hingga 1080p/4K",
        audioOnly = false,
        formatSelector = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best"
    ),
    DATA_SAVER(
        formatLabel = "MP4 720p",
        displayTitle = "Hemat Kuota (720p/480p)",
        subtitle = "Ukuran ringkas untuk menghemat kuota",
        audioOnly = false,
        formatSelector = "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best[height<=720]/best"
    ),
    AUDIO_ONLY(
        formatLabel = "MP3",
        displayTitle = "Audio Saja (MP3)",
        subtitle = "Audio MP3 berkualitas tinggi + thumbnail",
        audioOnly = true,
        formatSelector = null
    );

    companion object {
        fun fromName(name: String?): DownloadPreset {
            return entries.firstOrNull { it.name == name } ?: BEST_VIDEO
        }

        fun fromTask(formatLabel: String?, audioOnly: Boolean): DownloadPreset {
            if (audioOnly) return AUDIO_ONLY
            return when {
                formatLabel?.contains("720", ignoreCase = true) == true -> DATA_SAVER
                formatLabel?.contains("480", ignoreCase = true) == true -> DATA_SAVER
                else -> BEST_VIDEO
            }
        }
    }
}
