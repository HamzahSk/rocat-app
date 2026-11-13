package app.rocat.ui.browser

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import app.rocat.core.common.util.WebViewUtil
import app.rocat.core.common.injekt.Injekt
import app.rocat.di.AppViewModelFactory
import app.rocat.domain.script.GetScripts
import app.rocat.downloads.DownloadRepository
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource
import app.rocat.scripting.api.model.Script
import org.json.JSONObject

/** Logcat tag for JavaScript console messages piped from the page (Tahap 26.4). */
private const val TAG_JS = "WebViewJS-Console"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Tahap 25: the modern in-app browser tab. A free address bar accepts ANY URL (or a
 * plain search query), and the page is rendered by a real WebView configured for heavy
 * JavaScript sites (SPAs, local storage, mixed content).
 *
 * Because the app's `AndroidCookieJar` is backed by the WebView [android.webkit.CookieManager],
 * every cookie set while browsing here (logins, Cloudflare `cf_clearance`, ...) is
 * automatically shared with the OkHttp stack the scraper scripts run on - so a session
 * solved in the browser is immediately available to script `fetch()` calls.
 *
 * The top bar follows Material 3: a pill-shaped address bar with SSL lock / clear-text /
 * Go, back/forward, a refresh↔stop button and a three-dot overflow menu (Desktop mode,
 * reload, copy link, open in external browser). Pull-to-refresh reloads the current
 * page, and a thin [LinearProgressIndicator] tracks [WebChromeClient] load progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    initialUrl: String? = null,
    onOpenScripts: () -> Unit = {},
    onInstallScript: (String) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
) {
    val viewModel: BrowserViewModel = viewModel(factory = AppViewModelFactory)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val getScripts: GetScripts = remember { Injekt.get() }
    val downloadRepository: DownloadRepository = remember { Injekt.get() }
    val scripts by remember(getScripts) { getScripts.subscribe() }.collectAsState(initial = emptyList())
    val currentScripts = rememberUpdatedState(scripts)
    val currentInstallCallback = rememberUpdatedState(onInstallScript)

    var webView by remember { mutableStateOf<WebView?>(null) }
    // Tahap 27.4: bumped when the renderer process dies (blank-white-page symptom) so
    // AndroidView rebuilds a fresh WebView and reloads the current page.
    var webViewEpoch by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    // Tahap 26.2 (DOCS_WEBVIEW.md §"WebChromeClient"): the page handed a custom
    // (fullscreen HTML5 video) view to the host app via onShowCustomView. Rendered as
    // a full-screen overlay; null means nothing is fullscreen.
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    // Guards against re-loading the start page right after the factory has already
    // loaded it (the AndroidView factory runs before the LaunchedEffect below).
    var lastAppliedNonce by remember { mutableStateOf(-1) }

    // Captured in the composable so the non-composable callbacks below can use them.
    val linkCopiedMessage = stringResource(StringKey.linkCopied)
    val sslDialogTitle = stringResource(StringKey.insecureConnectionTitle)
    val sslDialogMessage = stringResource(StringKey.insecureConnectionMessage)
    val sslProceedLabel = stringResource(StringKey.proceed)
    val sslCancelLabel = stringResource(StringKey.cancel)
    val downloadTitle = stringResource(StringKey.downloadFile)
    val downloadMessage = stringResource(StringKey.downloadConfirm)

    fun navState(): BrowserViewModel.NavigationState =
        BrowserViewModel.NavigationState(
            canGoBack = webView?.canGoBack() ?: false,
            canGoForward = webView?.canGoForward() ?: false,
        )

    val webViewClient = remember(viewModel) {
        object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                viewModel.onPageStarted(url, navState())
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                viewModel.onPageFinished(url, navState())
                isRefreshing = false
                // Tahap 27.4: flag pages that "finish" without a title — a strong
                // signal the SPA did not hydrate and the render came out blank, so the
                // emulator CI job can see it from `adb logcat -s WebViewJS`.
                if (view?.title.isNullOrBlank()) {
                    Log.w(TAG_JS, "onPageFinished with blank title for $url — page may be blank / JS not hydrated")
                }
                if (view != null && !url.isNullOrBlank()) {
                    currentScripts.value
                        .asSequence()
                        .filter { it.enabled && it.matches.any { pattern -> urlMatches(pattern, url) } }
                        .forEach { script ->
                            view.evaluateJavascript(userscriptPayload(script), null)
                        }
                }
            }

            // Tahap 28.2 (sweb-master `shouldOverrideUrlLoading`): `intent://` links
            // (Play Store / app deep links) fall back to their `browser_fallback_url`
            // so the in-app browser never dead-ends on a scheme it cannot load.
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && isUserScriptUrl(url)) {
                    currentInstallCallback.value(url)
                    return true
                }
                if (url?.startsWith("intent://") == true) {
                    val fallbackMarker = ";S.browser_fallback_url="
                    val start = url.indexOf(fallbackMarker)
                    if (start != -1) {
                        val valueStart = start + fallbackMarker.length
                        val end = url.indexOf(';', valueStart)
                        if (end != -1 && end != valueStart) {
                            val fallback = Uri.decode(url.substring(valueStart, end))
                            view?.loadUrl(fallback)
                            return true
                        }
                    }
                }
                return false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                shouldOverrideUrlLoading(view, request?.url?.toString())

            // Tahap 28.2 (sweb-master `onReceivedSslError`): by default WebView CANCELS
            // the load on any SSL error → blank white page. Like the reference browser,
            // surface a dialog so the user can explicitly proceed (mirroring
            // sweb-master's "Proceed" / "Cancel" flow).
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                if (handler == null || error == null) return
                val errorDescription = error.primaryError.let { code ->
                    when (code) {
                        android.net.http.SslError.SSL_NOTYETVALID -> "Not yet valid"
                        android.net.http.SslError.SSL_EXPIRED -> "Expired"
                        android.net.http.SslError.SSL_IDMISMATCH -> "Hostname mismatch"
                        android.net.http.SslError.SSL_UNTRUSTED -> "Untrusted CA"
                        android.net.http.SslError.SSL_DATE_INVALID -> "Invalid date"
                        else -> "Error $code"
                    }
                }
                AlertDialog.Builder(context)
                    .setTitle(sslDialogTitle)
                    .setMessage(String.format(sslDialogMessage, error.url, errorDescription))
                    .setPositiveButton(sslProceedLabel) { _, _ -> handler.proceed() }
                    .setNegativeButton(sslCancelLabel) { _, _ -> handler.cancel() }
                    .show()
            }

            // Tahap 27.4: the classic "blank white screen" root cause on modern sites —
            // the renderer process crashed or was killed. Destroy the dead WebView and
            // bump the epoch so AndroidView rebuilds it fresh and reloads the URL.
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                val crashed = detail?.didCrash() != false
                if (crashed) {
                    Log.e(TAG_JS, "renderer process gone (didCrash=$crashed) — rebuilding WebView")
                    isRefreshing = false
                    runCatching { view?.destroy() }
                    webViewEpoch += 1
                }
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                viewModel.refreshNavState(navState())
                isRefreshing = false
            }
        }
    }

    val chromeClient = remember(viewModel) {
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                viewModel.onProgressChanged(newProgress)
                if (newProgress >= 100) isRefreshing = false
            }

            // Tahap 26.4: pipe every JS console.log / warning / error straight to Logcat
            // under a dedicated tag so a failing page (SyntaxError, cross-origin, CSP,
            // ...) can be diagnosed from `adb logcat -s WebViewJS`.
            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                if (message == null) return false
                val detail =
                    "level=${message.messageLevel()} ${message.sourceId()}:${message.lineNumber()}: ${message.message()}"
                when (message.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG_JS, detail)
                    ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG_JS, detail)
                    ConsoleMessage.MessageLevel.DEBUG -> Log.d(TAG_JS, detail)
                    else -> Log.i(TAG_JS, detail)
                }
                return true
            }

            // Tahap 27.4: log the page title to the WebViewJS tag — positive evidence
            // in the emulator CI job that the SPA actually hydrated and rendered.
            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) {
                    Log.i(TAG_JS, "page title: $title")
                    viewModel.onTitleChanged(title)
                }
            }

            // Tahap 26.2 (DOCS_WEBVIEW.md §"WebChromeClient"): grant permission
            // requests (geolocation / media / protected media) so modern sites that
            // prompt at runtime never leave the page stuck waiting for an answer.
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            // Tahap 26.2 (DOCS_WEBVIEW.md §"WebChromeClient"): fullscreen support for
            // HTML5 video — the page hands us a custom view to show edge-to-edge.
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (fullscreenView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                customViewCallback = callback
            }

            override fun onHideCustomView() {
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                fullscreenView = null
            }
        }
    }

    fun closeFullscreen() {
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        fullscreenView = null
    }

    // Home searches, deep links and script-triggered navigation load without extra taps.
    LaunchedEffect(initialUrl) {
        initialUrl?.let(viewModel::acceptInitialUrl)
    }

    // Navigation requests (address bar / search) arrive through the load nonce.
    LaunchedEffect(state.loadNonce) {
        if (lastAppliedNonce != state.loadNonce) {
            lastAppliedNonce = state.loadNonce
            webView?.loadUrl(state.currentUrl)
        }
    }

    // Commands that must touch the live WebView (desktop-mode switch, reload).
    LaunchedEffect(Unit) {
        viewModel.commands.collect { command ->
            val view = webView ?: return@collect
            when (command) {
                is BrowserCommand.Reload -> view.reload()
                is BrowserCommand.SetDesktopMode -> {
                    WebViewUtil.applyDesktopMode(view, command.enabled)
                    view.reload()
                }
            }
        }
    }

    // System back = go back in browser history when possible, otherwise fall through to
    // the navigation stack handled by RoCatNav.
    BackHandler(enabled = state.canGoBack) { webView?.goBack() }

    // Tahap 26.2: while a fullscreen (HTML5 video) view is on screen, Back exits it
    // first (this handler is composed after the history one, so it wins while enabled).
    BackHandler(enabled = fullscreenView != null) { closeFullscreen() }

    // Always tear the WebView down when the tab leaves the composition (no leaked views).
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            runCatching { webView?.destroy() }
            webView = null
        }
    }

    val pullRefreshState = rememberPullToRefreshState()
    val isSecure = Uri.parse(state.currentUrl).scheme == "https"

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            BrowserTabStrip(
                tabs = state.tabs,
                activeTabId = state.activeTabId,
                onSelect = viewModel::selectTab,
                onClose = viewModel::closeTab,
                onNew = viewModel::newTab,
            )
            // Material 3 top bar: back / forward, pill address bar, refresh↔stop, overflow.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            ) {
                IconButton(onClick = { webView?.goBack() }, enabled = state.canGoBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(StringKey.back),
                    )
                }
                IconButton(onClick = { webView?.goForward() }, enabled = state.canGoForward) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(StringKey.forward),
                    )
                }
                AddressBar(
                    value = state.urlInput,
                    isSecure = isSecure,
                    onValueChange = viewModel::onUrlInputChange,
                    onGo = viewModel::submitUrl,
                    onClear = viewModel::clearUrlInput,
                    modifier = Modifier.weight(1f),
                )
                if (state.isLoading) {
                    IconButton(onClick = { webView?.stopLoading() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(StringKey.stop))
                    }
                } else {
                    IconButton(onClick = viewModel::reload) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(StringKey.refresh),
                        )
                    }
                }
                IconButton(onClick = { showOptions = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(StringKey.moreOptions),
                    )
                }
            }

            // Thin, animated progress bar wired straight to WebChromeClient.onProgressChanged.
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    webView?.reload()
                },
                modifier = Modifier.fillMaxSize(),
                state = pullRefreshState,
            ) {
                // The browser engine: a real WebView rendered through AndroidView.
                // `key(webViewEpoch)` lets onRenderProcessGone rebuild the WebView from
                // scratch (fresh AndroidView + factory) instead of a blank white screen.
                key(webViewEpoch) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // AndroidView normally supplies the Activity context. Keep that
                            // guarantee explicit so WebView is never backed by Application.
                            val activityContext = ctx.findActivity() ?: context.findActivity()
                            checkNotNull(activityContext) {
                                "Browser WebView requires an Activity context"
                            }
                            WebView(activityContext).also { view ->
                                view.layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                // Tahap 32: use the installed WebView's native mobile UA,
                                // matching sweb-master and avoiding a synthetic Chrome
                                // version that can trip anti-bot UA consistency checks.
                                WebViewUtil.setDefaultSettings(view)
                                if (state.desktopMode) {
                                    WebViewUtil.applyDesktopMode(view, true)
                                }
                                view.webViewClient = webViewClient
                                view.webChromeClient = chromeClient
                                // Route every page download through the app queue so the
                                // Downloads tab can show progress/speed and retain the SAF URI.
                                view.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                                    val guessed = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                                    val size = if (contentLength > 0) " (${contentLength / 1024} KB)" else ""
                                    AlertDialog.Builder(context)
                                        .setTitle(downloadTitle)
                                        .setMessage(String.format(downloadMessage, guessed + size))
                                        .setNegativeButton(sslCancelLabel, null)
                                        .setPositiveButton(downloadTitle) { _, _ ->
                                            val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
                                            val headers = buildMap {
                                                put("User-Agent", userAgent ?: view.settings.userAgentString)
                                                if (cookie.isNotBlank()) put("Cookie", cookie)
                                                view.url?.let { put("Referer", it) }
                                            }
                                            downloadRepository.enqueue(
                                                url = url,
                                                fileName = guessed,
                                                mimeType = mimeType ?: "application/octet-stream",
                                                headers = headers,
                                            )
                                            Toast.makeText(context, "Download added", Toast.LENGTH_SHORT).show()
                                        }
                                        .show()
                                }
                                webView = view
                                lastAppliedNonce = state.loadNonce
                                viewModel.refreshNavState(navState())
                                view.loadUrl(state.currentUrl)
                            }
                        },
                        update = { },
                    )
                }
            }
        }

        // Tahap 26.2: fullscreen HTML5 video overlay — the view the page requested via
        // WebChromeClient.onShowCustomView, shown edge-to-edge with a close button.
        fullscreenView?.let { video ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                AndroidView(factory = { video }, modifier = Modifier.fillMaxSize())
                IconButton(
                    onClick = ::closeFullscreen,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(StringKey.closeFullscreen),
                        tint = Color.White,
                    )
                }
            }
        }
    }

    if (showOptions) {
        BrowserOptionsSheet(
            desktopMode = state.desktopMode,
            matchingScripts = scripts.count { script ->
                script.enabled && script.matches.any { urlMatches(it, state.currentUrl) }
            },
            onDismiss = { showOptions = false },
            onDesktopModeChange = viewModel::setDesktopMode,
            onReload = {
                showOptions = false
                viewModel.reload()
            },
            onCopyLink = {
                showOptions = false
                clipboard.setText(AnnotatedString(state.currentUrl))
                Toast.makeText(context, linkCopiedMessage, Toast.LENGTH_SHORT).show()
            },
            onOpenExternal = {
                showOptions = false
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(state.currentUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onOpenScripts = {
                showOptions = false
                onOpenScripts()
            },
            onOpenDownloads = {
                showOptions = false
                onOpenDownloads()
            },
        )
    }
}

@Composable
private fun BrowserTabStrip(
    tabs: List<BrowserViewModel.BrowserTab>,
    activeTabId: Int,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onNew: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
    ) {
        LazyRow(modifier = Modifier.weight(1f)) {
            items(tabs, key = { it.id }) { tab ->
                Surface(
                    onClick = { onSelect(tab.id) },
                    color = if (tab.id == activeTabId) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.widthIn(min = 112.dp, max = 190.dp).padding(end = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Text(
                            tab.title,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onClose(tab.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close tab", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
        IconButton(onClick = onNew) {
            Icon(Icons.Filled.Add, contentDescription = "New tab")
        }
    }
}

/**
 * The pill-shaped Material 3 address bar: SSL lock indicator, the editable URL/search
 * text, a clear-text button (when non-empty) and a Go action. Enter on a hardware
 * keyboard and the IME "Go" key both submit.
 */
@Composable
private fun AddressBar(
    value: String,
    isSecure: Boolean,
    onValueChange: (String) -> Unit,
    onGo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(
                imageVector = if (isSecure) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = stringResource(
                    if (isSecure) StringKey.secureSite else StringKey.insecureSite,
                ),
                tint = if (isSecure) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            onGo()
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(StringKey.clearText),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            IconButton(onClick = onGo, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(StringKey.go),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Elegant three-dot overflow menu (Material 3 bottom sheet): Desktop-mode switch,
 * reload, copy link and open-in-external-browser actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserOptionsSheet(
    desktopMode: Boolean,
    matchingScripts: Int,
    onDismiss: () -> Unit,
    onDesktopModeChange: (Boolean) -> Unit,
    onReload: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenExternal: () -> Unit,
    onOpenScripts: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = stringResource(StringKey.moreOptions),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Filled.Extension, contentDescription = null) },
                headlineContent = { Text("Scripts on this page") },
                supportingContent = { Text("$matchingScripts enabled and matching") },
                modifier = Modifier.clickable(onClick = onOpenScripts),
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                headlineContent = { Text("Downloads") },
                supportingContent = { Text("View progress and saved files") },
                modifier = Modifier.clickable(onClick = onOpenDownloads),
            )
            ListItem(
                leadingContent = {
                    Icon(
                        Icons.Filled.DesktopWindows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                headlineContent = { Text(stringResource(StringKey.desktopMode)) },
                trailingContent = {
                    Switch(
                        checked = desktopMode,
                        onCheckedChange = onDesktopModeChange,
                    )
                },
                modifier = Modifier.clickable { onDesktopModeChange(!desktopMode) },
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                headlineContent = { Text(stringResource(StringKey.reload)) },
                modifier = Modifier.clickable(onClick = onReload),
            )
            ListItem(
                leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                headlineContent = { Text(stringResource(StringKey.copyLink)) },
                modifier = Modifier.clickable(onClick = onCopyLink),
            )
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                headlineContent = { Text(stringResource(StringKey.openInBrowser)) },
                modifier = Modifier.clickable(onClick = onOpenExternal),
            )
        }
    }
}

private fun isUserScriptUrl(url: String): Boolean {
    val path = Uri.parse(url).lastPathSegment.orEmpty().lowercase()
    return path.endsWith(".user.js") || path.endsWith(".userscript.js")
}

/** Tampermonkey-style wildcard matching for common @match values. */
private fun urlMatches(pattern: String, url: String): Boolean {
    val normalized = pattern.trim()
    if (normalized.isBlank()) return false
    if (normalized == "<all_urls>" || normalized == "*") return true
    if (!normalized.contains("://") && !normalized.contains('/') && !normalized.contains('*')) {
        val host = Uri.parse(url).host.orEmpty()
        return host.equals(normalized, ignoreCase = true) ||
            host.endsWith(".${normalized.removePrefix(".")}", ignoreCase = true)
    }
    val regex = buildString {
        append('^')
        normalized.forEach { char ->
            when (char) {
                '*' -> append(".*")
                '.', '?', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' -> {
                    append('\\')
                    append(char)
                }
                else -> append(char)
            }
        }
        append('$')
    }
    return runCatching { Regex(regex, RegexOption.IGNORE_CASE).matches(url) }.getOrDefault(false)
}

/** Injects a small, page-local userscript API without exposing Android objects to websites. */
private fun userscriptPayload(script: Script): String {
    val id = JSONObject.quote(script.id)
    val name = JSONObject.quote(script.name)
    val version = JSONObject.quote(script.version)
    return """
        (function() {
          const __rocatNamespace = $id;
          const GM_info = { script: { name: $name, version: $version } };
          const GM_log = (...args) => console.log('[RoCat]', ...args);
          const GM_addStyle = (css) => {
            const style = document.createElement('style');
            style.textContent = String(css);
            (document.head || document.documentElement).appendChild(style);
            return style;
          };
          const GM_openInTab = (url) => window.open(String(url), '_blank');
          const GM_setValue = (key, value) => localStorage.setItem('__rocat_' + __rocatNamespace + '_' + key, JSON.stringify(value));
          const GM_getValue = (key, fallback) => {
            const value = localStorage.getItem('__rocat_' + __rocatNamespace + '_' + key);
            if (value === null) return fallback;
            try { return JSON.parse(value); } catch (_) { return fallback; }
          };
          const GM_deleteValue = (key) => localStorage.removeItem('__rocat_' + __rocatNamespace + '_' + key);
          const unsafeWindow = window;
          try {
            ${script.source}
          } catch (error) {
            console.error('RoCat script ' + $name + ' failed', error);
          }
        })();
    """.trimIndent()
}
