package com.example.swiftgrab.service

import java.util.Locale
import java.util.regex.Pattern

data class ParsedProgress(
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: String,
    val eta: String
)

object DownloadProgressParser {

    private val YT_DLP_SPEED_PATTERN = Pattern.compile("""at\s+([0-9.]+\s*[KMGT]?i?B/s)""", Pattern.CASE_INSENSITIVE)
    private val ARIA2C_SPEED_PATTERN = Pattern.compile("""DL:([0-9.]+\s*[KMGT]?i?B(?:/s)?)""", Pattern.CASE_INSENSITIVE)
    private val GENERAL_SPEED_PATTERN = Pattern.compile("""\b([0-9.]+\s*[KMGT]?i?B/s)\b""", Pattern.CASE_INSENSITIVE)

    private val YT_DLP_TOTAL_BYTES_PATTERN = Pattern.compile(
        """\bof\s+~?\s*([0-9.]+\s*[KMGT]?i?B)""",
        Pattern.CASE_INSENSITIVE
    )
    private val ARIA2C_BYTES_PATTERN = Pattern.compile(
        """([0-9.]+\s*[KMGT]?i?B)/([0-9.]+\s*[KMGT]?i?B)""",
        Pattern.CASE_INSENSITIVE
    )

    private val PERCENT_PATTERN = Pattern.compile("""\b([0-9]+(?:\.[0-9]+)?)%""")
    private val ETA_PATTERN = Pattern.compile("""ETA\s+([0-9]+:[0-9]+(?::[0-9]+)?)""", Pattern.CASE_INSENSITIVE)
    private val ARIA2C_ETA_PATTERN = Pattern.compile("""ETA:(\d+m)?(\d+s)?""", Pattern.CASE_INSENSITIVE)

    /**
     * Parses the callback parameters and raw output line into structured progress data.
     */
    fun parse(
        callbackProgress: Float,
        callbackEtaSeconds: Long,
        rawLine: String,
        lastKnownTotalBytes: Long = 0L,
        lastKnownDownloadedBytes: Long = 0L
    ): ParsedProgress {
        var progress = if (callbackProgress > 0f) callbackProgress else parsePercent(rawLine)
        if (progress < 0f) progress = 0f
        if (progress > 100f) progress = 100f

        val speed = parseSpeed(rawLine)
        val eta = formatEta(callbackEtaSeconds, rawLine)

        var (downloadedBytes, totalBytes) = parseBytes(rawLine)
        if (totalBytes <= 0L && lastKnownTotalBytes > 0L) {
            totalBytes = lastKnownTotalBytes
        }
        if (downloadedBytes <= 0L) {
            if (totalBytes > 0L && progress > 0f) {
                downloadedBytes = (totalBytes * (progress / 100f)).toLong()
            } else if (lastKnownDownloadedBytes > 0L) {
                downloadedBytes = lastKnownDownloadedBytes
            }
        }

        return ParsedProgress(
            progress = progress,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speed = speed,
            eta = eta
        )
    }

    fun parseSpeed(line: String): String {
        val ytdlMatcher = YT_DLP_SPEED_PATTERN.matcher(line)
        if (ytdlMatcher.find()) {
            return formatSpeedUnit(ytdlMatcher.group(1)?.trim() ?: "")
        }

        val ariaMatcher = ARIA2C_SPEED_PATTERN.matcher(line)
        if (ariaMatcher.find()) {
            val raw = ariaMatcher.group(1)?.trim() ?: ""
            val withSlash = if (raw.endsWith("/s", ignoreCase = true)) raw else "$raw/s"
            return formatSpeedUnit(withSlash)
        }

        val genMatcher = GENERAL_SPEED_PATTERN.matcher(line)
        if (genMatcher.find()) {
            return formatSpeedUnit(genMatcher.group(1)?.trim() ?: "")
        }

        return ""
    }

