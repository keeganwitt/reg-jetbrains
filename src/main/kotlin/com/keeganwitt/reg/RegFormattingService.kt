package com.keeganwitt.reg

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AbstractDocumentFormattingService
import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class RegFormattingService : AbstractDocumentFormattingService() {
    override fun getFeatures() = setOf(FormattingService.Feature.FORMAT_FRAGMENTS)
    override fun canFormat(file: PsiFile) = file is RegFile

    override fun formatDocument(document: Document, formattingRanges: List<TextRange>, formattingContext: FormattingContext,
                                canChangeWhiteSpacesOnly: Boolean, quickFormat: Boolean) {
        val file = formattingContext.containingFile as? RegFile ?: return
        val manager = PsiDocumentManager.getInstance(file.project)
        manager.commitDocument(document)
        val settings = formattingContext.codeStyleSettings
        val margin = settings.getRightMargin(RegLanguage).coerceAtLeast(1)
        val indentSize = settings.getCommonSettings(RegLanguage).indentOptions?.CONTINUATION_INDENT_SIZE ?: 2
        val indent = " ".repeat(indentSize.coerceIn(0, 128))
        val edits = file.children.mapNotNull { element ->
            if (PsiTreeUtil.hasErrorElements(element)) return@mapNotNull null
            if (element is RegEntry) {
                val valueStart = element.valueTokens.firstOrNull()?.startOffset ?: return@mapNotNull null
                val valueEnd = element.valueTokens.last().startOffset + element.valueTokens.last().textLength
                if (element.tokens.any { it.elementType == RegTokens.COMMENT && it.startOffset in valueStart until valueEnd }) return@mapNotNull null
            }
            val tokens = when (element) {
                is RegEntry -> element.tokens.dropWhile { it.elementType != RegTokens.NAME_QUOTE && it.elementType != RegTokens.DEFAULT }
                    .takeWhile { it.elementType != RegTokens.COMMENT }.dropLastWhile { it.elementType == RegTokens.NEWLINE }
                is RegSection -> element.node.getChildren(null).filter { it.elementType in listOf(RegTokens.LEFT_BRACKET, RegTokens.PATH, RegTokens.DELETE, RegTokens.RIGHT_BRACKET) }
                else -> element.node.getChildren(null).filter { it.elementType == RegTokens.HEADER }
            }
            if (tokens.isEmpty()) return@mapNotNull null
            val contentStart = tokens.first().startOffset
            val contentEnd = tokens.last().startOffset + tokens.last().textLength
            val lineStart = document.getLineStartOffset(document.getLineNumber(contentStart))
            val start = if (document.charsSequence.subSequence(lineStart, contentStart).all { it == ' ' || it == '\t' } &&
                formattingRanges.any { it.contains(TextRange(lineStart, contentEnd)) }) lineStart else contentStart
            val range = TextRange(start, contentEnd)
            if (formattingRanges.none { it.contains(range) }) return@mapNotNull null
            val formatted = if (element is RegEntry) {
                val name = tokens.takeWhile { it.elementType != RegTokens.EQUALS }.joinToString("") { it.text }
                val values = element.valueTokens
                val prefix = "$name="
                if (values.firstOrNull()?.elementType == RegTokens.HEX_TYPE && !canChangeWhiteSpacesOnly) {
                    val bytes = values.filter { it.elementType == RegTokens.NUMBER }.map { it.text }
                    wrapBytes(prefix + values.first().text, bytes, margin, indent)
                } else {
                    prefix + values.joinToString("") { if (it.elementType == RegTokens.NEWLINE) "\n$indent" else it.text }
                }
            } else tokens.joinToString("") { it.text }
            if (document.charsSequence.subSequence(start, contentEnd).toString() == formatted) null else Edit(range, formatted)
        }
        for (edit in edits.sortedByDescending { it.range.startOffset }) {
            document.replaceString(edit.range.startOffset, edit.range.endOffset, edit.text)
        }
        manager.commitDocument(document)
    }

    private fun wrapBytes(prefix: String, bytes: List<String>, margin: Int, indent: String): String {
        if (bytes.isEmpty()) return prefix
        val lines = mutableListOf<String>()
        var line = prefix
        var bytesOnLine = 0
        bytes.forEachIndexed { index, byte ->
            val separator = if (bytesOnLine > 0) "," else ""
            val reserve = if (index < bytes.lastIndex) 2 else 0
            if (bytesOnLine > 0 && line.length + separator.length + byte.length + reserve > margin) {
                lines += "$line,\\"
                line = indent + byte
                bytesOnLine = 1
            } else {
                line += separator + byte
                bytesOnLine++
            }
        }
        lines += line
        return lines.joinToString("\n")
    }

    private data class Edit(val range: TextRange, val text: String)
}
