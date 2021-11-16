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
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.junit.Test
import snyk.container.KubernetesImageCacheIntegTest
import snyk.iac.IacResult
import snyk.oss.OssResult
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

@Suppress("FunctionName")
class SnykBulkFileListenerTest : BasePlatformTestCase() {

    private val imageCache get() = getKubernetesImageCache(project)

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
        val fakeIacResult = IacResult(null, null)
        val toolWindowPanel = project.service<SnykToolWindowPanel>()
        toolWindowPanel.currentIacResult = fakeIacResult

        myFixture.configureByText("k8s-deployment.yaml", "some text")

        assertNull(
            "cached IacResult should be dropped after IaC supported file changed",
            toolWindowPanel.currentIacResult
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

    @Test
    fun `test should update image cache when yaml file is changed`() {
        setUpContainerTest()
        val projectPath = Paths.get(project.basePath!!)
        if (!projectPath.exists()) {
            projectPath.createDirectories()
        }
        val path = Paths.get(project.basePath + File.separator + "kubernetes-test.yaml")
        path.toFile().createNewFile()
        Files.write(path, "\n".toByteArray(Charsets.UTF_8))
        val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(path)
        require(virtualFile != null)

        ApplicationManager.getApplication().runWriteAction {
            val file = PsiManager.getInstance(project).findFile(virtualFile)
            require(file != null)
            PsiDocumentManager.getInstance(project).getDocument(file)
                ?.setText(KubernetesImageCacheIntegTest().podYaml())
        }
        FileDocumentManager.getInstance().saveAllDocuments()

        val kubernetesWorkloadFiles = imageCache.getKubernetesWorkloadImages()

        assertNotNull(kubernetesWorkloadFiles)
        assertNotEmpty(kubernetesWorkloadFiles)
        assertEquals(1, kubernetesWorkloadFiles.size)
        assertEquals(path, kubernetesWorkloadFiles.first().psiFile.virtualFile.toNioPath())
        assertEquals("nginx:1.14.2", kubernetesWorkloadFiles.first().image)
        virtualFile.toNioPath().delete(true)
    }

    @Test
    fun `test should delete from cache when yaml file is deleted`() {
        setUpContainerTest()
        val file = myFixture.addFileToProject("kubernetes-test.yaml", "")

        ApplicationManager.getApplication().runWriteAction {
            PsiDocumentManager.getInstance(project).getDocument(file)
                ?.setText(KubernetesImageCacheIntegTest().podYaml())
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
