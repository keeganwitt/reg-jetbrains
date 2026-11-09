package com.keeganwitt.reg

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Colors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import javax.swing.Icon

class RegSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = RegLexer()
    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        attributes[tokenType]?.let { arrayOf(it) } ?: emptyArray()

    companion object {
        val HEADER = key("HEADER", Colors.KEYWORD)
        val PATH = key("PATH", Colors.CLASS_NAME)
        val NAME = key("NAME", Colors.INSTANCE_FIELD)
        val STRING = key("STRING", Colors.STRING)
        val ESCAPE = key("ESCAPE", Colors.VALID_STRING_ESCAPE)
        val TYPE = key("TYPE", Colors.KEYWORD)
        val NUMBER = key("NUMBER", Colors.NUMBER)
        val COMMENT = key("COMMENT", Colors.LINE_COMMENT)
        val PUNCTUATION = key("PUNCTUATION", Colors.BRACKETS)
        val DELETE = key("DELETE", Colors.OPERATION_SIGN)
        private fun key(name: String, fallback: TextAttributesKey) = TextAttributesKey.createTextAttributesKey("REG_$name", fallback)
        private val attributes = mapOf(
            RegTokens.HEADER to HEADER, RegTokens.PATH to PATH,
            RegTokens.NAME to NAME, RegTokens.NAME_QUOTE to NAME, RegTokens.DEFAULT to NAME,
            RegTokens.STRING to STRING, RegTokens.VALUE_QUOTE to STRING, RegTokens.ESCAPE to ESCAPE,
            RegTokens.DWORD_TYPE to TYPE, RegTokens.HEX_TYPE to TYPE, RegTokens.NUMBER to NUMBER,
            RegTokens.COMMENT to COMMENT, RegTokens.DELETE to DELETE,
            RegTokens.LEFT_BRACKET to PUNCTUATION, RegTokens.RIGHT_BRACKET to PUNCTUATION,
            RegTokens.EQUALS to PUNCTUATION, RegTokens.COMMA to PUNCTUATION, RegTokens.CONTINUATION to PUNCTUATION,
            TokenType.BAD_CHARACTER to HighlighterColors.BAD_CHARACTER,
        )
    }
}

class RegSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = RegSyntaxHighlighter()
}

class RegColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Windows Registry"
    override fun getIcon(): Icon? = null
    override fun getHighlighter() = RegSyntaxHighlighter()
    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null
    override fun getColorDescriptors() = com.intellij.openapi.options.colors.ColorDescriptor.EMPTY_ARRAY
    override fun getAttributeDescriptors() = arrayOf(
        AttributesDescriptor("Header", RegSyntaxHighlighter.HEADER),
        AttributesDescriptor("Key path", RegSyntaxHighlighter.PATH),
        AttributesDescriptor("Value name", RegSyntaxHighlighter.NAME),
        AttributesDescriptor("String", RegSyntaxHighlighter.STRING),
        AttributesDescriptor("Escape sequence", RegSyntaxHighlighter.ESCAPE),
        AttributesDescriptor("Data type", RegSyntaxHighlighter.TYPE),
        AttributesDescriptor("Number", RegSyntaxHighlighter.NUMBER),
        AttributesDescriptor("Comment", RegSyntaxHighlighter.COMMENT),
        AttributesDescriptor("Punctuation", RegSyntaxHighlighter.PUNCTUATION),
        AttributesDescriptor("Deletion operator", RegSyntaxHighlighter.DELETE),
    )
    override fun getDemoText() = """
        Windows Registry Editor Version 5.00

        ; Example settings
        [HKEY_CURRENT_USER\Software\Example]
        @="Default value"
        "Path"="C:\\Example"
        "Enabled"=dword:00000001
        "Data"=hex:01,ab,\
          cd,ef
        "Expandable"=hex(2):00,00
        "Removed"=-
        [-HKEY_CURRENT_USER\Software\OldExample]
    """.trimIndent()
}
