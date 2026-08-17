package app.rocat.domain.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptMetadataParserTest {

    @Test
    fun `parses userscript block`() {
        val source = """
            // ==UserScript==
            // @name         Test Script
            // @version      2.1.0
            // @description  A test script
            // @author       Someone
            // @match        https://example.com/*
            // @match        https://*.example.org/*
            // @include      https://other.com/*
            // @icon         https://example.com/icon.png
            // ==/UserScript==
            function main() { return 1; }
        """.trimIndent()

        val meta = ScriptMetadataParser.parse(source)

        assertEquals("Test Script", meta.name)
        assertEquals("2.1.0", meta.version)
        assertEquals("A test script", meta.description)
        assertEquals("Someone", meta.author)
        assertEquals("https://example.com/icon.png", meta.icon)
        assertEquals(3, meta.matches.size)
        assertTrue(meta.matches.contains("https://example.com/*"))
        assertTrue(meta.matches.contains("https://other.com/*"))
    }

    @Test
    fun `parses loose tags without a block`() {
        val source = """
            // @name Loose Script
            // @version 0.0.1
            function main() {}
        """.trimIndent()

        val meta = ScriptMetadataParser.parse(source)

        assertEquals("Loose Script", meta.name)
        assertEquals("0.0.1", meta.version)
    }

    @Test
    fun `joins multiline description`() {
        val source = """
            // ==UserScript==
            // @description line one
            //              line two
            // @name x
            // ==/UserScript==
        """.trimIndent()

        val meta = ScriptMetadataParser.parse(source)

        assertEquals("line one\nline two", meta.description)
    }

    @Test
    fun `returns defaults for empty source`() {
        val meta = ScriptMetadataParser.parse("function main() {}")

        assertEquals("", meta.name)
        assertEquals("0.0.0", meta.version)
        assertEquals(emptyList<String>(), meta.matches)
    }

    @Test
    fun `parses category tag and falls back to legacy group`() {
        val source = """
            // ==UserScript==
            // @name x
            // @category Comics
            // ==/UserScript==
        """.trimIndent()
        assertEquals("Comics", ScriptMetadataParser.parse(source).category)

        val groupOnly = """
            // ==UserScript==
            // @name y
            // @group Utilities
            // ==/UserScript==
        """.trimIndent()
        assertEquals("Utilities", ScriptMetadataParser.parse(groupOnly).category)

        assertEquals("", ScriptMetadataParser.parse("function main() {}").category)
    }

    @Test
    fun `legacy iconurl tag is honoured`() {
        val source = """
            // ==UserScript==
            // @name x
            // @iconURL https://example.com/favicon.ico
            // ==/UserScript==
        """.trimIndent()

        val meta = ScriptMetadataParser.parse(source)

        assertEquals("https://example.com/favicon.ico", meta.icon)
    }
}
