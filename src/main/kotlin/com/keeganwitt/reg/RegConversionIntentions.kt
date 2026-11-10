package com.keeganwitt.reg

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets.UTF_16LE

class RegDwordConversionIntention : RegConversionIntention("Convert DWORD representation", RegConversionKind.DWORD)
class RegStringConversionIntention : RegConversionIntention("Convert REG_SZ representation", RegConversionKind.STRING)

enum class RegConversionKind { DWORD, STRING }

abstract class RegConversionIntention(private val label: String, private val kind: RegConversionKind) : IntentionAction, DumbAware {
    override fun getText() = label
    override fun getFamilyName() = label
    override fun startInWriteAction() = true
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = file is RegFile && editor != null && edit(file, editor.caretModel.offset) != null
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file is RegFile && editor != null) edit(file, editor.caretModel.offset)?.apply(file)
    }
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val edit = edit(file, editor.caretModel.offset) ?: return IntentionPreviewInfo.EMPTY
        IntentionPreviewUtils.write<RuntimeException> { edit.apply(file) }
        return IntentionPreviewInfo.DIFF
    }

    fun edit(file: PsiFile, offset: Int): RegTextEdit? {
        if (file !is RegFile || file.textLength == 0 || offset !in 0..file.textLength) return null
        var element = file.findElementAt(offset.coerceAtMost(file.textLength - 1)) ?: return null
        if (element.node.elementType == RegTokens.NEWLINE && offset > 0) element = file.findElementAt(offset - 1) ?: return null
        if (element.node.elementType in listOf(TokenType.WHITE_SPACE, RegTokens.COMMENT, RegTokens.NEWLINE)) return null
        val entry = PsiTreeUtil.getParentOfType(element, RegEntry::class.java, false) ?: return null
        if (!entry.valid) return null
        val tokens = entry.valueTokens
        val first = tokens.firstOrNull() ?: return null
        val end = tokens.last().startOffset + tokens.last().textLength
        if (entry.tokens.any { it.elementType == RegTokens.COMMENT && it.startOffset in first.startOffset until end }) return null
        val replacement = when (kind) {
            RegConversionKind.DWORD -> dword(entry)
            RegConversionKind.STRING -> string(entry)
        } ?: return null
        return RegTextEdit(TextRange(first.startOffset, end), replacement)
    }

    private fun dword(entry: RegEntry): String? {
        val tokens = entry.valueTokens
        if (tokens.first().elementType == RegTokens.DWORD_TYPE) {
            val number = tokens.singleOrNull { it.elementType == RegTokens.NUMBER } ?: return null
            val bytes = number.text.padStart(8, '0').chunked(2).reversed().joinToString(",") { it.lowercase() }
            return "hex(4):$bytes"
        }
        if (hexType(entry) != 4.toBigInteger()) return null
        val bytes = bytes(entry)?.takeIf { it.size == 4 } ?: return null
        return "dword:" + BigInteger(1, bytes.reversedArray()).toString(16).padStart(8, '0')
    }

    private fun string(entry: RegEntry): String? {
        val header = entry.containingFile.node.findChildByType(RegTokens.HEADER_NODE) ?: return null
        if (PsiTreeUtil.hasErrorElements(header.psi) || header.findChildByType(RegTokens.HEADER)?.text != "Windows Registry Editor Version 5.00") return null
        if (entry.valueTokens.first().elementType == RegTokens.VALUE_QUOTE) {
            val string = entry.quotedStringValue() ?: return null
            if (string.length > RegValueDecoder.MAX_DECODE_BYTES / 2 - 1 || '\u0000' in string) return null
            val buffer = try {
                UTF_16LE.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap("$string\u0000"))
            } catch (_: CharacterCodingException) { return null }
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
            return "hex(1):" + bytes.joinToString(",") { "%02x".format(it.toInt() and 0xff) }
        }
        if (hexType(entry) != BigInteger.ONE) return null
        val bytes = bytes(entry) ?: return null
        val decoded = try {
            UTF_16LE.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) { return null }
        if (!decoded.endsWith('\u0000')) return null
        val text = decoded.dropLast(1)
        if (text.any { it == '\u0000' || it == '\r' || it == '\n' }) return null
        val quoted = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val originalFile = IntentionPreviewUtils.getOriginalFile(entry.containingFile) ?: entry.containingFile
        val charset = originalFile.viewProvider.virtualFile.charset
        val encodable = try { charset.newEncoder().canEncode(quoted) } catch (_: UnsupportedOperationException) { false }
        return quoted.takeIf { encodable }
    }

    private fun hexType(entry: RegEntry): BigInteger? {
        val first = entry.valueTokens.first()
        if (first.elementType != RegTokens.HEX_TYPE) return null
        return first.text.substringAfter('(', "3").substringBefore(')').substringBefore(':').toBigIntegerOrNull(16)
    }

    private fun bytes(entry: RegEntry): ByteArray? {
        val numbers = entry.valueTokens.filter { it.elementType == RegTokens.NUMBER }
        if (numbers.size > RegValueDecoder.MAX_DECODE_BYTES) return null
        return ByteArray(numbers.size) { numbers[it].text.toInt(16).toByte() }
    }
}
