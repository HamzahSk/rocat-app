package app.rocat.scripting.rhino

/**
 * The auto-injected **general-purpose browser automation polyfill** (Tahap 25).
 * `[RhinoScriptEngine]` evaluates this into every fresh Rhino scope **after** the
 * low-level `RoCatPage` native bridge and **before** any user script runs, exposing the
 * ergonomic global `RoCatBrowser` object with a Playwright/Puppeteer-like surface:
 *
 *  - `RoCatBrowser.launch(options)` / `.getInstance()` / `.connect()`  -> Browser
 *  - `browser.newPage()` / `browser.page()` / `browser.close()`        -> Page lifecycle
 *  - `page.goto(url, options)` / `page.back()` / `page.forward()` / `page.reload()`
 *  - `page.locator(selector)` / `page.click` / `page.fill` / `page.text`
 *  - `page.waitForSelector` / `page.waitForLoad` / `page.waitForTimeout`
 *  - `page.evaluate(fn, args)` / `page.content()` / `page.url()` / `page.title()`
 *  - `page.screenshot(options)` / `page.cookies()` / `page.setCookie` / `page.clearCookies()`
 *  - `page.scrollTo(x, y)` / `page.scrollBottom()`                      (Tahap 29)
 *  - `locator.click/fill/text/getAttribute/exists/waitFor/all/clickAll/type/scrollIntoView/getBoundingRect`
 *
 *  Tahap 29 adds a Puppeteer-like **global `page`** convenience facade that drives the
 *  singleton Browser directly (`page.goto(url)` / `page.click(sel)` / `page.type(sel, txt)`
 *  / `page.scrollBottom()` / `page.screenshot(...)`), so scripts with a single live tab
 *  don't need to touch `RoCatBrowser` at all.
 *
 * Everything is built on top of the synchronous `RoCatPage.*` primitives so it works in
 * Rhino-1.7.15 (no `async`/`await`, no `class`, no spread, no optional chaining — the
 * wrapper is deliberately ES5). Every page-side operation round-trips through one
 * `RoCatPage.evaluate(script)` call (WebView -> JS).
 *
 * When the host does not supply a browser bridge (`RoCatPage` absent), the wrapper still
 * defines `RoCatBrowser` but `launch()`/`newPage()` throw a clear error so scripts can
 * `try/catch` availability. Callers can also probe `typeof RoCatPage === "undefined"`.
 */
