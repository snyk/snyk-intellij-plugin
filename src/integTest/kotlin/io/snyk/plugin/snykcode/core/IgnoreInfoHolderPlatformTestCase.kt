package io.snyk.plugin.snykcode.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.snyk.plugin.resetSettings
import java.io.File
import java.io.FileNotFoundException

// `Heavy` tests should be used due to Project partial re-usage/erasure:
// .dcignore is deleted between calls but Project is not reopened in BasePlatformTestCase
// See: https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html
class IgnoreInfoHolderPlatformTestCase : HeavyPlatformTestCase() {

    override fun setUp() {
        super.setUp()
        resetSettings(project)
        ModuleRootModificationUtil.addContentRoot(module, project.basePath!!)
    }

    override fun tearDown() {
        resetSettings(project)
        super.tearDown()
    }

    fun testGenericDcIgnoreAddedOnProjectOpening() {
        val genericDcIgnoreFile = File(project.basePath + "/.dcignore")
        assertTrue(
            "Generic .dcignore file was NOT added to the project",
            genericDcIgnoreFile.exists()
        )
    }

    fun testNodeModulesAreExcluded() {
        assertTrue("No path found for project", project.basePath != null)
        val filePathToCheck = project.basePath + "/node_modules/1.js"
        File(filePathToCheck).parentFile.mkdir()
        File(filePathToCheck).createNewFile()

        val psiFileToCheck = findPsiFile(filePathToCheck)
        initiateAllMissedIgnoreFilesRescan()
        assertTrue(
            "Files inside `node_modules` should be excluded(ignored) by default",
            SnykCodeIgnoreInfoHolder.instance.isIgnoredFile(psiFileToCheck)
        )
    }

    private fun initiateAllMissedIgnoreFilesRescan() {
        SnykCodeUtils.instance.getAllSupportedFilesInProject(project, true, null)
    }

    fun testIgnoreCacheUpdateOnIgnoreFileContentChange() {
        val filePathToCheck = project.basePath + "/2.js"
        File(filePathToCheck).createNewFile()

        initiateAllMissedIgnoreFilesRescan()
        assertFalse(
            "File $filePathToCheck should NOT be excluded(ignored) by default .dcignore",
            SnykCodeIgnoreInfoHolder.instance.isIgnoredFile(findPsiFile(filePathToCheck))
        )

        val dcignoreFilePath = project.basePath + "/.dcignore"
        File(dcignoreFilePath).writeText("2.js")
        // trigger BulkFileListener to proceed .dcignore file change
        ApplicationManager.getApplication().runWriteAction {
            VirtualFileManager.getInstance().syncRefresh()
        }
        assertTrue(
            "File $filePathToCheck should be excluded(ignored) by updated .dcignore",
            SnykCodeIgnoreInfoHolder.instance.isIgnoredFile(findPsiFile(filePathToCheck))
        )
    }

    fun testIgnoreCacheUpdateOnProjectClose() {
        // to check default ignore behaviour still works
        val filePathToCheck = project.basePath + "/node_modules/1.js"
        File(filePathToCheck).parentFile.mkdir()
        File(filePathToCheck).createNewFile()

        val psiFileToCheck = findPsiFile(filePathToCheck)
        initiateAllMissedIgnoreFilesRescan()
        assertTrue(
            "Files inside `node_modules` should be excluded(ignored) by default",
            SnykCodeIgnoreInfoHolder.instance.isIgnoredFile(psiFileToCheck)
        )

        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
        myProject = null // to avoid double disposing effort in tearDown

        assertFalse(
            "No files should be excluded(ignored) after Project closing -> caches cleanUp",
            SnykCodeIgnoreInfoHolder.instance.isIgnoredFile(psiFileToCheck)
        )
    }

    private fun findPsiFile(path: String): PsiFile {
        val vFileToCheck = StandardFileSystems.local().refreshAndFindFileByPath(path)
            ?: throw FileNotFoundException(path)
        return psiManager.findFile(vFileToCheck)
            ?: throw FileNotFoundException(path)
    }
}
