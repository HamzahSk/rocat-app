package app.rocat.i18n

/**
 * A resolved translation table for a single language. Instances are immutable maps from
 * [StringKey] to the localized literal; unknown keys fall back to English so the app never
 * shows an empty string when a translation is missing.
 */
open class Strings(
    val language: AppLanguage,
    private val map: Map<StringKey, String>,
) {
    operator fun get(key: StringKey): String = map[key] ?: EnglishStrings[key]

    /** Returns the localized label of [language] itself (e.g. "English" / "Indonesia"). */
    fun languageLabel(language: AppLanguage): String = this[language.labelKey]

    companion object {
        /** Builds a [Strings] for [language], falling back to English when needed. */
        fun of(language: AppLanguage): Strings = when (language) {
            AppLanguage.ENGLISH -> EnglishStrings
            AppLanguage.INDONESIAN -> IndonesianStrings
        }
    }
}

/** English (base) translations. */
object EnglishStrings : Strings(
    language = AppLanguage.ENGLISH,
    map = mapOf(
        StringKey.scripts to "Scripts",
        StringKey.browser to "Browser",
        StringKey.settings to "Settings",

        StringKey.back to "Back",
        StringKey.cancel to "Cancel",
        StringKey.delete to "Delete",
        StringKey.edit to "Edit",
        StringKey.save to "Save",
        StringKey.active to "Active",
        StringKey.inactive to "Inactive",
        StringKey.noDescription to "No description",

        StringKey.addScript to "Add script",
        StringKey.noScriptsTitle to "No scripts installed",
        StringKey.noScriptsBody to "Import a userscript to start scraping from the script canvas.",
        StringKey.scriptNotFound to "Script not found",
        StringKey.version to "version",
        StringKey.othersCategory to "Others",

        StringKey.deleteScriptTitle to "Delete script",
        StringKey.deleteScriptBody to "Are you sure you want to delete \"%1\$s\"?",
        StringKey.description to "Description",
        StringKey.author to "Author",
        StringKey.icon to "Icon",
        StringKey.id to "ID",
        StringKey.matches to "Matches",
        StringKey.source to "Source",
        StringKey.editSource to "Edit source",
        StringKey.script to "Script",

        StringKey.addScriptTitle to "Add Script",
        StringKey.importFromUrl to "Import from URL",
        StringKey.importFromUrlBody to "Point to a raw .js file (GitHub blob links are rewritten automatically).",
        StringKey.scriptUrl to "Script URL",
        StringKey.fetchImport to "Fetch & Import",
        StringKey.pasteSource to "Paste source",
        StringKey.canvasDemo to "Canvas demo",
        StringKey.loadExample to "Load example",
        StringKey.scriptSource to "Script source",
        StringKey.importSource to "Import source",

        StringKey.running to "Running…",
        StringKey.output to "Output",
        StringKey.download to "Download",
        StringKey.downloadVideo to "Download Video",
        StringKey.playInline to "Play Inline",
        StringKey.closePlayer to "Close Player",
        StringKey.imageSaved to "Image saved to the Scrapes folder",
        StringKey.videoSaved to "Video saved to the Scrapes folder",
        StringKey.downloadFailed to "Download failed",

        StringKey.copyJson to "Copy JSON",
        StringKey.jsonCopied to "JSON copied to clipboard",
        StringKey.play to "Play",
        StringKey.pause to "Pause",
        StringKey.downloadAudio to "Download Audio",
        StringKey.audioSaved to "Audio saved to the Scrapes folder",

        StringKey.blankCanvas to "Blank canvas",
        StringKey.blankCanvasBody to "The script did not publish any UI. Canvas-driven scripts define onLaunch() " +
            "and draw with RoCatUI.{addInput,addButton,addGrid,log,...}.",
        StringKey.rerunOnLaunch to "Re-run onLaunch()",
        StringKey.rebuildCanvas to "Rebuild canvas",

        StringKey.addressBar to "Address",
        StringKey.urlPrompt to "Type a URL or search",
        StringKey.go to "Go",
        StringKey.refresh to "Refresh",
        StringKey.stop to "Stop",
        StringKey.forward to "Forward",
        StringKey.moreOptions to "More options",
        StringKey.desktopMode to "Desktop mode",
        StringKey.copyLink to "Copy link",
        StringKey.openInBrowser to "Open in external browser",
        StringKey.linkCopied to "Link copied to clipboard",
        StringKey.clearText to "Clear text",
        StringKey.reload to "Reload",
        StringKey.secureSite to "Secure connection",
        StringKey.insecureSite to "Insecure connection",
        StringKey.closeFullscreen to "Exit fullscreen",

        StringKey.settingsTitle to "Settings",
        StringKey.language to "Language",
        StringKey.languageEnglish to "English",
        StringKey.languageIndonesian to "Indonesian",
        StringKey.storage to "Storage",
        StringKey.storageStatus to "Storage location",
        StringKey.storageConfigured to "Configured",
        StringKey.storageNotConfigured to "Not configured",
        StringKey.changeStorage to "Change storage directory",
        StringKey.chooseStorageFolder to "Choose a folder",
        StringKey.dataManagement to "Data management",
        StringKey.clearCache to "Clear cache",
        StringKey.clearCacheConfirm to "Remove all cached images and temporary files?",
        StringKey.clearCookies to "Clear cookies",
        StringKey.clearCookiesConfirm to "Delete all stored cookies?",
        StringKey.clearHistory to "Clear history",
        StringKey.clearHistoryConfirm to "Delete the full usage history?",
        StringKey.cancelDelete to "Cancel",
        StringKey.done to "Done",
        StringKey.cacheCleared to "Cache cleared",
        StringKey.cookiesCleared to "Cookies cleared",
        StringKey.historyCleared to "History cleared",
        StringKey.storageChanged to "Storage directory changed",
        StringKey.storagePermissionDenied to "Could not persist storage permission",
        StringKey.failure to "Failed",

        StringKey.network to "Network",
        StringKey.userAgent to "User-Agent",
        StringKey.userAgentHint to "Custom User-Agent (leave empty to use the default)",
        StringKey.userAgentBlank to "Default (Chrome 141)",
        StringKey.dnsSelection to "DNS over HTTPS",
        StringKey.dnsSystemDefault to "System default",
        StringKey.dnsCloudflare to "Cloudflare (1.1.1.1)",
        StringKey.dnsGoogle to "Google (8.8.8.8)",
        StringKey.dnsQuad9 to "Quad9 (9.9.9.9)",
        StringKey.dnsCustom to "Custom DoH URL",
        StringKey.customDnsUrl to "Custom DNS URL",
        StringKey.customDnsUrlHint to "https://example.com/dns-query",

        StringKey.setupStorageTitle to "Choose a storage folder",
        StringKey.setupStorageBody to "RoCat saves scrape results to a folder on your device. " +
            "Pick a folder you can easily access (for example your Downloads folder).",
        StringKey.setupStorageButton to "Choose folder",

        StringKey.scrapes to "Scrapes",
        StringKey.scrapeFolderCreated to "Scrape folder created",
    ),
)

