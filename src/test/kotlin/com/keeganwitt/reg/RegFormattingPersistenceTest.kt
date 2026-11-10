package com.keeganwitt.reg

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class RegFormattingPersistenceTest : BasePlatformTestCase() {
    fun testSavingPreservesEncodingBomLineEndingsAndFinalNewline() {
        for (charset in listOf(StandardCharsets.UTF_8, StandardCharsets.UTF_16LE)) {
            for (newline in listOf("\n", "\r\n")) {
                for (suffix in listOf("", newline)) {
                    val path = Files.createTempFile("registry-format-", ".reg")
                    try {
                        val source = "Windows Registry Editor Version 5.00${newline}[HKEY_CURRENT_USER\\例]${newline}@ = \"café\"$suffix"
                        val bom = if (charset == StandardCharsets.UTF_16LE) byteArrayOf(0xff.toByte(), 0xfe.toByte()) else byteArrayOf()
                        Files.write(path, bom + source.toByteArray(charset))
                        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
                        val document = FileDocumentManager.getInstance().getDocument(file)!!
                        val psiFile = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(document)!!
                        assertEquals("Detected line separator before formatting", newline, FileDocumentManager.getInstance().getLineSeparator(file, project))
                        WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(psiFile) }
                        assertEquals(source.replace("@ = ", "@=").replace("\r\n", "\n"), document.text)
                        FileDocumentManager.getInstance().saveDocument(document)
                        (com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.getInstance() as com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl)
                            .flushPendingUpdates(file)
                        assertEquals(charset, file.charset)
                        val expected = bom + source.replace("@ = ", "@=").toByteArray(charset)
                        val actual = Files.readAllBytes(path)
                        assertEquals("$charset, newline=${newline.length}, final=${suffix.isNotEmpty()}", expected.toList(), actual.toList())
                    } finally { Files.deleteIfExists(path) }
                }
            }
        }
    }
}
