package io.snyk.plugin.core

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.snyk.plugin.snykcode.core.PDU
import io.snyk.plugin.snykcode.core.SnykCodeFile
import java.io.File

@Suppress("FunctionName")
class PDUTest : LightPlatformTestCase() {

    fun testOutOfProjectFile_shouldBeCorrectlyConvertingPath2PsiAndVs() {
        val testFilePath = FileUtil.toSystemIndependentName(PlatformTestUtil.getCommunityPath()) + "/testFile"
        val testFile = File(testFilePath).apply { createNewFile() }
        try {
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(testFilePath)
                ?: throw IllegalStateException("Didn't find virtualfile for $testFilePath")
            val snykCodeFile = SnykCodeFile(project, virtualFile)

            val expected = testFilePath.removePrefix("/")
            val actual = PDU.instance.getDeepCodedFilePath(snykCodeFile).removePrefix("/")
            assertEquals(expected, actual)

            assertEquals(
                snykCodeFile, PDU.instance.getFileByDeepcodedPath(testFilePath, project)
            )
        } finally {
            testFile.delete()
        }
    }
}
