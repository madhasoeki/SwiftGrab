package com.example.swiftgrab.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `cleanup removes staging directory and temp files`() {
        val rootDir = tempFolder.newFolder("downloads")
        val taskId = "test-task-123"
        val stagingDir = File(rootDir, ".staging_$taskId").apply { mkdirs() }

        val partFile = File(stagingDir, "video.mp4.part").apply { writeText("partial") }
        val ariaFile = File(stagingDir, "video.mp4.aria2").apply { writeText("aria") }

        assertTrue(partFile.exists())
        assertTrue(ariaFile.exists())

        stagingDir.deleteRecursively()
        assertFalse(stagingDir.exists())
    }

    @Test
    fun `completed media file is identified and promoted`() {
        val rootDir = tempFolder.newFolder("downloads_final")
        val taskId = "test-task-456"
        val stagingDir = File(rootDir, ".staging_$taskId").apply { mkdirs() }

        val completedVideo = File(stagingDir, "sample_video [123].mp4").apply {
            writeText("video-content-complete")
        }

        val mediaFiles = stagingDir.listFiles { file ->
            !file.name.endsWith(".part") &&
                !file.name.endsWith(".aria2") &&
                !file.name.endsWith(".ytdl") &&
                !file.name.endsWith(".temp")
        }

        assertNotNull(mediaFiles)
        assertEquals(1, mediaFiles?.size)
        assertEquals("sample_video [123].mp4", mediaFiles?.first()?.name)

        val targetFile = File(rootDir, completedVideo.name)
        assertTrue(completedVideo.renameTo(targetFile))
        assertTrue(targetFile.exists())
        assertEquals("video-content-complete", targetFile.readText())
        stagingDir.deleteRecursively()
        assertFalse(stagingDir.exists())
    }
}
