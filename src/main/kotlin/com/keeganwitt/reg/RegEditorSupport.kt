package com.keeganwitt.reg

import com.intellij.lang.BracePair
import com.intellij.lang.Commenter
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class RegCommenter : Commenter {
    override fun getLineCommentPrefix() = ";"
    override fun getBlockCommentPrefix(): String? = null
    override fun getBlockCommentSuffix(): String? = null
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}

class RegBraceMatcher : PairedBraceMatcher {
    override fun getPairs() = arrayOf(BracePair(RegTokens.LEFT_BRACKET, RegTokens.RIGHT_BRACKET, false))
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?) = true
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) = openingBraceOffset
}
