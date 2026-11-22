package com.example.swiftgrab.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.data.prefs.SwiftGrabSettings
import com.example.swiftgrab.service.MediaMetadata
import com.example.swiftgrab.ui.AppUpdateState
import com.example.swiftgrab.ui.YtDlpUpdateState
import com.example.swiftgrab.ui.theme.SwiftGrabTheme
import com.example.swiftgrab.update.ApkDownloadState
import com.example.swiftgrab.update.AppUpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class BrandAndSheetsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `SwiftGrabHorizontalLockup exposes app name text and heading semantics`() {
        composeTestRule.setContent {
            SwiftGrabTheme {
                SwiftGrabHorizontalLockup()
            }
        }

        composeTestRule.onNodeWithText("SwiftGrab")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun `SwiftGrabPresetOptionCard renders title, subtitle, radio semantics, and click callback`() {
        var clickedPreset: DownloadPreset? = null

        composeTestRule.setContent {
            SwiftGrabTheme {
                Column {
                    SwiftGrabPresetOptionCard(
                        preset = DownloadPreset.BEST_VIDEO,
                        isSelected = true,
                        onClick = { clickedPreset = DownloadPreset.BEST_VIDEO }
                    )
                    SwiftGrabPresetOptionCard(
                        preset = DownloadPreset.DATA_SAVER,
                        isSelected = false,
                        onClick = { clickedPreset = DownloadPreset.DATA_SAVER }
                    )
                }
            }
        }

        composeTestRule.onNodeWithText(DownloadPreset.BEST_VIDEO.displayTitle)
            .assertIsDisplayed()
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeTestRule.onNodeWithText(DownloadPreset.BEST_VIDEO.subtitle).assertIsDisplayed()

        composeTestRule.onNodeWithText(DownloadPreset.DATA_SAVER.displayTitle)
            .assertIsDisplayed()
            .assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performClick()
        composeTestRule.onNodeWithText(DownloadPreset.DATA_SAVER.subtitle).assertIsDisplayed()

        assertEquals(DownloadPreset.DATA_SAVER, clickedPreset)
    }

    @Test
    fun `QuickFormatSheetContent displays metadata, presets, and triggers download and dismiss`() {
        var selectedPreset: DownloadPreset? = null
        var downloadStarted = false
        var dismissed = false

        composeTestRule.setContent {
            SwiftGrabTheme {
                QuickFormatSheetContent(
                    url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                    selectedPreset = DownloadPreset.BEST_VIDEO,
                    metadataState = QuickFormatMetadataState.Loaded(
                        MediaMetadata(
                            title = "Meteor Catch Demo",
                            thumbnailUrl = null,
                            durationSeconds = 754L
                        )
                    ),
                    onSelectPreset = { selectedPreset = it },
                    onStartDownload = { downloadStarted = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Pilihan Format Unduhan").assertIsDisplayed()
        val domainNodes = composeTestRule.onAllNodesWithText("youtube.com")
        domainNodes.assertCountEquals(2)
        domainNodes[0].assertIsDisplayed()
        domainNodes[1].assertIsDisplayed()
        composeTestRule.onNodeWithText("Meteor Catch Demo").assertIsDisplayed()
        composeTestRule.onNodeWithText("12:34").assertIsDisplayed()

        DownloadPreset.entries.forEach { preset ->
            composeTestRule.onNodeWithText(preset.displayTitle).performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText(preset.subtitle).performScrollTo().assertIsDisplayed()
        }

        composeTestRule.onNodeWithText(DownloadPreset.AUDIO_ONLY.displayTitle)
            .performScrollTo()
            .performClick()
        assertEquals(DownloadPreset.AUDIO_ONLY, selectedPreset)

        composeTestRule.onNodeWithText("Mulai Unduh").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Batal").performScrollTo().performClick()

        assertTrue(downloadStarted)
        assertTrue(dismissed)
    }

    @Test
    fun `SettingsBottomSheetContent displays brand header, presets, switches, and action callbacks`() {
        var toggledInstantShare: Boolean? = null
        var selectedDefaultPreset: DownloadPreset? = null
        var toggledAutoClipboard: Boolean? = null
        var openXLoginClicked = false
        var checkYtDlpClicked = false
        var checkAppUpdateClicked = false
        var dismissed = false

        composeTestRule.setContent {
            SwiftGrabTheme {
                SettingsBottomSheetContent(
                    settings = SwiftGrabSettings(
                        instantShareMode = true,
                        defaultPreset = DownloadPreset.BEST_VIDEO,
                        autoStartClipboard = false
                    ),
                    ytDlpVersion = "2026.03.17",
                    updateState = YtDlpUpdateState.Idle,
                    isXLoggedIn = false,
                    appUpdateState = AppUpdateState.Idle,
                    currentAppVersion = "0.0.5",
                    onToggleInstantShare = { toggledInstantShare = it },
                    onSelectDefaultPreset = { selectedDefaultPreset = it },
                    onToggleAutoStartClipboard = { toggledAutoClipboard = it },
                    onCheckYtDlpUpdate = { checkYtDlpClicked = true },
                    onCheckAppUpdate = { checkAppUpdateClicked = true },
                    onShowUpdateDialog = {},
                    onOpenXLogin = { openXLoginClicked = true },
                    onLogoutX = {},
                    onPickStorageDir = {},
                    onResetStorageDir = {},
                    onDismiss = { dismissed = true },
                    defaultStorageDisplayPath = "/storage/emulated/0/Download/SwiftGrab"
                )
            }
        }

        composeTestRule.onNodeWithText("v0.0.5 • Zero-Paste Media Downloader").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pengaturan SwiftGrab")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))

        composeTestRule.onNodeWithContentDescription("Tutup").performClick()
        assertTrue(dismissed)

        val switches = composeTestRule.onAllNodes(isToggleable())
        switches[0].performScrollTo().performClick()
        assertEquals(false, toggledInstantShare)

        composeTestRule.onNodeWithText(DownloadPreset.AUDIO_ONLY.displayTitle)
            .performScrollTo()
            .performClick()
        assertEquals(DownloadPreset.AUDIO_ONLY, selectedDefaultPreset)

        switches[1].performScrollTo().performClick()
        assertEquals(true, toggledAutoClipboard)

        composeTestRule.onNodeWithText("Masuk ke Akun X").performScrollTo().performClick()
        assertTrue(openXLoginClicked)

        composeTestRule.onNodeWithText("Periksa Pembaruan yt-dlp").performScrollTo().performClick()
        assertTrue(checkYtDlpClicked)

        composeTestRule.onNodeWithText("Periksa Update Aplikasi").performScrollTo().performClick()
        assertTrue(checkAppUpdateClicked)
    }

    @Test
    fun `UpdateAvailableDialog displays version badge, changelog, and triggers actions`() {
        var downloadClicked = false
        var githubClicked = false
        var dismissed = false

        val updateInfo = AppUpdateInfo(
            versionName = "0.0.5",
            tagName = "v0.0.5",
            changelog = "Pembaruan desain Meteor Catch",
            htmlUrl = "https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.5",
            apkDownloadUrl = "https://github.com/madhasoeki/SwiftGrab/releases/download/v0.0.5/app.apk",
            apkFileName = "app.apk",
            apkSizeBytes = 58_400_000L
        )

        composeTestRule.setContent {
            SwiftGrabTheme {
                UpdateAvailableDialog(
                    updateInfo = updateInfo,
                    currentVersion = "0.0.4",
                    downloadState = ApkDownloadState.Idle,
                    onDownloadAndInstall = { downloadClicked = true },
                    onInstallNow = {},
                    onOpenInstallPermissionSettings = {},
                    onOpenGitHub = { githubClicked = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Update Tersedia: v0.0.5").assertIsDisplayed()
        composeTestRule.onNodeWithText("v0.0.4 → v0.0.5 • 55.7 MB").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pembaruan desain Meteor Catch").assertIsDisplayed()

        composeTestRule.onNodeWithText("Unduh & Install Sekarang").performClick()
        composeTestRule.onNodeWithText("Buka di GitHub").performClick()
        composeTestRule.onNodeWithText("Nanti").performClick()

        assertTrue(downloadClicked)
        assertTrue(githubClicked)
        assertTrue(dismissed)
    }

    @Test
    fun `UpdateAvailableDialog displays downloading progress metrics`() {
        val updateInfo = AppUpdateInfo(
            versionName = "0.0.5",
            tagName = "v0.0.5",
            changelog = "Catatan rilis",
            htmlUrl = "https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.5",
            apkDownloadUrl = "https://github.com/madhasoeki/SwiftGrab/releases/download/v0.0.5/app.apk",
            apkFileName = "app.apk",
            apkSizeBytes = 50L * 1024L * 1024L
        )

        composeTestRule.setContent {
            SwiftGrabTheme {
                UpdateAvailableDialog(
                    updateInfo = updateInfo,
                    currentVersion = "0.0.4",
                    downloadState = ApkDownloadState.Downloading(
                        downloadId = 42L,
                        releaseTag = "v0.0.5",
                        progress = 0.5f,
                        downloadedBytes = 25L * 1024L * 1024L,
                        totalBytes = 50L * 1024L * 1024L
                    ),
                    onDownloadAndInstall = {},
                    onInstallNow = {},
                    onOpenInstallPermissionSettings = {},
                    onOpenGitHub = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Mengunduh berkas APK pembaruan…")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("50% (25.0 MB / 50.0 MB)")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
