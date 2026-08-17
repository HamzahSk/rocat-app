# Tahap 22.2 — Simplification API (`RoCat` Wrapper) + Template Cards Script Canvas

**Tanggal:** 2026-08-09 · **Status:** SELESAI · **Build:** `rocat-app` `sh gradlew :app:assembleDebug` + `sh gradlew test` SUCCESS (**rhino 31 test hijau**: 20 RhinoScriptEngineTest + 2 AnichinScraperTest + 9 RoCatScriptTemplateTest).

## Tujuan
Tahap 22.2 (melanjutkan Tahap 22): (1) **Simplification API** — wrapper global `RoCat` yang di-inject otomatis ke setiap scope Rhino supaya skrip bisa menggambar seluruh kanvas dengan satu panggilan `RoCat.render([...])` dan parsing payload tanpa harus menulis boilerplate try/catch, serta (2) **Template Cards** — 5 komponen UI baru (JSON viewer, HTML preview, audio player, alert banner, badge group) lengkap dari bridge Rhino → `ScriptUiBridge` → `ScriptCanvasViewModel` → `ScriptCanvasScreen`, plus fix `fetch().json()` agar JSON invalid menjadi error JS yang bisa di-`try/catch`.

## Perubahan

### Tahap 22.1 — Simplification API (`RoCat`)
- `scripting/rhino/.../RoCatCoreWrapper.kt` (BARU): `const val RO_CAT_CORE_WRAPPER_JS` — source ES5 (Rhino-1.7.15-safe, tanpa async/class/spread/optional-chaining) yang dievaluasi ke scope di `RhinoScriptEngine.createScope` (`cx.evaluateString(scope, RO_CAT_CORE_WRAPPER_JS, "RoCatCore.js", 1, null)`) SETELAH bridge `RoCatUI`/`RoCatDOM`/`document` dan SEBELUM kode user. Menyediakan:
  - `RoCat.render(items)` — menerima satu descriptor ATAU array descriptor; tiap `renderOne(item)` di-`try/catch` dan di-guard `hasUI()` (`typeof RoCatUI !== "undefined"`). Tipe didukung: `clear`/`reset`, `input`, `button` (label + `fn`/`function`/`onClick`), `image` (`url`/`src` + title + `download`), `video` (`hls`), `audio`, `json` (`data`/`json`, non-string di-`JSON.stringify`), `html` (`html`/`content`), `alert` (`message`/`text` + `level`), `badges` (`badges`/`items`/`list` — array JS atau string JSON), `grid` (`columns` + `items`/`entries` + `onClick`/`fn`), `log` (`text`/`message`). Helper `pick(o,key,def)`/`pickBool(o,key,def)` toleran terhadap key hilang/null.
  - `RoCat.safeParseJson(str, fallback)` — tak pernah throw; return `fallback` (default `null`) untuk input null/undefined/invalid.
  - `RoCat.fetchJson(url, options)` — wrapper `fetch()`; return objek JSON dari `safeParseJson(res.body)` bila `res.ok`, else `null`. (Sengaja tidak pakai `res.json()` — lihat catatan teknis.)
- `RhinoScriptEngine` fix **`fetch().json()`**: `BridgeFetch.json()` kini membungkus `JsonParser(cx, scope).parseValue(result.body)` dalam `try/catch` dan melempar lewat helper baru `throwJsError(cx, scope, message)` (`cx.newObject(scope, "Error", arrayOf<Any?>(message))` + `throw JavaScriptException(error, null, 0)`). Body kosong juga → `throwJsError`. Import `org.mozilla.javascript.JavaScriptException` ditambah. Efek: script bisa `try { res.json() } catch (e) {...}` untuk JSON malformed (sebelumnya ParseException meledak tanpa bisa di-catch).

