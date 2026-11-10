package com.keeganwitt.reg

import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class RegInlayHintsProvider : InlayHintsProvider, DumbAware {
    override fun createCollector(file: PsiFile, editor: Editor) = object : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            if (element !is RegEntry) return
            val value = element.valueInfo() ?: return
            if (!value.encoded) return
            sink.addPresentation(InlineInlayPosition(value.endOffset, true), tooltip = value.type,
                hintFormat = com.intellij.codeInsight.hints.declarative.HintFormat.default) {
                text(RegValueDecoder.bounded("${value.type} · ${value.preview}"))
            }
        }
    }
}
