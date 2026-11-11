package com.example.swiftgrab.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.swiftgrab.R
import com.example.swiftgrab.SwiftGrabApp
import com.example.swiftgrab.data.cookies.CookieStorageManager
import com.example.swiftgrab.data.db.DownloadStatus
import com.example.swiftgrab.data.db.DownloadTaskEntity
import com.example.swiftgrab.data.prefs.DownloadPreset
import com.example.swiftgrab.data.repository.DownloadRepository
import com.example.swiftgrab.ui.MainActivity
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground Service for downloading media in the background using yt-dlp and aria2c.
 * Complies with Android 14+ dataSync foreground service type and Android 15 timeout handling.
 */
class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: DownloadRepository
    private lateinit var notificationManager: NotificationManager

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedTaskIds = ConcurrentHashMap.newKeySet<String>()
    private val canceledTaskIds = ConcurrentHashMap.newKeySet<String>()
    private val throttlers = ConcurrentHashMap<String, UpdateThrottler>()

    private var queueCollectorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as SwiftGrabApp).repository
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        startQueueObserver()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()

        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)
                val taskId = intent.getStringExtra(EXTRA_TASK_ID)
                val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)
                val presetName = intent.getStringExtra(EXTRA_PRESET)
                val preset = if (presetName != null) {
                    DownloadPreset.fromName(presetName)
                } else if (audioOnly) {
                    DownloadPreset.AUDIO_ONLY
                } else {
                    DownloadPreset.BEST_VIDEO
                }

                serviceScope.launch {
                    try {
                        if (taskId != null) {
                            val existing = repository.getTask(taskId)
                            if (existing != null) {
                                repository.updateStatus(taskId, DownloadStatus.QUEUED)
                            }
                        } else if (!url.isNullOrBlank()) {
                            repository.createTask(
                                url = url,
                                formatLabel = preset.formatLabel,
                                audioOnly = preset.audioOnly
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing start download intent", e)
                    }
                }
            }
            DownloadActionReceiver.ACTION_PAUSE -> {
                val taskId = intent.getStringExtra(DownloadActionReceiver.EXTRA_TASK_ID)
                if (!taskId.isNullOrBlank()) {
                    pauseTask(taskId)
                }
            }
            DownloadActionReceiver.ACTION_RESUME -> {
                val taskId = intent.getStringExtra(DownloadActionReceiver.EXTRA_TASK_ID)
                if (!taskId.isNullOrBlank()) {
                    resumeTask(taskId)
                }
            }
            DownloadActionReceiver.ACTION_CANCEL -> {
                val taskId = intent.getStringExtra(DownloadActionReceiver.EXTRA_TASK_ID)
                if (!taskId.isNullOrBlank()) {
                    cancelTask(taskId)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun startQueueObserver() {
        queueCollectorJob?.cancel()
        queueCollectorJob = serviceScope.launch {
            repository.observeTasksByStatuses(listOf(DownloadStatus.QUEUED))
                .collectLatest { queuedTasks ->
                    for (task in queuedTasks) {
                        if (activeJobs.size >= MAX_CONCURRENT_DOWNLOADS) {
                            break
                        }
                        if (!activeJobs.containsKey(task.id)) {
                            startDownloadJob(task)
                        }
                    }
                }
        }
    }

    private fun startDownloadJob(task: DownloadTaskEntity) {
        pausedTaskIds.remove(task.id)
        canceledTaskIds.remove(task.id)

        val job = serviceScope.launch {
            executeDownload(task)
        }
        activeJobs[task.id] = job
    }

    /**
     * Menjalankan proses unduhan yt-dlp dan aria2c untuk satu [DownloadTaskEntity], memperbarui
     * progres ke Room serta notifikasi foreground secara periodik, dan menangani hasil/error.
     */
    private suspend fun executeDownload(task: DownloadTaskEntity) {
        val taskId = task.id
        Log.i(TAG, "Starting download execution for task: $taskId (url: ${task.url})")

        repository.updateStatus(taskId, DownloadStatus.DOWNLOADING)
        val stagingDir = DownloadFileManager.getStagingDir(this, taskId)
        val preset = DownloadPreset.fromTask(task.formatLabel, task.audioOnly)
        val cookiesFile = CookieStorageManager.getXCookiesFile(this)
        val request = DownloadRequestBuilder.build(
            url = task.url,
            outputDir = stagingDir,
            preset = preset,
            cookiesFile = cookiesFile
        )
        val throttler = throttlers.getOrPut(taskId) { UpdateThrottler(minIntervalMs = 250L) }

        var latestProgress = task.progress
        var latestSpeed = task.speed
        var latestEta = task.eta
        var latestDownloadedBytes = task.downloadedBytes
        var latestTotalBytes = task.totalBytes
        var latestTitle = task.title

        try {
            val app = application as SwiftGrabApp
            val engineResult = app.awaitEngineReady()
            if (engineResult.isFailure) {
                val error = engineResult.exceptionOrNull()
                val rootCause = generateSequence(error) { it.cause }.lastOrNull()
                val details = listOfNotNull(
                    error?.message,
                    rootCause?.takeIf { it != error }?.let { "${it.javaClass.simpleName}: ${it.message}" }
                ).filter { it.isNotBlank() }.joinToString(" -> ")
                val err = details.ifBlank { error?.javaClass?.simpleName ?: "Engine init failed" }
                throw IllegalStateException("Download engine failed to initialize: $err")
            }

            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(request, taskId) { progress, etaSeconds, line ->
                    val parsed = DownloadProgressParser.parse(
                        callbackProgress = progress,
                        callbackEtaSeconds = etaSeconds,
                        rawLine = line,
                        lastKnownTotalBytes = latestTotalBytes,
                        lastKnownDownloadedBytes = latestDownloadedBytes
                    )

                    latestProgress = parsed.progress
                    if (parsed.speed.isNotEmpty()) latestSpeed = parsed.speed
                    if (parsed.eta.isNotEmpty()) latestEta = parsed.eta
                    if (parsed.totalBytes > 0L) latestTotalBytes = parsed.totalBytes
                    if (parsed.downloadedBytes > 0L) latestDownloadedBytes = parsed.downloadedBytes

                    // Extract real title from destination log line if current title is just the URL
                    if (line.contains("Destination: ")) {
                        val pathPart = line.substringAfter("Destination: ").trim()
                        val fileName = File(pathPart).nameWithoutExtension
                        val cleanTitle = if (fileName.contains(" [")) {
                            fileName.substringBeforeLast(" [").trim()
                        } else {
                            fileName.trim()
                        }
                        if (cleanTitle.isNotBlank() && cleanTitle != taskId && cleanTitle != latestTitle) {
                            latestTitle = cleanTitle
                            serviceScope.launch {
                                repository.updateMetadata(
                                    id = taskId,
                                    title = cleanTitle,
                                    thumbnailUrl = task.thumbnailUrl,
                                    formatLabel = task.formatLabel
                                )
                            }
                        }
                    }

                    if (throttler.shouldUpdate()) {
                        serviceScope.launch {
                            repository.updateProgress(
                                id = taskId,
                                progress = latestProgress,
                                downloadedBytes = latestDownloadedBytes,
                                totalBytes = latestTotalBytes,
                                speed = latestSpeed,
                                eta = latestEta
                            )
                        }
                        updateForegroundProgressNotification(
                            taskId = taskId,
                            title = latestTitle,
                            progress = latestProgress,
                            speed = latestSpeed,
                            eta = latestEta
                        )
                    }
                }
            }

            // Download completed successfully
            val completed = DownloadFileManager.finalizeCompletedMedia(this, taskId)
                ?: throw java.io.IOException("Berkas unduhan selesai tidak ditemukan di direktori staging")
            val finalPath = completed.outputPath
            val finalSize = completed.fileSize.takeIf { it > 0L } ?: latestTotalBytes
            val completedFile = completed.file
            val completedTitle = completedFile?.nameWithoutExtension
                ?: completed.displayName.takeIf { it.isNotBlank() }?.let { File(it).nameWithoutExtension }
            if (completedTitle != null && latestTitle == task.url) {
                val cleanTitle = if (completedTitle.contains(" [")) completedTitle.substringBeforeLast(" [") else completedTitle
                if (cleanTitle.isNotBlank()) {
                    latestTitle = cleanTitle
                    repository.updateMetadata(taskId, cleanTitle, task.thumbnailUrl, task.formatLabel)
                }
            }

            // Pemindaian MediaScannerConnection dipanggil untuk berkas dengan path fisik lokal
            // agar video/audio langsung otomatis muncul di Galeri HP dan aplikasi Pemutar Musik/Video.
            // Untuk ekspor SAF dokumen non-lokal (content://), pengindeksan bergantung pada penyedia dokumen SAF terkait.
            if (completedFile != null && completedFile.exists()) {
                DownloadFileManager.scanMediaFile(this, completedFile)
            } else if (finalPath.startsWith("/") && File(finalPath).exists()) {
                DownloadFileManager.scanMediaPath(this, finalPath)
            }

            repository.markCompleted(taskId, finalPath, finalSize)
            showCompletedNotification(taskId, latestTitle, finalPath, finalSize)
            Log.i(TAG, "Download completed for task: $taskId -> $finalPath")
        } catch (t: Throwable) {
            handleDownloadError(task, t, latestProgress, latestDownloadedBytes, latestTotalBytes)
        } finally {
            activeJobs.remove(taskId)
            throttlers.remove(taskId)
            checkAndStopIfIdle()
        }
    }

    /**
     * Menangani kegagalan atau pembatalan unduhan berdasarkan status jeda/batal pengguna,
     * cancellation coroutine, maupun error atau exception native dari mesin unduhan.
     */
    private suspend fun handleDownloadError(
        task: DownloadTaskEntity,
        t: Throwable,
        lastProgress: Float,
        lastDownloadedBytes: Long,
        lastTotalBytes: Long
    ) {
        val taskId = task.id
        when {
            pausedTaskIds.remove(taskId) -> {
                Log.d(TAG, "Task $taskId was paused by user")
                repository.updateStatus(taskId, DownloadStatus.PAUSED)
                updatePausedNotification(taskId, task.title, lastProgress)
            }
            canceledTaskIds.remove(taskId) -> {
                Log.d(TAG, "Task $taskId was canceled by user")
                DownloadFileManager.cleanupTemporaryFiles(this, taskId)
                repository.updateStatus(taskId, DownloadStatus.CANCELED)
                notificationManager.cancel(NOTIFICATION_FOREGROUND_ID)
            }
            t is CancellationException -> {
                Log.d(TAG, "Task $taskId coroutine was canceled")
                repository.updateStatus(taskId, DownloadStatus.PAUSED)
                updatePausedNotification(taskId, task.title, lastProgress)
            }
            else -> {
                Log.e(TAG, "Task $taskId failed with exception: ${t.message}", t)
                val errorMessage = t.message ?: "Download failed"
                repository.updateStatus(taskId, DownloadStatus.FAILED, errorMessage = errorMessage)
                showFailedNotification(taskId, task.title, errorMessage)
            }
        }
    }

    fun pauseTask(taskId: String) {
        pausedTaskIds.add(taskId)
        canceledTaskIds.remove(taskId)

        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Pausing process for task: $taskId")
                YoutubeDL.getInstance().destroyProcessById(taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying process on pause: $taskId", e)
            }
            val job = activeJobs.remove(taskId)
            job?.cancel()

            repository.updateStatus(taskId, DownloadStatus.PAUSED)
            val task = repository.getTask(taskId)
            if (task != null) {
                updatePausedNotification(taskId, task.title, task.progress)
            }
            checkAndStopIfIdle()
        }
    }

    fun resumeTask(taskId: String) {
        pausedTaskIds.remove(taskId)
        canceledTaskIds.remove(taskId)

        serviceScope.launch {
            repository.updateStatus(taskId, DownloadStatus.QUEUED)
            val task = repository.getTask(taskId)
            if (task != null && !activeJobs.containsKey(taskId)) {
                startDownloadJob(task)
            }
        }
    }

    fun cancelTask(taskId: String) {
        canceledTaskIds.add(taskId)
        pausedTaskIds.remove(taskId)

        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Canceling and destroying process for task: $taskId")
                YoutubeDL.getInstance().destroyProcessById(taskId)
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying process on cancel: $taskId", e)
            }
            val job = activeJobs.remove(taskId)
            job?.cancel()

            DownloadFileManager.cleanupTemporaryFiles(this@DownloadForegroundService, taskId)
            repository.updateStatus(taskId, DownloadStatus.CANCELED)

            notificationManager.cancel(NOTIFICATION_FOREGROUND_ID)
            checkAndStopIfIdle()
        }
    }

    private fun checkAndStopIfIdle() {
        if (activeJobs.isEmpty()) {
            serviceScope.launch {
                val queued = repository.getTasksByStatus(DownloadStatus.QUEUED)
                if (queued.isEmpty()) {
                    Log.d(TAG, "No active or queued downloads remaining, stopping foreground service.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Log.w(TAG, "Foreground service dataSync timeout reached; pausing all running downloads safely.")

        serviceScope.launch(Dispatchers.IO) {
            for ((taskId, job) in activeJobs) {
                try {
                    pausedTaskIds.add(taskId)
                    YoutubeDL.getInstance().destroyProcessById(taskId)
                    job.cancel()
                    repository.updateStatus(taskId, DownloadStatus.PAUSED)
                } catch (e: Exception) {
                    Log.e(TAG, "Error pausing task $taskId during onTimeout", e)
                }
            }
            activeJobs.clear()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        queueCollectorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureForeground() {
        val notification = buildInitialNotification()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_FOREGROUND_ID, notification, serviceType)
    }

    private fun buildInitialNotification(): Notification {
        val openAppPendingIntent = createOpenAppPendingIntent()

        return NotificationCompat.Builder(this, SwiftGrabApp.CHANNEL_PROGRESS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.download_preparing))
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForegroundProgressNotification(
        taskId: String,
        title: String,
        progress: Float,
        speed: String,
        eta: String
    ) {
        val openAppPendingIntent = createOpenAppPendingIntent()
        val pausePendingIntent = createActionPendingIntent(DownloadActionReceiver.ACTION_PAUSE, taskId)
        val cancelPendingIntent = createActionPendingIntent(DownloadActionReceiver.ACTION_CANCEL, taskId)

        val percentInt = progress.toInt().coerceIn(0, 100)
        val contentText = buildString {
            append("$percentInt%")
            if (speed.isNotEmpty()) append(" • $speed")
            if (eta.isNotEmpty()) append(" • Sisa $eta")
        }

        val notification = NotificationCompat.Builder(this, SwiftGrabApp.CHANNEL_PROGRESS_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.ifBlank { getString(R.string.app_name) })
            .setContentText(contentText)
            .setProgress(100, percentInt, progress <= 0f)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.action_pause),
                pausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_cancel),
                cancelPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_FOREGROUND_ID, notification)
    }

    private fun updatePausedNotification(taskId: String, title: String, progress: Float) {
        val openAppPendingIntent = createOpenAppPendingIntent()
        val resumePendingIntent = createActionPendingIntent(DownloadActionReceiver.ACTION_RESUME, taskId)
        val cancelPendingIntent = createActionPendingIntent(DownloadActionReceiver.ACTION_CANCEL, taskId)

        val percentInt = progress.toInt().coerceIn(0, 100)
        val notification = NotificationCompat.Builder(this, SwiftGrabApp.CHANNEL_PROGRESS_ID)
            .setSmallIcon(android.R.drawable.ic_media_pause)
            .setContentTitle(title.ifBlank { getString(R.string.app_name) })
            .setContentText("${getString(R.string.download_paused)} ($percentInt%)")
            .setProgress(100, percentInt, false)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_play,
                getString(R.string.action_resume),
                resumePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_cancel),
                cancelPendingIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_FOREGROUND_ID, notification)
    }

    private fun showCompletedNotification(taskId: String, title: String, filePath: String, fileSize: Long) {
        val openAppPendingIntent = createOpenAppPendingIntent()
        val sizeFormatted = if (fileSize > 0) " • ${formatBytes(fileSize)}" else ""
        val fallbackName = if (filePath.startsWith("content://")) {
            filePath.substringAfterLast("%2F").substringAfterLast("/")
        } else {
            File(filePath).name
        }

        val notification = NotificationCompat.Builder(this, SwiftGrabApp.CHANNEL_COMPLETE_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.download_completed))
            .setContentText("${title.ifBlank { fallbackName }}$sizeFormatted")
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(taskId.hashCode(), notification)
    }

    private fun showFailedNotification(taskId: String, title: String, errorMessage: String) {
        val resumePendingIntent = createActionPendingIntent(DownloadActionReceiver.ACTION_RESUME, taskId)

        val notification = NotificationCompat.Builder(this, SwiftGrabApp.CHANNEL_COMPLETE_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(getString(R.string.download_failed))
            .setContentText("${title.ifBlank { "Tugas unduhan" }}: $errorMessage")
            .setContentIntent(createOpenAppPendingIntent())
            .addAction(
                android.R.drawable.ic_menu_rotate,
                "Coba Lagi",
                resumePendingIntent
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(taskId.hashCode(), notification)
    }

    private fun createOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createActionPendingIntent(action: String, taskId: String): PendingIntent {
        val intent = Intent(this, DownloadActionReceiver::class.java).apply {
            this.action = action
            putExtra(DownloadActionReceiver.EXTRA_TASK_ID, taskId)
        }
        val requestCode = (taskId.hashCode() * 31) + action.hashCode()
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1000) {
            String.format(Locale.US, "%.1f GB", mb / 1024)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }

    companion object {
        private const val TAG = "DownloadForegroundSvc"
        const val NOTIFICATION_FOREGROUND_ID = 1001
        const val MAX_CONCURRENT_DOWNLOADS = 2

        const val ACTION_START_DOWNLOAD = "com.example.swiftgrab.action.START_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_AUDIO_ONLY = "extra_audio_only"
        const val EXTRA_PRESET = "extra_preset"

        fun startDownload(
            context: Context,
            url: String,
            preset: DownloadPreset = DownloadPreset.BEST_VIDEO,
            taskId: String? = null
        ) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_AUDIO_ONLY, preset.audioOnly)
                putExtra(EXTRA_PRESET, preset.name)
                if (taskId != null) {
                    putExtra(EXTRA_TASK_ID, taskId)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startDownload(context: Context, url: String, audioOnly: Boolean = false, taskId: String? = null) {
            val preset = if (audioOnly) DownloadPreset.AUDIO_ONLY else DownloadPreset.BEST_VIDEO
            startDownload(context = context, url = url, preset = preset, taskId = taskId)
        }

        fun pause(context: Context, taskId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadActionReceiver.ACTION_PAUSE
                putExtra(DownloadActionReceiver.EXTRA_TASK_ID, taskId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun resume(context: Context, taskId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadActionReceiver.ACTION_RESUME
                putExtra(DownloadActionReceiver.EXTRA_TASK_ID, taskId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context, taskId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = DownloadActionReceiver.ACTION_CANCEL
                putExtra(DownloadActionReceiver.EXTRA_TASK_ID, taskId)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

