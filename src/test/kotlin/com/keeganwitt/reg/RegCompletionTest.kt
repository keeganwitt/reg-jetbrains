package com.keeganwitt.reg

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RegCompletionTest : BasePlatformTestCase() {
    private val prefix = "REGEDIT4\n[HKEY_CURRENT_USER\\Software\\Example]\n"

    private fun complete(source: String): Set<String> {
        myFixture.configureByText(RegFileType, source)
        return myFixture.complete(CompletionType.BASIC)?.map { it.lookupString }?.toSet() ?: emptySet()
    }

    fun testHeaderSuggestions() {
        assertEquals(RegLexer.HEADERS.toSet(), complete("<caret>"))
    }

    fun testHeaderInsertionReplacesMultiwordPrefix() {
        complete("Windows Registry E<caret>")
        if (myFixture.lookup != null) myFixture.finishLookup('\n')
        myFixture.checkResult("Windows Registry Editor Version 5.00")
    }

    fun testTypeInsertionPreservesExistingColonAndData() {
        complete("${prefix}@=hex<caret>:01")
        myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == "hex:" }
        myFixture.finishLookup('\n')
        myFixture.checkResult("${prefix}@=hex:<caret>01")
    }

    fun testDwordInsertionPreservesExistingData() {
        complete("${prefix}@=dword<caret>:1234")
        if (myFixture.lookup != null) myFixture.finishLookup('\n')
        myFixture.checkResult("${prefix}@=dword:<caret>1234")
    }

    fun testRootCompletionPreservesBracket() {
        val items = complete("REGEDIT4\n[HKEY_<caret>]")
        assertEquals(RegCompletionContributor.ROOTS.toSet(), items)
        myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == "HKEY_CURRENT_USER" }
        myFixture.finishLookup('\n')
        myFixture.checkResult("REGEDIT4\n[HKEY_CURRENT_USER]")
    }

    fun testValueFormsAndQuotedEqualsInName() {
        assertEquals(RegCompletionContributor.VALUES.map { it.first }.toSet(), complete("${prefix}\"a=b\"=<caret>"))
    }

    fun testNoCompletionInStringsCommentsOrBytes() {
        for (entry in listOf("; <caret>", "\"n\"=\"text<caret>\"", "\"n\"=hex:01,<caret>", "\"n\"=hex:01,\\\n02<caret>", "\"na<caret>me\"=\"v\"")) {
            assertEmpty(complete(prefix + entry))
        }
    }

    fun testStringInsertionPreservesExistingQuote() {
        complete("${prefix}@=<caret>\"")
        myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == "\"\"" }
        myFixture.finishLookup('\n')
        myFixture.checkResult("${prefix}@=\"<caret>\"")
    }

    fun testDwordInsertionSelectsEditableDigits() {
        complete("${prefix}@=<caret>")
        myFixture.lookup.currentItem = myFixture.lookupElements!!.first { it.lookupString == "dword:00000000" }
        myFixture.finishLookup('\n')
        assertEquals("00000000", myFixture.editor.selectionModel.selectedText)
        myFixture.type("ffffffff")
        myFixture.checkResult("${prefix}@=dword:ffffffff")
    }
}
