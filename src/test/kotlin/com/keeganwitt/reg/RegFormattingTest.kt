package com.keeganwitt.reg

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RegFormattingTest : BasePlatformTestCase() {
    private val header = "Windows Registry Editor Version 5.00\n[HKEY_CURRENT_USER\\Example]\n"
    private fun format(source: String, margin: Int = 120): String {
        myFixture.configureByText(RegFileType, source)
        com.intellij.application.options.CodeStyle.getSettings(myFixture.file).setRightMargin(RegLanguage, margin)
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(myFixture.file) }
        return myFixture.editor.document.text
    }

    fun testAssignmentsStringsCommentsAndOrder() {
        val original = "$header  \"a b\"  =  \"c ; d\\\\path\"  ; keep comment\n; another comment\n\n@ = -\n\"d\" = DWORD:Ab\n"
        val expected = "$header\"a b\"=\"c ; d\\\\path\"  ; keep comment\n; another comment\n\n@=-\n\"d\"=DWORD:Ab\n"
        assertEquals(expected, format(original))
        assertEquals(expected, format(expected))
    }

    fun testHexWrapAndUnwrapPreservesBytesAndCase() {
        val bytes = List(20) { if (it % 2 == 0) "aB" else "01" }
        val original = "$header\"Data\" = hex: " + bytes.joinToString(" , ")
        val wrapped = format(original, 30)
        assertTrue(wrapped.contains(",\\\n  "))
        assertTrue(wrapped.lines().drop(2).all { it.length <= 30 })
        assertEmpty(PsiTreeUtil.collectElementsOfType(myFixture.file, PsiErrorElement::class.java))
        assertEquals(wrapped, format(wrapped, 30))
        assertEquals("$header\"Data\"=hex:" + bytes.joinToString(","), format(wrapped, 200))
    }

    fun testNarrowRightMarginIsRespected() {
        val formatted = format(header + "@=hex:" + List(20) { "01" }.joinToString(","), 16)
        assertTrue(formatted.lines().drop(2).all { it.length <= 16 })
        assertEquals(formatted, format(formatted, 16))
    }

    fun testMalformedEntriesAreUnchanged() {
        val invalid = "$header  \"x\"  =  hex:0g,\\\n[HKEY_CURRENT_USER\\Next]\n  @ = \"valid\""
        assertEquals(invalid.replace("  @ = \"valid\"", "@=\"valid\""), format(invalid))
    }

    fun testCommentsInsideContinuedDataPreventReflow() {
        val source = "$header  @ = hex:01,\\ ; keep this comment\n  02\n\"next\" = dword:1"
        assertEquals(source.replace("\"next\" = ", "\"next\"="), format(source, 30))
    }

    fun testSelectedRangeLeavesOtherEntriesUntouched() {
        val source = "$header  \"a\" = dword:1\n  \"b\" = hex:01,02\n  \"c\" = \"unchanged\""
        myFixture.configureByText(RegFileType, source)
        val start = source.indexOf("  \"b\"")
        val end = source.indexOf('\n', start)
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformatText(myFixture.file, start, end) }
        assertEquals(source.replace("  \"b\" = hex:01,02", "\"b\"=hex:01,02"), myFixture.editor.document.text)
    }

    fun testPartialEntryIsNotRewritten() {
        val source = "$header  \"Data\" = hex:01,02,03"
        myFixture.configureByText(RegFileType, source)
        val start = source.indexOf("01")
        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformatText(myFixture.file, start, start + 2) }
        assertEquals(source, myFixture.editor.document.text)
    }

    fun testFinalNewlineStateAndRepeatedSectionsArePreserved() {
        for (suffix in listOf("", "\n")) {
            val source = "REGEDIT4\n  [HKEY_CURRENT_USER]\n  @ = \"first\"\n\n[HKEY_CURRENT_USER]\n@ = \"second\"$suffix"
            val expected = source.replace("  [", "[").replace("  @ = ", "@=").replace("@ = ", "@=")
            assertEquals(expected, format(source))
        }
    }
}
