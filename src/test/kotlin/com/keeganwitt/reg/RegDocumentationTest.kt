package com.keeganwitt.reg

import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RegDocumentationTest : BasePlatformTestCase() {
    private val prefix = "Windows Registry Editor Version 5.00\n[HKEY_CURRENT_USER]\n"

    private fun documentation(source: String): String {
        myFixture.configureByText(RegFileType, source)
        val targets = RegDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset)
        assertEquals(1, targets.size)
        assertNotNull(targets.single().computeDocumentation())
        return targets.single().computeDocumentationHint()!!
    }

    fun testRegisteredProviderAndBothHeaders() {
        assertTrue(DocumentationTargetProvider.EP_NAME.extensionList.any { it is RegDocumentationTargetProvider })
        assertTrue(documentation("Windows Registry Editor Version <caret>5.00").contains("UTF-16LE"))
        assertTrue(documentation("REG<caret>EDIT4").contains("legacy code page"))
    }

    fun testRootAndDeletionDocumentation() {
        assertTrue(documentation("REGEDIT4\n[HKEY_CURRENT_<caret>USER\\Example]").contains("current user"))
        assertTrue(documentation("REGEDIT4\n[<caret>-HKEY_CURRENT_USER\\Example]").contains("key and its subkeys"))
        assertTrue(documentation(prefix + "@=<caret>-").contains("containing key remains"))
        assertTrue(documentation(prefix + "<caret>@=\"text\"").contains("default (unnamed)"))
    }

    fun testTypesAndDecodedValues() {
        assertTrue(documentation(prefix + "@=dwo<caret>rd:ffffffff").contains("4294967295"))
        assertTrue(documentation(prefix + "@=hex(<caret>b):01,00,00,00,00,00,00,00").contains("64-bit"))
        assertTrue(documentation(prefix + "@=hex(<caret>7):41,00,00,00,00,00").contains("sequence of strings"))
        assertTrue(documentation(prefix + "@=hex(<caret>abc):01").contains("payload is not interpreted"))
        assertTrue(documentation(prefix + "@=hex(<caret>2):25,00,54,00,45,00,4d,00,50,00,25,00,00,00").contains("%TEMP%"))
    }

    fun testEscapesAndContinuations() {
        assertTrue(documentation(prefix + "@=\"C:<caret>\\\\Path\"").contains("literal backslash"))
        assertTrue(documentation(prefix + "@=hex:01,<caret>\\\n02").contains("comma followed by a backslash"))
    }

    fun testFileContentsAreEscapedForHtml() {
        val html = documentation(prefix + "\"<script>&name\"=\"<caret><img src=x>\"")
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("<img src=x>"))
        assertTrue(html.contains("&lt;script&gt;&amp;name"))
        assertTrue(html.contains("&lt;img src=x&gt;"))
        assertTrue(documentation("REGEDIT4\n[HKEY_CURRENT_USER\\<caret><script>]").contains("&lt;script&gt;"))
    }

    fun testCommentsAndOtherLanguagesHaveNoTargets() {
        myFixture.configureByText(RegFileType, prefix + "; <caret>comment")
        assertEmpty(RegDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset))
        myFixture.configureByText(com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE, "<caret>text")
        assertEmpty(RegDocumentationTargetProvider().documentationTargets(myFixture.file, 0))
    }

    fun testMalformedDataAndPointerInvalidation() {
        assertTrue(documentation(prefix + "@=dword:<caret>gg").contains("32-bit"))
        val target = RegDocumentationTargetProvider().documentationTargets(myFixture.file, myFixture.caretOffset).single()
        val pointer = target.createPointer()
        assertNotNull(pointer.dereference())
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("") }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertNull(pointer.dereference())
    }
}
