package com.keeganwitt.reg

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RegConversionIntentionsTest : BasePlatformTestCase() {
    private val prefix = "Windows Registry Editor Version 5.00\n[HKEY_CURRENT_USER]\n"

    private fun convert(source: String, name: String): String {
        myFixture.configureByText(RegFileType, source)
        myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(name))
        return myFixture.editor.document.text
    }

    fun testDwordRoundTripsUnsignedValues() {
        for ((number, bytes) in listOf("1" to "01,00,00,00", "12345678" to "78,56,34,12", "ffffffff" to "ff,ff,ff,ff", "0" to "00,00,00,00")) {
            assertEquals(prefix + "@=hex(4):$bytes ; comment", convert(prefix + "@=dword:<caret>$number ; comment", "Convert DWORD representation"))
            assertEquals(prefix + "@=dword:${number.padStart(8, '0')} ; comment",
                convert(prefix + "@=hex(4):<caret>$bytes ; comment", "Convert DWORD representation"))
        }
    }

    fun testDwordContinuedHexAndLegacyFiles() {
        assertEquals("REGEDIT4\n[HKEY_CURRENT_USER]\n\"Name\"=dword:12345678",
            convert("REGEDIT4\n[HKEY_CURRENT_USER]\n\"Name\"=hex(4):78,<caret>56,\\\n  34,12", "Convert DWORD representation"))
    }

    fun testStringRoundTripUsesFullTextAndPreservesEscapesAndUnicode() {
        val value = "Hello café 例 😀 " + "long text ".repeat(20) + """C:\\Path \"quoted\" %TEMP%"""
        val original = prefix + "\"Name\"=\"$value\" ; keep comment\n\"Other\"=dword:1"
        val encoded = convert(original.replace("\"Name\"=", "\"Name\"=<caret>"), "Convert REG_SZ representation")
        assertTrue(encoded.contains("hex(1):"))
        assertEquals(original, convert(encoded.replace("hex(1):", "<caret>hex(1):"), "Convert REG_SZ representation"))
    }

    fun testEmptyAndSimpleStringConversion() {
        assertEquals(prefix + "@=hex(1):00,00", convert(prefix + "@=<caret>\"\"", "Convert REG_SZ representation"))
        assertEquals(prefix + "@=\"Hi\"", convert(prefix + "@=hex(1):<caret>48,00,69,00,00,00", "Convert REG_SZ representation"))
    }

    fun testUnsafeOrUnsupportedConversionsAreNotOffered() {
        for ((entry, action) in listOf(
            "@=hex(4):<caret>01,02" to "Convert DWORD representation",
            "@=hex(5):<caret>01,00,00,00" to "Convert DWORD representation",
            "@=dword:<caret>123456789" to "Convert DWORD representation",
            "@=hex(4):01,<caret>\\ ; comment\n00,00,00" to "Convert DWORD representation",
            "@=hex(1):<caret>41,00" to "Convert REG_SZ representation",
            "@=hex(1):<caret>41,00,00" to "Convert REG_SZ representation",
            "@=hex(1):<caret>00,d8,00,00" to "Convert REG_SZ representation",
            "@=hex(1):<caret>0a,00,00,00" to "Convert REG_SZ representation",
            "@=hex(1):<caret>41,00,00,00,42,00,00,00" to "Convert REG_SZ representation",
            "@=hex(2):<caret>41,00,00,00" to "Convert REG_SZ representation",
            "@=hex(b):<caret>01,00,00,00,00,00,00,00" to "Convert DWORD representation",
            "; <caret>@=dword:1" to "Convert DWORD representation",
        )) {
            myFixture.configureByText(RegFileType, prefix + entry)
            assertEmpty(myFixture.filterAvailableIntentions(action))
        }
    }

    fun testLegacyStringsAndOversizedValuesAreNotConverted() {
        for (source in listOf("REGEDIT4\n[HKEY_CURRENT_USER]\n@=<caret>\"text\"", prefix + "@=<caret>\"${"a".repeat(2048)}\"",
            prefix + "@=hex(1):<caret>" + List(4098) { "00" }.joinToString(","))) {
            myFixture.configureByText(RegFileType, source)
            assertEmpty(myFixture.filterAvailableIntentions("Convert REG_SZ representation"))
        }
    }

    fun testPreviewDoesNotChangeOriginalDocumentAndActionIsUndoable() {
        val source = prefix + "@=dword:1"
        myFixture.configureByText(RegFileType, source.replace("dword:", "<caret>dword:"))
        val action = myFixture.findSingleIntention("Convert DWORD representation")
        assertTrue(myFixture.getIntentionPreviewText(action)!!.contains("hex(4):01,00,00,00"))
        myFixture.checkResult(source)
        myFixture.launchAction(action)
        myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_UNDO)
        myFixture.checkResult(source)
    }

    fun testQuotedConversionRespectsTheCurrentFileEncoding() {
        myFixture.configureByText(RegFileType, prefix + "@=hex(1):<caret>8b,4f,00,00")
        myFixture.file.virtualFile.charset = java.nio.charset.StandardCharsets.US_ASCII
        assertEmpty(myFixture.filterAvailableIntentions("Convert REG_SZ representation"))
        myFixture.configureByText(RegFileType, prefix + "@=hex(1):<caret>e9,00,00,00")
        val charset = java.nio.charset.Charset.forName("windows-1252")
        myFixture.file.virtualFile.charset = charset
        myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention("Convert REG_SZ representation"))
        myFixture.checkResult(prefix + "@=\"é\"")
        assertEquals(charset, myFixture.file.virtualFile.charset)
    }
}
