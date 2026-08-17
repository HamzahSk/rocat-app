package app.rocat.ui.browser

import android.net.Uri
import app.rocat.core.common.injekt.Injekt
import app.rocat.core.viewmodel.StateViewModel
import app.rocat.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

/** A command for the (UI-layer owned) WebView instance, decoupled from [UiState]. */
sealed interface BrowserCommand {
    /** Reloads the current page. */
    data object Reload : BrowserCommand

    /** Applies (or reverts) the desktop User-Agent, then reloads. */
    data class SetDesktopMode(val enabled: Boolean) : BrowserCommand
}

/**
 * Backs the in-app browser (Tahap 25). Holds every bit of UI state — the address bar
 * text, the current URL, navigation availability, load progress and the Desktop-mode
 * flag (persisted via [SettingsRepository]) — while the actual [android.webkit.WebView]
 * stays in the Compose layer. Browser actions that must touch the live WebView are
 * emitted as [BrowserCommand]s through [commands] so the screen applies them directly.
 *
 * The WebView itself is main-thread bound, so [android.webkit.WebViewClient] callbacks
 * can safely push their values straight into the [StateFlow] behind this state.
 */
class BrowserViewModel(
    private val settings: SettingsRepository = Injekt.get(),
) : StateViewModel<BrowserViewModel.UiState>(
    UiState(desktopMode = settings.desktopMode),
) {

    data class UiState(
        val urlInput: String = DEFAULT_HOME,
        val currentUrl: String = DEFAULT_HOME,
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val progress: Int = 0,
        val desktopMode: Boolean = false,
        val isLoading: Boolean = false,
        /** Incremented on every navigation request so the UI re-loads even the same URL. */
        val loadNonce: Int = 0,
    )

    private val _commands = MutableSharedFlow<BrowserCommand>(extraBufferCapacity = 4)
    val commands: SharedFlow<BrowserCommand> = _commands.asSharedFlow()

    // Tahap 27.2: guard so a deep-link URL (app.rocat.EXTRA_URL) is only applied once
    // per ViewModel instance — tab switches back to the browser don't re-trigger it.
    private var initialUrlConsumed = false

    /** Applies a deep-link URL exactly once, if the instance hasn't handled one yet. */
    fun acceptInitialUrl(url: String) {
        if (initialUrlConsumed) return
        initialUrlConsumed = true
        navigateTo(url)
    }

    /** Programmatically loads [url] (deep links, scripts, ...) like an address-bar submit. */
    fun navigateTo(url: String) {
        val normalized = normalizeUrl(url)
        mutableState.update {
            it.copy(
                urlInput = normalized,
                currentUrl = normalized,
                loadNonce = it.loadNonce + 1,
            )
        }
    }

    fun onUrlInputChange(value: String) {
        mutableState.update { it.copy(urlInput = value) }
    }

    /** Normalizes the address-bar input and requests a navigation. */
    fun submitUrl() {
        val normalized = normalizeUrl(mutableState.value.urlInput)
        mutableState.update {
            it.copy(
                urlInput = normalized,
                currentUrl = normalized,
                loadNonce = it.loadNonce + 1,
            )
        }
    }

    /** Clears the address bar so the user can type a fresh URL. */
    fun clearUrlInput() {
        mutableState.update { it.copy(urlInput = "") }
    }

    /** Callback from [android.webkit.WebViewClient.onPageStarted]. */
    fun onPageStarted(url: String?, navState: NavigationState) {
        mutableState.update {
            it.copy(
                progress = 10,
                isLoading = true,
                canGoBack = navState.canGoBack,
                canGoForward = navState.canGoForward,
            )
        }
    }

    /** Callback from [android.webkit.WebChromeClient.onProgressChanged]. */
    fun onProgressChanged(newProgress: Int) {
        mutableState.update {
            it.copy(
                progress = if (newProgress in 1 until 100) newProgress else 0,
                isLoading = newProgress in 1 until 100,
            )
        }
    }

    /** Callback from [android.webkit.WebViewClient.onPageFinished]. */
    fun onPageFinished(url: String?, navState: NavigationState) {
        mutableState.update {
            it.copy(
                currentUrl = url?.takeIf(String::isNotBlank) ?: it.currentUrl,
                progress = 0,
                isLoading = false,
                canGoBack = navState.canGoBack,
                canGoForward = navState.canGoForward,
            )
        }
    }

    /** Callback from [android.webkit.WebViewClient.onReceivedError] / nav-state queries. */
    fun refreshNavState(navState: NavigationState) {
        mutableState.update {
            it.copy(canGoBack = navState.canGoBack, canGoForward = navState.canGoForward)
        }
    }

    /** Persists + toggles Desktop mode and asks the screen to apply + reload. */
    fun setDesktopMode(enabled: Boolean) {
        if (mutableState.value.desktopMode == enabled) return
        settings.desktopMode = enabled
        mutableState.update { it.copy(desktopMode = enabled) }
        _commands.tryEmit(BrowserCommand.SetDesktopMode(enabled))
    }

    /** Requests a plain page reload. */
    fun reload() {
        _commands.tryEmit(BrowserCommand.Reload)
    }

    /** A snapshot of the WebView's navigation availability, passed in by the screen. */
    data class NavigationState(val canGoBack: Boolean, val canGoForward: Boolean)
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