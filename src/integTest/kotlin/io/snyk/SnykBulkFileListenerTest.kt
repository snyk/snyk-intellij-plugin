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
import snyk.oss.OssResult

class SnykBulkFileListenerTest : BasePlatformTestCase() {

    @Test
    fun testBuildFileChanged() {
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
    fun testBuildFileChangedOutOfProjectContent() {
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
}
