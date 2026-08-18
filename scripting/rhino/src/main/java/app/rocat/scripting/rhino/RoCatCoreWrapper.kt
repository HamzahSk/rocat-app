package app.rocat.scripting.rhino

/**
 * The auto-injected **universal core wrapper** (Tahap 22.1). `[RhinoScriptEngine]`
 * evaluates this into every fresh Rhino scope *before* any user script runs, exposing
 * the ergonomic global `RoCat` object:
 *
 * - `RoCat.render(items)` — accepts a single UI descriptor or an array of them and
*  dispatches each to the matching `RoCatUI.add*` call, so scripts can draw a whole
 *  canvas with one statement instead of ten. `image`/`video`/`audio` descriptors accept
 *  a `headers` object (Tahap 24.1) that is forwarded to the native loader (Coil /
 *  ExoPlayer) — e.g. `{ type:"video", url:"…", hls:true, headers:{"Referer":"https://…"} }`.
 * - `RoCat.safeParseJson(str, fallback)` — never throws; returns [fallback] (default
 *   `null`) when the input is `null`/`undefined`/not valid JSON.
 * - `RoCat.fetchJson(url, options)` — `fetch()` wrapper that returns the parsed JSON
 *   object, or `null` when the request is not `ok` or the body is not JSON.
 *
 * Deliberately written in Rhino-1.7.15-safe ES5 (no `async`/`await`, no `class`, no
 * spread, no optional chaining). Every dispatch is wrapped in try/catch so a malformed
 * descriptor can never crash a script.
 */
