package com.example.swiftgrab.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaMetadataExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `parseJson extracts title thumbnail and duration from standard single json`() {
        val raw = """
            {
              "id": "abc123",
              "title": "Sample 4K Video",
              "thumbnail": "https://img.example.com/poster.jpg",
              "duration": 185.7
            }
        """.trimIndent()

        val result = MediaMetadataExtractor.parseJson(raw)
        assertNotNull(result)
        assertEquals("Sample 4K Video", result?.title)
        assertEquals("https://img.example.com/poster.jpg", result?.thumbnailUrl)
        assertEquals(185L, result?.durationSeconds)
    }

    @Test
    fun `parseJson ignores json null literals and falls back to thumbnails array and fulltitle`() {
        val raw = """
            {
              "title": null,
              "fulltitle": "Full Video Title",
              "thumbnail": null,
              "duration": null,
              "thumbnails": [
                {"url": "ftp://invalid.example.com/a.jpg"},
                {"url": "https://img.example.com/small.jpg"},
                {"url": "https://img.example.com/large.webp"}
              ]
            }
        """.trimIndent()

        val result = MediaMetadataExtractor.parseJson(raw)
        assertNotNull(result)
        assertEquals("Full Video Title", result?.title)
        assertEquals("https://img.example.com/large.webp", result?.thumbnailUrl)
        assertEquals(0L, result?.durationSeconds)
    }

    @Test
    fun `parseJson falls back to entries0 for playlist or carousel payloads`() {
        val raw = """
            [debug] Extracting URL: https://x.com/user/status/123
            {"_type":"playlist","title":null,"entries":[{"title":"X Clip Title","thumbnail":"https://pbs.twimg.com/thumb.jpg","duration":42}]}
        """.trimIndent()

        val result = MediaMetadataExtractor.parseJson(raw)
        assertNotNull(result)
        assertEquals("X Clip Title", result?.title)
        assertEquals("https://pbs.twimg.com/thumb.jpg", result?.thumbnailUrl)
        assertEquals(42L, result?.durationSeconds)
    }

    @Test
    fun `parseJson returns null on blank or malformed json`() {
        assertNull(MediaMetadataExtractor.parseJson(null))
        assertNull(MediaMetadataExtractor.parseJson("   "))
        assertNull(MediaMetadataExtractor.parseJson("not json at all"))
        assertNull(MediaMetadataExtractor.parseJson("{}"))
    }

    @Test
    fun `formatDuration formats mm ss and h mm ss accurately`() {
        assertEquals("", MediaMetadataExtractor.formatDuration(0L))
        assertEquals("", MediaMetadataExtractor.formatDuration(-5L))
        assertEquals("00:09", MediaMetadataExtractor.formatDuration(9L))
        assertEquals("03:05", MediaMetadataExtractor.formatDuration(185L))
        assertEquals("1:02:05", MediaMetadataExtractor.formatDuration(3725L))
    }

    @Test
    fun `buildMetadataRequest configures lightweight metadata flags and optional cookies`() {
        val requestNoCookies = MediaMetadataExtractor.buildMetadataRequest("https://example.com/v")
        val cmdNoCookies = requestNoCookies.buildCommand()
        assertTrue(cmdNoCookies.contains("--dump-single-json"))
        assertTrue(cmdNoCookies.contains("--no-download"))
        assertTrue(cmdNoCookies.contains("--flat-playlist"))
        assertTrue(cmdNoCookies.contains("--no-warnings"))
        assertTrue(cmdNoCookies.contains("--no-update"))
        assertFalse(cmdNoCookies.contains("--cookies"))

        val cookieFile = tempFolder.newFile("cookies.txt").apply {
            writeText(".x.com\tTRUE\t/\tTRUE\t0\tauth_token\tabc\n")
        }
        val requestWithCookies = MediaMetadataExtractor.buildMetadataRequest(
            url = "https://x.com/user/status/1",
            cookiesFile = cookieFile
        )
        val cmdWithCookies = requestWithCookies.buildCommand()
        assertTrue(cmdWithCookies.contains("--cookies"))
        assertTrue(cmdWithCookies.contains(cookieFile.absolutePath))
    }
}
