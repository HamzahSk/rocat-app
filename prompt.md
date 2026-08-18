Prompt Fase 35: Sistem Pengaturan Skrip Fleksibel & Peningkatan UI Canvas

Role & Objective

Kamu adalah Senior Android Engineer dan UI/UX Designer untuk aplikasi RoCat. Kita memasuki Tahap 35: Sistem Pengaturan Skrip yang Fleksibel & Peningkatan UI Canvas.

Berdasarkan hasil pengujian dan umpan balik dari Fase 34, ditemukan beberapa area yang perlu ditingkatkan:

1. Pengaturan Skrip Terbatas — Saat ini skrip tidak memiliki sistem konfigurasi yang terstruktur. Pengguna harus mengedit kode untuk mengubah parameter seperti username, password, atau preferensi lainnya.
2. UI Canvas Kurang Fleksibel — Komponen UI di Canvas terbatas pada layout vertikal. Tidak ada opsi untuk menata tombol secara horizontal, membuat grup komponen, atau menggunakan layout yang lebih kompleks.
3. Input Terbatas — Hanya ada addInput untuk teks biasa. Tidak ada dukungan untuk password, checkbox, toggle, dropdown, number picker, atau input dengan autocomplete dari riwayat.

Tujuan Utama

1. Sistem Pengaturan Skrip (Script Settings)

Buat sistem konfigurasi terstruktur yang memungkinkan skrip mendefinisikan pengaturan yang dapat diubah pengguna melalui UI tanpa mengedit kode.

1.1 Struktur Metadata Pengaturan

Skrip dapat mendefinisikan blok @settings di metadata:

```javascript
// ==UserScript==
// @name         Sfile Scraper
// @version      2.0.0
// @description  Scraper untuk sfile.co dengan pengaturan fleksibel
// @author       RoCat AI
// @category     Download
// @match        https://sfile.co/*
// 
// @settings     username: string: default=admin, label=Username, placeholder=Masukkan username
// @settings     password: password: default=, label=Password, placeholder=Masukkan password
// @settings     autoDownload: boolean: default=true, label=Auto Download
// @settings     maxResults: number: default=50, min=10, max=100, label=Maksimal Hasil
// @settings     quality: select: options=low,medium,high, default=medium, label=Kualitas
// @settings     saveHistory: boolean: default=true, label=Simpan Riwayat
// @settings     timeout: number: default=30000, min=5000, max=120000, step=5000, label=Timeout (ms)
// @settings     language: select: options=id,en,ja, default=id, label=Bahasa
// ==/UserScript==
```

1.2 Tipe Pengaturan yang Didukung

Tipe Deskripsi Parameter Contoh
string Input teks biasa default, label, placeholder, maxLength username: string: default=admin
password Input password (tersembunyi) default, label, placeholder password: password: default=
boolean Toggle/switch ON/OFF default, label autoDownload: boolean: default=true
number Input angka dengan step default, min, max, step, label timeout: number: default=30000, min=5000
select Dropdown pilihan options, default, label quality: select: options=low,medium,high
multiline Text area multi-baris default, label, placeholder, rows notes: multiline: default=, label=Catatan
color Picker warna default, label themeColor: color: default=#FF6B6B
email Input email default, label, placeholder email: email: default=, label=Email

1.3 API Pengaturan di Skrip

Skrip dapat mengakses pengaturan melalui objek RoCat.settings:

```javascript
// Akses pengaturan
var username = RoCat.settings.username;  // "admin"
var autoDownload = RoCat.settings.autoDownload;  // true
var quality = RoCat.settings.quality;  // "medium"

// Mendengarkan perubahan pengaturan (opsional)
RoCat.onSettingsChanged(function(newSettings) {
    // Settings berubah, refresh UI
    RoCatUI.log("Pengaturan diperbarui!");
    onLaunch();
});

// Menyimpan pengaturan sementara (per session)
RoCat.settings.setTemp("lastSearch", "naruto");
var last = RoCat.settings.getTemp("lastSearch");
```

1.4 UI Pengaturan di Aplikasi

· Halaman Pengaturan Skrip: Setiap skrip memiliki halaman pengaturan sendiri di samping tab Canvas/Browser/Console.
· Layout Pengaturan: Grup pengaturan dengan ikon, label, dan kontrol yang sesuai dengan tipe.
· Reset ke Default: Tombol "Reset ke Default" untuk mengembalikan semua pengaturan ke nilai awal.
· Ekspor/Impor: Ekspor pengaturan sebagai JSON untuk dibagikan atau dicadangkan.

2. Peningkatan UI Canvas (Layout & Komponen)

2.1 Layout Fleksibel

Tambahkan kemampuan untuk mengatur komponen dalam layout yang lebih kompleks:

