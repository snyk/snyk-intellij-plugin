package io.snyk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.awaitility.Awaitility.await
import org.junit.Test
import snyk.container.KubernetesImageCache
import snyk.container.TestYamls
import snyk.iac.IacIssue
import snyk.iac.IacIssuesForFile
import snyk.iac.IacResult
import snyk.iac.ui.toolwindow.IacFileTreeNode
import snyk.oss.OssResult
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

@Suppress("FunctionName")
class SnykBulkFileListenerTest : BasePlatformTestCase() {

    private val imageCache get() = project.service<KubernetesImageCache>()

    override fun setUp() {
        super.setUp()
        resetSettings(project)
        isContainerEnabledRegistryValue.setValue(isContainerEnabledDefaultValue)
    }

    override fun tearDown() {
        resetSettings(project)
        removeDummyCliFile()
        isContainerEnabledRegistryValue.setValue(isContainerEnabledDefaultValue)
        super.tearDown()
    }

    private val isContainerEnabledRegistryValue = Registry.get("snyk.preview.container.enabled")
    private val isContainerEnabledDefaultValue: Boolean by lazy { isContainerEnabledRegistryValue.asBoolean() }

    private fun setUpContainerTest() {
        imageCache.clear()
        isContainerEnabledRegistryValue.setValue(true)
    }

    @Test
    fun testCurrentOssResults_shouldDropCachedResult_whenBuildFileChanged() {
        val fakeOssResult = OssResult(null, null)
        val toolWindowPanel = project.service<SnykToolWindowPanel>()
        toolWindowPanel.currentOssResults = fakeOssResult

        myFixture.configureByText("package.json", "main project file")

        assertNull(
            "cached OssResult should be dropped after project build file changed",
            toolWindowPanel.currentOssResults
        )
    }

    @Test
    fun testCurrentOssResults_shouldKeepCachedResult_whenOutOfProjectContentBuildFileChanged() {
        val fakeOssResult = OssResult(null, null)
        val toolWindowPanel = project.service<SnykToolWindowPanel>()

        val file = myFixture.addFileToProject("exclude/package.json", "")
        val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(file.virtualFile)!!
        PsiTestUtil.addExcludedRoot(module, file.virtualFile.parent)

        toolWindowPanel.currentOssResults = fakeOssResult

        // change and save excluded file to trigger BulkFileListener to proceed events
        ApplicationManager.getApplication().runWriteAction {
            PsiDocumentManager.getInstance(project).getDocument(file)?.setText("updated content")
        }
        FileDocumentManager.getInstance().saveAllDocuments()

        // dispose virtual pointer manually created before
        PsiTestUtil.removeExcludedRoot(module, file.virtualFile.parent)

        assertEquals(
            "cached OssResult should NOT be dropped after NON project build file changed",
            fakeOssResult,
            toolWindowPanel.currentOssResults
        )
    }

    @Test
    fun testCurrentIacResults_shouldDropCachedResult_whenIacSupportedFileChanged() {
        val file = "k8s-deployment.yaml"
        val filePath = "/src/$file"
        createFakeIacResultInCache(file, filePath)

        myFixture.configureByText(file, "some text")

        await().atMost(2, TimeUnit.SECONDS).until { cacheInvalidatedForFilePath(filePath) }
    }

    /**
     * `filePath == null` is the case when we want to check if _any_ IaC file with issues been marked as obsolete
     */
    private fun cacheInvalidatedForFilePath(filePath: String?): Boolean {
        val iacCachedIssues = project.service<SnykToolWindowPanel>().currentIacResult!!.allCliIssues!!
        return iacCachedIssues.any { iacFile ->
            (filePath == null || iacFile.targetFilePath == filePath) && iacFile.obsolete
        }
    }

    private fun isIacUpdateNeeded(): Boolean =
        project.service<SnykToolWindowPanel>().currentIacResult?.iacScanNeeded ?: true

