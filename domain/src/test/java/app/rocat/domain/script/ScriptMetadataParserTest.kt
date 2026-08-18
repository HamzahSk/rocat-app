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

    // --- Tahap 35: @settings metadata ---

    @Test
    fun `parses typed settings with defaults and constraints`() {
        val source = """
            // ==UserScript==
            // @name x
            // @settings username: string: default=admin, label=Username, placeholder=enter name
            // @settings enabled: boolean: default=true
            // @settings limit: number: default=10, min=1, max=100, step=5
            // @settings mode: select: default=auto, options=auto,manual,off
            // @settings token: password: default=
            // @settings notes: multiline: default=hello, rows=5
            // @settings accent: color: default=#ff0000
            // @settings contact: email: default=a@b.c
            // ==/UserScript==
        """.trimIndent()

        val settings = ScriptMetadataParser.parse(source).settings

        assertEquals(8, settings.size)

        val username = settings[0]
        assertEquals("username", username.key)
        assertEquals(ScriptSettingType.STRING, username.type)
        assertEquals("admin", username.defaultValue)
        assertEquals("Username", username.label)
        assertEquals("enter name", username.placeholder)

        val enabled = settings[1]
        assertEquals(ScriptSettingType.BOOLEAN, enabled.type)
        assertEquals("true", enabled.normalizedDefault)

        val limit = settings[2]
        assertEquals(ScriptSettingType.NUMBER, limit.type)
        assertEquals(1.0, limit.min!!, 0.0)
        assertEquals(100.0, limit.max!!, 0.0)
        assertEquals(5.0, limit.step!!, 0.0)
        assertEquals("10", limit.normalizedDefault)

        val mode = settings[3]
        assertEquals(ScriptSettingType.SELECT, mode.type)
        assertEquals(listOf("auto", "manual", "off"), mode.options)
        assertEquals("auto", mode.normalizedDefault)

        assertEquals(ScriptSettingType.PASSWORD, settings[4].type)
        assertEquals("", settings[4].normalizedDefault)

        val notes = settings[5]
        assertEquals(ScriptSettingType.MULTILINE, notes.type)
        assertEquals(5, notes.rows)
        assertEquals("hello", notes.normalizedDefault)

        assertEquals(ScriptSettingType.COLOR, settings[6].type)
        assertEquals("#ff0000", settings[6].normalizedDefault)

        assertEquals(ScriptSettingType.EMAIL, settings[7].type)
        assertEquals("a@b.c", settings[7].normalizedDefault)
    }

    @Test
    fun `normalizes boolean and numeric defaults`() {
        val source = """
            // ==UserScript==
            // @name x
            // @settings on: boolean: default=1
            // @settings off: boolean: default=0
            // @settings n: number: default=3.5
            // @settings bad: number: default=abc
            // ==/UserScript==
        """.trimIndent()

        val settings = ScriptMetadataParser.parse(source).settings

        assertEquals("true", settings[0].normalizedDefault)
        assertEquals("false", settings[1].normalizedDefault)
        assertEquals("3.5", settings[2].normalizedDefault)
        assertEquals("", settings[3].normalizedDefault)
    }

    @Test
    fun `label falls back to key`() {
        val source = """
            // ==UserScript==
            // @name x
            // @settings bare: string: default=x
            // ==/UserScript==
        """.trimIndent()

        val setting = ScriptMetadataParser.parse(source).settings.first()
        assertEquals("bare", setting.displayLabel)
    }

    @Test
    fun `unknown type falls back to string and malformed lines are skipped`() {
        val source = """
            // ==UserScript==
            // @name x
            // @settings ok: bogus: default=1
            // @settings missingtype
            // @settings nocolon
            // ==/UserScript==
        """.trimIndent()

        val settings = ScriptMetadataParser.parse(source).settings

        assertEquals(1, settings.size)
        assertEquals(ScriptSettingType.STRING, settings[0].type)
        assertEquals("1", settings[0].defaultValue)
    }

    @Test
    fun `no settings declared yields empty list`() {
        assertEquals(emptyList<ScriptSetting>(), ScriptMetadataParser.parse("function main() {}").settings)
    }
}
