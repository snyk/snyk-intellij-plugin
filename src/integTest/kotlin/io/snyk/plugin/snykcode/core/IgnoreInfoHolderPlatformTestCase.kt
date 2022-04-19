package io.snyk.plugin.snykcode.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.snyk.plugin.getSnykCode
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

    fun testGenericDcIgnoreNotAddedOnProjectOpening() {
        val genericDcIgnoreFile = File(project.basePath + "/.dcignore")
        assertFalse(
            "Generic .dcignore file should NOT be added on project opening", genericDcIgnoreFile.exists()
        )
    }

    fun testGenericDcIgnoreAddedOnCodeScan() {
        val genericDcIgnoreFile = File(project.basePath + "/.dcignore")
        getSnykCode(project)?.scan()
        assertTrue(
            "Generic .dcignore file was NOT added to the project after Code scan", genericDcIgnoreFile.exists()
        )
    }

    fun testNodeModulesAreExcluded() {
        assertTrue("No path found for project", project.basePath != null)
        SnykCodeIgnoreInfoHolder.instance.createDcIgnoreIfNeeded(project)
        val fileToCheck = setUpTest()
        assertTrue(
            "Files inside `node_modules` should be excluded(ignored) by default",
            SnykCodeIgnoreInfoHolder.instance.isIgnoredFile(fileToCheck)
        )
    }

    private fun refreshVirtualFileSystem() {
        ApplicationManager.getApplication().runWriteAction {
            VirtualFileManager.getInstance().syncRefresh()
        }
    }

    private fun initiateAllMissedIgnoreFilesRescan() {
        // trigger Virtual File System to proceed .dcignore file appearance
        refreshVirtualFileSystem()
        SnykCodeUtils.instance.getAllSupportedFilesInProject(project, true, null)
    }

    fun testIgnoreCacheUpdateOnIgnoreFileContentChange() {
        SnykCodeIgnoreInfoHolder.instance.createDcIgnoreIfNeeded(project)

        val filePathToCheck = project.basePath + "/2.js"
        File(filePathToCheck).createNewFile()

        initiateAllMissedIgnoreFilesRescan()

        val file = findFile(project, filePathToCheck)
        assertFalse(
            "File $filePathToCheck should NOT be excluded(ignored) by default .dcignore",
            SnykCodeIgnoreInfoHolder.instance.isIgnoredFile(file)
        )

        val dcignoreFilePath = project.basePath + "/.dcignore"
        File(dcignoreFilePath).writeText("2.js")
        // trigger BulkFileListener to proceed .dcignore file change
        refreshVirtualFileSystem()
        assertTrue(
            "File $filePathToCheck should be excluded(ignored) by updated .dcignore",
            SnykCodeIgnoreInfoHolder.instance.isIgnoredFile(file)
        )
    }

    fun testIgnoreCacheUpdateOnProjectClose() {
        // to check default ignore behaviour still works
        val fileToCheck = setUpTest()

        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
        myProject = null // to avoid double disposing effort in tearDown
        assertFalse(
            "No files should be excluded(ignored) after Project closing -> caches cleanUp",
            SnykCodeIgnoreInfoHolder.instance.isIgnoredFile(fileToCheck)
        )
    }

    private fun setUpTest(): SnykCodeFile {
        val filePathToCheck = project.basePath + "/node_modules/1.js"
        File(filePathToCheck).parentFile.mkdir()
        File(filePathToCheck).createNewFile()

        val fileToCheck = findFile(project, filePathToCheck)
        initiateAllMissedIgnoreFilesRescan()
        return fileToCheck
    }

    private fun findFile(project: Project, path: String): SnykCodeFile {
        val file = StandardFileSystems.local().refreshAndFindFileByPath(path) ?: throw FileNotFoundException(path)
        return SnykCodeFile(project, file)
    }
}