    private fun createFakeIacResultInCache(file: String, filePath: String) {
        val iacIssue = IacIssue(
            id = "SNYK-CC-TF-74",
            title = "Credentials are configured via provider attributes",
            lineNumber = 1,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile = IacIssuesForFile(listOf(iacIssue), file, filePath, "npm")
        val iacVulnerabilities = listOf(iacIssuesForFile)
        val fakeIacResult = IacResult(iacVulnerabilities, null)
        val toolWindowPanel = project.service<SnykToolWindowPanel>()
        toolWindowPanel.currentIacResult = fakeIacResult
        val rootIacIssuesTreeNode = toolWindowPanel.getRootIacIssuesTreeNode()
        rootIacIssuesTreeNode.add(IacFileTreeNode(iacIssuesForFile, project))
    }

    @Test
    fun `test current IacResults should mark IacScanNeeded when IaC supported file CREATED`() {
        val existingFile = "existing.yaml"
        createFakeIacResultInCache(existingFile, "/src/$existingFile")

        assertFalse(isIacUpdateNeeded())

        myFixture.addFileToProject("new.yaml", "some text")

        assertTrue(isIacUpdateNeeded())
        assertFalse(
            "None of IaC file with issues should been marked as obsolete here",
            cacheInvalidatedForFilePath(null)
        )
    }

    @Test
    fun `test current IacResults should mark IacScanNeeded when IaC supported file COPIED`() {
        val originalFileName = "existing.yaml"
        val originalFile = myFixture.addFileToProject(originalFileName, "some text")
        createFakeIacResultInCache(originalFileName, originalFile.virtualFile.path)

        assertFalse(isIacUpdateNeeded())

        ApplicationManager.getApplication().runWriteAction {
            originalFile.virtualFile.copy(null, originalFile.virtualFile.parent, "copied.yaml")
        }

        assertTrue(isIacUpdateNeeded())
        assertFalse(
            "None of IaC file with issues should been marked as obsolete here",
            cacheInvalidatedForFilePath(null)
        )
    }

    @Test
    fun `test current IacResults should drop cache and mark IacScanNeeded when IaC supported file MOVED`() {
        val originalFileName = "existing.yaml"
        val originalFile = myFixture.addFileToProject(originalFileName, "some text")
        val originalFilePath = originalFile.virtualFile.path
        createFakeIacResultInCache(originalFileName, originalFilePath)

        assertFalse(isIacUpdateNeeded())

        ApplicationManager.getApplication().runWriteAction {
            val moveToDirVirtualFile = originalFile.virtualFile.parent.createChildDirectory(null, "subdir")
            originalFile.virtualFile.move(null, moveToDirVirtualFile)
        }

        assertTrue(isIacUpdateNeeded())
        assertTrue(
            "Moved IaC file with issues should been marked as obsolete here",
            cacheInvalidatedForFilePath(originalFilePath)
        )
    }

    @Test
    fun `test current IacResults should drop cache and mark IacScanNeeded when IaC supported file DELETED`() {
        val originalFileName = "existing.yaml"
        val originalFile = myFixture.addFileToProject(originalFileName, "some text")
        createFakeIacResultInCache(originalFileName, originalFile.virtualFile.path)

        assertFalse(isIacUpdateNeeded())

        ApplicationManager.getApplication().runWriteAction {
            originalFile.virtualFile.delete(null)
        }

        assertTrue(isIacUpdateNeeded())
        assertTrue(
            "Deleted IaC file with issues should been marked as obsolete here",
            cacheInvalidatedForFilePath(originalFile.virtualFile.path)
        )
    }

    @Test
    fun testCurrentIacResults_shouldKeepCachedResult_whenOutOfProjectContentIacSupportedFileChanged() {
        val fakeIacResult = IacResult(null, null)
        val toolWindowPanel = project.service<SnykToolWindowPanel>()

        val file = myFixture.addFileToProject("exclude/k8s-deployment.yaml", "")
        val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(file.virtualFile)!!
        PsiTestUtil.addExcludedRoot(module, file.virtualFile.parent)

        toolWindowPanel.currentIacResult = fakeIacResult

        // change and save excluded file to trigger BulkFileListener to proceed events
        ApplicationManager.getApplication().runWriteAction {
            PsiDocumentManager.getInstance(project).getDocument(file)?.setText("updated content")
        }
        FileDocumentManager.getInstance().saveAllDocuments()

        // dispose virtual pointer manually created before
        PsiTestUtil.removeExcludedRoot(module, file.virtualFile.parent)

        assertEquals(
            "cached IacResult should NOT be dropped after NON project build file changed",
            fakeIacResult,
            toolWindowPanel.currentIacResult
        )
    }

    private fun createNewFileInProjectRoot(name: String): File {
        val projectPath = Paths.get(project.basePath!!)
        if (!projectPath.exists()) {
            projectPath.createDirectories()
        }
        return File(project.basePath + File.separator + name).apply { createNewFile() }
    }

    @Test
    fun `test should update image cache when yaml file is changed`() {
        setUpContainerTest()
        val path = createNewFileInProjectRoot("kubernetes-test.yaml").toPath()
        Files.write(path, "\n".toByteArray(Charsets.UTF_8))
        val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(path)
        require(virtualFile != null)

        ApplicationManager.getApplication().runWriteAction {
            val file = PsiManager.getInstance(project).findFile(virtualFile)
            require(file != null)
            PsiDocumentManager.getInstance(project).getDocument(file)
                ?.setText(TestYamls.podYaml())
        }
        FileDocumentManager.getInstance().saveAllDocuments()

        val kubernetesWorkloadFiles = imageCache.getKubernetesWorkloadImages()

        assertNotNull(kubernetesWorkloadFiles)
        assertNotEmpty(kubernetesWorkloadFiles)
        assertEquals(1, kubernetesWorkloadFiles.size)
        assertEquals(path, kubernetesWorkloadFiles.first().psiFile.virtualFile.toNioPath())
        assertEquals("nginx:1.16.0", kubernetesWorkloadFiles.first().image)
        virtualFile.toNioPath().delete(true)
    }

    @Test
    fun `test Container should delete images from cache when yaml file is deleted`() {
        setUpContainerTest()
        val file = myFixture.addFileToProject("kubernetes-test.yaml", "")

        ApplicationManager.getApplication().runWriteAction {
            PsiDocumentManager.getInstance(project).getDocument(file)
                ?.setText(TestYamls.podYaml())
        }
        FileDocumentManager.getInstance().saveAllDocuments()
        assertNotEmpty(imageCache.getKubernetesWorkloadImages())

        ApplicationManager.getApplication().runWriteAction {
            file.virtualFile.delete(null)
        }
        FileDocumentManager.getInstance().saveAllDocuments()
        assertEmpty(imageCache.getKubernetesWorkloadImages())
    }
}
