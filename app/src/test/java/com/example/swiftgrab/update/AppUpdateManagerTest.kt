package com.example.swiftgrab.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetSocketAddress

class AppUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `compareSemVer and isNewerVersion handle standard and prefixed versions`() {
        assertTrue(AppUpdateManager.isNewerVersion("v0.0.3", "0.0.2"))
        assertTrue(AppUpdateManager.isNewerVersion("v0.1.0", "0.0.9"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.0", "0.9.9"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.10.0", "1.2.0"))
        assertTrue(AppUpdateManager.isNewerVersion("V2.0", "1.9.9"))

        assertFalse(AppUpdateManager.isNewerVersion("v0.0.2", "0.0.2"))
        assertFalse(AppUpdateManager.isNewerVersion("v0.0.1", "0.0.2"))
        assertFalse(AppUpdateManager.isNewerVersion("0.0.2", "0.0.2"))
        assertFalse(AppUpdateManager.isNewerVersion("v1.0.0-beta.1", "1.0.0"))
        assertTrue(AppUpdateManager.isNewerVersion("v1.0.0", "1.0.0-beta.1"))
    }

    @Test
    fun `parseLatestRelease extracts tag, changelog, htmlUrl, and best ABI apk`() {
        val json = """
            {
              "tag_name": "v0.0.3",
              "html_url": "https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.3",
              "body": "### What's Changed\n- Auto-updater\n- Public Download folder",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 512,
                  "browser_download_url": "https://github.com/madhasoeki/SwiftGrab/releases/download/v0.0.3/checksums.txt"
                },
                {
                  "name": "SwiftGrab-v0.0.3-armeabi-v7a.apk",
                  "size": 45000000,
                  "browser_download_url": "https://github.com/madhasoeki/SwiftGrab/releases/download/v0.0.3/SwiftGrab-v0.0.3-armeabi-v7a.apk"
                },
                {
                  "name": "SwiftGrab-v0.0.3-arm64-v8a.apk",
                  "size": 58000000,
                  "browser_download_url": "https://github.com/madhasoeki/SwiftGrab/releases/download/v0.0.3/SwiftGrab-v0.0.3-arm64-v8a.apk"
                },
                {
                  "name": "SwiftGrab-v0.0.3-universal.apk",
                  "size": 95000000,
                  "browser_download_url": "https://github.com/madhasoeki/SwiftGrab/releases/download/v0.0.3/SwiftGrab-v0.0.3-universal.apk"
                }
              ]
            }
        """.trimIndent()

        val infoArm64 = AppUpdateManager.parseLatestRelease(json, arrayOf("arm64-v8a", "armeabi-v7a"))
        assertNotNull(infoArm64)
        assertEquals("0.0.3", infoArm64?.versionName)
        assertEquals("v0.0.3", infoArm64?.tagName)
        assertEquals("https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.3", infoArm64?.htmlUrl)
        assertTrue(infoArm64?.changelog?.contains("Auto-updater") == true)
        assertEquals("SwiftGrab-v0.0.3-arm64-v8a.apk", infoArm64?.apkFileName)
        assertEquals(
            "https://github.com/madhasoeki/SwiftGrab/releases/download/v0.0.3/SwiftGrab-v0.0.3-arm64-v8a.apk",
            infoArm64?.apkDownloadUrl
        )

        val infoFallbackUniversal = AppUpdateManager.parseLatestRelease(json, arrayOf("riscv64"))
        assertEquals("SwiftGrab-v0.0.3-universal.apk", infoFallbackUniversal?.apkFileName)
    }

    @Test
    fun `parseLatestRelease ignores drafts, prereleases, and non-https asset URLs`() {
        val draftJson = """{"tag_name":"v9.9.9","draft":true,"prerelease":false,"assets":[]}"""
        assertNull(AppUpdateManager.parseLatestRelease(draftJson))

        val unsafeUrlJson = """
            {
              "tag_name": "v0.0.5",
              "html_url": "http://insecure.example.com/release",
              "body": "",
              "assets": [
                {
                  "name": "SwiftGrab-v0.0.5.apk",
                  "browser_download_url": "http://insecure.example.com/app.apk"
                }
              ]
            }
        """.trimIndent()
        val parsed = AppUpdateManager.parseLatestRelease(unsafeUrlJson)
        assertNotNull(parsed)
        assertEquals(AppUpdateManager.GITHUB_RELEASES_PAGE_URL, parsed?.htmlUrl)
        assertNull(parsed?.apkDownloadUrl)
    }

    @Test
    fun `sanitizeApkFileName removes path traversal and invalid characters`() {
        assertEquals(
            "SwiftGrab-v0.0.3.apk",
            AppUpdateManager.sanitizeApkFileName("../../evil/SwiftGrab-v0.0.3.apk", "v0.0.3")
        )
        assertEquals(
            "SwiftGrab-0.0.4.apk",
            AppUpdateManager.sanitizeApkFileName(null, "v0.0.4")
        )
    }

    @Test
    fun `checkForUpdate fetches local HTTP server and detects update or up-to-date`() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val payload = """
            {
              "tag_name": "v0.0.3",
              "html_url": "https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.3",
              "body": "Rilis v0.0.3",
              "assets": [
                {
                  "name": "SwiftGrab-v0.0.3-universal.apk",
                  "size": 123456,
                  "browser_download_url": "https://github.com/madhasoeki/SwiftGrab/releases/download/v0.0.3/SwiftGrab-v0.0.3-universal.apk"
                }
              ]
            }
        """.trimIndent().toByteArray(Charsets.UTF_8)

        server.createContext("/latest") { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.createContext("/notfound") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"

            val updateResult = AppUpdateManager.checkForUpdate(
                currentVersionName = "0.0.2",
                supportedAbis = arrayOf("arm64-v8a"),
                endpointUrl = "$baseUrl/latest"
            )
            assertTrue(updateResult is AppUpdateCheckResult.UpdateAvailable)
            assertEquals("0.0.3", (updateResult as AppUpdateCheckResult.UpdateAvailable).info.versionName)

            val upToDateResult = AppUpdateManager.checkForUpdate(
                currentVersionName = "0.0.3",
                supportedAbis = arrayOf("arm64-v8a"),
                endpointUrl = "$baseUrl/latest"
            )
            assertTrue(upToDateResult is AppUpdateCheckResult.UpToDate)

            val notFoundResult = AppUpdateManager.checkForUpdate(
                currentVersionName = "0.0.2",
                supportedAbis = arrayOf("arm64-v8a"),
                endpointUrl = "$baseUrl/notfound"
            )
            assertTrue(notFoundResult is AppUpdateCheckResult.Error)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `selectBestApkAsset matches gradle release split names and refuses incompatible ABIs`() {
        val gradleSplits = listOf(
            ReleaseAsset("app-armeabi-v7a-release.apk", "https://example.com/v7a.apk"),
            ReleaseAsset("app-arm64-v8a-release.apk", "https://example.com/arm64.apk"),
            ReleaseAsset("app-x86_64-release.apk", "https://example.com/x86_64.apk")
        )

        val pickedArm64 = AppUpdateManager.selectBestApkAsset(gradleSplits, arrayOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("app-arm64-v8a-release.apk", pickedArm64?.name)

        // Ensure x86 does NOT falsely match x86_64
        val x86OnlyDevice = AppUpdateManager.selectBestApkAsset(
            listOf(ReleaseAsset("app-x86_64-release.apk", "https://example.com/x86_64.apk")),
            arrayOf("x86")
        )
        assertNull(x86OnlyDevice)

        // Incompatible ABI-only assets must return null when no universal/generic APK exists
        val incompatibleResult = AppUpdateManager.selectBestApkAsset(
            listOf(ReleaseAsset("app-x86_64-release.apk", "https://example.com/x86_64.apk")),
            arrayOf("arm64-v8a", "armeabi-v7a")
        )
        assertNull(incompatibleResult)
    }

    @Test
    fun `resolveUniqueApkFileName chooses unique destination without deleting existing file`() {
        val downloadsDir = tempFolder.newFolder("downloads")
        val firstName = AppUpdateManager.resolveUniqueApkFileName(downloadsDir, "SwiftGrab-v0.0.3.apk")
        assertEquals("SwiftGrab-v0.0.3.apk", firstName)

        val existing = File(downloadsDir, "SwiftGrab-v0.0.3.apk").apply { writeText("old-apk") }
        val secondName = AppUpdateManager.resolveUniqueApkFileName(downloadsDir, "SwiftGrab-v0.0.3.apk")
        assertEquals("SwiftGrab-v0.0.3_1.apk", secondName)
        assertTrue(existing.exists())
        assertEquals("old-apk", existing.readText())

        File(downloadsDir, "SwiftGrab-v0.0.3_1.apk").writeText("second-apk")
        val thirdName = AppUpdateManager.resolveUniqueApkFileName(downloadsDir, "SwiftGrab-v0.0.3.apk")
        assertEquals("SwiftGrab-v0.0.3_2.apk", thirdName)
    }

    @Test
    fun `ApkDownloadState matchesRelease binds install state to release tag`() {
        val releaseV3 = AppUpdateInfo(
            versionName = "0.0.3",
            tagName = "v0.0.3",
            changelog = "v3",
            htmlUrl = "https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.3",
            apkDownloadUrl = "https://example.com/v3.apk",
            apkFileName = "v3.apk"
        )
        val releaseV4 = releaseV3.copy(versionName = "0.0.4", tagName = "v0.0.4")

        val downloadingV3 = ApkDownloadState.Downloading(
            downloadId = 10L,
            releaseTag = "v0.0.3",
            progress = 0.5f,
            downloadedBytes = 100L,
            totalBytes = 200L
        )
        assertTrue(downloadingV3.matchesRelease(releaseV3))
        assertFalse(downloadingV3.matchesRelease(releaseV4))

        val failedV3 = ApkDownloadState.Failed(releaseTag = "v0.0.3", message = "err")
        assertTrue(failedV3.matchesRelease(releaseV3))
        assertFalse(failedV3.matchesRelease(releaseV4))
    }
}
