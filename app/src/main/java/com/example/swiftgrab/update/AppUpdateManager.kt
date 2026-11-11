package com.example.swiftgrab.update

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.example.swiftgrab.BuildConfig
import com.example.swiftgrab.R
import com.example.swiftgrab.SwiftGrabApp
import com.example.swiftgrab.data.prefs.UserPreferences
import com.example.swiftgrab.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long = 0L
)

data class AppUpdateInfo(
    val versionName: String,
    val tagName: String,
    val changelog: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val apkSizeBytes: Long = 0L
)

sealed interface AppUpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : AppUpdateCheckResult
    data class UpToDate(val currentVersion: String) : AppUpdateCheckResult
    data class Error(val message: String) : AppUpdateCheckResult
}

sealed interface ApkDownloadState {
    data object Idle : ApkDownloadState
    data class Downloading(
        val downloadId: Long,
        val releaseTag: String,
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : ApkDownloadState
    data class ReadyToInstall(
        val downloadId: Long,
        val releaseTag: String,
        val apkUri: Uri,
        val localFilePath: String?
    ) : ApkDownloadState
    data class PermissionRequired(
        val downloadId: Long,
        val releaseTag: String,
        val apkUri: Uri,
        val localFilePath: String?
    ) : ApkDownloadState
    data class Failed(
        val releaseTag: String,
        val message: String
    ) : ApkDownloadState

    fun matchesRelease(info: AppUpdateInfo): Boolean {
        return when (this) {
            Idle -> false
            is Downloading -> releaseTag == info.tagName
            is ReadyToInstall -> releaseTag == info.tagName
            is PermissionRequired -> releaseTag == info.tagName
            is Failed -> releaseTag == info.tagName
        }
    }
}

/**
 * Pengelola pembaruan aplikasi otomatis (In-App Auto-Updater) berbasis GitHub Releases API,
 * perbandingan versi SemVer, pengunduhan APK lewat Android [DownloadManager], notifikasi,
 * dan instalasi paket via [FileProvider].
 */
object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/madhasoeki/SwiftGrab/releases/latest"
    const val GITHUB_RELEASES_PAGE_URL =
        "https://github.com/madhasoeki/SwiftGrab/releases"