const val RO_CAT_CORE_WRAPPER_JS: String = """
var RoCat = (function () {
    function safeParseJson(str, fallback) {
        if (fallback === undefined) fallback = null;
        if (str === null || str === undefined) return fallback;
        try { return JSON.parse(String(str)); } catch (e) { return fallback; }
    }

    function fetchJson(url, options) {
        var res = fetch(url, options);
        if (res && res.ok) {
            // Deliberately parse the body string with safeParseJson instead of
            // res.json(): Rhino's JsonParser throws a non-JS-catchable ParseException
            // on invalid JSON, while JSON.parse raises a catchable SyntaxError.
            return safeParseJson(res.body, null);
        }
        return null;
    }

    function isArrayLike(v) {
        if (Array.isArray) return Array.isArray(v);
        return Object.prototype.toString.call(v) === "[object Array]";
    }

    function hasUI() {
        return typeof RoCatUI !== "undefined" && RoCatUI !== null;
    }

    function pick(o, key, def) {
        if (o === null || o === undefined) return def;
        var v = o[key];
        return (v === null || v === undefined) ? def : v;
    }

    function pickBool(o, key, def) {
        var v = pick(o, key, def);
        if (typeof v === "boolean") return v;
        if (typeof v === "string") {
            var s = String(v).toLowerCase();
            return s === "true" || s === "1";
        }
        return def === true;
    }

    function render(items) {
        if (items === null || items === undefined) return;
        if (isArrayLike(items)) {
            for (var i = 0; i < items.length; i++) renderOne(items[i]);
        } else {
            renderOne(items);
        }
    }

    function renderOne(item) {
        if (item === null || item === undefined || typeof item !== "object") return;
        if (!hasUI()) return;
        var type = item.type || "";
        try {
            if (type === "clear" || type === "reset") { RoCatUI.clear(); return; }
            if (type === "input") { RoCatUI.addInput(pick(item, "id", ""), pick(item, "hint", "")); return; }
            if (type === "button") {
                RoCatUI.addButton(pick(item, "label", ""), pick(item, "fn", "") || pick(item, "function", "") || pick(item, "onClick", ""));
                return;
            }
            if (type === "image") {
                RoCatUI.addImage(pick(item, "url", "") || pick(item, "src", ""), pick(item, "title", ""), pickBool(item, "download", true), pick(item, "headers", null));
                return;
            }
            if (type === "video") {
                RoCatUI.addVideo(pick(item, "url", ""), pick(item, "title", ""), pickBool(item, "hls", false), pickBool(item, "download", true), pick(item, "headers", null));
                return;
            }
            if (type === "audio") {
                RoCatUI.addAudio(pick(item, "url", ""), pick(item, "title", ""), pickBool(item, "download", true), pick(item, "headers", null));
                return;
            }
            if (type === "json") {
                var data = pick(item, "data", null);
                if (data === null || data === undefined) data = pick(item, "json", "");
                if (data !== null && data !== undefined && typeof data !== "string") data = JSON.stringify(data);
                RoCatUI.addJsonLog(String(data), pick(item, "title", ""), pickBool(item, "copy", true));
                return;
            }
            if (type === "html") {
                RoCatUI.addHtmlPreview(String(pick(item, "html", "") || pick(item, "content", "")), pick(item, "title", ""));
                return;
            }
            if (type === "alert") {
                RoCatUI.addAlert(String(pick(item, "message", "") || pick(item, "text", "")), pick(item, "level", "info"));
                return;
            }
            if (type === "badges") {
                var badges = item.badges || item.items || item.list;
                if (typeof badges === "string") {
                    RoCatUI.addBadgeGroup(badges);
                } else if (badges !== null && badges !== undefined) {
                    RoCatUI.addBadgeGroup(JSON.stringify(badges));
                }
                return;
            }
            if (type === "grid") {
                var columns = pick(item, "columns", 3);
                var entries = item.items || item.entries || [];
                RoCatUI.addGrid(columns, typeof entries === "string" ? entries : JSON.stringify(entries), pick(item, "onClick", "") || pick(item, "fn", ""));
                return;
            }
            if (type === "log") {
                RoCatUI.log(String(pick(item, "text", "") || pick(item, "message", "")));
                return;
            }
            // --- Tahap 35: flexible layouts & rich input controls ---
            if (type === "text") {
                RoCatUI.addText(String(pick(item, "content", "") || pick(item, "text", "")), pick(item, "style", "body"));
                return;
            }
            if (type === "divider") {
                RoCatUI.addDivider(pick(item, "thickness", 1), pick(item, "color", "#cccccc"));
                return;
            }
            if (type === "checkbox") {
                RoCatUI.addCheckbox(pick(item, "id", ""), pick(item, "label", "") || pick(item, "id", ""), pickBool(item, "checked", false) || pickBool(item, "default", false));
                return;
            }
            if (type === "toggle") {
                RoCatUI.addToggle(pick(item, "id", ""), pick(item, "label", "") || pick(item, "id", ""), pickBool(item, "checked", false) || pickBool(item, "default", false));
                return;
            }
            if (type === "dropdown") {
                var ddOptions = item.options || [];
                if (typeof ddOptions === "string") ddOptions = String(ddOptions).split(",");
                RoCatUI.addDropdown(pick(item, "id", ""), ddOptions, pick(item, "default", "") || pick(item, "selected", ""), pick(item, "label", ""));
                return;
            }
            if (type === "number") {
                RoCatUI.addNumber(pick(item, "id", ""), pick(item, "default", null), pick(item, "min", null), pick(item, "max", null), pick(item, "step", null), pick(item, "label", ""));
                return;
            }
            if (type === "colorpicker") {
                RoCatUI.addColorPicker(pick(item, "id", ""), pick(item, "default", "#000000"), pick(item, "label", ""));
                return;
            }
            if (type === "textarea") {
                RoCatUI.addTextArea(pick(item, "id", ""), pick(item, "hint", ""), pick(item, "rows", 3), pick(item, "default", ""));
                return;
            }
            if (type === "autocomplete") {
                var acOptions = item.suggestions || [];
                if (typeof acOptions === "string") acOptions = String(acOptions).split(",");
                RoCatUI.addAutocomplete(
                    pick(item, "id", ""),
                    pick(item, "hint", ""),
                    acOptions,
                    pick(item, "historyKey", ""),
                    pick(item, "maxHistory", 20),
                    pickBool(item, "showHistory", true),
                    pickBool(item, "showClearHistory", true),
                    pick(item, "default", "")
                );
                return;
            }
            if (type === "group") {
                var groupChildren = item.children || [];
                if (typeof groupChildren !== "string") groupChildren = JSON.stringify(groupChildren);
                RoCatUI.addGroup(pick(item, "title", ""), pickBool(item, "collapsed", false), groupChildren);
                return;
            }
            if (type === "layout") {
                var layoutChildren = item.children || [];
                if (typeof layoutChildren !== "string") layoutChildren = JSON.stringify(layoutChildren);
                RoCatUI.addLayout(pick(item, "layout", "column"), pick(item, "columns", 2), pick(item, "padding", 0), pickBool(item, "divider", false), layoutChildren, pick(item, "flex", null));
                return;
            }
        } catch (e) {
            // A bad descriptor must never break the caller.
        }
    }

    return {
        render: render,
        safeParseJson: safeParseJson,
        fetchJson: fetchJson
    };
})();
"""
