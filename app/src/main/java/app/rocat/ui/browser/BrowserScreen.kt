package app.rocat.ui.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.rocat.core.common.util.WebViewUtil
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource

/**
 * Tahap 16.4: a freestyle in-app web browser tab. The user types ANY URL (or a plain
 * search query) into the free address bar and the page is rendered by a real WebView.
 *
 * Because the app's `AndroidCookieJar` is backed by the WebView [android.webkit.CookieManager],
 * every cookie set while browsing here (logins, Cloudflare `cf_clearance`, ...) is
 * automatically shared with the OkHttp stack the scraper scripts run on - so a session
 * solved in the browser is immediately available to script `fetch()` calls.
 *
 * Navigation controls (back / forward / refresh / stop) act directly on the WebView's
 * own history, exactly like a standalone browser.
 */
@Composable
fun BrowserScreen() {
    var urlInput by rememberSaveable { mutableStateOf(DEFAULT_HOME) }
    var currentUrl by rememberSaveable { mutableStateOf(DEFAULT_HOME) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun refreshNavState() {
        webView?.let { view ->
            canGoBack = view.canGoBack()
            canGoForward = view.canGoForward()
        }
    }

    val webViewClient = remember {
        object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress = 10
                refreshNavState()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                url?.let { currentUrl = it }
                view?.let { currentUrl = it.url ?: currentUrl }
                progress = 0
                refreshNavState()
            }
        }
    }

    val chromeClient = remember {
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = if (newProgress in 1 until 100) newProgress else 0
            }
        }
    }

    fun navigate(raw: String) {
        val normalized = normalizeUrl(raw)
        currentUrl = normalized
        urlInput = normalized
        webView?.loadUrl(normalized)
    }

    // System back = go back in browser history when possible, otherwise fall through to
    // the navigation stack handled by RoCatNav.
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    // Always tear the WebView down when the tab leaves the composition (no leaked views).
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            runCatching { webView?.destroy() }
            webView = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Free address bar: type ANY url / search term.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp),
        ) {
            IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(StringKey.back))
            }
            IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(StringKey.forward))
            }
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            navigate(urlInput)
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                label = { Text(stringResource(StringKey.urlPrompt)) },
                leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigate(urlInput) }),
                trailingIcon = {
                    IconButton(onClick = { navigate(urlInput) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(StringKey.go),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            if (progress > 0) {
                IconButton(onClick = { webView?.stopLoading() }) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(StringKey.stop))
                }
            } else {
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(StringKey.refresh))
                }
            }
        }

        if (progress > 0 && progress < 100) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // The browser engine: a real WebView rendered through AndroidView.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).also { view ->
                    WebViewUtil.setDefaultSettings(view)
                    view.webViewClient = webViewClient
                    view.webChromeClient = chromeClient
                    webView = view
                    view.loadUrl(currentUrl)
                }
            },
            update = { },
        )
    }
}

/** Default start page opened when the browser tab is first shown. */
private const val DEFAULT_HOME = "https://www.google.com"

/** Google search used for free-text queries typed into the address bar. */
private const val SEARCH_URL = "https://www.google.com/search?q="

/**
 * Resolves arbitrary address-bar input into a loadable URL, mimicking desktop/mobile
 * browsers: "https://" is injected when missing, "www." domains are prefixed, and
 * anything that is not a URL (no dots, contains spaces, ...) becomes a web search.
 */
private fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return DEFAULT_HOME
    val lower = trimmed.lowercase()
    return when {
        lower.startsWith("http://") || lower.startsWith("https://") -> trimmed
        lower.startsWith("www.") -> "https://$trimmed"
        !trimmed.contains(' ') && trimmed.contains('.') -> "https://$trimmed"
        else -> SEARCH_URL + Uri.encode(trimmed)
    }
}
