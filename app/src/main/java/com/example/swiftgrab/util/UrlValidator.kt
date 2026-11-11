package com.example.swiftgrab.util

import java.util.regex.Pattern

/**
 * Utility for extracting and validating media URLs from mixed sharing text and clipboard.
 * Enforces strict security rules:
 * - Only http and https protocols are allowed.
 * - Explicitly rejects local/internal URI schemes (file://, content://, javascript:, data:).
 * - Trims trailing punctuation like '.', ',', ')', ']' attached by messaging apps.
 */
object UrlValidator {

    private val WEB_URL_PATTERN: Pattern = Pattern.compile(
        """(https?://[a-zA-Z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+)""",
        Pattern.CASE_INSENSITIVE
    )

    private val FORBIDDEN_SCHEMES = listOf(
        "file://",
        "content://",
        "javascript:",
        "data:",
        "about:",
        "ftp://",
        "android.resource://"
    )

    /**
     * Extracts the first safe HTTP or HTTPS URL found in [text].
     * Returns null if no valid web URL is found or if forbidden schemes are detected.
     */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null

        // Explicit check against forbidden URI schemes to prevent intent/path traversal attacks
        val lower = text.trim().lowercase()
        for (forbidden in FORBIDDEN_SCHEMES) {
            if (lower.startsWith(forbidden)) {
                return null
            }
        }

        val matcher = WEB_URL_PATTERN.matcher(text)
        if (matcher.find()) {
            val rawUrl = matcher.group(1) ?: return null
            val cleaned = cleanTrailingPunctuation(rawUrl)
            return if (isValidWebUrl(cleaned)) cleaned else null
        }
        return null
    }

    /**
     * Verifies that the given [url] is a valid HTTP or HTTPS URL and does not contain forbidden schemes.
     */
    fun isValidWebUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()

        val isHttp = trimmed.startsWith("http://", ignoreCase = true)
        val isHttps = trimmed.startsWith("https://", ignoreCase = true)
        if (!isHttp && !isHttps) return false

        // Double-check no forbidden schemes embedded
        val lower = trimmed.lowercase()
        for (forbidden in FORBIDDEN_SCHEMES) {
            if (lower.contains(forbidden)) {
                return false
            }
        }

        // Host must exist after protocol
        val withoutProtocol = if (isHttps) trimmed.substring(8) else trimmed.substring(7)
        val host = withoutProtocol.takeWhile { it != '/' && it != '?' && it != '#' && it != ':' }
        if (host.isBlank() || !host.contains('.')) return false

        return true
    }

    /**
     * Extracts the clean host/domain name from a valid URL for compact UI display.
     */
    fun extractDomain(url: String): String {
        val trimmed = url.trim()
        val afterProtocol = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed.substring(8)
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed.substring(7)
            else -> return trimmed
        }
        return afterProtocol
            .takeWhile { it != '/' && it != '?' && it != '#' && it != ':' }
            .removePrefix("www.")
    }

    /**
     * Strips punctuation characters that are often mistakenly attached to URLs by social sharing apps.
     */
    private fun cleanTrailingPunctuation(url: String): String {
        var result = url
        while (result.isNotEmpty()) {
            val last = result.last()
            val shouldDrop = when (last) {
                '.', ',', ';', '>', '"', '\'' -> true
                ')' -> result.count { it == ')' } > result.count { it == '(' }
                ']' -> result.count { it == ']' } > result.count { it == '[' }
                else -> false
            }
            if (shouldDrop) {
                result = result.dropLast(1)
            } else {
                break
            }
        }
        return result
    }
}
