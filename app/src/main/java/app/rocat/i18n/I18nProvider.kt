package app.rocat.i18n

import app.rocat.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Reactive, custom i18n provider (no Android resource files). Holds the current
 * [AppLanguage] as a [StateFlow] and derives the resolved [Strings] table from it, so the
 * Compose UI recomposes instantly when the language changes.
 *
 * Compose screens read strings through [LocalStrings] / [stringResource]; the provider is
 * fed into the composition by [I18nApp].
 */
class I18nProvider(private val settings: SettingsRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _language = MutableStateFlow(settings.language)

    /** Current language; setting it persists to [SettingsRepository] and triggers recomposition. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** Resolved translation table for the current language. */
    val strings: StateFlow<Strings> = _language
        .map(Strings::of)
        .stateIn(scope, SharingStarted.Eagerly, Strings.of(_language.value))

    fun setLanguage(language: AppLanguage) {
        settings.language = language
        _language.value = language
    }
}

/** Provided by [I18nApp] so any composable can call [stringResource]. */
val LocalStrings = staticCompositionLocalOf<Strings> { EnglishStrings }

/** Provided by [I18nApp]; used by e.g. the settings language picker. */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

/**
 * Wraps [content] with the active language + strings so all descendants resolve translated
 * strings. Must be called from a composable that collects the provider's flows.
 */
@Composable
fun I18nApp(
    strings: Strings,
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalAppLanguage provides language,
        content = content,
    )
}

/** Resolves a single [StringKey] from the active translation table. */
@Composable
fun stringResource(key: StringKey): String = LocalStrings.current[key]

/** Resolves a [StringKey] and applies [args] through `String.format` (e.g. "%1\$s"). */
@Composable
fun stringResource(key: StringKey, vararg args: Any): String {
    val template = LocalStrings.current[key]
    return if (args.isEmpty()) template else runCatching { String.format(template, *args) }.getOrDefault(template)
}
