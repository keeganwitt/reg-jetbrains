package com.keeganwitt.reg

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import java.math.BigInteger
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class RegSection(node: ASTNode) : ASTWrapperPsiElement(node) {
    val path: String get() = node.findChildByType(RegTokens.PATH)?.text ?: "<invalid key>"
    val deleted: Boolean get() = node.findChildByType(RegTokens.DELETE) != null
    val entries: List<RegEntry> get() = generateSequence(nextSibling) { it.nextSibling }
        .takeWhile { it !is RegSection }.filterIsInstance<RegEntry>().toList()
}

class RegEntry(node: ASTNode) : ASTWrapperPsiElement(node) {
    val tokens: List<ASTNode> get() = node.getChildren(null).filter { it.elementType != TokenType.WHITE_SPACE }
    val valid: Boolean get() = !PsiTreeUtil.hasErrorElements(this)
    val valueTokens: List<ASTNode> get() = tokens.dropWhile { it.elementType != RegTokens.EQUALS }.drop(1)
        .filter { it.elementType != RegTokens.COMMENT }.dropLastWhile { it.elementType == RegTokens.NEWLINE }
    val valueName: String get() {
        val nameTokens = tokens.takeWhile { it.elementType != RegTokens.EQUALS }
        return if (nameTokens.any { it.elementType == RegTokens.DEFAULT }) "(Default)"
        else unescape(nameTokens.filter { it.elementType == RegTokens.NAME || it.elementType == RegTokens.ESCAPE }.joinToString("") { it.text })
            .ifEmpty { if (valid) "\"\"" else "<invalid value>" }
    }

    fun quotedStringValue(): String? {
        if (!valid || valueTokens.firstOrNull()?.elementType != RegTokens.VALUE_QUOTE) return null
        return unescape(valueTokens.filter { it.elementType == RegTokens.STRING || it.elementType == RegTokens.ESCAPE }.joinToString("") { it.text })
    }

    fun valueInfo(): RegValueInfo? {
        if (!valid) return null
        val value = valueTokens
        val first = value.firstOrNull() ?: return null
        val end = value.last().startOffset + value.last().textLength
        if (first.elementType == RegTokens.DELETE) return RegValueInfo("Delete value", null, false, end)
        if (first.elementType == RegTokens.VALUE_QUOTE) {
            val string = quotedStringValue() ?: return null
            return RegValueInfo("REG_SZ", RegValueDecoder.quoted(string), false, end)
        }
        if (first.elementType == RegTokens.DWORD_TYPE) {
            val number = value.singleOrNull { it.elementType == RegTokens.NUMBER } ?: return null
            return RegValueInfo("REG_DWORD", RegValueDecoder.number(BigInteger(number.text, 16), 8), true, end)
        }
        if (first.elementType != RegTokens.HEX_TYPE) return null
        val typeId = first.text.substringAfter('(', "3").substringBefore(')').substringBefore(':').toBigIntegerOrNull(16) ?: return null
        val numbers = value.filter { it.elementType == RegTokens.NUMBER }
        val type = RegValueDecoder.typeName(typeId)
        val decoded = if (numbers.size > RegValueDecoder.MAX_DECODE_BYTES) {
            RegDecodedValue("${RegValueDecoder.byteCount(numbers.size)} (preview omitted)",
                RegValueDecoder.numericSizeProblem(typeId, numbers.size))
        } else {
            val bytes = ByteArray(numbers.size) { numbers[it].text.toInt(16).toByte() }
            val header = containingFile.node.findChildByType(RegTokens.HEADER_NODE)
            val headerText = header?.findChildByType(RegTokens.HEADER)?.text
            if ((header == null || PsiTreeUtil.hasErrorElements(header.psi)) && typeId in listOf(1, 2, 7).map { it.toBigInteger() }) {
                RegDecodedValue("${RegValueDecoder.byteCount(bytes.size)} (valid file header required to decode text)")
            } else {
                val charset = if (headerText == "REGEDIT4") legacyCharset(bytes) else StandardCharsets.UTF_16LE
                RegValueDecoder.decode(typeId, bytes, charset)
            }
        }
        return RegValueInfo(type, decoded.preview, true, end, decoded.problem, first.startOffset)
    }

    private fun legacyCharset(bytes: ByteArray): Charset? {
        if (bytes.all { it >= 0 }) return StandardCharsets.US_ASCII
        val charset = containingFile.virtualFile?.charset ?: return null
        return charset.takeUnless { it == StandardCharsets.UTF_8 || it.name().startsWith("UTF-16") || it.name().startsWith("UTF-32") }
    }

    private fun unescape(text: String) = text.replace(Regex("\\\\([\\\\\"])"), "$1")
}

data class RegValueInfo(val type: String, val preview: String?, val encoded: Boolean, val endOffset: Int,
                        val problem: String? = null, val startOffset: Int = endOffset)
