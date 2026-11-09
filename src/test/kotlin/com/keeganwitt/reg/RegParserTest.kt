package com.keeganwitt.reg

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RegParserTest : BasePlatformTestCase() {
    private fun errors(source: String): List<String> {
        val file = myFixture.configureByText(RegFileType, source)
        return PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).map { it.errorDescription }
    }

    private val section = "[HKEY_CURRENT_USER\\Software\\Example]"

    fun testValidFormatsAndValues() {
        val entries = """
            ; Comment
            $section
            @=""
            "String"="Unicode 例 ; text"
            "Escaped"="C:\\Path\\\"quoted\""
            "Dword"=DWORD:1
            "Max"=dword:ffffffff
            "Empty"=hex:
            "Binary"=hex:01,ff
            "Expand"=hex(2):00,00
            "Multi"=hex(7):00,00,00,00
            "Qword"=hex(b):00,00,00,00,00,00,00,00
            "UnknownType"=hex(abc):01
            "Continued"=hex:01,\
              02,\
              03
            "Deleted"=-
            @=-
            [-HKEY_CURRENT_USER\Software\Removed]
        """.trimIndent()
        for (header in RegLexer.HEADERS) {
            for (newline in listOf("\n", "\r\n")) {
                val source = "$header\n\n$entries".replace("\n", newline)
                assertEquals(source, emptyList<String>(), errors(source))
            }
        }
    }

    fun testMissingOrMalformedHeader() {
        for (source in listOf("", section, "Windows Registry Editor Version 6.00\n$section")) {
            assertTrue(errors(source).any { "header" in it })
        }
    }

    fun testMalformedEntries() {
        val invalid = listOf(
            "\"n\" \"v\"" to "Expected =",
            "\"n\"=\"unterminated" to "Expected closing quote",
            "\"n\"=dword:123456789" to "DWORD",
            "\"n\"=dword:" to "DWORD",
            "\"n\"=hex:1" to "two hexadecimal",
            "\"n\"=hex:00 01" to "comma between",
            "\"n\"=hex:00," to "after comma",
            "\"n\"=hex:,00" to "Expected hexadecimal byte",
            "\"n\"=hex:gg" to "Expected hexadecimal byte",
            "\"n\"=hex:00,\\" to "newline after continuation",
        )
        invalid.forEach { (entry, message) ->
            assertTrue("$entry: ${errors("REGEDIT4\n$section\n$entry")}",
                errors("REGEDIT4\n$section\n$entry").any { message in it })
        }
    }

    fun testValuesOutsideSectionsAndUnclosedSections() {
        assertTrue(errors("REGEDIT4\n@=\"a\"").any { "inside" in it })
        assertTrue(errors("REGEDIT4\n[HKEY_CURRENT_USER\n").any { "closing ]" in it })
    }

    fun testRecoveryAfterBrokenStringAndContinuation() {
        for (broken in listOf("\"n\"=\"unterminated", "\"n\"=hex:00,\\")) {
            val file = myFixture.configureByText(RegFileType, "REGEDIT4\n$section\n$broken\n$section\n@=\"recovered\"")
            assertTrue(PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).isNotEmpty())
            assertEquals(2, file.node.getChildren(null).count { it.elementType == RegTokens.SECTION })
            assertEquals(2, file.node.getChildren(null).count { it.elementType == RegTokens.ENTRY })
        }
    }

    fun testRecoveryToEntryAfterBrokenContinuation() {
        val file = myFixture.configureByText(RegFileType, "REGEDIT4\n$section\n\"n\"=hex:00,\\\n@=\"recovered\"")
        assertEquals(2, file.node.getChildren(null).count { it.elementType == RegTokens.ENTRY })
    }

    fun testIncrementalEditsReparseStringsAndContinuedHex() {
        myFixture.configureByText(RegFileType, "REGEDIT4\n$section\n\"n\"=\"abc<caret>\"\n@=hex:00,\\\n01")
        val offset = myFixture.caretOffset
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.deleteString(offset, offset + 1)
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java).isNotEmpty())
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(offset, "\"")
        }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEmpty(PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java))
    }
}
