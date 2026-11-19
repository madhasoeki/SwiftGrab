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
        """(https?://(?:(?!,https?://)[a-zA-Z0-9\-._~:/?#\[\]@!$&'()*+,;=%])+)""",
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

    private val MAX_FORBIDDEN_SCHEME_LENGTH = FORBIDDEN_SCHEMES.maxOf { it.length }

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
     * Extracts all safe, unique HTTP or HTTPS URLs found in [text] (e.g., multi-line batch input).
     * Lines starting with forbidden schemes or URLs containing forbidden schemes are ignored.
     */
    fun extractUrls(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()

        val results = LinkedHashSet<String>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val lower = trimmed.lowercase()
            if (FORBIDDEN_SCHEMES.any { lower.startsWith(it) }) {
                continue
            }

            val matcher = WEB_URL_PATTERN.matcher(trimmed)
            while (matcher.find()) {
                val matchStart = matcher.start()
                val prefixStart = (matchStart - MAX_FORBIDDEN_SCHEME_LENGTH).coerceAtLeast(0)
                val prefix = trimmed.substring(prefixStart, matchStart)
                if (FORBIDDEN_SCHEMES.any { prefix.endsWith(it, ignoreCase = true) }) {
                    continue
                }
                val rawUrl = matcher.group(1) ?: continue
                val cleaned = cleanTrailingPunctuation(rawUrl)
                if (isValidWebUrl(cleaned)) {
                    results.add(cleaned)
                }
            }
        }
        return results.toList()
    }

    /**
     * Verifies that the given [url] is a valid HTTP or HTTPS URL, does not contain forbidden schemes,
     * does not contain userinfo, and does not target local/private/loopback IPs or localhost.
     */
    fun isValidWebUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (trimmed.contains('\\')) return false

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

        val withoutProtocol = if (isHttps) trimmed.substring(8) else trimmed.substring(7)
        val authority = withoutProtocol.takeWhile { it != '/' && it != '?' && it != '#' && it != '\\' }
        if (authority.isBlank()) return false

        // Reject userinfo (e.g., https://user:pass@host/ or https://user@host/)
        if (authority.contains('@') || authority.contains('\\')) return false

        val host = if (authority.startsWith('[')) {
            val closeBracket = authority.indexOf(']')
            if (closeBracket == -1) return false
            authority.substring(0, closeBracket + 1)
        } else {
            authority.takeWhile { it != ':' }
        }

        if (host.isBlank() || host.contains('\\')) return false

        // Reject local/private/loopback IPs and localhost
        if (isLocalOrPrivateHost(host)) return false

        val cleanHost = host.removePrefix("[").removeSuffix("]")
        if (!cleanHost.contains('.') && !cleanHost.contains(':')) return false

        return true
    }

    internal fun isLocalOrPrivateHost(host: String): Boolean {
        if (host.contains('\\') || host.contains('@')) return true
        val lower = host.lowercase().removePrefix("[").removeSuffix("]")
        if (lower == "localhost" || lower.endsWith(".localhost") || lower.endsWith(".local") || lower.endsWith(".localdomain")) {
            return true
        }
        if (lower == "::1" || lower == "0:0:0:0:0:0:0:1" || lower == "::" || lower == "0:0:0:0:0:0:0:0") {
            return true
        }
        // IPv4 0.0.0.0/8 (routes to loopback on Android/Linux)
        if (lower == "0.0.0.0" || lower.startsWith("0.")) return true
        // IPv4 Loopback and RFC 1918 Private ranges
        if (lower.startsWith("127.")) return true
        if (lower.startsWith("10.")) return true
        if (lower.startsWith("192.168.")) return true
        if (lower.startsWith("169.254.")) return true
        if (lower.startsWith("172.")) {
            val parts = lower.split(".")
            if (parts.size >= 2) {
                val second = parts[1].toIntOrNull()
                if (second != null && second in 16..31) {
                    return true
                }
            }
        }
        // IPv6 Link-Local (fe80::/10: fe8*, fe9*, fea*, feb*)
        if (lower.startsWith("fe80:") || lower.startsWith("fe8") || lower.startsWith("fe9") || lower.startsWith("fea") || lower.startsWith("feb")) {
            return true
        }
        // IPv6 Unique Local Address (fc00::/7: fc*, fd*)
        if (lower.startsWith("fc") || lower.startsWith("fd")) {
            return true
        }
        // IPv4-mapped IPv6 (::ffff:0:0/96)
        if (lower.startsWith("::ffff:")) {
            val v4 = lower.removePrefix("::ffff:")
            if (v4 == "0.0.0.0" || v4.startsWith("0.") || v4.startsWith("127.") || v4.startsWith("10.") || v4.startsWith("192.168.") || v4.startsWith("169.254.")) {
                return true
            }
            if (v4.startsWith("172.")) {
                val parts = v4.split(".")
                if (parts.size >= 2) {
                    val second = parts[1].toIntOrNull()
                    if (second != null && second in 16..31) {
                        return true
                    }
                }
            }
        }
        return false
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
        val authority = afterProtocol.takeWhile { it != '/' && it != '?' && it != '#' }
        val withoutUser = authority.substringAfterLast('@')
        val host = if (withoutUser.startsWith('[')) {
            val close = withoutUser.indexOf(']')
            if (close != -1) withoutUser.substring(0, close + 1) else withoutUser.takeWhile { it != ':' }
        } else {
            withoutUser.takeWhile { it != ':' }
        }
        return host.removePrefix("www.")
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
