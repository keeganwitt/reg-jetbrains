package com.keeganwitt.reg

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.ErrorQuickFixProvider
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil

class RegSyntaxQuickFixProvider : ErrorQuickFixProvider, DumbAware {
    override fun registerErrorQuickFix(errorElement: PsiErrorElement, builder: HighlightInfo.Builder) {
        if (errorElement.containingFile !is RegFile) return
        RegSyntaxFixKind.entries.filter { it.error == errorElement.errorDescription }.forEach { kind ->
            val action = RegSyntaxQuickFix(errorElement, kind)
            val range = if (kind == RegSyntaxFixKind.ASSIGNMENT) PsiTreeUtil.getParentOfType(errorElement, RegEntry::class.java)?.textRange
                ?: errorElement.textRange else errorElement.textRange
            if (action.edit(errorElement.containingFile) != null) builder.registerFix(action, null, null, range, null)
        }
    }
}

enum class RegSyntaxFixKind(val error: String, val label: String) {
    MODERN_HEADER("Expected Windows Registry Editor Version 5.00 or REGEDIT4 header", "Insert Version 5.00 Registry header"),
    LEGACY_HEADER("Expected Windows Registry Editor Version 5.00 or REGEDIT4 header", "Insert REGEDIT4 Registry header"),
    SECTION_BRACKET("Expected closing ]", "Insert closing section bracket"),
    ASSIGNMENT("Expected =", "Insert missing assignment operator"),
}

class RegSyntaxQuickFix(error: PsiErrorElement, private val kind: RegSyntaxFixKind) : IntentionAction, DumbAware {
    private val pointer = SmartPointerManager.createPointer(error)
    override fun getText() = kind.label
    override fun getFamilyName() = "Windows Registry syntax"
    override fun startInWriteAction() = true
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = file is RegFile && edit(file) != null
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (file is RegFile) edit(file)?.apply(file)
    }
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val edit = edit(file) ?: return IntentionPreviewInfo.EMPTY
        IntentionPreviewUtils.write<RuntimeException> { edit.apply(file) }
        return IntentionPreviewInfo.DIFF
    }

    fun edit(file: PsiFile): RegTextEdit? {
        val original = pointer.element ?: return null
        val error = if (original.containingFile === file) original
        else if (IntentionPreviewUtils.isPreviewElement(file)) PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
            .firstOrNull { it.errorDescription == original.errorDescription && it.textOffset == original.textOffset }
        else null
        if (error == null || error.errorDescription != kind.error) return null
        return when (kind) {
            RegSyntaxFixKind.MODERN_HEADER, RegSyntaxFixKind.LEGACY_HEADER -> header(file)
            RegSyntaxFixKind.SECTION_BRACKET -> bracket(error)
            RegSyntaxFixKind.ASSIGNMENT -> assignment(error)
        }
    }

    private fun header(file: PsiFile): RegTextEdit? {
        val lexer = RegLexer()
        lexer.start(file.text)
        while (lexer.tokenType in listOf(TokenType.WHITE_SPACE, RegTokens.NEWLINE, RegTokens.COMMENT)) lexer.advance()
        if (lexer.tokenType != null && lexer.tokenType != RegTokens.LEFT_BRACKET) return null
        val offset = if (file.text.startsWith('\uFEFF')) 1 else 0
        val header = if (kind == RegSyntaxFixKind.LEGACY_HEADER) "REGEDIT4" else "Windows Registry Editor Version 5.00"
        return RegTextEdit(TextRange(offset, offset), "$header\n\n")
    }

    private fun bracket(error: PsiErrorElement): RegTextEdit? {
        val section = PsiTreeUtil.getParentOfType(error, RegSection::class.java) ?: return null
        val path = section.node.findChildByType(RegTokens.PATH) ?: return null
        if (';' in path.text || '[' in path.text || path.text.substringBefore('\\').uppercase(java.util.Locale.ROOT) !in RegCompletionContributor.ROOTS) return null
        val offset = path.startOffset + path.textLength
        return RegTextEdit(TextRange(offset, offset), "]")
    }

    private fun assignment(error: PsiErrorElement): RegTextEdit? {
        val entry = PsiTreeUtil.getParentOfType(error, RegEntry::class.java) ?: return null
        val errors = PsiTreeUtil.collectElementsOfType(entry, PsiErrorElement::class.java)
        if (errors.any { it.errorDescription !in listOf("Expected =", "Unexpected text after entry") }) return null
        val tokens = entry.tokens
        val nameEnd = if (tokens.firstOrNull()?.elementType == RegTokens.DEFAULT) tokens.first()
            else tokens.filter { it.elementType == RegTokens.NAME_QUOTE }.takeIf { it.size == 2 }?.last() ?: return null
        val offset = nameEnd.startOffset + nameEnd.textLength
        val relative = offset - entry.textRange.startOffset
        val candidate = "Windows Registry Editor Version 5.00\n[HKEY_CURRENT_USER]\n" + entry.text.replaceRange(relative, relative, "=")
        val parsed = PsiFileFactory.getInstance(entry.project).createFileFromText("assignment.reg", RegFileType, candidate)
        if (PsiTreeUtil.hasErrorElements(parsed) || parsed.children.filterIsInstance<RegEntry>().size != 1) return null
        return RegTextEdit(TextRange(offset, offset), "=")
    }
}

data class RegTextEdit(val range: TextRange, val replacement: String) {
    fun apply(file: PsiFile) {
        if (IntentionPreviewUtils.isPreviewElement(file)) {
            val replacementFile = PsiFileFactory.getInstance(file.project).createFileFromText(file.name, RegFileType,
                file.text.replaceRange(range.startOffset, range.endOffset, replacement))
            file.node.replaceAllChildrenToChildrenOf(replacementFile.node)
            return
        }
        val manager = PsiDocumentManager.getInstance(file.project)
        val document = manager.getDocument(file) ?: return
        document.replaceString(range.startOffset, range.endOffset, replacement)
        manager.commitDocument(document)
    }
}
