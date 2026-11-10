package com.keeganwitt.reg

import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

class RegStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile) = object : TreeBasedStructureViewBuilder() {
        override fun createStructureViewModel(editor: Editor?) = StructureViewModelBase(psiFile, editor, RegStructureElement(psiFile))
            .withSuitableClasses(RegSection::class.java, RegEntry::class.java)
        override fun isRootNodeShown() = false
    }
}

class RegStructureElement(private val element: PsiElement) : StructureViewTreeElement {
    override fun getValue() = element
    override fun getChildren(): Array<StructureViewTreeElement> {
        if (!element.isValid) return emptyArray()
        val children = when (element) {
            is RegFile -> {
                var sectionSeen = false
                element.children.filter {
                    if (it is RegSection) sectionSeen = true
                    it is RegSection || (it is RegEntry && !sectionSeen)
                }
            }
            is RegSection -> element.entries
            else -> emptyList()
        }
        return children.map { RegStructureElement(it) }.toTypedArray()
    }

    override fun getPresentation() = object : ItemPresentation {
        override fun getPresentableText(): String = when (element) {
            is RegFile -> element.name
            is RegSection -> element.path
            is RegEntry -> element.valueName
            else -> ""
        }
        override fun getLocationString(): String? = when (element) {
            is RegSection -> if (element.deleted) "Delete key" else null
            is RegEntry -> element.valueInfo()?.let { listOfNotNull(it.type, it.preview).joinToString(" · ") } ?: "Invalid entry"
            else -> null
        }
        override fun getIcon(unused: Boolean): Icon? = when (element) {
            is RegSection -> com.intellij.icons.AllIcons.Nodes.Folder
            is RegEntry -> com.intellij.icons.AllIcons.Nodes.Property
            else -> null
        }
    }

    override fun canNavigate() = element.isValid && element.containingFile?.virtualFile != null
    override fun canNavigateToSource() = canNavigate()
    override fun navigate(requestFocus: Boolean) {
        if (canNavigate()) OpenFileDescriptor(element.project, element.containingFile.virtualFile, element.textOffset).navigate(requestFocus)
    }
}
