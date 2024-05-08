package io.snyk.plugin.core

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.snyk.plugin.snykcode.core.PDU
import io.snyk.plugin.SnykFile
import java.io.File

@Suppress("FunctionName")
class PDUTest : LightPlatformTestCase() {

    fun testOutOfProjectFile_shouldBeCorrectlyConvertingPath2PsiAndVs() {
        val testFilePath = FileUtil.toSystemIndependentName(PlatformTestUtil.getCommunityPath()) + "/testFile"
        val testFile = File(testFilePath).apply { createNewFile() }
        try {
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(testFilePath)
                ?: throw IllegalStateException("Didn't find virtualfile for $testFilePath")
            val snykFile = SnykFile(project, virtualFile)

            val expected = testFilePath.removePrefix("/")
            val actual = PDU.instance.getDeepCodedFilePath(snykFile).removePrefix("/")
            assertEquals(expected, actual)

            assertEquals(
                snykFile, PDU.instance.getFileByDeepcodedPath(testFilePath, project)
            )
        } finally {
            testFile.delete()
        }
    }
}