    const val NOTIFICATION_UPDATE_ID = 1002
    const val EXTRA_SHOW_UPDATE_DIALOG = "extra_show_update_dialog"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val POLL_INTERVAL_MS = 250L
    private val KNOWN_ABI_MARKERS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /**
     * Memeriksa rilis terbaru di GitHub Releases pada [Dispatchers.IO] dan membandingkannya
     * secara SemVer dengan [currentVersionName].
     */
    suspend fun checkForUpdate(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS ?: emptyArray(),
        endpointUrl: String = GITHUB_LATEST_RELEASE_URL
    ): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpointUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "SwiftGrab/${normalizeVersion(currentVersionName)}")
            }

            val statusCode = connection.responseCode
            when (statusCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    evaluateReleasePayload(body, currentVersionName, supportedAbis)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    AppUpdateCheckResult.Error("Belum ada rilis aplikasi yang dipublikasikan (HTTP 404)")
                }
                HttpURLConnection.HTTP_FORBIDDEN, 429 -> {
                    AppUpdateCheckResult.Error("Batas permintaan GitHub API tercapai. Coba lagi nanti.")
                }
                else -> {
                    AppUpdateCheckResult.Error("Gagal memeriksa pembaruan (HTTP $statusCode)")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check GitHub releases", e)
            AppUpdateCheckResult.Error(e.message ?: "Tidak dapat terhubung ke GitHub")
        } finally {
            connection?.disconnect()
        }
    }

    internal fun evaluateReleasePayload(
        jsonBody: String,
        currentVersionName: String,
        supportedAbis: Array<String>
    ): AppUpdateCheckResult {
        val releaseInfo = parseLatestRelease(jsonBody, supportedAbis)
            ?: return AppUpdateCheckResult.UpToDate(normalizeVersion(currentVersionName))

        return if (isNewerVersion(releaseInfo.tagName, currentVersionName)) {
            AppUpdateCheckResult.UpdateAvailable(releaseInfo)
        } else {
            AppUpdateCheckResult.UpToDate(normalizeVersion(currentVersionName))
        }
    }

    /**
     * Mem-parsing JSON response GitHub Releases (`/releases/latest`) menjadi [AppUpdateInfo].
     */
    internal fun parseLatestRelease(
        jsonBody: String,
        supportedAbis: Array<String> = emptyArray()
    ): AppUpdateInfo? {
        val json = JSONObject(jsonBody)
        if (json.optBoolean("draft", false) || json.optBoolean("prerelease", false)) {
            return null
        }

        val rawTag = json.optString("tag_name", "").trim()
        if (rawTag.isEmpty()) return null

        val normalizedVersion = normalizeVersion(rawTag)
        val rawBody = json.optString("body", "").trim()
        val changelog = rawBody.ifBlank { "Perbaikan bug dan peningkatan performa." }

        val rawHtmlUrl = json.optString("html_url", "").trim()
        val htmlUrl = if (isValidHttpsUrl(rawHtmlUrl)) rawHtmlUrl else GITHUB_RELEASES_PAGE_URL

        val apkAssets = mutableListOf<ReleaseAsset>()
        val assetsArray = json.optJSONArray("assets")
        if (assetsArray != null) {
            for (i in 0 until assetsArray.length()) {
                val assetObj = assetsArray.optJSONObject(i) ?: continue
                val assetName = assetObj.optString("name", "").trim()
                val downloadUrl = assetObj.optString("browser_download_url", "").trim()
                val sizeBytes = assetObj.optLong("size", 0L)

                if (isValidHttpsUrl(downloadUrl) && isApkUrl(downloadUrl, assetName)) {
                    val resolvedName = assetName.ifBlank {
                        downloadUrl.substringBefore('?').substringAfterLast('/', "SwiftGrab-$rawTag.apk")
                    }
                    apkAssets.add(
                        ReleaseAsset(
                            name = resolvedName,
                            downloadUrl = downloadUrl,
                            sizeBytes = sizeBytes
                        )
                    )
                }
            }
        }

        val bestAsset = selectBestApkAsset(apkAssets, supportedAbis)

        return AppUpdateInfo(
            versionName = normalizedVersion,
            tagName = rawTag,
            changelog = changelog,
            htmlUrl = htmlUrl,
            apkDownloadUrl = bestAsset?.downloadUrl,
            apkFileName = bestAsset?.name,
            apkSizeBytes = bestAsset?.sizeBytes ?: 0L
        )
    }

    /**
     * Memilih aset APK paling sesuai dengan arsitektur ABI perangkat (mis. `arm64-v8a`,
     * baik format `SwiftGrab-v0.0.3-arm64-v8a.apk` maupun `app-arm64-v8a-release.apk`),
     * dengan fallback ke APK universal atau APK generik tanpa ABI. Jika tidak ada APK yang kompatibel,
     * mengembalikan `null` alih-alih memilih ABI yang tidak kompatibel.
     */
    internal fun selectBestApkAsset(
        assets: List<ReleaseAsset>,
        supportedAbis: Array<String>
    ): ReleaseAsset? {
        if (assets.isEmpty()) return null

        for (abi in supportedAbis) {
            val cleanAbi = abi.trim().lowercase()
            if (cleanAbi.isEmpty()) continue
            val matched = assets.firstOrNull { asset ->
                detectAbiInAssetName(asset.name) == cleanAbi
            }
            if (matched != null) return matched
        }

        val universal = assets.firstOrNull { asset ->
            asset.name.contains("universal", ignoreCase = true)
        }
        if (universal != null) return universal

        return assets.firstOrNull { asset ->
            detectAbiInAssetName(asset.name) == null
        }
    }

    internal fun detectAbiInAssetName(fileName: String): String? {
        val stem = fileName.substringBeforeLast('.', fileName).lowercase()
        // Periksa dari marker terpanjang agar "x86_64" tidak tertukar dengan "x86"
        return KNOWN_ABI_MARKERS.firstOrNull { marker ->
            val pattern = Regex("(^|[-_.])${Regex.escape(marker)}($|[-_.])")
            pattern.containsMatchIn(stem)
        }
    }

    /**
     * Menghapus awalan huruf 'v'/'V' dan spasi dari string versi.
     */
    fun normalizeVersion(version: String): String {
        return version.trim().removePrefix("v").removePrefix("V").trim()
    }

    /**
     * Mengembalikan true jika [latestTag] lebih baru daripada [currentVersion] menurut SemVer.
     */
    fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
        return compareSemVer(latestTag, currentVersion) > 0
    }

    /**
     * Membandingkan dua string versi semver (`> 0` jika v1 > v2, `< 0` jika v1 < v2, `0` jika sama).
     */
    fun compareSemVer(v1: String, v2: String): Int {
        val parsed1 = parseSemVer(v1)
        val parsed2 = parseSemVer(v2)

        val maxCoreLen = maxOf(parsed1.core.size, parsed2.core.size)
        for (i in 0 until maxCoreLen) {
            val n1 = parsed1.core.getOrElse(i) { 0 }
            val n2 = parsed2.core.getOrElse(i) { 0 }
            if (n1 != n2) return n1.compareTo(n2)
        }

        val pre1 = parsed1.preRelease
        val pre2 = parsed2.preRelease
        if (pre1 == null && pre2 == null) return 0
        if (pre1 == null) return 1
        if (pre2 == null) return -1

        val ids1 = pre1.split('.')
        val ids2 = pre2.split('.')
        val minLen = minOf(ids1.size, ids2.size)
        for (i in 0 until minLen) {
            val id1 = ids1[i]
            val id2 = ids2[i]
            val num1 = id1.toIntOrNull()
            val num2 = id2.toIntOrNull()
            val cmp = when {
                num1 != null && num2 != null -> num1.compareTo(num2)
                num1 != null -> -1
                num2 != null -> 1
                else -> id1.compareTo(id2)
            }
            if (cmp != 0) return cmp
        }
        return ids1.size.compareTo(ids2.size)
    }

    private data class ParsedSemVer(val core: List<Int>, val preRelease: String?)

    private fun parseSemVer(raw: String): ParsedSemVer {
        val cleaned = normalizeVersion(raw).substringBefore('+')
        val dashIdx = cleaned.indexOf('-')
        val coreStr = if (dashIdx >= 0) cleaned.substring(0, dashIdx) else cleaned
        val preRelease = if (dashIdx >= 0) {
            cleaned.substring(dashIdx + 1).takeIf { it.isNotBlank() }
        } else {
            null
        }
        val core = coreStr.split('.').map { segment ->
            segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        return ParsedSemVer(core = core, preRelease = preRelease)
    }

    private fun isValidHttpsUrl(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true)
    }

    private fun isApkUrl(url: String, name: String): Boolean {
        val cleanUrl = url.substringBefore('?')
        return cleanUrl.endsWith(".apk", ignoreCase = true) ||
            name.endsWith(".apk", ignoreCase = true)
    }

    internal fun sanitizeApkFileName(rawName: String?, tagName: String): String {
        val fallback = "SwiftGrab-${normalizeVersion(tagName).ifBlank { "latest" }}.apk"
        val candidate = rawName?.trim()?.takeIf { it.isNotBlank() } ?: fallback
        val baseName = candidate.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trimStart('.')
            .ifBlank { fallback }
        return if (baseName.endsWith(".apk", ignoreCase = true)) baseName else "$baseName.apk"
    }

    internal fun resolveUniqueApkFileName(destDir: File?, baseFileName: String): String {
        if (destDir == null) return baseFileName
        val candidate = File(destDir, baseFileName)
        if (!candidate.exists()) return baseFileName

        val dotIdx = baseFileName.lastIndexOf('.')
        val stem = if (dotIdx > 0) baseFileName.substring(0, dotIdx) else baseFileName
        val ext = if (dotIdx > 0) baseFileName.substring(dotIdx) else ""

        var index = 1
        while (true) {
            val nextName = "${stem}_$index$ext"
            if (!File(destDir, nextName).exists()) {
                return nextName
            }
            index++
        }
    }

    /**
     * Memulai pengunduhan berkas APK melalui [DownloadManager] ke nama tujuan unik tanpa menghapus
     * APK lama, lalu menyimpan ID unduhan serta identitas rilis agar progres dapat dipulihkan.
     */
    fun enqueueApkDownload(
        context: Context,
        updateInfo: AppUpdateInfo,
        preferences: UserPreferences? = null
    ): Result<Long> {
        val apkUrl = updateInfo.apkDownloadUrl
        if (apkUrl.isNullOrBlank() || !isValidHttpsUrl(apkUrl)) {
            return Result.failure(IllegalArgumentException("URL berkas APK tidak ditemukan pada rilis ini."))
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return Result.failure(IllegalStateException("DownloadManager tidak tersedia di perangkat ini."))

        val fileName = sanitizeApkFileName(updateInfo.apkFileName, updateInfo.tagName)
        val canWritePublicDirect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

        return try {
            val destDir = if (canWritePublicDirect) {
                runCatching {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                }.getOrNull()
            } else {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            }
            val uniqueFileName = resolveUniqueApkFileName(destDir, fileName)

            val request = DownloadManager.Request(apkUrl.toUri()).apply {
                setTitle("Update SwiftGrab ${updateInfo.tagName}")
                setDescription(context.getString(R.string.update_downloading_desc))
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                addRequestHeader("User-Agent", "SwiftGrab/${BuildConfig.VERSION_NAME}")

                if (canWritePublicDirect) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uniqueFileName)
                } else {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, uniqueFileName)
                }
            }

            val downloadId = downloadManager.enqueue(request)
            val prefs = preferences ?: UserPreferences(context)
            prefs.saveActiveApkDownload(downloadId, updateInfo.tagName)
            Result.success(downloadId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue APK download", e)
            Result.failure(e)
        }
    }

    /**
     * Memulihkan state unduhan APK yang sebelumnya diantrekan di [DownloadManager] setelah proses
     * aplikasi dimulai ulang, baik yang masih berjalan maupun yang sudah selesai diunduh.
     */
    fun restorePersistedApkDownloadState(
        context: Context,
        preferences: UserPreferences
    ): ApkDownloadState? {
        val (downloadId, releaseTag) = preferences.getActiveApkDownload() ?: return null
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            ?: return null

        val snapshot = queryApkDownloadSnapshot(context, downloadManager, downloadId, releaseTag)
        if (snapshot == null || snapshot is ApkDownloadState.Failed) {
            preferences.clearActiveApkDownload()
            return null
        }
        return snapshot
    }

    /**
     * Mengamati progres dan hasil pengunduhan APK dari [DownloadManager] secara berkala.
     */
    fun observeApkDownload(
        context: Context,
        downloadId: Long,
        releaseTag: String,
        preferences: UserPreferences? = null
    ): Flow<ApkDownloadState> = flow {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (downloadManager == null) {
            emit(ApkDownloadState.Failed(releaseTag, "DownloadManager tidak tersedia."))
            return@flow
        }
        val prefs = preferences ?: UserPreferences(context)

        while (currentCoroutineContext().isActive) {
            val snapshot = queryApkDownloadSnapshot(context, downloadManager, downloadId, releaseTag)
            if (snapshot == null) {
                prefs.clearActiveApkDownload()
                emit(ApkDownloadState.Failed(releaseTag, "Unduhan dibatalkan."))
                break
            }

            emit(snapshot)

            when (snapshot) {
                is ApkDownloadState.ReadyToInstall,
                is ApkDownloadState.PermissionRequired -> break
                is ApkDownloadState.Failed -> {
                    prefs.clearActiveApkDownload()
                    break
                }
                else -> Unit
            }

            delay(POLL_INTERVAL_MS)
        }
    }.flowOn(Dispatchers.IO)

    internal fun queryApkDownloadSnapshot(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
        releaseTag: String
    ): ApkDownloadState? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }

            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

            val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
            val downloaded = if (downloadedIdx >= 0) cursor.getLong(downloadedIdx).coerceAtLeast(0L) else 0L
            val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L
            val localUriStr = if (localUriIdx >= 0) cursor.getString(localUriIdx) else null
            val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else 0

            when (status) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_RUNNING -> {
                    val progress = if (total > 0L) {
                        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    ApkDownloadState.Downloading(
                        downloadId = downloadId,
                        releaseTag = releaseTag,
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total.coerceAtLeast(0L)
                    )
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val resolved = resolveInstallUri(
                        context = context,
                        downloadManager = downloadManager,
                        downloadId = downloadId,
                        localUriStr = localUriStr
                    )
                    if (resolved == null) {
                        ApkDownloadState.Failed(releaseTag, "Berkas APK hasil unduhan tidak dapat diakses.")
                    } else if (canInstallPackages(context)) {
                        ApkDownloadState.ReadyToInstall(
                            downloadId = downloadId,
                            releaseTag = releaseTag,
                            apkUri = resolved.first,
                            localFilePath = resolved.second
                        )
                    } else {
                        ApkDownloadState.PermissionRequired(
                            downloadId = downloadId,
                            releaseTag = releaseTag,
                            apkUri = resolved.first,
                            localFilePath = resolved.second
                        )
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    ApkDownloadState.Failed(releaseTag, mapDownloadFailureReason(reason))
                }
                else -> null
            }
        }
    }

    private fun resolveInstallUri(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
        localUriStr: String?
    ): Pair<Uri, String?>? {
        if (!localUriStr.isNullOrBlank() && localUriStr.startsWith("file://")) {
            val filePath = localUriStr.toUri().path
            if (!filePath.isNullOrBlank()) {
                val apkFile = File(filePath)
                if (apkFile.exists() && apkFile.canRead()) {
                    try {
                        val providerUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            apkFile
                        )
                        return providerUri to apkFile.absolutePath
                    } catch (e: Exception) {
                        Log.w(TAG, "FileProvider failed for ${apkFile.absolutePath}, trying fallback URI", e)
                    }
                }
            }
        }

        val dmUri = try {
            downloadManager.getUriForDownloadedFile(downloadId)
        } catch (e: Exception) {
            Log.e(TAG, "DownloadManager.getUriForDownloadedFile failed for $downloadId", e)
            null
        }
        return dmUri?.let { it to null }
    }

    private fun mapDownloadFailureReason(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Ruang penyimpanan tidak mencukupi."
            DownloadManager.ERROR_CANNOT_RESUME -> "Unduhan terputus dan tidak dapat dilanjutkan."
            DownloadManager.ERROR_FILE_ERROR -> "Gagal menulis berkas APK ke penyimpanan."
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Koneksi terputus saat mengunduh APK."
            else -> "Gagal mengunduh berkas APK (kode: $reason)."
        }
    }

    /**
     * Memeriksa apakah aplikasi diizinkan memasang paket APK dari sumber tidak dikenal (Android 8+).
     */
    fun canInstallPackages(context: Context): Boolean {
        return try {
            context.packageManager.canRequestPackageInstalls()
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Membuka installer APK sistem menggunakan [FileProvider] URI dan tipe MIME paket Android.
     */
    fun launchApkInstaller(context: Context, apkUri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No package installer activity found", e)
            false
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied launching APK installer", e)
            false
        }
    }

    /**
     * Membuka layar pengaturan sistem untuk mengizinkan instalasi aplikasi dari sumber ini.
     */
    fun launchUnknownSourcesSettings(context: Context): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri()
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open unknown app sources settings", e)
            false
        }
    }

    /**
     * Membuka halaman rilis GitHub di browser perangkat.
     */
    fun openReleasePage(context: Context, url: String) {
        val safeUrl = if (isValidHttpsUrl(url)) url else GITHUB_RELEASES_PAGE_URL
        val intent = Intent(Intent.ACTION_VIEW, safeUrl.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to open browser for release URL: $safeUrl", e)
        }
    }

    /**
     * Menampilkan notifikasi pembaruan aplikasi jika izin notifikasi tersedia.
     */
    fun showUpdateNotification(context: Context, info: AppUpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SHOW_UPDATE_DIALOG, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_UPDATE_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, SwiftGrabApp.CHANNEL_UPDATES_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.update_dialog_title, info.versionName))
            .setContentText(context.getString(R.string.update_notification_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.changelog.take(280)))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_UPDATE_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing notification permission when posting update notification", e)
        }
    }
}