/** Indonesian translations. */
object IndonesianStrings : Strings(
    language = AppLanguage.INDONESIAN,
    map = mapOf(
        StringKey.scripts to "Skrip",
        StringKey.browser to "Browser",
        StringKey.settings to "Pengaturan",

        StringKey.back to "Kembali",
        StringKey.cancel to "Batal",
        StringKey.delete to "Hapus",
        StringKey.edit to "Edit",
        StringKey.save to "Simpan",
        StringKey.active to "Aktif",
        StringKey.inactive to "Nonaktif",
        StringKey.noDescription to "Tidak ada deskripsi",

        StringKey.addScript to "Tambah skrip",
        StringKey.noScriptsTitle to "Belum ada skrip terpasang",
        StringKey.noScriptsBody to "Impor userscript untuk mulai scraping lewat kanvas skrip.",
        StringKey.scriptNotFound to "Skrip tidak ditemukan",
        StringKey.version to "versi",
        StringKey.othersCategory to "Lainnya",

        StringKey.deleteScriptTitle to "Hapus skrip",
        StringKey.deleteScriptBody to "Yakin ingin menghapus \"%1\$s\"?",
        StringKey.description to "Deskripsi",
        StringKey.author to "Penulis",
        StringKey.icon to "Ikon",
        StringKey.id to "ID",
        StringKey.matches to "Kecocokan",
        StringKey.source to "Sumber",
        StringKey.editSource to "Edit sumber",
        StringKey.script to "Skrip",

        StringKey.addScriptTitle to "Tambah Skrip",
        StringKey.importFromUrl to "Impor dari URL",
        StringKey.importFromUrlBody to "Tunjuk ke file .js mentah (tautan blob GitHub ditulis ulang otomatis).",
        StringKey.scriptUrl to "URL Skrip",
        StringKey.fetchImport to "Ambil & Impor",
        StringKey.pasteSource to "Tempel sumber",
        StringKey.canvasDemo to "Demo canvas",
        StringKey.loadExample to "Muat contoh",
        StringKey.scriptSource to "Sumber skrip",
        StringKey.importSource to "Impor sumber",

        StringKey.running to "Menjalankan…",
        StringKey.output to "Keluaran",
        StringKey.download to "Unduh",
        StringKey.downloadVideo to "Unduh Video",
        StringKey.playInline to "Putar Inline",
        StringKey.closePlayer to "Tutup Pemutar",
        StringKey.imageSaved to "Gambar berhasil disimpan di folder Scrapes",
        StringKey.videoSaved to "Video berhasil disimpan di folder Scrapes",
        StringKey.downloadFailed to "Unduhan gagal",

        StringKey.copyJson to "Salin JSON",
        StringKey.jsonCopied to "JSON disalin ke clipboard",
        StringKey.play to "Putar",
        StringKey.pause to "Jeda",
        StringKey.downloadAudio to "Unduh Audio",
        StringKey.audioSaved to "Audio berhasil disimpan di folder Scrapes",

        StringKey.blankCanvas to "Kanvas kosong",
        StringKey.blankCanvasBody to "Skrip belum menerbitkan UI apa pun. Skrip berbasis canvas mendefinisikan " +
            "onLaunch() dan menggambar dengan RoCatUI.{addInput,addButton,addGrid,log,...}.",
        StringKey.rerunOnLaunch to "Jalankan ulang onLaunch()",
        StringKey.rebuildCanvas to "Bangun ulang kanvas",

        StringKey.addressBar to "Alamat",
        StringKey.urlPrompt to "Ketik URL atau kata kunci pencarian",
        StringKey.go to "Buka",
        StringKey.refresh to "Muat ulang",
        StringKey.stop to "Berhenti",
        StringKey.forward to "Maju",
        StringKey.moreOptions to "Opsi lainnya",
        StringKey.desktopMode to "Mode desktop",
        StringKey.copyLink to "Salin tautan",
        StringKey.openInBrowser to "Buka di browser eksternal",
        StringKey.linkCopied to "Tautan disalin ke clipboard",
        StringKey.clearText to "Bersihkan teks",
        StringKey.reload to "Muat ulang",
        StringKey.secureSite to "Koneksi aman",
        StringKey.insecureSite to "Koneksi tidak aman",
        StringKey.closeFullscreen to "Keluar layar penuh",

        StringKey.settingsTitle to "Pengaturan",
        StringKey.language to "Bahasa",
        StringKey.languageEnglish to "Inggris",
        StringKey.languageIndonesian to "Indonesia",
        StringKey.storage to "Penyimpanan",
        StringKey.storageStatus to "Lokasi penyimpanan",
        StringKey.storageConfigured to "Terkonfigurasi",
        StringKey.storageNotConfigured to "Belum dikonfigurasi",
        StringKey.changeStorage to "Ubah direktori penyimpanan",
        StringKey.chooseStorageFolder to "Pilih folder",
        StringKey.dataManagement to "Manajemen data",
        StringKey.clearCache to "Hapus cache",
        StringKey.clearCacheConfirm to "Hapus semua gambar yang di-cache dan file sementara?",
        StringKey.clearCookies to "Hapus cookie",
        StringKey.clearCookiesConfirm to "Hapus semua cookie yang tersimpan?",
        StringKey.clearHistory to "Hapus riwayat",
        StringKey.clearHistoryConfirm to "Hapus seluruh riwayat penggunaan?",
        StringKey.cancelDelete to "Batal",
        StringKey.done to "Selesai",
        StringKey.cacheCleared to "Cache dibersihkan",
        StringKey.cookiesCleared to "Cookie dihapus",
        StringKey.historyCleared to "Riwayat dihapus",
        StringKey.storageChanged to "Direktori penyimpanan diubah",
        StringKey.storagePermissionDenied to "Izin penyimpanan tidak dapat dipertahankan",
        StringKey.failure to "Gagal",

        StringKey.network to "Jaringan",
        StringKey.userAgent to "User-Agent",
        StringKey.userAgentHint to "User-Agent kustom (biarkan kosong untuk memakai bawaan)",
        StringKey.userAgentBlank to "Bawaan (Chrome 141)",
        StringKey.dnsSelection to "DNS over HTTPS",
        StringKey.dnsSystemDefault to "Sistem bawaan",
        StringKey.dnsCloudflare to "Cloudflare (1.1.1.1)",
        StringKey.dnsGoogle to "Google (8.8.8.8)",
        StringKey.dnsQuad9 to "Quad9 (9.9.9.9)",
        StringKey.dnsCustom to "URL DoH kustom",
        StringKey.customDnsUrl to "URL DNS kustom",
        StringKey.customDnsUrlHint to "https://example.com/dns-query",

        StringKey.setupStorageTitle to "Pilih folder penyimpanan",
        StringKey.setupStorageBody to "RoCat menyimpan hasil scrape ke sebuah folder di perangkat Anda. " +
            "Pilih folder yang mudah diakses (misalnya folder Downloads Anda).",
        StringKey.setupStorageButton to "Pilih folder",

        StringKey.scrapes to "Scrapes",
        StringKey.scrapeFolderCreated to "Folder scrape dibuat",
    ),
)