const val RO_CAT_BROWSER_WRAPPER_JS: String = """
var RoCatBrowser = (function () {
    var _defaultTimeout = 30000;
    var _browser = null;

    // ============ Internals ============

    function hasPage() {
        return typeof RoCatPage !== "undefined" && RoCatPage !== null;
    }

    function busyWait(ms) {
        if (hasPage() && RoCatPage.sleep) { RoCatPage.sleep(ms); return; }
        var end = Date.now() + ms;
        while (Date.now() < end) {}
    }

    function serializeArg(a) {
        if (typeof a === "function") return String(a);
        if (typeof a === "string" || typeof a === "number" || typeof a === "boolean" || a === null) {
            return JSON.stringify(a);
        }
        if (a === undefined) return "null";
        return JSON.stringify(a);
    }

    function toScript(fn, args) {
        if (typeof fn === "function") {
            var fnStr = fn.toString();
            var argStr = "";
            if (args && args.length) {
                var parts = [];
                for (var i = 0; i < args.length; i++) parts.push(serializeArg(args[i]));
                argStr = parts.join(", ");
            }
            return "(" + fnStr + ")(" + argStr + ")";
        }
        return String(fn);
    }

    function evaluateInPage(fn, args, fallback) {
        if (!hasPage()) return fallback;
        var res = RoCatPage.evaluate(toScript(fn, args));
        return (res === null || res === undefined) ? fallback : res;
    }

    function asResult(res, what) {
        if (res !== null && typeof res === "object") return res;
        if (res === true) return { success: true };
        return { success: false, error: (res === null || res === undefined) ? "No result: " + what : String(res) };
    }

    // ============ Locator Class ============

    function Locator(selector, page) {
        this.selector = selector;
        this.page = page;
    }

    Locator.prototype.click = function () {
        var self = this;
        // Tahap 30: prefer the native touch bridge (RoCatPage.click dispatches a real
        // ACTION_DOWN/ACTION_UP MotionEvent pair on the WebView). Untrusted synthetic
        // events from el.click()/dispatchEvent are ignored by SPA & anti-bot pages.
        if (hasPage() && RoCatPage.click) {
            var ok = RoCatPage.click(this.selector);
            if (ok === true) return { success: true };
            return { success: false, error: "Element not found or click failed: " + this.selector };
        }
        return asResult(evaluateInPage(function (sel) {
            var el = document.querySelector(sel);
            if (!el) return { success: false, error: "Element not found: " + sel };
            var opts = { bubbles: true, cancelable: true, view: window };
            try {
                el.dispatchEvent(new MouseEvent('pointerdown', opts));
                el.dispatchEvent(new MouseEvent('mousedown', opts));
                el.dispatchEvent(new MouseEvent('pointerup', opts));
                el.dispatchEvent(new MouseEvent('mouseup', opts));
                el.dispatchEvent(new MouseEvent('click', opts));
            } catch (e) {
                el.click();
            }
            return { success: true };
        }, [this.selector]), this.selector);
    };

    Locator.prototype.fill = function (text) {
        return asResult(evaluateInPage(function (sel, txt) {
            var el = document.querySelector(sel);
            if (!el) return { success: false, error: "Element not found: " + sel };
            try { el.focus(); } catch (e) {}
            el.value = txt;
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
            try { el.dispatchEvent(new Event('blur', { bubbles: true })); } catch (e) {}
            return { success: true };
        }, [this.selector, text]), this.selector);
    };

    Locator.prototype.text = function () {
        return evaluateInPage(function (sel) {
            var el = document.querySelector(sel);
            return el ? el.textContent.trim() : "";
        }, [this.selector], "");
    };

    Locator.prototype.getAttribute = function (attr) {
        return evaluateInPage(function (sel, name) {
            var el = document.querySelector(sel);
            return el ? el.getAttribute(name) : null;
        }, [this.selector, attr], null);
    };

    Locator.prototype.exists = function () {
        var res = evaluateInPage(function (sel) {
            return document.querySelector(sel) !== null;
        }, [this.selector], false);
        return res === true;
    };

    Locator.prototype.waitFor = function (timeout) {
        timeout = (typeof timeout === "number") ? timeout : _defaultTimeout;
        var deadline = Date.now() + timeout;
        while (true) {
            if (this.exists()) return true;
            if (Date.now() >= deadline) {
                throw new Error("Timeout waiting for selector: " + this.selector);
            }
            busyWait(100);
        }
    };

    Locator.prototype.all = function () {
        var res = evaluateInPage(function (sel) {
            var els = document.querySelectorAll(sel);
            var out = [];
            for (var i = 0; i < els.length; i++) {
                var el = els[i];
                var attrs = {};
                if (el.attributes) {
                    for (var a = 0; a < el.attributes.length; a++) {
                        var at = el.attributes[a];
                        attrs[at.name] = at.value;
                    }
                }
                out.push({ text: el.textContent.trim(), html: el.innerHTML, attributes: attrs });
            }
            return out;
        }, [this.selector], []);
        return (res instanceof Array) ? res : [];
    };

    Locator.prototype.clickAll = function () {
        var res = evaluateInPage(function (sel) {
            var els = document.querySelectorAll(sel);
            var out = [];
            for (var i = 0; i < els.length; i++) {
                try {
                    els[i].click();
                    out.push({ index: i, success: true });
                } catch (e) {
                    out.push({ index: i, success: false, error: e.message });
                }
            }
            return out;
        }, [this.selector], []);
        return (res instanceof Array) ? res : [];
    };

    Locator.prototype.type = function (text, delay) {
        delay = (typeof delay === "number") ? delay : 50;
        for (var i = 1; i <= text.length; i++) {
            this.fill(text.substring(0, i));
            busyWait(delay);
        }
        return { success: true };
    };

    Locator.prototype.scrollIntoView = function () {
        return evaluateInPage(function (sel) {
            var el = document.querySelector(sel);
            if (!el) return false;
            try { el.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch (e) { el.scrollIntoView(); }
            return true;
        }, [this.selector], false) === true;
    };

    Locator.prototype.getBoundingRect = function () {
        return evaluateInPage(function (sel) {
            var el = document.querySelector(sel);
            if (!el) return null;
            var r = el.getBoundingClientRect();
            return { x: r.x, y: r.y, width: r.width, height: r.height, top: r.top, right: r.right, bottom: r.bottom, left: r.left };
        }, [this.selector], null);
    };

    // ============ Page Class ============

    function Page() {
        this._url = "";
        this._title = "";
    }

    Page.prototype.goto = function (url, options) {
        options = options || {};
        var timeout = options.timeout || _defaultTimeout;
        if (!hasPage()) throw new Error("RoCatBrowser: no browser bridge available");
        var opened = RoCatPage.open(url, timeout);
        if (!opened) throw new Error("RoCatBrowser: failed to open " + url);
        this._url = RoCatPage.url() || url;
        this._title = RoCatPage.title() || "";
        var waitUntil = options.waitUntil;
        if (waitUntil === "domcontentloaded" || waitUntil === "interactive") {
            this.waitForLoad("domcontentloaded", timeout);
        } else if (waitUntil === "load" || waitUntil === "complete") {
            this.waitForLoad("load", timeout);
        }
        return this;
    };

    Page.prototype.type = function (selector, text, delay) {
        return this.locator(selector).type(text, delay);
    };

    Page.prototype.scrollTo = function (x, y) {
        if (!hasPage() || !RoCatPage.scrollTo) return false;
        return RoCatPage.scrollTo(
            (typeof x === "number") ? x : 0,
            (typeof y === "number") ? y : 0
        );
    };

    Page.prototype.scrollBottom = function () {
        if (!hasPage() || !RoCatPage.scrollBottom) return false;
        return RoCatPage.scrollBottom();
    };

    Page.prototype.waitForLoad = function (state, timeout) {
        state = state || "load";
        timeout = (typeof timeout === "number") ? timeout : _defaultTimeout;
        var target = (state === "load" || state === "complete") ? "complete" : "interactive";
        var deadline = Date.now() + timeout;
        while (true) {
            var ready = hasPage() ? RoCatPage.evaluate("document.readyState") : "";
            if (ready === target || (target === "interactive" && ready === "complete")) return true;
            if (Date.now() >= deadline) throw new Error("Timeout waiting for load state: " + state);
            busyWait(100);
        }
    };

    Page.prototype.waitForTimeout = function (ms) {
        busyWait((typeof ms === "number") ? ms : 0);
        return this;
    };

    Page.prototype.content = function () {
        return hasPage() ? (RoCatPage.getHtml() || "") : "";
    };

    Page.prototype.evaluate = function (fn, args) {
        return evaluateInPage(fn, args, null);
    };

    Page.prototype.locator = function (selector) {
        return new Locator(selector, this);
    };

    Page.prototype.click = function (selector) {
        return this.locator(selector).click();
    };

    Page.prototype.fill = function (selector, text) {
        return this.locator(selector).fill(text);
    };

    Page.prototype.text = function (selector) {
        return this.locator(selector).text();
    };

    Page.prototype.getAttribute = function (selector, attr) {
        return this.locator(selector).getAttribute(attr);
    };

    Page.prototype.waitForSelector = function (selector, timeout) {
        return this.locator(selector).waitFor(timeout);
    };

    Page.prototype.url = function () {
        var live = hasPage() ? RoCatPage.url() : "";
        return live || this._url;
    };

    Page.prototype.title = function () {
        var live = hasPage() ? RoCatPage.title() : "";
        return live || this._title;
    };

    Page.prototype.goBack = function () {
        return hasPage() ? RoCatPage.goBack() : false;
    };

    Page.prototype.goForward = function () {
        return hasPage() ? RoCatPage.goForward() : false;
    };

    Page.prototype.reload = function () {
        return hasPage() ? RoCatPage.reload() : false;
    };

    Page.prototype.stop = function () {
        return hasPage() ? RoCatPage.stop() : false;
    };

    Page.prototype.screenshot = function (options) {
        options = options || {};
        return hasPage() ? (RoCatPage.screenshot(options.path || "", options.quality || 80) || "") : "";
    };

    Page.prototype.cookies = function () {
        var raw = hasPage() ? RoCatPage.getCookies() : "[]";
        try { return JSON.parse(raw); } catch (e) { return []; }
    };

    Page.prototype.setCookie = function (cookie) {
        if (!hasPage()) return false;
        return RoCatPage.setCookie(typeof cookie === "string" ? cookie : JSON.stringify(cookie)) === true;
    };

    Page.prototype.clearCookies = function () {
        return hasPage() ? RoCatPage.clearCookies() : false;
    };

    Page.prototype.close = function () {
        if (hasPage()) RoCatPage.close();
    };

    // ============ Browser Class ============

    function Browser() {
        this.pages = [];
        this.currentPage = null;
        this.isHeadless = true;
        this.viewport = { width: 1366, height: 768 };
    }

    Browser.prototype.launch = function (options) {
        options = options || {};
        if (!hasPage()) throw new Error("RoCatBrowser: no browser bridge available (RoCatPage is missing)");
        this.isHeadless = options.headless !== false;
        if (options.viewport) this.viewport = options.viewport;
        return this;
    };

    Browser.prototype.newPage = function () {
        var page = new Page();
        this.pages.push(page);
        this.currentPage = page;
        return page;
    };

    Browser.prototype.page = function () {
        if (!this.currentPage) this.newPage();
        return this.currentPage;
    };

    Browser.prototype.close = function () {
        if (hasPage()) RoCatPage.close();
    };

    Browser.prototype.setDefaultTimeout = function (timeout) {
        _defaultTimeout = (typeof timeout === "number") ? timeout : _defaultTimeout;
        return this;
    };

    // ============ Public API ============

    return {
        launch: function (options) {
            var browser = new Browser();
            browser.launch(options);
            return browser;
        },
        getInstance: function () {
            if (!_browser) _browser = new Browser();
            return _browser;
        },
        connect: function () {
            return this.getInstance();
        },
        version: function () {
            return "RoCatBrowser v1.0.0";
        },
        setDefaultTimeout: function (timeout) {
            _defaultTimeout = (typeof timeout === "number") ? timeout : _defaultTimeout;
        },
        hasBrowser: hasPage
    };
})();

// Tahap 29 — a Puppeteer-like convenience **global `page`** facade. Scripts that only
// need one live tab can drive it directly (page.goto / page.click / page.type / ...)
// without creating a Browser instance, mirroring Puppeteer's `page.*` API. It shares
// the singleton Browser from RoCatBrowser.getInstance() so `page` and `RoCatBrowser`
// always operate on the same underlying WebView.
var page = (function () {
    function current() {
        return RoCatBrowser.getInstance().page();
    }
    return {
        goto: function (url, options) { return current().goto(url, options); },
        waitForSelector: function (selector, timeout) { return current().waitForSelector(selector, timeout); },
        waitForTimeout: function (ms) { return current().waitForTimeout(ms); },
        click: function (selector) { return current().click(selector); },
        type: function (selector, text, delay) { return current().type(selector, text, delay); },
        fill: function (selector, text) { return current().fill(selector, text); },
        scrollTo: function (x, y) { return current().scrollTo(x, y); },
        scrollBottom: function () { return current().scrollBottom(); },
        evaluate: function (fn, args) { return current().evaluate(fn, args); },
        content: function () { return current().content(); },
        url: function () { return current().url(); },
        title: function () { return current().title(); },
        screenshot: function (options) { return current().screenshot(options); },
        cookies: function () { return current().cookies(); },
        setCookie: function (cookie) { return current().setCookie(cookie); },
        clearCookies: function () { return current().clearCookies(); },
        locator: function (selector) { return current().locator(selector); },
        goBack: function () { return current().goBack(); },
        goForward: function () { return current().goForward(); },
        reload: function () { return current().reload(); },
        stop: function () { return current().stop(); },
        close: function () { return current().close(); }
    };
})();
"""