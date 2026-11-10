package com.keeganwitt.reg

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderExtensionBean
import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.codeInsight.hints.declarative.InlayPosition
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.lang.reflect.Proxy

class RegInlayHintsTest : BasePlatformTestCase() {
    private val prefix = "Windows Registry Editor Version 5.00\n[HKEY_CURRENT_USER]\n"
    private data class Hint(val offset: Int, val text: String, val tooltip: String?)

    private fun hints(): List<Hint> {
        val collector = RegInlayHintsProvider().createCollector(myFixture.file, myFixture.editor)
        val result = mutableListOf<Hint>()
        val sink = object : InlayTreeSink {
            override fun addPresentation(position: InlayPosition, payloads: List<InlayPayload>?, tooltip: String?, hintFormat: HintFormat,
                                         builder: PresentationTreeBuilder.() -> Unit) {
                val text = StringBuilder()
                val presentation = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(PresentationTreeBuilder::class.java)) { _, method, arguments ->
                    if (method.name == "text") text.append(arguments[0] as String)
                    null
                } as PresentationTreeBuilder
                builder(presentation)
                result += Hint((position as InlineInlayPosition).offset, text.toString(), tooltip)
            }
            override fun whenOptionEnabled(optionId: String, block: () -> Unit) = block()
        }
        com.intellij.psi.SyntaxTraverser.psiTraverser(myFixture.file).forEach { collector.collectFromElement(it, sink) }
        return result
    }

    fun testRegisteredProviderHasSettingsAndPreview() {
        val bean = InlayHintsProviderExtensionBean.EP.extensionList.single { it.providerId == "reg.decoded.values" }
        assertTrue(bean.isEnabledByDefault)
        assertEquals("Decoded Registry values", bean.getProviderName())
        assertNotNull(javaClass.classLoader.getResource("inlayProviders/reg.decoded.values/preview.reg"))
    }

    fun testEncodedValuesProduceHintsAtDataEnd() {
        val source = prefix + "\"String\"=hex(2):48,00,69,00,00,00 ; comment\n\"Count\"=dword:ffffffff\n\"Plain\"=\"visible\"\n\"Gone\"=-"
        myFixture.configureByText(RegFileType, source)
        val hints = hints()
        assertEquals(2, hints.size)
        assertEquals("REG_EXPAND_SZ · \"Hi\"", hints[0].text)
        assertEquals(source.indexOf(" ; comment"), hints[0].offset)
        assertEquals("REG_EXPAND_SZ", hints[0].tooltip)
        assertEquals("REG_DWORD · 4294967295 (0xffffffff)", hints[1].text)
        assertEquals(source.indexOf("\n\"Plain\""), hints[1].offset)
    }

    fun testContinuedDataAndLiveEdits() {
        myFixture.configureByText(RegFileType, prefix + "@=hex(2):48,00,\\\n  69,00,00,00\n\"Number\"=dword:<caret>1")
        assertEquals("REG_EXPAND_SZ · \"Hi\"", hints().first().text)
        myFixture.type('2')
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals("REG_DWORD · 33 (0x00000021)", hints().last().text)
    }

    fun testCommentWithinContinuedDataDoesNotTruncateTheValue() {
        myFixture.configureByText(RegFileType, prefix + "@=hex:01,\\ ; comment\n  02")
        assertEquals("REG_BINARY · 2 bytes", hints().single().text)
        assertEquals(myFixture.editor.document.textLength, hints().single().offset)
    }

    fun testInvalidDataUnknownTypesAndBounds() {
        myFixture.configureByText(RegFileType, prefix + "\"bad\"=hex:gg\n\"short\"=hex(b):01\n\"unknown\"=hex(abc):00\n\"large\"=hex(2):" + List(5000) { "00" }.joinToString(","))
        val hints = hints()
        assertEquals(3, hints.size)
        assertTrue(hints[0].text.contains("expected 8"))
        assertEquals("hex(abc) · 1 byte", hints[1].text)
        assertTrue(hints[2].text.contains("preview omitted"))
        assertTrue(hints.all { it.text.codePointCount(0, it.text.length) <= 80 })
    }

    fun testLegacyAsciiAndExplicitCodepage() {
        myFixture.configureByText(RegFileType, "REGEDIT4\n[HKEY_CURRENT_USER]\n@=hex(2):48,69,00\n\"Accent\"=hex(2):e9,00")
        myFixture.file.virtualFile.charset = java.nio.charset.StandardCharsets.UTF_8
        assertEquals("REG_EXPAND_SZ · \"Hi\"", hints()[0].text)
        assertTrue(hints()[1].text.contains("legacy file encoding"))
        myFixture.file.virtualFile.charset = java.nio.charset.Charset.forName("windows-1252")
        assertEquals("REG_EXPAND_SZ · \"é\"", hints()[1].text)
    }

    fun testMissingHeaderDoesNotGuessTextEncoding() {
        myFixture.configureByText(RegFileType, "[HKEY_CURRENT_USER]\n@=hex(2):48,00,00,00")
        assertTrue(hints().single().text.contains("valid file header required"))
    }
}
