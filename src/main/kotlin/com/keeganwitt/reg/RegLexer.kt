package com.keeganwitt.reg

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class RegLexer : LexerBase() {
    private var text: CharSequence = ""
    private var limit = 0
    private var start = 0
    private var end = 0
    private var mode = LINE_START
    private var nextMode = LINE_START
    private var type: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        text = buffer
        limit = endOffset
        start = startOffset
        end = startOffset
        mode = initialState
        locate()
    }

    override fun getState() = mode
    override fun getTokenType() = type
    override fun getTokenStart() = start
    override fun getTokenEnd() = end
    override fun getBufferSequence() = text
    override fun getBufferEnd() = limit

    override fun advance() {
        start = end
        mode = nextMode
        locate()
    }

    private fun locate() {
        type = null
        end = start
        nextMode = mode
        if (start >= limit) return
        val c = text[start]
        if (c == '\r' || c == '\n') {
            end = start + if (c == '\r' && start + 1 < limit && text[start + 1] == '\n') 2 else 1
            type = RegTokens.NEWLINE
            nextMode = if (mode == CONTINUED) HEX_LINE else LINE_START
            return
        }
        if (mode != NAME_STRING && mode != VALUE_STRING && mode != SECTION_PATH &&
            (c == ' ' || c == '\t' || c == '\uFEFF')) {
            takeWhile { it == ' ' || it == '\t' || it == '\uFEFF' }
            type = TokenType.WHITE_SPACE
            return
        }
        if (mode == NAME_STRING || mode == VALUE_STRING) {
            when (c) {
                '"' -> emit(if (mode == NAME_STRING) RegTokens.NAME_QUOTE else RegTokens.VALUE_QUOTE,
                    if (mode == NAME_STRING) AFTER_NAME else AFTER_VALUE)
                '\\' -> {
                    end = (start + 2).coerceAtMost(limit)
                    if (end > start + 1 && (text[start + 1] == '\r' || text[start + 1] == '\n')) end--
                    type = RegTokens.ESCAPE
                }
                else -> {
                    takeWhile { it != '"' && it != '\\' && it != '\r' && it != '\n' }
                    type = if (mode == NAME_STRING) RegTokens.NAME else RegTokens.STRING
                }
            }
            return
        }
        if (mode == SECTION_PATH) {
            when (c) {
                ']' -> emit(RegTokens.RIGHT_BRACKET, AFTER_VALUE)
                '-' -> if (start > 0 && text[start - 1] == '[') emit(RegTokens.DELETE) else path()
                else -> path()
            }
            return
        }
        if (c == ';') {
            takeWhile { it != '\r' && it != '\n' }
            type = RegTokens.COMMENT
            return
        }
        if (mode == LINE_START || mode == HEX_LINE) {
            if (c == '[') { emit(RegTokens.LEFT_BRACKET, SECTION_PATH); return }
            if (c == '"') { emit(RegTokens.NAME_QUOTE, NAME_STRING); return }
            if (c == '@') { emit(RegTokens.DEFAULT, AFTER_NAME); return }
            for (header in HEADERS) {
                if (matches(header)) {
                    end = start + header.length
                    type = RegTokens.HEADER
                    nextMode = AFTER_VALUE
                    return
                }
            }
        }
        when {
            c == '=' -> emit(RegTokens.EQUALS, VALUE_START)
            c == ']' -> emit(RegTokens.RIGHT_BRACKET)
            c == '[' -> emit(RegTokens.LEFT_BRACKET, SECTION_PATH)
            c == '"' -> emit(RegTokens.VALUE_QUOTE, VALUE_STRING)
            c == '-' -> emit(RegTokens.DELETE, AFTER_VALUE)
            c == ',' -> emit(RegTokens.COMMA)
            c == '\\' && (mode == HEX_DATA || mode == HEX_LINE) -> emit(RegTokens.CONTINUATION, CONTINUED)
            mode == VALUE_START && matches("dword:", true) -> {
                end = start + 6; type = RegTokens.DWORD_TYPE; nextMode = DWORD_DATA
            }
            mode == VALUE_START && hexType.matchAt(text, start) != null -> {
                val match = hexType.matchAt(text, start)!!
                end = match.range.last + 1; type = RegTokens.HEX_TYPE; nextMode = HEX_DATA
            }
            c in "0123456789abcdefABCDEF" && mode in setOf(HEX_DATA, HEX_LINE, DWORD_DATA) -> {
                takeWhile { it in "0123456789abcdefABCDEF" }
                type = RegTokens.NUMBER
                if (mode == HEX_LINE) nextMode = HEX_DATA
            }
            else -> {
                takeWhile { !it.isWhitespace() && it !in "[]=\";,\\" }
                if (end == start) end++
                type = RegTokens.WORD
            }
        }
    }

    private fun path() {
        takeWhile { it != ']' && it != '\r' && it != '\n' }
        type = RegTokens.PATH
    }

    private fun emit(token: IElementType, state: Int = mode) {
        end = start + 1
        type = token
        nextMode = state
    }

    private fun takeWhile(predicate: (Char) -> Boolean) {
        end = start
        while (end < limit && predicate(text[end])) end++
    }

    private fun matches(value: String, ignoreCase: Boolean = false): Boolean =
        start + value.length <= limit && text.subSequence(start, start + value.length).toString().equals(value, ignoreCase)

    companion object {
        val HEADERS = listOf("Windows Registry Editor Version 5.00", "REGEDIT4")
        private val hexType = Regex("hex(?:\\([0-9a-f]+\\))?:", RegexOption.IGNORE_CASE)
        private const val LINE_START = 0
        private const val AFTER_NAME = 1
        private const val VALUE_START = 2
        private const val SECTION_PATH = 3
        private const val NAME_STRING = 4
        private const val VALUE_STRING = 5
        private const val HEX_DATA = 6
        private const val DWORD_DATA = 7
        private const val AFTER_VALUE = 8
        private const val CONTINUED = 9
        private const val HEX_LINE = 10
    }
}
