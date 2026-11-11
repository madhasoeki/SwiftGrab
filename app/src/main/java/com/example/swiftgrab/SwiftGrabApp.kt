package com.example.swiftgrab

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.swiftgrab.data.db.AppDatabase
import com.example.swiftgrab.data.prefs.UserPreferences
import com.example.swiftgrab.data.repository.DownloadRepository
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class SwiftGrabApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: DownloadRepository by lazy { DownloadRepository(database.downloadDao()) }
    val userPreferences: UserPreferences by lazy { UserPreferences(this) }

    private val _isEngineReady = MutableStateFlow(false)
    val isEngineReady: StateFlow<Boolean> = _isEngineReady.asStateFlow()

    private val engineInitDeferred = CompletableDeferred<Result<Unit>>()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()
        initDownloadEnginesAsync()
    }

    suspend fun awaitEngineReady(): Result<Unit> = engineInitDeferred.await()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val progressChannel = NotificationChannel(
                CHANNEL_PROGRESS_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_description)
                setShowBadge(false)
            }

            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETE_ID,
                getString(R.string.notification_channel_completed_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_completed_description)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(progressChannel, completedChannel))
        }
    }

    private fun initDownloadEnginesAsync() {
        appScope.launch {
            try {
                Log.d(TAG, "Initializing YoutubeDL, FFmpeg, and Aria2c on Dispatchers.IO...")
                val startTime = System.currentTimeMillis()

                YoutubeDL.getInstance().init(this@SwiftGrabApp)
                FFmpeg.getInstance().init(this@SwiftGrabApp)
                Aria2c.getInstance().init(this@SwiftGrabApp)

                val elapsed = System.currentTimeMillis() - startTime
                _isEngineReady.value = true
                engineInitDeferred.complete(Result.success(Unit))
                Log.i(TAG, "Download engines successfully initialized in ${elapsed}ms")
            } catch (e: CancellationException) {
                engineInitDeferred.complete(Result.failure(e))
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize YoutubeDL/FFmpeg/Aria2c native engines", t)
                engineInitDeferred.complete(Result.failure(t))
            }
        }
    }

    companion object {
        const val TAG = "SwiftGrabApp"
        const val CHANNEL_PROGRESS_ID = "download_progress_channel"
        const val CHANNEL_COMPLETE_ID = "download_complete_channel"

        val internalTileToken: String = UUID.randomUUID().toString()

        lateinit var instance: SwiftGrabApp
            private set
    }
}
