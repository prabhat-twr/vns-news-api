package com.banarasbuzz.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Links that should stay in the phone's own apps rather than the in-app browser: maps (the
 * Maps app is far better), the Play Store, and anything that isn't a web page.
 */
fun opensOutsideApp(url: String): Boolean {
    // Plain string parsing: works for any URL (including Hindi characters) and in unit tests.
    val trimmed = url.trim()
    val scheme = trimmed.substringBefore(":", "").lowercase()
    if (scheme != "http" && scheme != "https") return true
    val rest = trimmed.substringAfter("://", "")
    val host = rest.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfter('@').substringBefore(':').lowercase()
    val path = rest.drop(rest.takeWhile { it != '/' && it != '?' && it != '#' }.length)
    if (host.isEmpty()) return true
    return host == "play.google.com" || host == "maps.app.goo.gl" || host == "maps.apple.com" ||
        host.startsWith("maps.google.") || ((host == "google.com" || host.endsWith(".google.com")) && path.startsWith("/maps"))
}

fun openExternally(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** In-app browser for news stories and other web links, so reading never leaves the app. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var title by remember(url) { mutableStateOf("") }
    var currentUrl by remember(url) { mutableStateOf(url) }
    var progress by remember(url) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val webView = remember(url) {
        WebView(context).apply {
            settings.javaScriptEnabled = true // news sites need it to render
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val target = request.url.toString()
                    // Frames inside the page (ads, embeds, about:blank) always load here.
                    if (!request.isForMainFrame) return false
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme == "about" || scheme == "data" || scheme == "blob" || scheme == "javascript") return false
                    // Web pages stay here; phone numbers, mail, app links and maps go to their apps.
                    if (opensOutsideApp(target)) {
                        openExternally(context, target)
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView, pageUrl: String, favicon: Bitmap?) {
                    currentUrl = pageUrl
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }

                override fun onReceivedTitle(view: WebView, pageTitle: String?) {
                    title = pageTitle.orEmpty()
                }
            }
            loadUrl(url)
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }
    // Back goes back within the page first, then closes the browser.
    BackHandler { if (webView.canGoBack()) webView.goBack() else onClose() }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(BB.Cream)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "बंद करें", tint = BB.Ink)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title.ifBlank { "लोड हो रहा है…" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BB.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Uri.parse(currentUrl).host?.removePrefix("www.").orEmpty(),
                    fontSize = 12.sp,
                    color = BB.Muted,
                    maxLines = 1,
                )
            }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, listOf(title, currentUrl).filter { it.isNotBlank() }.joinToString("\n"))
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            }) {
                Icon(Icons.Filled.Share, contentDescription = "शेयर", tint = BB.Ink)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "और विकल्प", tint = BB.Ink)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("रीफ़्रेश करें") },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = { menu = false; webView.reload() },
                    )
                    DropdownMenuItem(
                        text = { Text("ब्राउज़र में खोलें") },
                        onClick = { menu = false; openExternally(context, currentUrl) },
                    )
                }
            }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = BB.Saffron,
                trackColor = BB.Line,
            )
        } else {
            Box(Modifier.fillMaxWidth().height(2.dp).background(BB.Line))
        }
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
    }
}
