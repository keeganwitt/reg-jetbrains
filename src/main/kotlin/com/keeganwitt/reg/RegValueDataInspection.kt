package com.keeganwitt.reg

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

class RegValueDataInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            val entry = element as? RegEntry ?: return
            val info = entry.valueInfo() ?: return
            val problem = info.problem ?: return
            holder.registerProblem(entry, "${info.type}: $problem",
                com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                TextRange(info.startOffset - entry.textRange.startOffset, info.endOffset - entry.textRange.startOffset))
        }
    }
}
