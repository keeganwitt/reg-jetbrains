package com.keeganwitt.reg

import com.intellij.codeInspection.LocalInspectionEP
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class RegValueDataInspectionTest : BasePlatformTestCase() {
    private val prefix = "Windows Registry Editor Version 5.00\n[HKEY_CURRENT_USER]\n"

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(RegValueDataInspection())
    }

    private fun warnings() = myFixture.doHighlighting(HighlightSeverity.WARNING)
        .filter { it.severity == HighlightSeverity.WARNING }

    private fun checkData(data: String, message: String?) {
        myFixture.configureByText(RegFileType, prefix + "@=$data ; trailing comment")
        val warnings = warnings()
        if (message == null) assertEmpty(warnings)
        else {
            assertEquals(1, warnings.size)
            assertTrue(warnings.single().description, warnings.single().description.contains(message))
            val warning = warnings.single()
            assertEquals(data, myFixture.editor.document.text.substring(warning.startOffset, warning.endOffset))
        }
    }

    fun testRegistrationAndDescription() {
        val extension = LocalInspectionEP.LOCAL_INSPECTION.extensionList.single { it.shortName == "RegValueData" }
        assertEquals("Registry value data", extension.displayName)
        assertTrue(extension.enabledByDefault)
        assertEquals("WARNING", extension.level)
        assertNotNull(javaClass.classLoader.getResource("inspectionDescriptions/RegValueData.html"))
    }

    fun testNumericSizesAndContinuedRange() {
        for (type in listOf("4", "5", "b")) {
            checkData("hex($type):01", if (type == "b") "expected 8" else "expected 4")
            checkData("hex($type):" + List(if (type == "b") 8 else 4) { "ff" }.joinToString(","), null)
        }
        checkData("hex(4):", "expected 4")
        checkData("hex(b):01,\\\n  02", "expected 8")
    }

    fun testStringEncodingAndTerminators() {
        checkData("hex(1):41", "invalid UTF-16LE")
        checkData("hex(2):00,d8,00,00", "invalid UTF-16LE")
        checkData("hex(1):41,00", "missing NUL terminator")
        checkData("hex(2):41,00,00,00,42,00,00,00", "embedded NUL")
        checkData("hex(1):00,00", null)
        checkData("hex(2):41,00,00,00", null)
        checkData("hex(7):41,00,00,00", "missing double NUL terminator")
        checkData("hex(7):41,00,00,00,00,00,42,00,00,00,00,00", "empty item")
        checkData("hex(7):00,00", null)
        checkData("hex(7):00,00,00,00", null)
        checkData("hex(7):41,00,00,00,42,00,00,00,00,00", null)
    }

    fun testIgnoredValuesAndSyntaxErrors() {
        for (data in listOf("hex:41", "hex(abc):41", "hex(100000000000000000000):41", "hex(6):41",
            "hex(4):gg", "hex(4):1", "hex(4):01,", "dword:ffffffff", "\"plain\"", "-")) {
            checkData(data, null)
        }
        myFixture.configureByText(RegFileType, prefix + "\"bad\"=hex(4):gg\n\"next\"=hex(b):01")
        assertEquals(1, warnings().size)
        assertTrue(warnings().single().description.contains("expected 8"))
    }

    fun testHeaderAndLegacyEncoding() {
        for (header in listOf("", "Windows Registry Editor Version 5.00 extra\n")) {
            myFixture.configureByText(RegFileType, header + "[HKEY_CURRENT_USER]\n@=hex(1):41")
            assertEmpty(warnings())
        }
        myFixture.configureByText(RegFileType, "REGEDIT4\n[HKEY_CURRENT_USER]\n@=hex(2):e9")
        myFixture.file.virtualFile.charset = StandardCharsets.UTF_8
        assertEmpty(warnings())
        myFixture.file.virtualFile.charset = Charset.forName("windows-1252")
        assertTrue(warnings().single().description.contains("missing NUL"))
        myFixture.configureByText(RegFileType, "REGEDIT4\n[HKEY_CURRENT_USER]\n@=hex(2):41,00")
        assertEmpty(warnings())
    }

    fun testSizeLimits() {
        checkData("hex(1):" + List(4096) { "41" }.joinToString(","), "missing NUL")
        checkData("hex(1):" + List(4097) { "41" }.joinToString(","), null)
        checkData("hex(4):" + List(4097) { "41" }.joinToString(","), "expected 4")
    }

    fun testWarningClearsAfterEditing() {
        myFixture.configureByText(RegFileType, prefix + "@=hex(4):01<caret>")
        assertEquals(1, warnings().size)
        myFixture.type(",00,00,00")
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEmpty(warnings())
    }
}