```javascript
// Layout dengan grid
RoCat.render([
    { type: "layout", layout: "grid", columns: 2, children: [
        { type: "button", label: "Kiri", fn: "leftAction" },
        { type: "button", label: "Kanan", fn: "rightAction" }
    ]},
    
    // Layout horizontal
    { type: "layout", layout: "row", children: [
        { type: "input", id: "search", hint: "Cari..." },
        { type: "button", label: "🔍", fn: "search" }
    ]},
    
    // Layout dengan padding/divider
    { type: "layout", layout: "column", padding: 16, divider: true, children: [
        { type: "text", content: "Judul Section" },
        { type: "badges", badges: ["Tag1", "Tag2"] }
    ]}
]);
```

2.2 Komponen UI Baru

Komponen Deskripsi Contoh
text Teks statis dengan gaya { type: "text", content: "Hello", style: "heading" }
divider Garis pemisah { type: "divider", thickness: 1, color: "#ccc" }
checkbox Checkbox dengan label { type: "checkbox", id: "agree", label: "Setuju" }
toggle Switch ON/OFF { type: "toggle", id: "notif", label: "Notifikasi", default: true }
dropdown Dropdown pilihan { type: "dropdown", id: "quality", options: ["Low","Medium","High"], default: "Medium" }
number Input angka dengan +/- { type: "number", id: "count", default: 5, min: 1, max: 10 }
colorpicker Picker warna { type: "colorpicker", id: "theme", default: "#FF6B6B" }
textarea Text area multi-baris { type: "textarea", id: "notes", rows: 3, hint: "Catatan..." }
autocomplete Input dengan saran { type: "autocomplete", id: "search", suggestions: ["naruto","one piece"] }
group Grup komponen dengan border { type: "group", title: "Opsi Lanjutan", collapsed: true, children: [...] }

2.3 Input dengan Riwayat (History)

```javascript
// Input dengan autocomplete dari database lokal
RoCat.render([
    { type: "autocomplete", 
      id: "search", 
      hint: "Cari anime...",
      historyKey: "search_history",  // Kunci untuk menyimpan riwayat
      maxHistory: 20,                // Maksimal item riwayat
      showHistory: true,            // Tampilkan riwayat saat fokus
      onSelect: "doSearch"          // Dipanggil saat item dipilih
    }
]);

// Input dengan riwayat dan tombol hapus riwayat
RoCat.render([
    { type: "autocomplete", 
      id: "search", 
      historyKey: "search_history",
      showClearHistory: true        // Tampilkan tombol "Hapus Riwayat"
    },
    { type: "button", label: "Hapus Riwayat", fn: "clearHistory" }
]);
```

2.4 Layout Responsif

· Auto-wrap: Komponen otomatis wrap ke baris berikutnya jika tidak muat.
· Weight/Flex: Komponen bisa diberi bobot untuk mengisi ruang.
· Sticky: Komponen bisa "sticky" di bagian atas/bawah saat scroll.

```javascript
{ type: "layout", layout: "row", children: [
    { type: "button", label: "Action 1", flex: 1 },
    { type: "button", label: "Action 2", flex: 2 },
    { type: "button", label: "Action 3", flex: 1 }
]}
```

3. Default Settings yang Direkomendasikan

Setiap skrip baru akan memiliki pengaturan default berikut (bisa di-override):

```javascript
// Default settings yang selalu tersedia
var DEFAULT_SETTINGS = {
    // Prilaku Skrip
    "autoRun": true,              // Jalankan otomatis saat halaman dimuat
    "debugMode": false,           // Tampilkan log debug
    "cacheEnabled": true,         // Cache hasil scraping
    
    // Privasi & Keamanan
    "clearCookies": false,        // Hapus cookie setelah scraping
    "clearHistory": false,        // Hapus history browser setelah scraping
    "clearCache": false,          // Hapus cache setelah scraping
    "incognitoMode": false,       // Mode penyamaran (tidak menyimpan data)
    
    // Download & Penyimpanan
    "autoDownload": false,        // Unduh otomatis file yang ditemukan
    "downloadPath": "",           // Path kustom untuk unduhan
    "maxConcurrentDownloads": 3,  // Maksimal unduhan bersamaan
    "preferredQuality": "auto",   // Kualitas preferensi (auto, low, medium, high)
    
    // Network
    "timeout": 30000,             // Timeout request (ms)
    "maxRetries": 3,              // Maksimal percobaan ulang
    "retryDelay": 1000,           // Delay antar percobaan (ms)
    "userAgent": "",              // Custom User-Agent (kosong = default)
    "followRedirects": true,      // Ikuti redirect
    
    // UI
    "showNotifications": true,    // Tampilkan notifikasi
    "compactMode": false,         // Mode tampilan kompak
    "theme": "system"             // light, dark, system
};
```

