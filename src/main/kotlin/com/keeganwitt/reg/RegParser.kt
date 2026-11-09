package com.keeganwitt.reg

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

class RegParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        Reader(builder).parse(root)
        return builder.treeBuilt
    }

    private class Reader(private val b: PsiBuilder) {
        private var section = false
        private val token get() = b.tokenType
        private fun advance() = b.advanceLexer()
        private fun error(message: String) = b.error(message)
        private fun atEnd() = b.eof() || token == RegTokens.NEWLINE || token == RegTokens.COMMENT

        fun parse(root: IElementType) {
            val file = b.mark()
            skipLines()
            if (token == RegTokens.HEADER) {
                val header = b.mark()
                advance()
                finishLine()
                header.done(RegTokens.HEADER_NODE)
            } else error("Expected Windows Registry Editor Version 5.00 or REGEDIT4 header")
            while (!b.eof()) {
                when (token) {
                    RegTokens.NEWLINE, RegTokens.COMMENT -> advance()
                    RegTokens.LEFT_BRACKET -> parseSection()
                    else -> parseEntry()
                }
            }
            file.done(root)
        }

        private fun skipLines() {
            while (token == RegTokens.NEWLINE || token == RegTokens.COMMENT) advance()
        }

        private fun parseSection() {
            val node = b.mark()
            advance()
            if (token == RegTokens.DELETE) advance()
            if (token == RegTokens.PATH && !b.tokenText.isNullOrBlank()) advance()
            else error("Expected Registry key path")
            section = token == RegTokens.RIGHT_BRACKET
            if (section) advance() else error("Expected closing ]")
            finishLine()
            node.done(RegTokens.SECTION)
        }

        private fun parseEntry() {
            val node = b.mark()
            var recoveredAtNextLine = false
            if (!section) error("Value must appear inside a Registry key section")
            when (token) {
                RegTokens.NAME_QUOTE -> string(RegTokens.NAME_QUOTE)
                RegTokens.DEFAULT -> advance()
                else -> {
                    error("Expected quoted value name or @")
                    discardLine()
                    node.done(RegTokens.ENTRY)
                    return
                }
            }
            if (token == RegTokens.EQUALS) {
                advance()
                when (token) {
                    RegTokens.VALUE_QUOTE -> string(RegTokens.VALUE_QUOTE)
                    RegTokens.DELETE -> advance()
                    RegTokens.DWORD_TYPE -> dword()
                    RegTokens.HEX_TYPE -> recoveredAtNextLine = hex()
                    else -> error("Expected string, dword:, hex:, hex(type):, or -")
                }
            } else error("Expected =")
            if (!recoveredAtNextLine) finishLine()
            node.done(RegTokens.ENTRY)
        }

        private fun string(quote: IElementType) {
            advance()
            while (!atEnd() && token != quote) {
                if (token == RegTokens.ESCAPE && b.tokenText !in listOf("\\\\", "\\\"")) {
                    val escape = b.mark()
                    advance()
                    escape.error("Expected escaped backslash or quote")
                } else advance()
            }
            if (token == quote) advance() else error("Expected closing quote")
        }

        private fun dword() {
            advance()
            if (token == RegTokens.NUMBER && b.tokenText!!.length in 1..8) advance()
            else error("Expected 1 to 8 hexadecimal digits for DWORD")
        }

        private fun hex(): Boolean {
            advance()
            var needsByte = false
            var hasByte = false
            while (!atEnd()) {
                when (token) {
                    RegTokens.NUMBER -> {
                        if (hasByte && !needsByte) error("Expected comma between hexadecimal bytes")
                        if (b.tokenText!!.length != 2) error("Expected two hexadecimal digits per byte")
                        advance()
                        hasByte = true
                        needsByte = false
                    }
                    RegTokens.COMMA -> {
                        if (!hasByte || needsByte) error("Expected hexadecimal byte")
                        advance()
                        needsByte = true
                    }
                    RegTokens.CONTINUATION -> {
                        if (!hasByte || !needsByte) error("Expected comma before line continuation")
                        advance()
                        if (token != RegTokens.NEWLINE) {
                            error("Expected newline after continuation")
                            return false
                        }
                        advance()
                        if (atEnd() || token in listOf(RegTokens.LEFT_BRACKET, RegTokens.NAME_QUOTE, RegTokens.DEFAULT)) {
                            error("Expected hexadecimal byte after continuation")
                            return token in listOf(RegTokens.LEFT_BRACKET, RegTokens.NAME_QUOTE, RegTokens.DEFAULT)
                        }
                    }
                    else -> {
                        error("Expected hexadecimal byte or comma")
                        advance()
                    }
                }
            }
            if (needsByte) error("Expected hexadecimal byte after comma")
            return false
        }

        private fun finishLine() {
            if (!atEnd()) {
                error("Unexpected text after entry")
                discardLine()
            }
            if (token == RegTokens.COMMENT) advance()
            if (token == RegTokens.NEWLINE) advance()
        }

        private fun discardLine() {
            while (!b.eof() && token != RegTokens.NEWLINE) advance()
        }
    }
}
