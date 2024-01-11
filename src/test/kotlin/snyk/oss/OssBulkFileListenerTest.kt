package snyk.oss

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.resetSettings
import org.junit.Test

@Suppress("FunctionName")
class OssBulkFileListenerTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        resetSettings(project)
    }

    override fun tearDown() {
        resetSettings(project)
        try {
            super.tearDown()
        } catch (ignore: Exception) {
            // nothing to do
        }
    }

    @Test
    fun `test currentOssResults should be dropped when build file changed`() {
        val fakeOssResult = OssResult(null)
        getSnykCachedResults(project)?.currentOssResults = fakeOssResult

        myFixture.configureByText("package.json", "main project file")

        assertNull(
            "cached OssResult should be dropped after project build file changed",
            getSnykCachedResults(project)?.currentOssResults
        )
    }

    @Test
    fun `test keep currentOssResults when out-of-project build file content changed`() {
        val fakeOssResult = OssResult(null)

        val file = myFixture.addFileToProject("exclude/package.json", "")
        val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(file.virtualFile)!!
        PsiTestUtil.addExcludedRoot(module, file.virtualFile.parent)

        getSnykCachedResults(project)?.currentOssResults = fakeOssResult

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
            getSnykCachedResults(project)?.currentOssResults
        )
    }
}
