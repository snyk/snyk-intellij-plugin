package snyk.oss

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.mockk
import io.mockk.unmockkAll
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.resetSettings
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper

class OssBulkFileListenerTest : BasePlatformTestCase() {
    private val lsMock = mockk<LanguageServer>(relaxed = true)
    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        languageServerWrapper.languageServer = lsMock
        languageServerWrapper.isInitialized = true
    }

    override fun tearDown() {
        resetSettings(project)
        unmockkAll()
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