4. API Penyimpanan Pengaturan

4.1 Penyimpanan Per-Skrip

```kotlin
// ScriptSettingsManager.kt
class ScriptSettingsManager {
    // Menyimpan pengaturan skrip
    fun saveSettings(scriptId: String, settings: Map<String, Any>)
    
    // Memuat pengaturan skrip
    fun loadSettings(scriptId: String): Map<String, Any>
    
    // Mendapatkan nilai pengaturan dengan default
    fun getSetting(scriptId: String, key: String, defaultValue: Any): Any
    
    // Reset ke default
    fun resetToDefault(scriptId: String)
    
    // Ekspor/Impor
    fun exportSettings(scriptId: String): String  // JSON
    fun importSettings(scriptId: String, json: String): Boolean
}
```

4.2 Storage Location

· Database SQLite: Menyimpan pengaturan dalam tabel script_settings
· Format: script_id TEXT, key TEXT, value TEXT, type TEXT
· Backup: Pengaturan dicadangkan bersama skrip saat ekspor

5. Peningkatan Console & Debug

5.1 Filter Console

```javascript
// Di UI Console
RoCat.console.filter("error");    // Hanya tampilkan error
RoCat.console.filter("warn");     // Hanya tampilkan warning
RoCat.console.filter("info");     // Hanya tampilkan info
RoCat.console.filter("debug");    // Hanya tampilkan debug
RoCat.console.filter("all");      // Tampilkan semua
```

5.2 Logging dengan Konteks

```javascript
// Log dengan konteks
RoCatUI.log("Proses scraping dimulai", { 
    module: "scraper", 
    script: "sfile",
    timestamp: new Date().toISOString()
});
```

6. Kompatibilitas & Migrasi

6.1 Backward Compatibility

· API Lama Tetap Berfungsi: RoCatUI.addInput, RoCatUI.addButton, dll. tetap bekerja.
· Konversi Otomatis: Skrip lama yang menggunakan addInput akan tetap berjalan.
· Fallback: Jika RoCat.settings tidak didefinisikan, akan mengembalikan nilai default.

6.2 Migrasi Skrip

Skrip dapat secara bertahap beralih ke sistem pengaturan baru:

```javascript
// Script lama
function onLaunch() {
    // Input manual
    RoCatUI.addInput("username", "Username");
    RoCatUI.addInput("password", "Password");
}

// Script baru dengan settings
// @settings username: string: default=admin
// @settings password: password: default=

function onLaunch() {
    var username = RoCat.settings.username;
    var password = RoCat.settings.password;
    // UI otomatis menggunakan pengaturan
}
```

Execution Plan

Tahap A: Sistem Pengaturan (Backend)

1. Buat Database Settings: Tambahkan tabel script_settings di database aplikasi.
2. Implementasi ScriptSettingsManager: Buat class untuk mengelola penyimpanan dan pengambilan pengaturan.
3. Parser Metadata Settings: Perbarui ScriptMetadataParser untuk membaca blok @settings.
4. API Settings di Rhino: Tambahkan objek RoCat.settings dengan getter/setter.

Tahap B: UI Pengaturan

1. Halaman Pengaturan Skrip: Buat tab baru di layar skrip untuk mengedit pengaturan.
2. Renderer Pengaturan: Implementasikan renderer untuk setiap tipe pengaturan (boolean → toggle, number → slider, dll.).
3. Preview & Reset: Tambahkan preview nilai dan tombol reset ke default.

Tahap C: Peningkatan UI Canvas

1. Layout Engine: Implementasikan sistem layout yang mendukung row, column, grid, group.
2. Komponen Baru: Tambahkan text, divider, checkbox, toggle, dropdown, number, colorpicker, textarea, autocomplete.
3. History System: Implementasikan penyimpanan riwayat input di database.
4. Autocomplete Input: Buat komponen input dengan saran dari riwayat.

Tahap D: Testing & Dokumentasi

1. Unit Test: Test parser settings, storage, dan API.
2. Integration Test: Test skrip contoh dengan berbagai tipe pengaturan.
3. Update Dokumentasi: Perbarui DOCS_SCRIPTING.md dengan bagian tentang settings dan komponen UI baru.

Constraints & Memory

Stabilitas

· Tidak Ada Crash: Semua operasi settings harus fault-tolerant.
· Validasi Input: Validasi tipe data dan range sebelum menyimpan.
· Default Fallback: Jika setting tidak valid, gunakan nilai default.

Kompatibilitas

