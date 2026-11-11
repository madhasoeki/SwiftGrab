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
    fun `isValidWebUrl validates host and protocol`() {
        assertTrue(UrlValidator.isValidWebUrl("https://www.youtube.com/watch?v=123"))
        assertTrue(UrlValidator.isValidWebUrl("http://example.org/video.mp4"))
        assertFalse(UrlValidator.isValidWebUrl("https://localhost/test"))
        assertFalse(UrlValidator.isValidWebUrl("file:///sdcard/test"))
        assertFalse(UrlValidator.isValidWebUrl("content://media/1"))
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
