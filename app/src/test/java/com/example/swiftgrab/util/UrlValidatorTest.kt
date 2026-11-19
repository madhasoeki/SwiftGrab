package com.example.swiftgrab.util

import com.example.swiftgrab.data.prefs.DownloadPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun `extractUrl handles mixed YouTube share text with trailing punctuation`() {
        val shared = "Tonton video keren ini! https://youtu.be/dQw4w9WgXcQ?si=abc123. Wajib lihat!"
        assertEquals("https://youtu.be/dQw4w9WgXcQ?si=abc123", UrlValidator.extractUrl(shared))
    }

    @Test
    fun `extractUrl handles TikTok and Instagram share texts`() {
        val tiktok = "Check out this post on TikTok: (https://vt.tiktok.com/ZS2abc123/)"
        assertEquals("https://vt.tiktok.com/ZS2abc123/", UrlValidator.extractUrl(tiktok))

        val ig = "Lihat reel ini https://www.instagram.com/reel/C8XYZ999/?igsh=123, seru!"
        assertEquals("https://www.instagram.com/reel/C8XYZ999/?igsh=123", UrlValidator.extractUrl(ig))
    }

    @Test
    fun `extractUrl preserves balanced parentheses inside URL`() {
        val wiki = "Info: https://en.wikipedia.org/wiki/Kotlin_(programming_language)."
        assertEquals(
            "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
            UrlValidator.extractUrl(wiki)
        )
    }

    @Test
    fun `extractUrl rejects local file and content URIs and unsafe schemes`() {
        assertNull(UrlValidator.extractUrl("file:///storage/emulated/0/Download/test.mp4"))
        assertNull(UrlValidator.extractUrl("content://com.android.providers.media/external/video/1"))
        assertNull(UrlValidator.extractUrl("javascript:alert(1)"))
        assertNull(UrlValidator.extractUrl("data:text/plain;base64,SGVsbG8="))
        assertNull(UrlValidator.extractUrl("ftp://example.com/file.mp4"))
        assertNull(UrlValidator.extractUrl("https://example.com/?next=file:///etc/passwd"))
    }

    @Test
    fun `extractUrl returns null for blank or non-URL text`() {
        assertNull(UrlValidator.extractUrl(null))
        assertNull(UrlValidator.extractUrl("   "))
        assertNull(UrlValidator.extractUrl("Tidak ada tautan sama sekali di pesan ini"))
    }

    @Test
    fun `extractUrls extracts multiple unique valid URLs across lines and mixed text`() {
        val multiLine = """
            https://youtu.be/dQw4w9WgXcQ?si=abc123
            
            Lihat ini: (https://vt.tiktok.com/ZS2abc123/)
            https://youtu.be/dQw4w9WgXcQ?si=abc123
            https://www.instagram.com/reel/C8XYZ999/?igsh=123, https://x.com/user/status/456.
        """.trimIndent()

        assertEquals(
            listOf(
                "https://youtu.be/dQw4w9WgXcQ?si=abc123",
                "https://vt.tiktok.com/ZS2abc123/",
                "https://www.instagram.com/reel/C8XYZ999/?igsh=123",
                "https://x.com/user/status/456"
            ),
            UrlValidator.extractUrls(multiLine)
        )
    }

    @Test
    fun `extractUrls splits adjacent comma-delimited links without whitespace while preserving commas inside URLs`() {
        val commaSeparated = "https://youtu.be/dQw4w9WgXcQ,https://vt.tiktok.com/ZS2abc123/"
        assertEquals(
            listOf(
                "https://youtu.be/dQw4w9WgXcQ",
                "https://vt.tiktok.com/ZS2abc123/"
            ),
            UrlValidator.extractUrls(commaSeparated)
        )

        val urlWithInternalCommas = "https://example.com/watch/a,b?ids=1,2,3"
        assertEquals(
            listOf("https://example.com/watch/a,b?ids=1,2,3"),
            UrlValidator.extractUrls(urlWithInternalCommas)
        )
    }

    @Test
    fun `extractUrls skips forbidden schemes while keeping valid URLs`() {
        val mixed = """
            file:///storage/emulated/0/Download/test.mp4
            javascript:https://evil.com/video.mp4
            foo javascript:https://evil.com/other.mp4
            Some preceding text before JAVASCRIPT:https://evil.com/upper.mp4
            https://example.com/?next=file:///etc/passwd
            https://www.youtube.com/watch?v=valid123
        """.trimIndent()

        assertEquals(
            listOf("https://www.youtube.com/watch?v=valid123"),
            UrlValidator.extractUrls(mixed)
        )
    }

    @Test
    fun `extractUrls returns empty list for blank or non-URL text`() {
        assertTrue(UrlValidator.extractUrls(null).isEmpty())
        assertTrue(UrlValidator.extractUrls("   \n  ").isEmpty())
        assertTrue(UrlValidator.extractUrls("Tidak ada tautan sama sekali").isEmpty())
    }

    @Test
    fun `isValidWebUrl validates host and protocol`() {
        assertTrue(UrlValidator.isValidWebUrl("https://www.youtube.com/watch?v=123"))
        assertTrue(UrlValidator.isValidWebUrl("http://example.org/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://localhost/test"))
        assertFalse(UrlValidator.isValidWebUrl("file:///sdcard/test"))
        assertFalse(UrlValidator.isValidWebUrl("content://media/1"))
    }

    @Test
    fun `isValidWebUrl rejects local, private, and loopback IPs and hosts`() {
        assertFalse(UrlValidator.isValidWebUrl("http://localhost/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://localhost:8080/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://app.localhost/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://127.0.0.1/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://127.0.0.1:8000/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://127.255.0.1/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://10.0.0.1/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://10.254.1.1:8443/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://192.168.0.1/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://192.168.100.50/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://169.254.1.1/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://169.254.169.254/latest/meta-data/"))
        assertFalse(UrlValidator.isValidWebUrl("https://172.16.0.1/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://172.24.1.1/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://172.31.255.255/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://[::1]/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://[::1]:8080/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://::1/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://0.0.0.0/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://0.0.0.1:8080/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://[fe80::1]/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://[fc00::1]/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://[fd00::1]/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("http://localhost\\example.com/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://evil.com\\.example.com/video.mp4"))

        // Public IPs should remain allowed
        assertTrue(UrlValidator.isValidWebUrl("https://8.8.8.8/video.mp4"))
        assertTrue(UrlValidator.isValidWebUrl("https://172.32.0.1/video.mp4"))

        // Public domains starting with fc, fd, fe, or digits should NOT be falsely blocked as private IPs
        assertTrue(UrlValidator.isValidWebUrl("https://fc2.com/video"))
        assertTrue(UrlValidator.isValidWebUrl("https://video.fc2.com/content/123"))
        assertTrue(UrlValidator.isValidWebUrl("https://fdroid.org/repo/app.apk"))
        assertTrue(UrlValidator.isValidWebUrl("https://febspot.com/v/123"))
        assertTrue(UrlValidator.isValidWebUrl("https://feature.fm/track/123"))
        assertTrue(UrlValidator.isValidWebUrl("https://10.example.com/video.mp4"))
    }

    @Test
    fun `isValidWebUrl rejects userinfo in URLs`() {
        assertFalse(UrlValidator.isValidWebUrl("https://user:password@youtube.com/watch?v=123"))
        assertFalse(UrlValidator.isValidWebUrl("https://user@x.com/status/123"))
        assertFalse(UrlValidator.isValidWebUrl("https://user:pass@127.0.0.1/video.mp4"))
    }

    @Test
    fun `extractUrl and extractUrls reject local IPs and userinfo`() {
        assertNull(UrlValidator.extractUrl("Check out http://127.0.0.1:8080/video.mp4"))
        assertNull(UrlValidator.extractUrl("Check out https://user:pass@example.com/video.mp4"))
        assertNull(UrlValidator.extractUrl("Check out http://[::1]/video.mp4"))

        val mixed = """
            http://127.0.0.1/local.mp4
            https://user:pass@example.com/cred.mp4
            http://[::1]:8080/ipv6.mp4
            https://www.youtube.com/watch?v=valid999
        """.trimIndent()
        assertEquals(
            listOf("https://www.youtube.com/watch?v=valid999"),
            UrlValidator.extractUrls(mixed)
        )
    }

    @Test
    fun `extractDomain removes protocol and www prefix`() {
        assertEquals("youtube.com", UrlValidator.extractDomain("https://www.youtube.com/watch?v=abc"))
        assertEquals("vt.tiktok.com", UrlValidator.extractDomain("https://vt.tiktok.com/ZS2xyz/"))
    }

    @Test
    fun `download preset resolves correctly from task and name`() {
        assertEquals(DownloadPreset.DATA_SAVER, DownloadPreset.fromTask("MP4 720p", audioOnly = false))
        assertEquals(DownloadPreset.AUDIO_ONLY, DownloadPreset.fromTask("MP3", audioOnly = true))
        assertEquals(DownloadPreset.BEST_VIDEO, DownloadPreset.fromTask("MP4 Terbaik", audioOnly = false))
        assertEquals(DownloadPreset.DATA_SAVER, DownloadPreset.fromName("DATA_SAVER"))
        assertEquals(DownloadPreset.BEST_VIDEO, DownloadPreset.fromName("UNKNOWN_PRESET"))
    }
}
