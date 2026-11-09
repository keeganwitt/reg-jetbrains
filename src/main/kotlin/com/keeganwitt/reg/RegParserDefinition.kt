package com.keeganwitt.reg

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.TokenSet

class RegParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = RegLexer()
    override fun createParser(project: Project?) = RegParser()
    override fun getFileNodeType() = RegTokens.FILE
    override fun getCommentTokens() = TokenSet.create(RegTokens.COMMENT)
    override fun getStringLiteralElements() = TokenSet.create(RegTokens.STRING)
    override fun createElement(node: ASTNode) = ASTWrapperPsiElement(node)
    override fun createFile(viewProvider: FileViewProvider) = RegFile(viewProvider)
}

class RegFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, RegLanguage) {
    override fun getFileType() = RegFileType
}
