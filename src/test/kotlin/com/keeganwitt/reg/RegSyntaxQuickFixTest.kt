package com.keeganwitt.reg

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RegSyntaxQuickFixTest : BasePlatformTestCase() {
    private val prefix = "Windows Registry Editor Version 5.00\n[HKEY_CURRENT_USER]\n"

    private fun apply(source: String, action: String, expected: String) {
        myFixture.configureByText(RegFileType, source)
        val actions = myFixture.filterAvailableIntentions(action)
        val diagnostics = com.intellij.psi.util.PsiTreeUtil.collectElementsOfType(myFixture.file, com.intellij.psi.PsiErrorElement::class.java)
            .map { error -> "${error.errorDescription}@${error.textOffset}: " + RegSyntaxFixKind.entries.filter { it.error == error.errorDescription }.map { RegSyntaxQuickFix(error, it).edit(myFixture.file) } }
        assertEquals("$action at ${myFixture.caretOffset}; $diagnostics", 1, actions.size)
        myFixture.checkPreviewAndLaunchAction(actions.single())
        myFixture.checkResult(expected)
    }

    fun testModernHeaderInsertionAndCommentPreservation() {
        apply("; keep comment\n[HKEY_CURRENT_USER<caret>]\n@=\"text\"", "Insert Version 5.00 Registry header",
            "Windows Registry Editor Version 5.00\n\n; keep comment\n[HKEY_CURRENT_USER]\n@=\"text\"")
    }

    fun testLegacyHeaderInsertionIsAnExplicitChoice() {
        apply("<caret>[HKEY_CURRENT_USER]\n@=hex:01", "Insert REGEDIT4 Registry header", "REGEDIT4\n\n[HKEY_CURRENT_USER]\n@=hex:01")
    }

    fun testEmptyFileHeaderFix() {
        apply("<caret>", "Insert Version 5.00 Registry header", "Windows Registry Editor Version 5.00\n\n")
    }

    fun testClosingSectionBracketPreservesFollowingEntries() {
        apply("REGEDIT4\n[HKEY_CURRENT_USER\\Example<caret>\n@=\"text\"\n[HKEY_CURRENT_USER\\Next]\n@=-",
            "Insert closing section bracket", "REGEDIT4\n[HKEY_CURRENT_USER\\Example]\n@=\"text\"\n[HKEY_CURRENT_USER\\Next]\n@=-")
    }

    fun testAssignmentInsertionForNamesDefaultAndTypes() {
        for ((name, value) in listOf("\"a=b\"" to "\"text\"", "@" to "dword:00000001", "\"binary\"" to "hex:01,02", "\"remove\"" to "-")) {
            apply(prefix + "$name <caret>$value ; keep", "Insert missing assignment operator", prefix + "$name= $value ; keep")
        }
    }

    fun testAmbiguousOrMalformedInputHasNoFix() {
        for ((source, action) in listOf(
            "Windows Registry Editor Version 6.00<caret>\n[HKEY_CURRENT_USER]" to "Insert Version 5.00 Registry header",
            "REGEDIT4\n[not a recognizable path<caret>" to "Insert closing section bracket",
            "REGEDIT4\n[HKEY_CURRENT_USER\\Example ; comment<caret>" to "Insert closing section bracket",
            prefix + "@ <caret>hex:gg" to "Insert missing assignment operator",
            prefix + "\"unterminated <caret>dword:1" to "Insert missing assignment operator",
        )) {
            myFixture.configureByText(RegFileType, source)
            assertEmpty(myFixture.filterAvailableIntentions(action))
        }
    }

    fun testUndoAndStaleQuickFix() {
        val source = "REGEDIT4\n[HKEY_CURRENT_USER"
        myFixture.configureByText(RegFileType, "$source<caret>")
        val action = myFixture.findSingleIntention("Insert closing section bracket")
        myFixture.launchAction(action)
        assertFalse(action.isAvailable(project, myFixture.editor, myFixture.file))
        myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_UNDO)
        myFixture.checkResult(source)
    }
}
