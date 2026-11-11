package com.example.swiftgrab.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressParserTest {

    @Test
    fun `parses ytdlp standard download line accurately`() {
        val line = "[download]  45.2% of  120.00MiB at    8.40MiB/s ETA 00:09"
        val parsed = DownloadProgressParser.parse(
            callbackProgress = 45.2f,
            callbackEtaSeconds = 9L,
            rawLine = line
        )

        assertEquals(45.2f, parsed.progress, 0.01f)
        assertEquals("8.40 MB/s", parsed.speed)
        assertEquals("00:09", parsed.eta)
        assertEquals(120L * 1024L * 1024L, parsed.totalBytes)
        assertTrue(parsed.downloadedBytes > 0L)
    }

    @Test
    fun `parses aria2c progress line accurately`() {
        val line = "[#1a2b3c 54MiB/120MiB(45%) CN:8 DL:8.4MiB ETA:1m20s]"
        val parsed = DownloadProgressParser.parse(
            callbackProgress = 45.0f,
            callbackEtaSeconds = -1L,
            rawLine = line
        )

        assertEquals(45.0f, parsed.progress, 0.01f)
        assertEquals("8.4 MB/s", parsed.speed)
        assertEquals("01:20", parsed.eta)
        assertEquals(54L * 1024L * 1024L, parsed.downloadedBytes)
        assertEquals(120L * 1024L * 1024L, parsed.totalBytes)
    }

    @Test
    fun `parses ytdlp line with tilde approximate size`() {
        val line = "[download]  50.0% of ~100.00MiB at   10.50MiB/s ETA 00:05"
        val parsed = DownloadProgressParser.parse(
            callbackProgress = 50.0f,
            callbackEtaSeconds = 5L,
            rawLine = line
        )

        assertEquals(50.0f, parsed.progress, 0.01f)
        assertEquals("10.50 MB/s", parsed.speed)
        assertEquals("00:05", parsed.eta)
        assertEquals(100L * 1024L * 1024L, parsed.totalBytes)
        assertEquals(50L * 1024L * 1024L, parsed.downloadedBytes)
    }

    @Test
    fun `formats eta above one hour correctly`() {
        val eta = DownloadProgressParser.formatEta(3665L, "")
        assertEquals("01:01:05", eta)
    }

    @Test
    fun `throttler limits updates to minimum 250ms interval`() {
        var fakeTime = 1000L
        val throttler = UpdateThrottler(minIntervalMs = 250L) { fakeTime }

        assertTrue(throttler.shouldUpdate())

        fakeTime = 1100L
        assertFalse(throttler.shouldUpdate())

        fakeTime = 1249L
        assertFalse(throttler.shouldUpdate())

        fakeTime = 1250L
        assertTrue(throttler.shouldUpdate())

        fakeTime = 1260L
        assertFalse(throttler.shouldUpdate())
        assertTrue(throttler.shouldUpdate(force = true))
    }
}
