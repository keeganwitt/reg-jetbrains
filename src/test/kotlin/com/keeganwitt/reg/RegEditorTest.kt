package com.keeganwitt.reg

import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.charset.StandardCharsets

class RegEditorTest : BasePlatformTestCase() {
    fun testUppercaseExtensionAndUtf8Loading() {
        val file = myFixture.tempDirFixture.createFile("example.REG", "REGEDIT4\n[HKEY_CURRENT_USER\\例]\n@=\"café\"")
        assertSame(RegFileType, file.fileType)
        myFixture.configureFromExistingVirtualFile(file)
        assertTrue(myFixture.file.text.contains("café"))
        assertEmpty(PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java))
    }

    fun testUtf16LeBomLoading() {
        val source = "Windows Registry Editor Version 5.00\r\n[HKEY_CURRENT_USER\\例]\r\n@=\"café\""
        val diskFile = java.nio.file.Files.createTempFile("registry-utf16-", ".reg")
        try {
            java.nio.file.Files.write(diskFile, byteArrayOf(0xff.toByte(), 0xfe.toByte()) + source.toByteArray(StandardCharsets.UTF_16LE))
            val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(diskFile)!!
            myFixture.configureFromExistingVirtualFile(file)
            assertEquals(source.replace("\r\n", "\n"), myFixture.file.text)
            assertEquals(StandardCharsets.UTF_16LE, file.charset)
            assertEmpty(PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java))
        } finally {
            java.nio.file.Files.deleteIfExists(diskFile)
        }
    }

    fun testHighlightingAndIncrementalContinuationEdit() {
        myFixture.configureByText(RegFileType, "REGEDIT4\n[HKEY_CURRENT_USER]\n@=hex:00,<caret>\\\n01\n\"s\"=\"abc\"")
        val before = highlightedTokens()
        assertTrue(before.contains(RegTokens.HEX_TYPE))
        assertTrue(before.contains(RegTokens.STRING))
        myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_DELETE)
        val after = highlightedTokens()
        assertTrue(after.contains(RegTokens.STRING))
        myFixture.type('\\')
        assertEquals(before, highlightedTokens())
    }

    private fun highlightedTokens(): List<com.intellij.psi.tree.IElementType> {
        val iterator: HighlighterIterator = (myFixture.editor as com.intellij.openapi.editor.ex.EditorEx).highlighter.createIterator(0)
        val result = mutableListOf<com.intellij.psi.tree.IElementType>()
        while (!iterator.atEnd()) {
            result += iterator.tokenType
            iterator.advance()
        }
        return result
    }

    fun testColorSettingsPreviewHasNoSyntaxErrors() {
        myFixture.configureByText(RegFileType, RegColorSettingsPage().demoText)
        assertEmpty(PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java))
        val highlighter = RegSyntaxHighlighter()
        assertNotEmpty(highlighter.getTokenHighlights(RegTokens.HEADER).toList())
        assertNotEmpty(highlighter.getTokenHighlights(RegTokens.STRING).toList())
        assertEquals(";", RegCommenter().lineCommentPrefix)
        assertEquals(RegTokens.RIGHT_BRACKET, RegBraceMatcher().pairs.single().rightBraceType)
    }

    fun testColorsResolveInLightAndDarkSchemes() {
        val manager = com.intellij.openapi.editor.colors.EditorColorsManager.getInstance()
        for (name in listOf("Default", "Darcula")) {
            val scheme = manager.getScheme(name)
            assertNotNull("Missing built-in color scheme $name", scheme)
            for (descriptor in RegColorSettingsPage().attributeDescriptors) {
                val attributes = scheme!!.getAttributes(descriptor.key)
                assertNotNull("${descriptor.displayName} in $name", attributes)
                val foreground = attributes.foregroundColor ?: scheme.defaultForeground
                assertFalse("${descriptor.displayName} invisible in $name", foreground == scheme.defaultBackground)
            }
        }
    }
}
