package app.rocat.i18n

/**
 * Every user-visible string in the app, keyed once here and resolved at runtime by the
 * custom i18n layer (see [Strings]). New UI text should be added here first, then given
 * a translation in both [EnglishStrings] and [IndonesianStrings] so the UI never has to
 * hard-code literals.
 */
enum class StringKey(val key: String) {
    // Navigation
    scripts("nav_scripts"),
    browser("nav_browser"),
    settings("nav_settings"),

    // Common
    back("back"),
    cancel("cancel"),
    delete("delete"),
    edit("edit"),
    save("save"),
    active("active"),
    inactive("inactive"),
    noDescription("no_description"),

    // Scripts screen
    addScript("add_script"),
    noScriptsTitle("no_scripts_title"),
    noScriptsBody("no_scripts_body"),
    scriptNotFound("script_not_found"),
    version("version"),
    othersCategory("others_category"),

    // Detail screen
    deleteScriptTitle("delete_script_title"),
    deleteScriptBody("delete_script_body"),
    description("description"),
    author("author"),
    icon("icon"),
    id("id"),
    matches("matches"),
    source("source"),
    editSource("edit_source"),
    script("script"),

    // Import screen
    addScriptTitle("add_script_title"),
    importFromUrl("import_from_url"),
    importFromUrlBody("import_from_url_body"),
    scriptUrl("script_url"),
    fetchImport("fetch_import"),
    pasteSource("paste_source"),
    canvasDemo("canvas_demo"),
    loadExample("load_example"),
    scriptSource("script_source"),
    importSource("import_source"),

    // Canvas screen
    blankCanvas("blank_canvas"),
    blankCanvasBody("blank_canvas_body"),
    rerunOnLaunch("rerun_on_launch"),
    rebuildCanvas("rebuild_canvas"),
    running("running"),
    output("output"),
    options("options"),

    // Script settings (Tahap 35)
    scriptSettings("script_settings"),
    noSettingsDeclared("no_settings_declared"),
    settingsSummary("settings_summary"),
    settingsSummaryBody("settings_summary_body"),
    resetToDefault("reset_to_default"),
    exportSettings("export_settings"),
    exportSettingsBody("export_settings_body"),
    importSettings("import_settings"),
    importSettingsBody("import_settings_body"),
    settingsReset("settings_reset"),
    settingsImported("settings_imported"),
    settingsImportFailed("settings_import_failed"),

    // Media previews (Tahap 18)
    download("download"),
    downloadVideo("download_video"),
    playInline("play_inline"),
    closePlayer("close_player"),
    imageSaved("image_saved"),
    videoSaved("video_saved"),
    downloadFailed("download_failed"),

    // UI templates (Tahap 22)
    copyJson("copy_json"),
    jsonCopied("json_copied"),
    play("play"),
    pause("pause"),
    downloadAudio("download_audio"),
    audioSaved("audio_saved"),

    // Browser screen
    addressBar("address_bar"),
    urlPrompt("url_prompt"),
    go("go"),
    refresh("refresh"),
    stop("stop"),
    forward("forward"),
    // Tahap 25: modern in-app browser
    moreOptions("more_options"),
    desktopMode("desktop_mode"),
    copyLink("copy_link"),
    openInBrowser("open_in_browser"),
    linkCopied("link_copied"),
    clearText("clear_text"),
    reload("reload"),
    secureSite("secure_site"),
    insecureSite("insecure_site"),
    // Tahap 26.2: fullscreen HTML5 video (WebChromeClient.onShowCustomView)
    closeFullscreen("close_fullscreen"),
    // Tahap 28.2: SSL error dialog (mirrors sweb-master WebViewClient.onReceivedSslError)
    insecureConnectionTitle("insecure_connection_title"),
    insecureConnectionMessage("insecure_connection_message"),
    proceed("proceed"),

    // Settings screen
    settingsTitle("settings_title"),
    language("language"),
    languageEnglish("language_english"),
    languageIndonesian("language_indonesian"),
    storage("storage"),
    storageStatus("storage_status"),
    storageConfigured("storage_configured"),
    storageNotConfigured("storage_not_configured"),
    changeStorage("change_storage"),
    chooseStorageFolder("choose_storage_folder"),
    dataManagement("data_management"),
    clearCache("clear_cache"),
    clearCacheConfirm("clear_cache_confirm"),
    clearCookies("clear_cookies"),
    clearCookiesConfirm("clear_cookies_confirm"),
    clearHistory("clear_history"),
    clearHistoryConfirm("clear_history_confirm"),
    cancelDelete("cancel_delete"),
    done("done"),
    cacheCleared("cache_cleared"),
    cookiesCleared("cookies_cleared"),
    historyCleared("history_cleared"),
    storageChanged("storage_changed"),
    storagePermissionDenied("storage_permission_denied"),
    failure("failure"),
    appearance("appearance"),
    themeSystem("theme_system"),
    themeLight("theme_light"),
    themeDark("theme_dark"),
    paste("paste"),
    chooseFile("choose_file"),
    undo("undo"),
    redo("redo"),
    format("format"),
    downloadFile("download_file"),
    downloadConfirm("download_confirm"),
    developerOptions("developer_options"),
    webViewDebugging("webview_debugging"),
    webViewDebuggingBody("webview_debugging_body"),

    // Network settings (Tahap 20)
    network("network"),
    userAgent("user_agent"),
    userAgentHint("user_agent_hint"),
    userAgentBlank("user_agent_blank"),
    dnsSelection("dns_selection"),
    dnsSystemDefault("dns_system_default"),
    dnsCloudflare("dns_cloudflare"),
    dnsGoogle("dns_google"),
    dnsQuad9("dns_quad9"),
    dnsCustom("dns_custom"),
    customDnsUrl("custom_dns_url"),
    customDnsUrlHint("custom_dns_url_hint"),

    // Storage setup (first launch)
    setupStorageTitle("setup_storage_title"),
    setupStorageBody("setup_storage_body"),
    setupStorageButton("setup_storage_button"),

    // Scrapes
    scrapes("scrapes"),
    scrapeFolderCreated("scrape_folder_created"),
}
