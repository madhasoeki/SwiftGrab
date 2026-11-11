package com.example.swiftgrab.service

import com.example.swiftgrab.data.prefs.DownloadPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadRequestBuilderTest {

    @Test
    fun `video request includes aria2c, continue, and mp4 merge options`() {
        val outDir = File("/tmp/swiftgrab/task1")
        val request = DownloadRequestBuilder.build(
            url = "https://example.com/video",
            outputDir = outDir,
            audioOnly = false
        )

        val commands = request.buildCommand()
        assertTrue(commands.contains("--continue"))
        assertTrue(commands.contains("--no-update"))
        assertTrue(commands.contains("--downloader"))
        assertEquals("libaria2c.so", request.getOption("--downloader"))
        assertEquals(
            "aria2c:\"-c -x 8 -s 8 -k 1M --file-allocation=none\"",
            request.getOption("--downloader-args")
        )
        assertEquals("mp4", request.getOption("--merge-output-format"))
        assertTrue(commands.contains("https://example.com/video"))
    }

    @Test
    fun `data saver preset limits resolution to 720p`() {
        val outDir = File("/tmp/swiftgrab/task3")
        val request = DownloadRequestBuilder.build(
            url = "https://example.com/video",
            outputDir = outDir,
            preset = DownloadPreset.DATA_SAVER
        )

        val formatArg = request.getOption("-f") ?: ""
        assertTrue(formatArg.contains("height<=720"))
        assertEquals("mp4", request.getOption("--merge-output-format"))
    }

    @Test
    fun `audio request includes mp3 extraction and metadata embedding`() {
        val outDir = File("/tmp/swiftgrab/task2")
        val request = DownloadRequestBuilder.build(
            url = "https://example.com/audio",
            outputDir = outDir,
            audioOnly = true
        )

        val commands = request.buildCommand()
        assertTrue(commands.contains("-x"))
        assertEquals("mp3", request.getOption("--audio-format"))
        assertTrue(commands.contains("--embed-thumbnail"))
        assertTrue(commands.contains("--embed-metadata"))
        assertTrue(commands.contains("--continue"))
    }

    @Test
    fun `request attaches --cookies flag when valid cookies file is provided`() {
        val outDir = File("/tmp/swiftgrab/task4")
        val tempCookieFile = File.createTempFile("x_cookies", ".txt").apply {
            writeText("# Netscape HTTP Cookie File\n.x.com\tTRUE\t/\tTRUE\t2147483647\tauth_token\tdemo\n")
            deleteOnExit()
        }

        val request = DownloadRequestBuilder.build(
            url = "https://x.com/user/status/123",
            outputDir = outDir,
            preset = DownloadPreset.BEST_VIDEO,
            cookiesFile = tempCookieFile
        )

        assertEquals(tempCookieFile.absolutePath, request.getOption("--cookies"))
    }
}
