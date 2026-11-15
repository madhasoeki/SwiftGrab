package com.example.swiftgrab.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSharingHelperTest {

    @Test
    fun `resolveFallbackMimeType maps common video and audio extensions`() {
        assertEquals("video/mp4", FileSharingHelper.resolveFallbackMimeType("mp4"))
        assertEquals("video/mp4", FileSharingHelper.resolveFallbackMimeType("MP4"))
        assertEquals("video/x-matroska", FileSharingHelper.resolveFallbackMimeType("mkv"))
        assertEquals("video/webm", FileSharingHelper.resolveFallbackMimeType("webm"))
        assertEquals("video/quicktime", FileSharingHelper.resolveFallbackMimeType("mov"))
        assertEquals("audio/mpeg", FileSharingHelper.resolveFallbackMimeType("mp3"))
        assertEquals("audio/mp4", FileSharingHelper.resolveFallbackMimeType("m4a"))
        assertEquals("audio/aac", FileSharingHelper.resolveFallbackMimeType("aac"))
        assertEquals("audio/opus", FileSharingHelper.resolveFallbackMimeType("opus"))
        assertEquals("audio/ogg", FileSharingHelper.resolveFallbackMimeType("ogg"))
        assertEquals("audio/wav", FileSharingHelper.resolveFallbackMimeType("wav"))
        assertEquals("audio/flac", FileSharingHelper.resolveFallbackMimeType("flac"))
        assertEquals("*/*", FileSharingHelper.resolveFallbackMimeType("unknown"))
    }
}
