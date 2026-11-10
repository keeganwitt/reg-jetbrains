package com.keeganwitt.reg

import com.intellij.model.Pointer
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil
import java.math.BigInteger

class RegDocumentationTargetProvider : DocumentationTargetProvider, DumbAware {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (file !is RegFile || file.textLength == 0 || offset !in 0..file.textLength) return emptyList()
        var element = file.findElementAt(offset.coerceAtMost(file.textLength - 1)) ?: return emptyList()
        if (element.node.elementType == RegTokens.NEWLINE && offset > 0) element = file.findElementAt(offset - 1) ?: return emptyList()
        if (element.node.elementType in listOf(TokenType.WHITE_SPACE, RegTokens.NEWLINE, RegTokens.COMMENT)) return emptyList()
        return if (RegDocumentation.render(element) == null) emptyList() else listOf(RegDocumentationTarget(element))
    }
}

class RegDocumentationTarget(private val element: PsiElement) : DocumentationTarget {
    override fun createPointer(): Pointer<RegDocumentationTarget> {
        val pointer = SmartPointerManager.createPointer(element)
        return Pointer { pointer.element?.takeIf { it.isValid }?.let { RegDocumentationTarget(it) } }
    }
    override fun computePresentation() = TargetPresentation.builder("Windows Registry").presentation()
    override fun computeDocumentationHint() = RegDocumentation.render(element)
    override fun computeDocumentation(): DocumentationResult? = RegDocumentation.render(element)?.let { DocumentationResult.documentation(it) }
}

object RegDocumentation {
    private val roots = mapOf(
        "HKEY_CLASSES_ROOT" to "File classes, associations, and COM registration information.",
        "HKEY_CURRENT_USER" to "Settings for the current user; maps to that user's branch under HKEY_USERS.",
        "HKEY_LOCAL_MACHINE" to "Computer-wide configuration, including software and system settings.",
        "HKEY_USERS" to "Settings associated with user profiles loaded on the computer.",
        "HKEY_CURRENT_CONFIG" to "Configuration for the current hardware profile.",
    )
    private val descriptions = mapOf(
        0 to "No defined data type. The bytes are shown without interpreting their contents.",
        1 to "A string (REG_SZ). Quoted strings use this type. In Version 5.00 files, hex(1): stores UTF-16LE bytes including a terminating NUL.",
        2 to "An expandable string (REG_EXPAND_SZ). It may contain references such as %TEMP%. The plugin displays these references literally and does not expand them.",
        3 to "Binary data (REG_BINARY). Each byte is written as two hexadecimal digits, separated by commas.",
        4 to "A 32-bit integer (REG_DWORD). dword: accepts 1 to 8 hexadecimal digits. hex(4): stores exactly four bytes in little-endian order.",
        5 to "A 32-bit integer stored as exactly four bytes in big-endian order.",
        6 to "A Registry symbolic-link target stored as a NUL-terminated Unicode string (REG_LINK).",
        7 to "A sequence of strings (REG_MULTI_SZ). Each string ends with NUL; an empty string terminates the list. In Version 5.00 files the bytes use UTF-16LE.",
        8 to "A resource list (REG_RESOURCE_LIST). The plugin displays its byte count without interpreting the resource descriptors.",
        9 to "A full resource descriptor (REG_FULL_RESOURCE_DESCRIPTOR). The plugin displays its byte count.",
        10 to "A resource requirements list (REG_RESOURCE_REQUIREMENTS_LIST). The plugin displays its byte count.",
        11 to "A 64-bit integer (REG_QWORD), stored as exactly eight little-endian bytes using hex(b):. The preview shows unsigned decimal and hexadecimal.",
    )

    fun render(element: PsiElement): String? {
        if (!element.isValid) return null
        val token = element.node.elementType
        if (token == RegTokens.HEADER) {
            val legacy = element.text == "REGEDIT4"
            val body = if (legacy) "Legacy Registry file format. Encoded string bytes use a legacy code page. Select the correct file encoding to decode non-ASCII data; the plugin does not guess the original code page."
                else "Modern Registry file format. Encoded string data uses UTF-16LE. File encoding is controlled separately by the IDE; Windows exports commonly include a UTF-16LE BOM."
            return page(element.text, body)
        }
        val section = PsiTreeUtil.getParentOfType(element, RegSection::class.java, false)
        if (section != null) {
            val root = section.path.substringBefore('\\').uppercase(java.util.Locale.ROOT)
            val operation = if (section.deleted) "A leading minus sign deletes this key and its subkeys when the file is imported."
                else "A bracketed section selects the key for the value entries that follow, up to the next section."
            return page(section.path, operation + " " + (roots[root] ?: "This is a Registry key path."),
                "https://learn.microsoft.com/en-us/windows/win32/sysinfo/predefined-keys")
        }
        val entry = PsiTreeUtil.getParentOfType(element, RegEntry::class.java, false) ?: return null
        val values = entry.valueTokens
        val first = values.firstOrNull()
        val type = when (first?.elementType) {
            RegTokens.VALUE_QUOTE -> BigInteger.ONE
            RegTokens.DWORD_TYPE -> 4.toBigInteger()
            RegTokens.HEX_TYPE -> first.text.substringAfter('(', "3").substringBefore(')').substringBefore(':').toBigIntegerOrNull(16)
            else -> null
        }
        val syntax = when (token) {
            RegTokens.ESCAPE -> "Inside quoted names and strings, use \\\\ for a literal backslash and \\\" for a literal quote. Other backslash escapes are not supported."
            RegTokens.CONTINUATION, RegTokens.COMMA -> "Hexadecimal bytes are separated by commas. To continue the list on another line, end the current line with a comma followed by a backslash."
            RegTokens.DEFAULT -> "@ denotes the key's default (unnamed) value. It is not a quoted value name."
            RegTokens.DELETE -> "A minus sign after = deletes this value when the file is imported; the containing key remains."
            else -> "Quoted value names or @ appear before =; the right side contains the value's data or - to delete it."
        }
        val description = if (first?.elementType == RegTokens.DELETE) "Delete this value while retaining its containing key."
            else if (type != null) descriptions.entries.firstOrNull { it.key.toBigInteger() == type }?.value
                ?: "This hexadecimal type identifier is accepted, but its payload is not interpreted by the plugin."
            else "Complete the value syntax to identify its type."
        val info = entry.valueInfo()
        val definition = entry.valueName + (type?.let { " : ${RegValueDecoder.typeName(it)}" } ?: "")
        return page(definition, "$syntax $description", "https://learn.microsoft.com/en-us/windows/win32/sysinfo/registry-value-types",
            info?.preview)
    }

    private fun page(title: String, text: String, reference: String? = null, preview: String? = null): String = buildString {
        append("<div class='definition'><pre>${escape(title)}</pre></div><div class='content'><p>${escape(text)}</p>")
        if (preview != null) append("<p>Decoded preview:</p><pre>${escape(preview)}</pre>")
        if (reference != null) append("<p><a href='$reference'>Microsoft Registry reference</a></p>")
        append("</div>")
    }

    private fun escape(text: String) = com.intellij.openapi.util.text.StringUtil.escapeXmlEntities(text)
}
