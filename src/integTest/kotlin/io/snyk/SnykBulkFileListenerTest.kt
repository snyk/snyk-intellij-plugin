package io.snyk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.junit.Test
import snyk.iac.IacResult
import snyk.oss.OssResult

class SnykBulkFileListenerTest : BasePlatformTestCase() {

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
}