    fun parseBytes(line: String): Pair<Long, Long> {
        val ariaMatcher = ARIA2C_BYTES_PATTERN.matcher(line)
        if (ariaMatcher.find()) {
            val downloaded = parseByteSize(ariaMatcher.group(1) ?: "")
            val total = parseByteSize(ariaMatcher.group(2) ?: "")
            return Pair(downloaded, total)
        }

        val ytdlMatcher = YT_DLP_TOTAL_BYTES_PATTERN.matcher(line)
        if (ytdlMatcher.find()) {
            val total = parseByteSize(ytdlMatcher.group(1) ?: "")
            return Pair(0L, total)
        }

        return Pair(0L, 0L)
    }

    fun parsePercent(line: String): Float {
        val matcher = PERCENT_PATTERN.matcher(line)
        if (matcher.find()) {
            return matcher.group(1)?.toFloatOrNull() ?: 0f
        }
        return 0f
    }

    fun formatEta(etaSeconds: Long, line: String): String {
        if (etaSeconds > 0L) {
            val hours = etaSeconds / 3600
            val minutes = (etaSeconds % 3600) / 60
            val seconds = etaSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

        val etaMatcher = ETA_PATTERN.matcher(line)
        if (etaMatcher.find()) {
            return etaMatcher.group(1)?.trim() ?: ""
        }

        val ariaMatcher = ARIA2C_ETA_PATTERN.matcher(line)
        if (ariaMatcher.find()) {
            val m = ariaMatcher.group(1)?.removeSuffix("m")?.toLongOrNull() ?: 0L
            val s = ariaMatcher.group(2)?.removeSuffix("s")?.toLongOrNull() ?: 0L
            return String.format(Locale.US, "%02d:%02d", m, s)
        }

        return ""
    }

    fun parseByteSize(sizeStr: String): Long {
        val trimmed = sizeStr.trim()
        if (trimmed.isEmpty()) return 0L

        val numberPart = trimmed.takeWhile { it.isDigit() || it == '.' }
        val unitPart = trimmed.substring(numberPart.length).trim().uppercase(Locale.US)

        val value = numberPart.toDoubleOrNull() ?: return 0L
        return when {
            unitPart.startsWith("TIB") -> (value * 1024L * 1024L * 1024L * 1024L).toLong()
            unitPart.startsWith("TB") -> (value * 1_000_000_000_000L).toLong()
            unitPart.startsWith("GIB") -> (value * 1024L * 1024L * 1024L).toLong()
            unitPart.startsWith("GB") -> (value * 1_000_000_000L).toLong()
            unitPart.startsWith("MIB") -> (value * 1024L * 1024L).toLong()
            unitPart.startsWith("MB") -> (value * 1_000_000L).toLong()
            unitPart.startsWith("KIB") -> (value * 1024L).toLong()
            unitPart.startsWith("KB") -> (value * 1000L).toLong()
            unitPart.startsWith("B") -> value.toLong()
            else -> value.toLong()
        }
    }

    private fun formatSpeedUnit(speed: String): String {
        val s = speed.trim()
        val num = s.takeWhile { it.isDigit() || it == '.' }
        val unit = s.substring(num.length).trim().replace("iB", "B", ignoreCase = true)
        val normalizedUnit = when {
            unit.startsWith("MB", ignoreCase = true) -> "MB/s"
            unit.startsWith("KB", ignoreCase = true) -> "KB/s"
            unit.startsWith("GB", ignoreCase = true) -> "GB/s"
            unit.startsWith("TB", ignoreCase = true) -> "TB/s"
            unit.startsWith("B", ignoreCase = true) -> "B/s"
            else -> if (unit.endsWith("/s", ignoreCase = true)) unit else "$unit/s"
        }
        return if (num.isNotEmpty()) "$num $normalizedUnit" else s
    }
}

class UpdateThrottler(
    private val minIntervalMs: Long = 250L,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var lastUpdateMs: Long = 0L
    private var hasEmitted: Boolean = false

    fun shouldUpdate(force: Boolean = false): Boolean {
        val now = nowProvider()
        if (force || !hasEmitted || now - lastUpdateMs >= minIntervalMs) {
            hasEmitted = true
            lastUpdateMs = now
            return true
        }
        return false
    }

    fun reset() {
        lastUpdateMs = 0L
        hasEmitted = false
    }
}
