package com.keeganwitt.reg

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RegStructureViewTest : BasePlatformTestCase() {
    private fun root(source: String): RegStructureElement = RegStructureElement(myFixture.configureByText(RegFileType, source))
    private fun children(node: StructureViewTreeElement) = node.children.map { it as StructureViewTreeElement }

    fun testSectionsValuesRepeatedKeysAndDeletions() {
        val root = root("""
            REGEDIT4
            [HKEY_CURRENT_USER\Example]
            @="default"
            "Enabled"=dword:1
            "Removed"=-
            [-HKEY_CURRENT_USER\Old]
            [HKEY_CURRENT_USER\Example]
            "Again"=hex:01,02
        """.trimIndent())
        val sections = children(root)
        assertEquals(listOf("HKEY_CURRENT_USER\\Example", "HKEY_CURRENT_USER\\Old", "HKEY_CURRENT_USER\\Example"), sections.map { it.presentation.presentableText })
        assertEquals(listOf("(Default)", "Enabled", "Removed"), children(sections[0]).map { it.presentation.presentableText })
        assertEquals("REG_DWORD · 1 (0x00000001)", children(sections[0])[1].presentation.locationString)
        assertEquals("Delete value", children(sections[0])[2].presentation.locationString)
        assertEquals("Delete key", sections[1].presentation.locationString)
        assertEmpty(children(sections[1]))
        assertEquals("REG_BINARY · 2 bytes", children(sections[2]).single().presentation.locationString)
    }

    fun testOrphanAndInvalidEntriesRemainNavigable() {
        val root = root("REGEDIT4\n@=\"orphan\"\n[broken\n\"bad\"=hex:gg\n[HKEY_CURRENT_USER]\n@=\"ok\"")
        val elements = children(root)
        assertEquals("(Default)", elements.first().presentation.presentableText)
        assertEquals("Invalid entry", elements.first().presentation.locationString)
        assertTrue(elements.all { it.canNavigateToSource() })
        val last = children(elements.last()).single()
        last.navigate(false)
        assertEquals((last.value as RegEntry).textOffset, myFixture.caretOffset)
    }

    fun testRegisteredModelTracksCaretAndEdits() {
        myFixture.configureByText(RegFileType, "REGEDIT4\n[HKEY_CURRENT_USER]\n\"Value\"=dword:<caret>1")
        val builder = RegStructureViewFactory().getStructureViewBuilder(myFixture.file)
        val model = builder.createStructureViewModel(myFixture.editor)
        try {
            assertTrue(model.currentEditorElement is RegEntry)
            myFixture.type('2')
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
            val value = children(children(model.root).single()).single()
            assertEquals("REG_DWORD · 33 (0x00000021)", value.presentation.locationString)
        } finally { model.dispose() }
    }
}