### Tahap 22.2 — Template Cards (bridge → UI)
- `ScriptUiBridge` (scripting/api): 5 method baru dengan **default no-op** (bukan abstract → semua implementasi lama/test recorder tetap valid): `addJsonLog(dataJson, title="", allowCopy=true)`, `addHtmlPreview(htmlContent, title="")`, `addAudio(url, title="", allowDownload=true)`, `addAlert(message, type="info")`, `addBadgeGroup(badgesJson: String)`.
- `ScriptUIComponent`: +5 data class `JsonLog(dataJson, title, allowCopy)`, `HtmlPreview(htmlContent, title)`, `Audio(url, title, allowDownload)`, `Alert(message, type)`, `BadgeGroup(badges: List<String>)`. Helper `parseBadgeGroup(badgesJson)` baru (parse JSON array string non-blank, return null bila kosong/invalid — pola sama seperti `parseGrid`).
- `ScriptCanvasViewModel.uiBridge`: override 5 method → `postUi(uiSession) { uiComponents.add(...) }`; `addBadgeGroup` lewat `parseBadgeGroup(badgesJson)?.let { add }` (payload buruk → tak dirender).
- `ScriptCanvasScreen`: case baru di render LazyColumn: `JsonLogCard`, `HtmlPreviewCard`, `AudioPreviewCard` (label i18n + `folder = viewModel::scrapeFolder`), `AlertBannerCard`, `BadgeGroupCard`.
- Komponen baru di `app/rocat/ui/components/`:
  - `JsonLogCard.kt` — kartu monospace: pretty-print JSON (try `Json { prettyPrint }` fallback raw), expandable, tombol "Copy JSON" (`LocalClipboardManager` + Toast).
  - `HtmlPreviewCard.kt` — `android.text.Html.fromHtml(html, FROM_HTML_MODE_COMPACT)` → `AnnotatedString` (StyleSpan BOLD/ITALIC/BOLD_ITALIC, UnderlineSpan, URLSpan); link pakai **`LinkAnnotation.Clickable` + `TextLinkStyles`** di `buildAnnotatedString.addLink(...)` dan dirender dengan `Text` (BUKAN `ClickableText` yang deprecated di Compose 1.7). Scrollable inline (`heightIn(max=320.dp)` + `verticalScroll`), tanpa WebView.
  - `AudioPreviewCard.kt` — Media3/ExoPlayer inline: Play/Pause toggle, seek bar progress, tombol "Download Audio" via `MediaDownloader`/`StorageManager.saveFileToScrapeFolder` (status Idle/Downloading/Done/Failed).
  - `AlertBannerCard.kt` — banner ber-ikon + warna sesuai `AlertType.from(type)` (info/warning/error/success; unknown → info).
  - `BadgeGroupCard.kt` — `FlowRow` chip (label `Surface`/`SuggestionChip`).
- Bridge Rhino `RoCatUiBridge` (RhinoScriptEngine): +5 `put("addJsonLog"...)`/`addHtmlPreview`/`addAudio`/`addAlert`/`addBadgeGroup`, semua dibungkus `runSafe`; helper baru `argJson(args, index, default)` — argumen `Scriptable` (objek/array JS) → `NativeJSON.stringify`, lainnya → `Context.toString`, `Undefined` → default.
- i18n: `StringKey` + EN/ID — `copyJson`/`jsonCopied`/`play`/`pause`/`downloadAudio`/`audioSaved`.
- Skrip contoh ditulis ulang memakai API baru:
  - `scrape_anichin.js` v3.0.0: `onLaunch`/`doSearch`/`openDetail`/`readChapter` memakai `RoCat.render([...])`; `JSON.parse(payloadStr)` → `RoCat.safeParseJson(payloadStr, {})`; pesan status → `RoCatUI.addAlert(msg, "warning"/"error"/"info")`; detail menampilkan chip genre/status via `parseDetailBadges(root)` → `RoCatUI.addBadgeGroup(JSON.stringify(badges))`.
  - `ImportScriptViewModel.CANVAS_EXAMPLE_SCRIPT` (Manga Scraper Mock v2.0.0): `onLaunch` `RoCat.render([clear, input, button])`; `doSearch` render clear/button + `addAlert("Hasil pencarian untuk: "+q, "info")` + grid; `openDetail` `RoCat.safeParseJson` + `RoCat.render([clear, button, image(download), badges, json(copy)])`; `readChapter` pakai `RoCatUI.addAlert`/`addAudio`.

