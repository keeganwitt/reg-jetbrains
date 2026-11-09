package com.keeganwitt.reg

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import javax.swing.Icon

object RegLanguage : Language("Windows Registry")

object RegFileType : LanguageFileType(RegLanguage) {
    override fun getName() = "Windows Registry"
    override fun getDescription() = "Windows Registry file"
    override fun getDefaultExtension() = "reg"
    override fun getIcon(): Icon? = null
}

object RegTokens {
    val FILE = IFileElementType(RegLanguage)
    val HEADER = token("HEADER")
    val NEWLINE = token("NEWLINE")
    val COMMENT = token("COMMENT")
    val LEFT_BRACKET = token("LEFT_BRACKET")
    val RIGHT_BRACKET = token("RIGHT_BRACKET")
    val PATH = token("PATH")
    val DELETE = token("DELETE")
    val DEFAULT = token("DEFAULT")
    val NAME_QUOTE = token("NAME_QUOTE")
    val VALUE_QUOTE = token("VALUE_QUOTE")
    val NAME = token("NAME")
    val STRING = token("STRING")
    val ESCAPE = token("ESCAPE")
    val EQUALS = token("EQUALS")
    val DWORD_TYPE = token("DWORD_TYPE")
    val HEX_TYPE = token("HEX_TYPE")
    val NUMBER = token("NUMBER")
    val COMMA = token("COMMA")
    val CONTINUATION = token("CONTINUATION")
    val WORD = token("WORD")
    val SECTION = token("SECTION")
    val ENTRY = token("ENTRY")
    val HEADER_NODE = token("HEADER_NODE")
    private fun token(name: String) = IElementType(name, RegLanguage)
}
