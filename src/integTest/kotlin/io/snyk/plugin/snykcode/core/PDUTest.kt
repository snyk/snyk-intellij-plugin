package io.snyk.plugin.snykcode.core

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import java.io.File

class PDUTest : LightPlatformTestCase() {

    fun testOutOfProjectFile_shouldBeCorrectlyConvertingPath2PsiAndVs() {
        val testFilePath = FileUtil.toSystemIndependentName(PlatformTestUtil.getCommunityPath()) + "/testFile"
        val testFile = File(testFilePath).apply { createNewFile() }
        try {
            val testVF = LocalFileSystem.getInstance().refreshAndFindFileByPath(testFilePath)
                ?: throw IllegalStateException()

            val testPsiFile = PsiManager.getInstance(project).findFile(testVF)
                ?: throw IllegalStateException()

            assertEquals(
                testFilePath.removePrefix("/"),
                PDU.instance.getDeepCodedFilePath(testPsiFile).removePrefix("/")
            )

            assertEquals(
                testPsiFile,
                PDU.instance.getFileByDeepcodedPath(testFilePath, project)
            )
        } finally {
            testFile.delete()
        }
    }
}
