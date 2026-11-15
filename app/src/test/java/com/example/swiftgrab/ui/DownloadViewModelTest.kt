package com.example.swiftgrab.ui

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.example.swiftgrab.data.db.DownloadDao
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import com.example.swiftgrab.data.prefs.DownloadPreferences
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.data.prefs.SwiftGrabSettings
import com.example.swiftgrab.data.repository.DownloadRepository
import com.example.swiftgrab.update.ApkDownloadState
import com.example.swiftgrab.update.AppUpdateCheckResult
import com.example.swiftgrab.update.AppUpdateInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DownloadViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    class MainDispatcherRule(
        val testDispatcher: TestDispatcher = StandardTestDispatcher()
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }

    private class FakeDownloadDao : DownloadDao {
        private val tasks = MutableStateFlow<Map<String, DownloadTaskEntity>>(emptyMap())

        override fun observeAllTasks(): Flow<List<DownloadTaskEntity>> =
            tasks.map { map -> map.values.sortedByDescending { it.createdAt } }

        override fun observeTasksByStatuses(statuses: List<DownloadStatus>): Flow<List<DownloadTaskEntity>> =
            tasks.map { map ->
                map.values.filter { it.status in statuses }.sortedByDescending { it.createdAt }
            }

        override fun observeTaskById(id: String): Flow<DownloadTaskEntity?> =
            tasks.map { it[id] }

        override suspend fun getTaskById(id: String): DownloadTaskEntity? = tasks.value[id]

        override suspend fun getTasksByStatus(status: DownloadStatus): List<DownloadTaskEntity> =
            tasks.value.values.filter { it.status == status }.sortedBy { it.createdAt }

        override suspend fun upsertTask(task: DownloadTaskEntity) {
            tasks.value = tasks.value + (task.id to task)
        }

        override suspend fun updateTask(task: DownloadTaskEntity) {
            tasks.value = tasks.value + (task.id to task)
        }

        override suspend fun updateProgress(
            id: String,
            progress: Float,
            downloadedBytes: Long,
            totalBytes: Long,
            speed: String,
            eta: String,
            activeStatus: DownloadStatus,
            updatedAt: Long
        ) {
            val current = tasks.value[id] ?: return
            if (current.status != activeStatus) return
            tasks.value = tasks.value + (
                id to current.copy(
                    progress = progress,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speed = speed,
                    eta = eta,
                    updatedAt = updatedAt
                )
            )
        }

        override suspend fun updateMetadata(
            id: String,
            title: String,
            thumbnailUrl: String?,
            formatLabel: String,
            updatedAt: Long
        ) {
            val current = tasks.value[id] ?: return
            tasks.value = tasks.value + (
                id to current.copy(
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    formatLabel = formatLabel,
                    updatedAt = updatedAt
                )
            )
        }

        override suspend fun updateStatus(
            id: String,
            status: DownloadStatus,
            errorMessage: String?,
            updatedAt: Long
        ) {
            val current = tasks.value[id] ?: return
            tasks.value = tasks.value + (
                id to current.copy(
                    status = status,
                    errorMessage = errorMessage,
                    updatedAt = updatedAt
                )
            )
        }

        override suspend fun reconcileDownloadingTasksToPaused(
            downloadingStatus: DownloadStatus,
            pausedStatus: DownloadStatus,
            updatedAt: Long
        ): Int = 0

        override suspend fun reconcileDownloadingTaskToPaused(
            id: String,
            downloadingStatus: DownloadStatus,
            pausedStatus: DownloadStatus,
            updatedAt: Long
        ): Int = 0

        override suspend fun markCompleted(
            id: String,
            outputPath: String,
            totalBytes: Long,
            completedStatus: DownloadStatus,
            updatedAt: Long
        ) {
            val current = tasks.value[id] ?: return
            tasks.value = tasks.value + (
                id to current.copy(
                    status = completedStatus,
                    progress = 100f,
                    outputPath = outputPath,
                    totalBytes = if (totalBytes > 0) totalBytes else current.totalBytes,
                    updatedAt = updatedAt
                )
            )
        }

        override suspend fun deleteTask(task: DownloadTaskEntity) {
            tasks.value = tasks.value - task.id
        }

        override suspend fun deleteTaskById(id: String) {
            tasks.value = tasks.value - id
        }

        override suspend fun deleteCompletedTasks(completedStatus: DownloadStatus) {
            tasks.value = tasks.value.filterValues { it.status != completedStatus }
        }
    }

    private class FakeDownloadPreferences : DownloadPreferences {
        private val _settings = MutableStateFlow(SwiftGrabSettings())
        override val settings: StateFlow<SwiftGrabSettings> = _settings.asStateFlow()

        private var lastHandledClipboardUrl: String? = null
        private var activeApkDownloadState: Pair<Long, String>? = null
        var clearCustomDirSuccess = true
        var clearActiveApkDownloadCalls = 0

        override fun isInstantShareMode(): Boolean = _settings.value.instantShareMode

        override fun setInstantShareMode(enabled: Boolean) {
            _settings.update { it.copy(instantShareMode = enabled) }
        }

        override fun getDefaultPreset(): DownloadPreset = _settings.value.defaultPreset

        override fun setDefaultPreset(preset: DownloadPreset) {
            _settings.update { it.copy(defaultPreset = preset) }
        }

        override fun isAutoStartClipboard(): Boolean = _settings.value.autoStartClipboard

        override fun setAutoStartClipboard(enabled: Boolean) {
            _settings.update { it.copy(autoStartClipboard = enabled) }
        }

        override fun getLastHandledClipboardUrl(): String? = lastHandledClipboardUrl

        override fun setLastHandledClipboardUrl(url: String?) {
            lastHandledClipboardUrl = url?.takeIf { it.isNotBlank() }
        }

        override fun getCustomDownloadPath(): String? = _settings.value.customDownloadPath

        override fun getCustomDownloadUri(): String? = _settings.value.customDownloadUri

        override fun clearCustomDownloadDir(): Boolean {
            if (!clearCustomDirSuccess) return false
            _settings.update { it.copy(customDownloadPath = null, customDownloadUri = null) }
            return true
        }

        override fun saveActiveApkDownload(downloadId: Long, releaseTag: String): Boolean {
            if (downloadId <= 0L || releaseTag.isBlank()) return false
            activeApkDownloadState = downloadId to releaseTag
            return true
        }

        override fun getActiveApkDownload(): Pair<Long, String>? = activeApkDownloadState

        override fun clearActiveApkDownload() {
            clearActiveApkDownloadCalls++
            activeApkDownloadState = null
        }
    }

    private class FakeDownloadEngineGateway : DownloadEngineGateway {
        val engineReadyFlow = MutableStateFlow(false)
        override val isEngineReady: StateFlow<Boolean> = engineReadyFlow.asStateFlow()

        var versionName = "2026.03.01"
        var updateResult: Result<String> = Result.success("2026.04.01")
        var updateCallCount = 0
        var updateGate: CompletableDeferred<Unit>? = null

        override fun getYtDlpVersion(): String = versionName

        override suspend fun updateYtDlp(): Result<String> {
            updateCallCount++
            updateGate?.await()
            return updateResult
        }
    }

    private data class StartedDownloadCall(
        val url: String,
        val preset: DownloadPreset,
        val taskId: String?
    )

    private class FakeDownloadServiceGateway : DownloadServiceGateway {
        val startedCalls = mutableListOf<StartedDownloadCall>()
        val pausedIds = mutableListOf<String>()
        val resumedIds = mutableListOf<String>()
        val canceledIds = mutableListOf<String>()
        val cleanedTempIds = mutableListOf<String>()

        override fun startDownload(url: String, preset: DownloadPreset, taskId: String?) {
            startedCalls += StartedDownloadCall(url, preset, taskId)
        }

        override fun pause(taskId: String) {
            pausedIds += taskId
        }

        override fun resume(taskId: String) {
            resumedIds += taskId
        }

        override fun cancel(taskId: String) {
            canceledIds += taskId
        }

        override fun cleanupTemporaryFiles(taskId: String) {
            cleanedTempIds += taskId
        }
    }

    private class FakeStorageGateway : StorageGateway {
        var needsLegacyPermission = false
        var saveTreeUriResult: String? = "/storage/custom/SwiftGrab"
        val savedUris = mutableListOf<Uri>()

        override fun needsLegacyWritePermission(customDownloadUri: String?): Boolean = needsLegacyPermission

        override fun saveCustomTreeUri(uri: Uri): String? {
            savedUris += uri
            return saveTreeUriResult
        }
    }

    private class FakeXCookieGateway : XCookieGateway {
        var hasValidCookies = false
        var clearCallCount = 0

        override fun hasValidXCookies(): Boolean = hasValidCookies

        override fun clearXCookies() {
            clearCallCount++
            hasValidCookies = false
        }
    }

    private class FakeAppUpdateGateway : AppUpdateGateway {
        var checkResult: AppUpdateCheckResult = AppUpdateCheckResult.UpToDate("0.0.3")
        var checkCallCount = 0
        var checkGate: CompletableDeferred<Unit>? = null
        val shownNotifications = mutableListOf<AppUpdateInfo>()
        var persistedApkState: ApkDownloadState? = null
        var enqueueResult: Result<Long> = Result.success(1001L)
        val enqueuedUpdates = mutableListOf<AppUpdateInfo>()
        private val downloadFlows = mutableMapOf<Long, MutableSharedFlow<ApkDownloadState>>()
        var canInstallPackagesResult = true
        val launchedInstallerUris = mutableListOf<Uri>()
        var unknownSourcesSettingsCalls = 0
        val openedReleaseUrls = mutableListOf<String>()

        fun flowFor(downloadId: Long): MutableSharedFlow<ApkDownloadState> {
            return downloadFlows.getOrPut(downloadId) { MutableSharedFlow(replay = 1) }
        }

        override suspend fun checkForUpdate(): AppUpdateCheckResult {
            checkCallCount++
            checkGate?.await()
            return checkResult
        }

        override fun showUpdateNotification(info: AppUpdateInfo) {
            shownNotifications += info
        }

        override fun restorePersistedApkDownloadState(): ApkDownloadState? = persistedApkState

        override fun enqueueApkDownload(updateInfo: AppUpdateInfo): Result<Long> {
            enqueuedUpdates += updateInfo
            return enqueueResult
        }

        override fun observeApkDownload(downloadId: Long, releaseTag: String): Flow<ApkDownloadState> {
            return flowFor(downloadId)
        }

        override fun canInstallPackages(): Boolean = canInstallPackagesResult

        override fun launchApkInstaller(apkUri: Uri): Boolean {
            launchedInstallerUris += apkUri
            return true
        }

        override fun launchUnknownSourcesSettings(): Boolean {
            unknownSourcesSettingsCalls++
            return true
        }

        override fun openReleasePage(url: String) {
            openedReleaseUrls += url
        }
    }

    private class Fixture(
        val dao: FakeDownloadDao = FakeDownloadDao(),
        val repository: DownloadRepository = DownloadRepository(dao),
        val preferences: FakeDownloadPreferences = FakeDownloadPreferences(),
        val engineGateway: FakeDownloadEngineGateway = FakeDownloadEngineGateway(),
        val serviceGateway: FakeDownloadServiceGateway = FakeDownloadServiceGateway(),
        val storageGateway: FakeStorageGateway = FakeStorageGateway(),
        val cookieGateway: FakeXCookieGateway = FakeXCookieGateway(),
        val updateGateway: FakeAppUpdateGateway = FakeAppUpdateGateway(),
        dispatcher: TestDispatcher
    ) {
        val viewModel by lazy {
            DownloadViewModel(
                application = ApplicationProvider.getApplicationContext(),
                repository = repository,
                preferences = preferences,
                engineGateway = engineGateway,
                taskDispatcher = serviceGateway,
                storageGateway = storageGateway,
                cookieGateway = cookieGateway,
                updateGateway = updateGateway,
                ioDispatcher = dispatcher
            )
        }
    }

    private fun createFixture(): Fixture = Fixture(dispatcher = mainDispatcherRule.testDispatcher)

    private fun TestScope.collectUiState(viewModel: DownloadViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    private fun sampleTask(
        id: String,
        status: DownloadStatus,
        createdAt: Long = 1_000L,
        formatLabel: String = "MP4 Terbaik",
        audioOnly: Boolean = false
    ) = DownloadTaskEntity(
        id = id,
        url = "https://example.com/video/$id",
        title = "Video $id",
        formatLabel = formatLabel,
        audioOnly = audioOnly,
        status = status,
        createdAt = createdAt
    )

    private fun sampleUpdateInfo(tag: String = "v0.0.4", version: String = "0.0.4") = AppUpdateInfo(
        versionName = version,
        tagName = tag,
        changelog = "Perbaikan stabilitas",
        htmlUrl = "https://github.com/madhasoeki/SwiftGrab/releases/tag/$tag",
        apkDownloadUrl = "https://github.com/madhasoeki/SwiftGrab/releases/download/$tag/SwiftGrab-$tag.apk",
        apkFileName = "SwiftGrab-$tag.apk",
        apkSizeBytes = 12_345_678L
    )

    @Test
    fun `setFilter filters tasks by ALL, ACTIVE, COMPLETED, and FAILED and updates dynamically`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)

            fixture.dao.upsertTask(sampleTask("downloading", DownloadStatus.DOWNLOADING, createdAt = 600))
            fixture.dao.upsertTask(sampleTask("paused", DownloadStatus.PAUSED, createdAt = 500))
            fixture.dao.upsertTask(sampleTask("queued", DownloadStatus.QUEUED, createdAt = 400))
            fixture.dao.upsertTask(sampleTask("completed", DownloadStatus.COMPLETED, createdAt = 300))
            fixture.dao.upsertTask(sampleTask("failed", DownloadStatus.FAILED, createdAt = 200))
            fixture.dao.upsertTask(sampleTask("canceled", DownloadStatus.CANCELED, createdAt = 100))
            advanceUntilIdle()

            assertEquals(TaskFilter.ALL, fixture.viewModel.uiState.value.activeFilter)
            assertEquals(6, fixture.viewModel.uiState.value.tasks.size)
            assertEquals(6, fixture.viewModel.uiState.value.filteredTasks.size)

            fixture.viewModel.setFilter(TaskFilter.ACTIVE)
            advanceUntilIdle()
            assertEquals(
                listOf("downloading", "paused", "queued"),
                fixture.viewModel.uiState.value.filteredTasks.map { it.id }
            )

            fixture.viewModel.setFilter(TaskFilter.COMPLETED)
            advanceUntilIdle()
            assertEquals(
                listOf("completed"),
                fixture.viewModel.uiState.value.filteredTasks.map { it.id }
            )

            fixture.viewModel.setFilter(TaskFilter.FAILED)
            advanceUntilIdle()
            assertEquals(
                listOf("failed", "canceled"),
                fixture.viewModel.uiState.value.filteredTasks.map { it.id }
            )

            // Dynamic repository update reflects immediately in filteredTasks
            fixture.dao.upsertTask(sampleTask("failed-2", DownloadStatus.FAILED, createdAt = 700))
            advanceUntilIdle()
            assertEquals(7, fixture.viewModel.uiState.value.tasks.size)
            assertEquals(
                listOf("failed-2", "failed", "canceled"),
                fixture.viewModel.uiState.value.filteredTasks.map { it.id }
            )
        }

    @Test
    fun `onClipboardUrlDetected debounces duplicate URL and shows banner when autoStart disabled`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)
            advanceUntilIdle()

            val url1 = "https://www.youtube.com/watch?v=abc123"
            fixture.viewModel.onClipboardUrlDetected(url1)
            advanceUntilIdle()

            assertEquals(url1, fixture.viewModel.uiState.value.clipboardSuggestionUrl)
            assertTrue(fixture.serviceGateway.startedCalls.isEmpty())

            // Dismiss marks URL as handled so re-focusing with the same clipboard does not re-show banner
            fixture.viewModel.dismissClipboardBanner()
            advanceUntilIdle()
            assertNull(fixture.viewModel.uiState.value.clipboardSuggestionUrl)
            assertEquals(url1, fixture.preferences.getLastHandledClipboardUrl())

            fixture.viewModel.onClipboardUrlDetected(url1)
            advanceUntilIdle()
            assertNull(fixture.viewModel.uiState.value.clipboardSuggestionUrl)

            // New URL bypasses debounce and shows banner
            val url2 = "https://x.com/user/status/999"
            fixture.viewModel.onClipboardUrlDetected(url2)
            advanceUntilIdle()
            assertEquals(url2, fixture.viewModel.uiState.value.clipboardSuggestionUrl)
        }

    @Test
    fun `onClipboardUrlDetected auto-starts download with default preset when autoStartClipboard enabled`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)
            fixture.viewModel.setAutoStartClipboard(true)
            fixture.viewModel.setDefaultPreset(DownloadPreset.AUDIO_ONLY)
            advanceUntilIdle()

            val url = "https://youtu.be/xyz789"
            fixture.viewModel.onClipboardUrlDetected(url)
            advanceUntilIdle()

            assertNull(fixture.viewModel.uiState.value.clipboardSuggestionUrl)
            assertEquals(url, fixture.preferences.getLastHandledClipboardUrl())
            assertEquals(
                listOf(StartedDownloadCall(url, DownloadPreset.AUDIO_ONLY, null)),
                fixture.serviceGateway.startedCalls
            )

            // Re-detecting same URL does not start duplicate download
            fixture.viewModel.onClipboardUrlDetected(url)
            advanceUntilIdle()
            assertEquals(1, fixture.serviceGateway.startedCalls.size)
        }

    @Test
    fun `startDownload and startBatchDownloads clear matching clipboard banner and debounce URL`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)
            advanceUntilIdle()

            val bannerUrl = "https://www.youtube.com/watch?v=banner1"
            fixture.viewModel.onClipboardUrlDetected(bannerUrl)
            advanceUntilIdle()
            assertEquals(bannerUrl, fixture.viewModel.uiState.value.clipboardSuggestionUrl)

            fixture.viewModel.startDownload(bannerUrl, DownloadPreset.DATA_SAVER)
            advanceUntilIdle()
            assertNull(fixture.viewModel.uiState.value.clipboardSuggestionUrl)
            assertEquals(bannerUrl, fixture.preferences.getLastHandledClipboardUrl())
            assertEquals(
                listOf(StartedDownloadCall(bannerUrl, DownloadPreset.DATA_SAVER, null)),
                fixture.serviceGateway.startedCalls
            )

            // Batch download clears banner when validUrls contains it, ignores invalid, and deduplicates
            val bannerUrl2 = "https://x.com/swiftgrab/status/42"
            fixture.viewModel.onClipboardUrlDetected(bannerUrl2)
            advanceUntilIdle()

            fixture.viewModel.startBatchDownloads(
                urls = listOf(
                    "not a url",
                    "Check this $bannerUrl2",
                    bannerUrl2,
                    "https://vimeo.com/12345"
                ),
                preset = DownloadPreset.BEST_VIDEO
            )
            advanceUntilIdle()

            assertNull(fixture.viewModel.uiState.value.clipboardSuggestionUrl)
            assertEquals(bannerUrl2, fixture.preferences.getLastHandledClipboardUrl())
            assertEquals(3, fixture.serviceGateway.startedCalls.size)
            assertEquals(bannerUrl2, fixture.serviceGateway.startedCalls[1].url)
            assertEquals("https://vimeo.com/12345", fixture.serviceGateway.startedCalls[2].url)
        }

    @Test
    fun `checkForAppUpdate auto-check runs once per session and handles UpdateAvailable`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)
            val updateInfo = sampleUpdateInfo(tag = "v0.0.4", version = "0.0.4")
            fixture.updateGateway.checkResult = AppUpdateCheckResult.UpdateAvailable(updateInfo)
            val gate = CompletableDeferred<Unit>()
            fixture.updateGateway.checkGate = gate
            advanceUntilIdle()

            fixture.viewModel.checkForAppUpdate(manual = false)
            runCurrent()
            assertEquals(AppUpdateState.Checking, fixture.viewModel.uiState.value.appUpdateState)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, fixture.updateGateway.checkCallCount)
            assertEquals(AppUpdateState.UpdateAvailable(updateInfo), fixture.viewModel.uiState.value.appUpdateState)
            assertTrue(fixture.viewModel.uiState.value.isUpdateDialogVisible)
            assertEquals(listOf(updateInfo), fixture.updateGateway.shownNotifications)

            // Second automatic check in same session is skipped
            fixture.viewModel.checkForAppUpdate(manual = false)
            advanceUntilIdle()
            assertEquals(1, fixture.updateGateway.checkCallCount)
        }

    @Test
    fun `checkForAppUpdate auto-check stays Idle on UpToDate and Error while manual check exposes state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)
            fixture.updateGateway.checkResult = AppUpdateCheckResult.UpToDate("0.0.3")
            advanceUntilIdle()

            fixture.viewModel.checkForAppUpdate(manual = false)
            advanceUntilIdle()
            assertEquals(AppUpdateState.Idle, fixture.viewModel.uiState.value.appUpdateState)
            assertFalse(fixture.viewModel.uiState.value.isUpdateDialogVisible)
            assertTrue(fixture.updateGateway.shownNotifications.isEmpty())

            // Manual check exposes UpToDate
            fixture.viewModel.checkForAppUpdate(manual = true)
            advanceUntilIdle()
            assertEquals(AppUpdateState.UpToDate("0.0.3"), fixture.viewModel.uiState.value.appUpdateState)
            assertEquals(2, fixture.updateGateway.checkCallCount)

            fixture.viewModel.resetUpdateStatus()
            advanceUntilIdle()
            assertEquals(AppUpdateState.Idle, fixture.viewModel.uiState.value.appUpdateState)

            // Manual check exposes Error
            fixture.updateGateway.checkResult = AppUpdateCheckResult.Error("HTTP 503")
            fixture.viewModel.checkForAppUpdate(manual = true)
            advanceUntilIdle()
            assertEquals(AppUpdateState.Error("HTTP 503"), fixture.viewModel.uiState.value.appUpdateState)

            fixture.viewModel.resetUpdateStatus()
            advanceUntilIdle()
            assertEquals(AppUpdateState.Idle, fixture.viewModel.uiState.value.appUpdateState)
        }

    @Test
    fun `showUpdateDialogOrCheck opens existing dialog or triggers manual check and dismissUpdateDialog hides it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)
            val updateInfo = sampleUpdateInfo()
            fixture.updateGateway.checkResult = AppUpdateCheckResult.UpdateAvailable(updateInfo)
            advanceUntilIdle()

            // Before any check, triggers manual check
            fixture.viewModel.showUpdateDialogOrCheck()
            advanceUntilIdle()
            assertEquals(1, fixture.updateGateway.checkCallCount)
            assertTrue(fixture.viewModel.uiState.value.isUpdateDialogVisible)
            assertTrue(fixture.updateGateway.shownNotifications.isEmpty())

            fixture.viewModel.dismissUpdateDialog()
            advanceUntilIdle()
            assertFalse(fixture.viewModel.uiState.value.isUpdateDialogVisible)

            // Now UpdateAvailable is cached, so showUpdateDialogOrCheck reopens without network check
            fixture.viewModel.showUpdateDialogOrCheck()
            advanceUntilIdle()
            assertEquals(1, fixture.updateGateway.checkCallCount)
            assertTrue(fixture.viewModel.uiState.value.isUpdateDialogVisible)
        }

    @Test
    fun `checkForAppUpdate clears stale non-downloading apkDownloadState when newer release arrives`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            val oldUri = "content://downloads/apk/1".toUri()
            fixture.updateGateway.persistedApkState = ApkDownloadState.ReadyToInstall(
                downloadId = 55L,
                releaseTag = "v0.0.3",
                apkUri = oldUri,
                localFilePath = "/tmp/old.apk"
            )
            collectUiState(fixture.viewModel)
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.apkDownloadState is ApkDownloadState.ReadyToInstall)

            val newRelease = sampleUpdateInfo(tag = "v0.0.4", version = "0.0.4")
            fixture.updateGateway.checkResult = AppUpdateCheckResult.UpdateAvailable(newRelease)
            fixture.viewModel.checkForAppUpdate(manual = true)
            advanceUntilIdle()

            assertEquals(ApkDownloadState.Idle, fixture.viewModel.uiState.value.apkDownloadState)
            assertEquals(1, fixture.preferences.clearActiveApkDownloadCalls)
        }

    @Test
    fun `startAppUpdateDownload prevents duplicate enqueue, observes progress, and auto-launches installer`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)
            val updateInfo = sampleUpdateInfo(tag = "v0.0.4", version = "0.0.4")
            fixture.updateGateway.enqueueResult = Result.success(777L)
            advanceUntilIdle()

            fixture.viewModel.startAppUpdateDownload(updateInfo)
            runCurrent()

            val initialState = fixture.viewModel.uiState.value.apkDownloadState
            assertTrue(initialState is ApkDownloadState.Downloading)
            assertEquals(1, fixture.updateGateway.enqueuedUpdates.size)

            // Second tap for the same release tag while Downloading is ignored
            fixture.viewModel.startAppUpdateDownload(updateInfo)
            runCurrent()
            assertEquals(1, fixture.updateGateway.enqueuedUpdates.size)

            val flow = fixture.updateGateway.flowFor(777L)
            flow.emit(
                ApkDownloadState.Downloading(
                    downloadId = 777L,
                    releaseTag = "v0.0.4",
                    progress = 0.5f,
                    downloadedBytes = 500L,
                    totalBytes = 1000L
                )
            )
            runCurrent()
            val midState = fixture.viewModel.uiState.value.apkDownloadState as ApkDownloadState.Downloading
            assertEquals(0.5f, midState.progress)
            assertTrue(fixture.updateGateway.launchedInstallerUris.isEmpty())

            val apkUri = "content://com.example.swiftgrab.fileprovider/apks/v4.apk".toUri()
            flow.emit(
                ApkDownloadState.ReadyToInstall(
                    downloadId = 777L,
                    releaseTag = "v0.0.4",
                    apkUri = apkUri,
                    localFilePath = "/sdcard/Download/SwiftGrab-v0.0.4.apk"
                )
            )
            runCurrent()

            assertTrue(fixture.viewModel.uiState.value.apkDownloadState is ApkDownloadState.ReadyToInstall)
            assertEquals(listOf(apkUri), fixture.updateGateway.launchedInstallerUris)
        }

    @Test
    fun `startAppUpdateDownload transitions to Failed when enqueue fails`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)
            val updateInfo = sampleUpdateInfo(tag = "v0.0.4", version = "0.0.4")
            fixture.updateGateway.enqueueResult = Result.failure(IllegalStateException("DownloadManager disabled"))
            advanceUntilIdle()

            fixture.viewModel.startAppUpdateDownload(updateInfo)
            advanceUntilIdle()

            val state = fixture.viewModel.uiState.value.apkDownloadState
            assertEquals(
                ApkDownloadState.Failed(releaseTag = "v0.0.4", message = "DownloadManager disabled"),
                state
            )
        }

    @Test
    fun `installDownloadedApk and recheckInstallPermissionOnResume handle PermissionRequired and installers`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            val apkUri = "content://com.example.swiftgrab.fileprovider/apks/v4.apk".toUri()
            fixture.updateGateway.persistedApkState = ApkDownloadState.PermissionRequired(
                downloadId = 88L,
                releaseTag = "v0.0.4",
                apkUri = apkUri,
                localFilePath = "/tmp/v4.apk"
            )
            fixture.updateGateway.canInstallPackagesResult = false
            collectUiState(fixture.viewModel)
            advanceUntilIdle()

            // Permission denied -> opens unknown sources settings
            fixture.viewModel.installDownloadedApk()
            advanceUntilIdle()
            assertEquals(1, fixture.updateGateway.unknownSourcesSettingsCalls)
            assertTrue(fixture.updateGateway.launchedInstallerUris.isEmpty())

            // Resuming before permission is granted is a no-op
            fixture.viewModel.recheckInstallPermissionOnResume()
            advanceUntilIdle()
            assertTrue(fixture.updateGateway.launchedInstallerUris.isEmpty())

            // Grant permission and resume -> promotes to ReadyToInstall and launches installer
            fixture.updateGateway.canInstallPackagesResult = true
            fixture.viewModel.recheckInstallPermissionOnResume()
            advanceUntilIdle()
            assertTrue(fixture.viewModel.uiState.value.apkDownloadState is ApkDownloadState.ReadyToInstall)
            assertEquals(listOf(apkUri), fixture.updateGateway.launchedInstallerUris)

            // Calling installDownloadedApk again launches installer
            fixture.viewModel.installDownloadedApk()
            advanceUntilIdle()
            assertEquals(2, fixture.updateGateway.launchedInstallerUris.size)

            fixture.viewModel.openInstallPermissionSettings()
            fixture.viewModel.openReleaseOnGitHub("https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.5")
            assertEquals(2, fixture.updateGateway.unknownSourcesSettingsCalls)
            assertEquals(
                listOf("https://github.com/madhasoeki/SwiftGrab/releases/tag/v0.0.5"),
                fixture.updateGateway.openedReleaseUrls
            )
        }

    @Test
    fun `init restores persisted Downloading apk state without auto-launching installer on completion`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            val persisted = ApkDownloadState.Downloading(
                downloadId = 321L,
                releaseTag = "v0.0.4",
                progress = 0.25f,
                downloadedBytes = 250L,
                totalBytes = 1000L
            )
            fixture.updateGateway.persistedApkState = persisted
            collectUiState(fixture.viewModel)
            advanceUntilIdle()

            assertEquals(persisted, fixture.viewModel.uiState.value.apkDownloadState)

            val apkUri = "content://com.example.swiftgrab.fileprovider/apks/v4.apk".toUri()
            fixture.updateGateway.flowFor(321L).emit(
                ApkDownloadState.ReadyToInstall(
                    downloadId = 321L,
                    releaseTag = "v0.0.4",
                    apkUri = apkUri,
                    localFilePath = "/tmp/v4.apk"
                )
            )
            runCurrent()

            assertTrue(fixture.viewModel.uiState.value.apkDownloadState is ApkDownloadState.ReadyToInstall)
            assertTrue(fixture.updateGateway.launchedInstallerUris.isEmpty())
        }

    @Test
    fun `legacy storage permission queues pending request and executes after permission granted`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)
            fixture.storageGateway.needsLegacyPermission = true
            advanceUntilIdle()

            val url = "https://youtube.com/watch?v=legacy1"
            fixture.viewModel.startDownload(url, DownloadPreset.BEST_VIDEO)
            advanceUntilIdle()

            assertEquals(
                PendingDownloadRequest.Single(url, DownloadPreset.BEST_VIDEO),
                fixture.viewModel.pendingLegacyStorageDownload.value
            )
            assertTrue(fixture.serviceGateway.startedCalls.isEmpty())

            // Grant permission and execute pending download
            fixture.storageGateway.needsLegacyPermission = false
            fixture.viewModel.executePendingLegacyStorageDownload()
            advanceUntilIdle()

            assertNull(fixture.viewModel.pendingLegacyStorageDownload.value)
            assertEquals(
                listOf(StartedDownloadCall(url, DownloadPreset.BEST_VIDEO, null)),
                fixture.serviceGateway.startedCalls
            )

            // Retry task when legacy storage permission needed -> clearPendingLegacyStorageDownload clears it
            val failedTask = sampleTask("retry-1", DownloadStatus.FAILED, formatLabel = "MP4 720p")
            fixture.storageGateway.needsLegacyPermission = true
            fixture.viewModel.retryTask(failedTask)
            assertEquals(
                PendingDownloadRequest.Retry(failedTask),
                fixture.viewModel.pendingLegacyStorageDownload.value
            )
            fixture.viewModel.clearPendingLegacyStorageDownload()
            assertNull(fixture.viewModel.pendingLegacyStorageDownload.value)
        }

    @Test
    fun `task control actions, deleteTask, clearCompletedTasks, and retryTask delegate correctly`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            collectUiState(fixture.viewModel)
            fixture.dao.upsertTask(sampleTask("t1", DownloadStatus.DOWNLOADING))
            fixture.dao.upsertTask(sampleTask("t2", DownloadStatus.COMPLETED))
            fixture.dao.upsertTask(sampleTask("t3", DownloadStatus.FAILED, formatLabel = "MP3", audioOnly = true))
            advanceUntilIdle()

            fixture.viewModel.pauseTask("t1")
            fixture.viewModel.resumeTask("t1")
            fixture.viewModel.cancelTask("t1")
            assertEquals(listOf("t1"), fixture.serviceGateway.pausedIds)
            assertEquals(listOf("t1"), fixture.serviceGateway.resumedIds)
            assertEquals(listOf("t1"), fixture.serviceGateway.canceledIds)

            fixture.viewModel.retryTask(sampleTask("t3", DownloadStatus.FAILED, formatLabel = "MP3", audioOnly = true))
            assertEquals(
                listOf(StartedDownloadCall("https://example.com/video/t3", DownloadPreset.AUDIO_ONLY, "t3")),
                fixture.serviceGateway.startedCalls
            )

            fixture.viewModel.clearCompletedTasks()
            advanceUntilIdle()
            assertNull(fixture.repository.getTask("t2"))

            fixture.viewModel.deleteTask("t1")
            advanceUntilIdle()
            assertEquals(listOf("t1"), fixture.serviceGateway.cleanedTempIds)
            assertNull(fixture.repository.getTask("t1"))
        }

    @Test
    fun `updateYoutubeDL, settings, storage dir, and X login actions update state correctly`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val fixture = createFixture()
            fixture.cookieGateway.hasValidCookies = true
            collectUiState(fixture.viewModel)
            advanceUntilIdle()

            assertTrue(fixture.viewModel.uiState.value.isXLoggedIn)
            assertEquals("2026.03.01", fixture.viewModel.getYtDlpVersion())

            fixture.engineGateway.engineReadyFlow.value = true
            advanceUntilIdle()
            assertTrue(fixture.viewModel.uiState.value.isEngineReady)

            // yt-dlp update success & concurrent guard
            val updateGate = CompletableDeferred<Unit>()
            fixture.engineGateway.updateGate = updateGate
            fixture.viewModel.updateYoutubeDL()
            runCurrent()
            assertEquals(YtDlpUpdateState.Updating, fixture.viewModel.uiState.value.ytdlUpdateStatus)
            fixture.viewModel.updateYoutubeDL()
            updateGate.complete(Unit)
            fixture.engineGateway.updateGate = null
            advanceUntilIdle()
            assertEquals(1, fixture.engineGateway.updateCallCount)
            assertEquals(YtDlpUpdateState.Success("2026.04.01"), fixture.viewModel.uiState.value.ytdlUpdateStatus)

            // yt-dlp update error & resetUpdateStatus
            fixture.engineGateway.updateResult = Result.failure(RuntimeException("Network timeout"))
            fixture.viewModel.updateYoutubeDL()
            advanceUntilIdle()
            assertEquals(YtDlpUpdateState.Error("Network timeout"), fixture.viewModel.uiState.value.ytdlUpdateStatus)

            fixture.viewModel.resetUpdateStatus()
            advanceUntilIdle()
            assertEquals(YtDlpUpdateState.Idle, fixture.viewModel.uiState.value.ytdlUpdateStatus)

            // Settings & storage dir
            fixture.viewModel.setInstantShareMode(false)
            fixture.viewModel.setDefaultPreset(DownloadPreset.DATA_SAVER)
            fixture.viewModel.setAutoStartClipboard(true)
            advanceUntilIdle()
            assertFalse(fixture.viewModel.uiState.value.settings.instantShareMode)
            assertEquals(DownloadPreset.DATA_SAVER, fixture.viewModel.uiState.value.settings.defaultPreset)
            assertTrue(fixture.viewModel.uiState.value.settings.autoStartClipboard)

            val treeUri = "content://com.android.externalstorage.documents/tree/primary%3ADownload".toUri()
            assertEquals("/storage/custom/SwiftGrab", fixture.viewModel.setStorageDirFromUri(treeUri))
            assertEquals(listOf(treeUri), fixture.storageGateway.savedUris)
            assertTrue(fixture.viewModel.resetStorageDir())

            // X account logout & refresh
            fixture.viewModel.logoutXAccount()
            advanceUntilIdle()
            assertFalse(fixture.viewModel.uiState.value.isXLoggedIn)
            assertEquals(1, fixture.cookieGateway.clearCallCount)

            fixture.cookieGateway.hasValidCookies = true
            fixture.viewModel.refreshXLoginState()
            advanceUntilIdle()
            assertTrue(fixture.viewModel.uiState.value.isXLoggedIn)
        }
}
