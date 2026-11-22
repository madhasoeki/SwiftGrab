package com.example.swiftgrab.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.swiftgrab.R
import com.example.swiftgrab.data.cookies.CookieStorageManager

/**
 * Dialog layar penuh berisi WebView resmi X (x.com) untuk mengambil dan menyimpan
 * session cookies secara lokal demi mendukung unduhan media sensitif.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun XLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var webProgress by remember { mutableFloatStateOf(0f) }
    var isAuthDetected by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler {
            if (webViewInstance?.canGoBack() == true) {
                webViewInstance?.goBack()
            } else {
                onDismiss()
            }
        }

        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = stringResource(R.string.x_login_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.x_login_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.btn_close),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            Button(
                                onClick = {
                                    val cm = CookieManager.getInstance()
                                    val xCookies = cm.getCookie("https://x.com") ?: ""
                                    val twitterCookies = cm.getCookie("https://twitter.com") ?: ""
                                    val merged = when {
                                        xCookies.isNotBlank() && twitterCookies.isNotBlank() -> "$xCookies; $twitterCookies"
                                        xCookies.isNotBlank() -> xCookies
                                        else -> twitterCookies
                                    }

                                    if (CookieStorageManager.containsAuthTokens(merged)) {
                                        CookieStorageManager.saveXCookies(context, merged)
                                        Toast.makeText(
                                            context,
                                            resources.getString(R.string.x_login_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onLoginSuccess()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Belum ada sesi login (auth_token) yang terdeteksi. Silakan login terlebih dahulu.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAuthDetected) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    contentColor = if (isAuthDetected) {
                                        MaterialTheme.colorScheme.onSecondary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    }
                                ),
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .heightIn(min = 40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.x_login_save_btn),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
            ) {
                // Loading Progress Bar
                if (webProgress > 0f && webProgress < 1f) {
                    LinearProgressIndicator(
                        progress = { webProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }

                // Authentication Status Banner
                AnimatedVisibility(
                    visible = isAuthDetected,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.x_login_auth_detected),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                            )
                        }
                    }
                }

                // Embedded Webview
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false

                                    // Replace '; wv' to appear as standard modern mobile browser
                                    val currentUa = userAgentString
                                    if (currentUa.contains("; wv")) {
                                        userAgentString = currentUa.replace("; wv", "")
                                    }
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        webProgress = (newProgress / 100f).coerceIn(0f, 1f)
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val uri = request?.url ?: return true
                                        return !isAllowedXLoginUri(uri)
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        url: String?
                                    ): Boolean {
                                        return !isAllowedXLoginUrl(url)
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        checkCookiesForAuth(url)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        checkCookiesForAuth(url)
                                    }

                                    private fun checkCookiesForAuth(url: String?) {
                                        val cookies = cookieManager.getCookie(url ?: "https://x.com")
                                        if (CookieStorageManager.containsAuthTokens(cookies)) {
                                            isAuthDetected = true
                                        }
                                    }
                                }

                                loadUrl("https://x.com/i/flow/login")
                                webViewInstance = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                webViewInstance?.stopLoading()
                webViewInstance?.destroy()
                webViewInstance = null
            }
        }
    }
}

private val ALLOWED_AUTH_DOMAINS = listOf(
    "x.com",
    "twitter.com",
    "accounts.google.com",
    "appleid.apple.com",
    "challenges.cloudflare.com"
)

internal fun isAllowedXLoginHost(host: String?): Boolean {
    if (host.isNullOrBlank()) return false
    val lower = host.lowercase()
    if (lower.contains('\\') || lower.contains('@')) return false
    return ALLOWED_AUTH_DOMAINS.any { domain ->
        lower == domain || lower.endsWith(".$domain")
    }
}

internal fun isAllowedXLoginUri(uri: Uri?): Boolean {
    if (uri == null) return false
    val uriString = uri.toString()
    if (uriString.contains('\\')) return false
    if (!"https".equals(uri.scheme, ignoreCase = true)) return false
    if (uri.userInfo != null) return false
    return isAllowedXLoginHost(uri.host)
}

internal fun isAllowedXLoginUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val trimmed = url.trim()
    if (trimmed.contains('\\')) return false
    val (scheme, host) = try {
        val uri = java.net.URI(trimmed)
        if (uri.userInfo != null) return false
        Pair(uri.scheme, uri.host)
    } catch (_: Exception) {
        val s = when {
            trimmed.startsWith("https://", ignoreCase = true) -> "https"
            trimmed.startsWith("http://", ignoreCase = true) -> "http"
            else -> null
        }
        val after = if (s != null) trimmed.substring(s.length + 3) else trimmed
        val authority = after.takeWhile { it != '/' && it != '?' && it != '#' && it != '\\' }
        if (authority.contains('@') || authority.contains('\\')) {
            return false
        }
        val h = authority.takeWhile { it != ':' }
        Pair(s, h)
    }
    if (!"https".equals(scheme, ignoreCase = true)) return false
    if (host.isNullOrBlank() || host.contains('@') || host.contains('\\')) return false
    return isAllowedXLoginHost(host)
}
