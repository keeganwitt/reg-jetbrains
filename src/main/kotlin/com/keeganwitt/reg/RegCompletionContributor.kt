package com.keeganwitt.reg

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder

class RegCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val source = parameters.originalFile.viewProvider.document?.charsSequence ?: return
        val offset = parameters.offset.coerceAtMost(source.length)
        val lexer = RegLexer()
        lexer.start(source)
        var tokenAtCaret: com.intellij.psi.tree.IElementType? = null
        while (lexer.tokenType != null && lexer.tokenStart < offset) {
            tokenAtCaret = lexer.tokenType
            if (lexer.tokenEnd >= offset) break
            lexer.advance()
        }
        if (tokenAtCaret in listOf(RegTokens.COMMENT, RegTokens.STRING, RegTokens.ESCAPE, RegTokens.NAME,
                RegTokens.NUMBER, RegTokens.CONTINUATION)) return
        val before = source.subSequence(0, offset).toString()
        val line = before.substringAfterLast('\n').substringAfterLast('\r')
        val trimmed = line.trimStart('\uFEFF', ' ', '\t')
        if (!before.contains('\n') && !before.contains('\r') && !trimmed.startsWith("[")) {
            val prefix = trimmed
            val matches = result.withPrefixMatcher(prefix)
            RegLexer.HEADERS.forEach { matches.addElement(LookupElementBuilder.create(it).withTypeText("Registry file header")) }
            return
        }
        if (trimmed.startsWith("[") && ']' !in trimmed) {
            val path = trimmed.removePrefix("[").removePrefix("-")
            if ('\\' in path) return
            val matches = result.withPrefixMatcher(path)
            ROOTS.forEach { matches.addElement(LookupElementBuilder.create(it).withTypeText("Registry root")) }
            return
        }
        val equals = assignmentOffset(line) ?: return
        val prefix = line.substring(equals + 1).trimStart()
        if (prefix.any { it == '"' || it == ';' || it == ',' || it == '\\' } || ':' in prefix) return
        val matches = result.withPrefixMatcher(prefix)
        VALUES.forEach { (value, description) ->
            matches.addElement(LookupElementBuilder.create(value).withTypeText(description).withInsertHandler { context, _ ->
                val document = context.document
                if (value == "\"\"") {
                    if (context.tailOffset < document.textLength && document.charsSequence[context.tailOffset] == '"') {
                        document.deleteString(context.tailOffset, context.tailOffset + 1)
                    }
                    context.editor.caretModel.moveToOffset(context.startOffset + 1)
                } else if (value == "dword:00000000") {
                    if (context.tailOffset < document.textLength && document.charsSequence[context.tailOffset] == ':') {
                        document.deleteString(context.startOffset + 6, context.tailOffset + 1)
                    } else {
                        context.editor.selectionModel.setSelection(context.startOffset + 6, context.tailOffset)
                    }
                    context.editor.caretModel.moveToOffset(context.startOffset + 6)
                } else if (value.endsWith(':')) {
                    val tail = context.tailOffset
                    val suffix = document.charsSequence.subSequence(tail, document.textLength).takeWhile { it == ')' || it == ':' }.toString()
                    if (suffix.isNotEmpty() && value.endsWith(suffix)) document.deleteString(tail, tail + suffix.length)
                }
            })
        }
    }

    private fun assignmentOffset(line: String): Int? {
        var quoted = false
        var escaped = false
        line.forEachIndexed { index, c ->
            if (escaped) escaped = false
            else if (c == '\\' && quoted) escaped = true
            else if (c == '"') quoted = !quoted
            else if (c == '=' && !quoted) return index
            else if (c == ';' && !quoted) return null
        }
        return null
    }

    companion object {
        val ROOTS = listOf("HKEY_CLASSES_ROOT", "HKEY_CURRENT_USER", "HKEY_LOCAL_MACHINE", "HKEY_USERS", "HKEY_CURRENT_CONFIG")
        val VALUES = listOf(
            "\"\"" to "String (REG_SZ)",
            "dword:00000000" to "DWORD (REG_DWORD)",
            "hex:" to "Binary (REG_BINARY)",
            "hex(2):" to "Expandable string (REG_EXPAND_SZ)",
            "hex(7):" to "Multi-string (REG_MULTI_SZ)",
            "hex(b):" to "QWORD (REG_QWORD)",
            "-" to "Delete value",
        )
    }
}