· API Lama Tetap Ada: Jangan hapus addInput, addButton, dll.
· Migrasi Bertahap: Skrip lama tetap berjalan tanpa perubahan.
· Database Upgrade: Tambahkan migrasi database untuk tabel settings.

Performa

· Caching Settings: Cache settings di memory untuk akses cepat.
· Lazy Loading: Load settings hanya saat dibutuhkan.
· Batch Update: Update settings dalam batch untuk mengurangi write ke database.

Memory

· Catat di ai_memory: Update ai_memory/00_INDEX.md dengan tag #Fase35-SettingsSystem.
· Dokumentasi: Perbarui dokumentasi pengembangan dengan sistem settings baru.

Target Akhir

1. Sistem Pengaturan — Skrip dapat mendefinisikan pengaturan yang dapat diedit pengguna melalui UI yang bersih dan intuitif.
2. UI Canvas Fleksibel — Pengembang skrip dapat membuat layout yang kompleks dengan berbagai tipe komponen.
3. Input dengan Riwayat — Input memiliki autocomplete dari riwayat penggunaan yang disimpan di database.
4. Dokumentasi Lengkap — Panduan penggunaan sistem settings dan komponen UI baru tersedia.

Lampiran: Contoh Skrip dengan Settings Lengkap

```javascript
// ==UserScript==
// @name         Sfile Advanced Scraper
// @version      2.0.0
// @description  Scraper canggih untuk sfile.co dengan pengaturan lengkap
// @author       RoCat AI
// @category     Download
// @match        https://sfile.co/*
//
// @settings     username: string: default=admin, label=Username, placeholder=Masukkan username
// @settings     password: password: default=, label=Password, placeholder=Masukkan password
// @settings     autoDownload: boolean: default=true, label=Unduh Otomatis
// @settings     maxResults: number: default=50, min=10, max=100, label=Maksimal Hasil
// @settings     quality: select: options=low,medium,high, default=medium, label=Kualitas
// @settings     saveHistory: boolean: default=true, label=Simpan Riwayat Pencarian
// @settings     timeout: number: default=30000, min=5000, max=120000, step=5000, label=Timeout (ms)
// @settings     clearCookies: boolean: default=false, label=Hapus Cookie Setelah Selesai
// @settings     clearHistory: boolean: default=false, label=Hapus History Setelah Selesai
// @settings     theme: select: options=light,dark,system, default=system, label=Tema
// ==/UserScript==

function onLaunch() {
    // Gunakan pengaturan
    var username = RoCat.settings.username;
    var autoDownload = RoCat.settings.autoDownload;
    var quality = RoCat.settings.quality;
    
    RoCat.render([
        { type: "clear" },
        { type: "text", content: "🔍 Sfile Scraper", style: "heading" },
        { type: "text", content: "Menggunakan pengaturan: " + JSON.stringify(RoCat.settings), style: "caption" },
        
        // Layout input
        { type: "layout", layout: "row", children: [
            { type: "autocomplete", id: "search", hint: "Cari file...", historyKey: "sfile_search" },
            { type: "button", label: "🔍 Cari", fn: "doSearch" }
        ]},
        
        // Kontrol cepat
        { type: "layout", layout: "row", children: [
            { type: "button", label: "⚙️ Settings", fn: "openSettings" },
            { type: "button", label: "🗑️ Clear Cache", fn: "clearCache" },
            { type: "button", label: "📊 Stats", fn: "showStats" }
        ]},
        
        // Status
        { type: "alert", message: "Siap menggunakan skrip v" + scriptVersion, level: "info" }
    ]);
}

function doSearch(inputs) {
    var query = (inputs && inputs.search || "").trim();
    if (!query) { 
        RoCatUI.addAlert("Masukkan kata kunci", "warning"); 
        return; 
    }
    
    // Ambil pengaturan
    var maxResults = RoCat.settings.maxResults;
    var timeout = RoCat.settings.timeout;
    var quality = RoCat.settings.quality;
    
    RoCatUI.log("🔍 Mencari: " + query + " (maks: " + maxResults + ", timeout: " + timeout + "ms)");
    
    // Simpan ke riwayat (otomatis jika autocomplete)
    if (RoCat.settings.saveHistory) {
        RoCat.saveHistory("sfile_search", query);
    }
    
    // ... lakukan scraping ...
}

function openSettings() {
    // Buka halaman pengaturan skrip
    RoCat.openSettings();
}
```

---

Catatan untuk AI Agent: Fokus utama adalah membuat sistem pengaturan yang fleksibel, mudah digunakan, dan terintegrasi dengan baik ke dalam ekosistem RoCat. Sistem harus mendukung berbagai tipe data dan memberikan pengalaman pengguna yang mulus. Pastikan untuk menjaga kompatibilitas dengan skrip yang sudah ada.