## Catatan Teknis Penting
- **`RoCatCoreWrapper.js` harus di-inject SETELAH bridge & SEBELUM kode user** di `createScope`, dan ditulis ES5 murni (Rhino mode interpretasi tanpa class/spread/optional-chaining). Wrapper tak boleh berasumsi `RoCatUI` ada (guard `hasUI()`) supaya eksekusi polos (unit test plain environment) tetap aman.
- **Jangan biarkan `fetch().json()` melempar `ParseException`**: `JsonParser` Rhino melempar checked exception yang TIDAK bisa di-catch oleh `try/catch` di JS. Semua jalur JSON invalid harus lewat `throwJsError` (`JavaScriptException` berisi objek `Error` JS). `RoCat.fetchJson` sengaja memakai `safeParseJson(res.body)` (JSON.parse → SyntaxError catchable) daripada `res.json()`.
- **Method `ScriptUiBridge` baru wajib default no-op** agar `RecordingUiBridge` (unit test) & implementasi lain tak perlu diubah tiap ekspansi API — pola yang sama sudah dipakai `decodeBase64` (Tahap 20).
- **`argJson(args, i)`** adalah satu-satunya jembatan antara nilai JS non-string (objek/array) dan `String` yang dibutuhkan bridge Kotlin: `Scriptable` → `NativeJSON.stringify(cx, scope, value, null, null)`, `ConsString`/primitif → `Context.toString`.
- **Link HTML tanpa WebView**: gunakan `LinkAnnotation.Clickable` + `TextLinkStyles` + `addLink(...)` di `buildAnnotatedString`, render dengan `Text` biasa (bukan `ClickableText` yang deprecated di Compose 1.7). Ini menghindari dependensi `android.text.Spanned` yang stub android.jar-nya malformed di environment CI ini (lihat kendala build).
- `parseBadgeGroup` toleran: JSON bukan array / array kosong / semua string blank → return null → card tidak dirender (tidak crash).

## Kendala Build (environment)
- `app/.../HtmlPreviewCard.kt` versi awal memakai `ClickableText`; setelah refactor ke `LinkAnnotation` tidak ada warning deprecation. Sempat muncul error `HtmlPreviewCard.kt:7:18 Constructor of sealed class 'Spanned' is internal` akibat **stub android.jar (API 35) yang rusak** di CI (javap menunjukkan `getSpans(int, int, java.lang.TargetException)`/`android.text.ExecutableArgumentNames` — kelas tak nyata). Solusi: tidak pernah menyebut tipe `Spanned` secara eksplisit sebagai import (versi final memang memakai `CharSequence` + cast lokal) — komponen lain yang tak menyentuh tipe `Spanned` aman.

## Verifikasi
- `cd rocat-app && sh gradlew :scripting:rhino:testDebugUnitTest` → **BUILD SUCCESSFUL** (31 test hijau).
- `sh gradlew :app:assembleDebug` → **BUILD SUCCESSFUL** (APK debug).
- `sh gradlew test` → **BUILD SUCCESSFUL** (semua modul; rhino 31 hijau: `RhinoScriptEngineTest` 20, `AnichinScraperTest` 2, `RoCatScriptTemplateTest` 9).
- `RoCatScriptTemplateTest` mencakup: `RoCat.render` dari descriptor list/single/toleran-malformed; `safeParseJson` fallback; `fetchJson` sukses/HTTP-error/bukan-JSON; `addBadgeGroup` array JS & string JSON & null; `addJsonLog` objek & string & copy=false; `addAlert` default info & unknown type; `addHtmlPreview` + `addAudio` (default args).
