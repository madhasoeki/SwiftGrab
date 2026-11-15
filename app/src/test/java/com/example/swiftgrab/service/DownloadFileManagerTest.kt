package com.example.swiftgrab.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

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

    @Test
    fun `resolveDownloadDir uses custom path when valid and falls back to default`() {
        val defaultPublicDir = tempFolder.newFolder("PublicDownloads", "SwiftGrab")
        val customFolder = tempFolder.newFolder("CustomFolder")

        val resolvedCustom = DownloadFileManager.resolveDownloadDir(
            customPath = customFolder.absolutePath,
            defaultPublicDir = defaultPublicDir
        )
        assertEquals(customFolder.absolutePath, resolvedCustom.absolutePath)

        val resolvedDefault = DownloadFileManager.resolveDownloadDir(
            customPath = null,
            defaultPublicDir = defaultPublicDir
        )
        assertEquals(defaultPublicDir.absolutePath, resolvedDefault.absolutePath)

        val resolvedBlank = DownloadFileManager.resolveDownloadDir(
            customPath = "   ",
            defaultPublicDir = defaultPublicDir
        )
        assertEquals(defaultPublicDir.absolutePath, resolvedBlank.absolutePath)
    }

    @Test
    fun `buildUniqueTargetFile preserves non-existing file name and appends timestamp on conflict`() {
        val targetDir = tempFolder.newFolder("target_dir")
        val newFile = DownloadFileManager.buildUniqueTargetFile(targetDir, "video.mp4", 99999L)
        assertEquals("video.mp4", newFile.name)

        // Simulate conflict
        File(targetDir, "video.mp4").writeText("exists")
        val conflicted = DownloadFileManager.buildUniqueTargetFile(targetDir, "video.mp4", 99999L)
        assertEquals("video_99999.mp4", conflicted.name)

        // Simulate second conflict with same timestamp
        File(targetDir, "video_99999.mp4").writeText("exists-2")
        val doubleConflicted = DownloadFileManager.buildUniqueTargetFile(targetDir, "video.mp4", 99999L)
        assertEquals("video_99999_1.mp4", doubleConflicted.name)

        // Conflict without extension
        File(targetDir, "audiofile").writeText("audio")
        val conflictedNoExt = DownloadFileManager.buildUniqueTargetFile(targetDir, "audiofile", 88888L)
        assertEquals("audiofile_88888", conflictedNoExt.name)
    }

    @Test
    fun `findCompletedMediaFile ignores intermediate download files`() {
        val staging = tempFolder.newFolder("staging_test")
        File(staging, "track.mp3.part").writeText("part")
        File(staging, "track.mp3.aria2").writeText("aria2")
        File(staging, "track.mp3.ytdl").writeText("ytdl")
        File(staging, "track.mp3.temp").writeText("temp")

        val foundBefore = DownloadFileManager.findCompletedMediaFile(staging)
        assertEquals(null, foundBefore)

        val completeMedia = File(staging, "track.mp3").apply { writeText("audio-data") }
        val foundAfter = DownloadFileManager.findCompletedMediaFile(staging)
        assertNotNull(foundAfter)
        assertEquals(completeMedia.absolutePath, foundAfter?.absolutePath)
    }

    @Test
    fun `reserveUniqueTargetFile atomically reserves unique target paths without overwriting`() {
        val targetDir = tempFolder.newFolder("atomic_reserve")

        val first = DownloadFileManager.reserveUniqueTargetFile(targetDir, "clip.mp4") { 12345L }
        val second = DownloadFileManager.reserveUniqueTargetFile(targetDir, "clip.mp4") { 12345L }
        val third = DownloadFileManager.reserveUniqueTargetFile(targetDir, "clip.mp4") { 12345L }

        assertEquals("clip.mp4", first.name)
        assertEquals("clip_12345.mp4", second.name)
        assertEquals("clip_12345_1.mp4", third.name)
        assertTrue(first.exists())
        assertTrue(second.exists())
        assertTrue(third.exists())
    }

    @Test
    fun `reserveUniqueTargetFile is thread safe under concurrent exports`() {
        val targetDir = tempFolder.newFolder("concurrent_reserve")
        val threadCount = 8
        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            val tasks = (1..threadCount).map {
                Callable {
                    DownloadFileManager.reserveUniqueTargetFile(
                        targetDir = targetDir,
                        fileName = "same.mp4",
                        timestampProvider = { 555L }
                    )
                }
            }
            val reserved = executor.invokeAll(tasks).map { it.get().absolutePath }.toSet()
            assertEquals(threadCount, reserved.size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `safeStagingDir rejects path traversal`() {
        val baseDir = tempFolder.newFolder("staging_base")
        DownloadFileManager.safeStagingDir(baseDir, "../outside")
    }

    @Test
    fun `isPathWithinAllowedRoots enforces FileProvider root boundaries`() {
        val roots = listOf(
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Android/data/com.example.swiftgrab/files/Download",
            "/data/user/0/com.example.swiftgrab/files/downloads"
        )

        assertTrue(
            DownloadFileManager.isPathWithinAllowedRoots(
                "/storage/emulated/0/Download/SwiftGrab/video.mp4",
                roots
            )
        )
        assertFalse(
            DownloadFileManager.isPathWithinAllowedRoots(
                "/storage/emulated/0/DownloadBackup/video.mp4",
                roots
            )
        )
        assertFalse(
            DownloadFileManager.isPathWithinAllowedRoots(
                "/storage/9876-5432/Movies/video.mp4",
                roots
            )
        )
        assertFalse(
            DownloadFileManager.isPathWithinAllowedRoots(
                "/storage/emulated/0/Movies/video.mp4",
                roots
            )
        )
    }
}
