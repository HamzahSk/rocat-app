package app.rocat.scripting.rhino

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Native DOM bridge for user scripts. Replaces external Node libraries (like
 * `cheerio`) so scripts only depend on primitives the app ships: [fetch] plus
 * this [RoCatDOM] bridge backed by Jsoup.
 *
 * The bridge is registered into the Rhino scope as the global `RoCatDOM` object
 * exposing:
 *  - `RoCatDOM.parse(html)`               -> root element wrapper
 *  - `RoCatDOM.select(html, selector)`    -> array of matched element wrappers
 *  - `RoCatDOM.selectText(html, sel)`     -> text of the first match
 *  - `RoCatDOM.selectAttr(html, sel, at)` -> attribute of the first match
 *  - `RoCatDOM.selectHtml(html, sel)`     -> outer HTML of the first match
 *  - `RoCatDOM.has(html, selector)`       -> whether a match exists
 *
 * Element wrappers expose the same subset Cheerio scripts commonly rely on:
 * `text`, `html`, `innerHtml`, `attrs`, `attr(name)`, `has(sel)`,
 * `find(sel)`, `textOf(sel)`, `attrOf(sel, name)`, `textsOf(sel)` and
 * `nextElement(sel)`.
 *
 * Keeping the core parsing here (plain Kotlin + Jsoup, no Rhino types) makes it
 * unit-testable and easy to re-use from other engines.
 */
object JsoupBridge {

    /** Parses [html] and returns a root element wrapper. */
    fun parse(html: String): JsoupElement =
        JsoupElement(Jsoup.parse(html))

    /** Returns the text of the first element matching [selector], or "" if none. */
    fun selectText(html: String, selector: String): String =
        Jsoup.parse(html).selectFirst(selector)?.text()?.trim() ?: ""

    /** Returns the [attr] value of the first element matching [selector], or "". */
    fun selectAttr(html: String, selector: String, attr: String): String =
        Jsoup.parse(html).selectFirst(selector)?.attr(attr) ?: ""

    /** Returns the outer HTML of the first element matching [selector], or "". */
    fun selectHtml(html: String, selector: String): String =
        Jsoup.parse(html).selectFirst(selector)?.outerHtml() ?: ""

    /** Returns whether [html] contains at least one element matching [selector]. */
    fun has(html: String, selector: String): Boolean =
        !Jsoup.parse(html).select(selector).isEmpty()

    /** Returns all elements of [html] matching [selector] as wrappers. */
    fun select(html: String, selector: String): List<JsoupElement> =
        Jsoup.parse(html).select(selector).map { JsoupElement(it) }
}

/**
 * Thin, script-friendly wrapper around a Jsoup [Element]. All read paths are
 * converted to plain Kotlin types (String/Boolean/List) so the engine can turn
 * them into native JS values without relying on LiveConnect reflection.
 */
class JsoupElement internal constructor(internal val node: Element) {

    /** Trimmed text content of the element itself. */
    val text: String get() = node.text().trim()

    /** Outer HTML of the element, including the element's own tags. */
    val html: String get() = node.outerHtml()

    /** Inner HTML of the element (children only, excluding its own tags). */
    val innerHtml: String get() = node.html()

    /** Attribute names present on the element. */
    val attrNames: List<String> get() = node.attributes().map { it.key }

    /** Value of [name] attribute, or "" when absent. */
    fun attr(name: String): String = node.attr(name)

    /** Whether the element itself matches [selector]. */
    fun has(selector: String): Boolean = node.`is`(selector)

    /** Whether any descendant matches [selector]. */
    fun contains(selector: String): Boolean = !node.select(selector).isEmpty()

    /** All descendant elements matching [selector]. */
    fun find(selector: String): List<JsoupElement> =
        node.select(selector).map { JsoupElement(it) }

    /** Text of the first descendant matching [selector], or "". */
    fun textOf(selector: String): String =
        node.selectFirst(selector)?.text()?.trim() ?: ""

    /** [attr] of the first descendant matching [selector], or "". */
    fun attrOf(selector: String, attr: String): String =
        node.selectFirst(selector)?.attr(attr) ?: ""

    /** Texts of every descendant matching [selector]. */
    fun textsOf(selector: String): List<String> =
        node.select(selector).map { it.text().trim() }

    /**
     * The next sibling element that matches [selector], walking forward until a
     * match is found (mirrors Cheerio's `$el.next(".cls")`), or null.
     */
    fun nextElement(selector: String): JsoupElement? {
        var sibling = node.nextElementSibling()
        while (sibling != null) {
            if (sibling.`is`(selector)) return JsoupElement(sibling)
            sibling = sibling.nextElementSibling()
        }
        return null
    }
}
