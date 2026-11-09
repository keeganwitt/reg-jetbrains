package com.keeganwitt.reg

import com.intellij.psi.tree.IElementType
import junit.framework.TestCase

class RegLexerTest : TestCase() {
    private data class Token(val type: IElementType, val text: String, val start: Int, val state: Int)

    private fun tokens(source: String, offset: Int = 0, state: Int = 0): List<Token> {
        val lexer = RegLexer()
        lexer.start(source, offset, source.length, state)
        val tokens = mutableListOf<Token>()
        while (lexer.tokenType != null) {
            assertTrue(lexer.tokenEnd > lexer.tokenStart)
            tokens += Token(lexer.tokenType!!, source.substring(lexer.tokenStart, lexer.tokenEnd), lexer.tokenStart, lexer.state)
            lexer.advance()
        }
        return tokens
    }

    fun testHeadersAndCaseInsensitiveTypes() {
        for (header in RegLexer.HEADERS) {
            val result = tokens("$header\r\n[HKEY_CURRENT_USER\\Software\\Demo]\r\n\"n\"=HeX(aB):01,af")
            assertEquals(RegTokens.HEADER, result.first().type)
            assertEquals("HeX(aB):", result.single { it.type == RegTokens.HEX_TYPE }.text)
            assertEquals(2, result.count { it.type == RegTokens.NUMBER })
        }
    }

    fun testSemicolonsAndWhitespaceInsideStringsAndPaths() {
        val result = tokens("[HKEY_CURRENT_USER\\a;b]\n\"a b\"=\"a ; b\"")
        assertEquals("HKEY_CURRENT_USER\\a;b", result.single { it.type == RegTokens.PATH }.text)
        assertFalse(result.any { it.type == RegTokens.COMMENT })
        assertEquals("a b", result.single { it.type == RegTokens.NAME }.text)
        assertEquals("a ; b", result.single { it.type == RegTokens.STRING }.text)
    }

    fun testRestartAtEveryTokenBoundary() {
        val source = "REGEDIT4\r\n[HKEY_CURRENT_USER\\例]\r\n\"a\"=\"hello \\\"world\\\"\\\\path\"\r\n\"b\"=hex:01,\\\r\n  ab,cd\r\n\"c\"=-"
        val all = tokens(source)
        all.forEachIndexed { index, token -> assertEquals(all.drop(index), tokens(source, token.start, token.state)) }
    }

    fun testMalformedStringDoesNotConsumeNextSection() {
        val result = tokens("\"a\"=\"broken\n[HKEY_CURRENT_USER\\Recovered]\n@=\"ok\"")
        assertTrue(result.any { it.type == RegTokens.LEFT_BRACKET })
        assertEquals("ok", result.last { it.type == RegTokens.STRING }.text)
    }

    fun testAllCharactersAreCovered() {
        val source = "\uFEFFREGEDIT4\n\"a\"=hex(zz):xx,#\\\n\"b\"=\"unfinished\\\n[]"
        assertEquals(source, tokens(source).joinToString("") { it.text })
    }
}
