package app.rocat.i18n

/**
 * Supported app languages for the custom i18n layer. The language code doubles as the
 * persistence key in [app.rocat.settings.SettingsRepository] and the label shown in the
 * language picker is itself localized through [Strings].
 */
enum class AppLanguage(val code: String, val labelKey: StringKey) {
    ENGLISH("en", StringKey.languageEnglish),
    INDONESIAN("id", StringKey.languageIndonesian),
}
