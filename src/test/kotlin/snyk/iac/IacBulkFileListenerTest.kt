package snyk.iac

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.awaitility.Awaitility.await
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper
import snyk.iac.ui.toolwindow.IacFileTreeNode
import java.util.concurrent.TimeUnit

class IacBulkFileListenerTest : BasePlatformTestCase() {
    private val lsMock = mockk<LanguageServer>(relaxed = true)

    override fun setUp() {
        super.setUp()
        resetSettings(project)
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        languageServerWrapper.isInitialized = true
        languageServerWrapper.languageServer = lsMock
    }

    override fun tearDown() {
        resetSettings(project)
        try {
            super.tearDown()
        } catch (ignore: Exception) {
            // nothing to do as we're shutting down the test
        }
    }

    /** `filePath == null` is the case when we want to check if _any_ IaC file with issues been marked as obsolete */
    private fun iacCacheInvalidatedForFilePath(filePath: String?): Boolean {
        val iacCachedIssues = getSnykCachedResults(project)?.currentIacResult!!.allCliIssues!!
        return iacCachedIssues.any { iacFile ->
            (filePath == null || iacFile.targetFilePath == filePath) && iacFile.obsolete
        }
    }

    private fun isIacUpdateNeeded(): Boolean =
        getSnykCachedResults(project)?.currentIacResult?.iacScanNeeded ?: true

    private fun createFakeIacResultInCache(file: String, filePath: String) {
        val iacIssue = IacIssue(
            id = "SNYK-CC-TF-74",
            title = "Credentials are configured via provider attributes",
            lineNumber = 1,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile = IacIssuesForFile(listOf(iacIssue), file, filePath, "npm", null, project)
        val iacVulnerabilities = listOf(iacIssuesForFile)
        val fakeIacResult = IacResult(iacVulnerabilities)
        getSnykCachedResults(project)?.currentIacResult = fakeIacResult
        val rootIacIssuesTreeNode = project.service<SnykToolWindowPanel>().getRootIacIssuesTreeNode()
        rootIacIssuesTreeNode.add(IacFileTreeNode(iacIssuesForFile, project))
    }

    @Test
    fun `test currentIacResults should be dropped when IaC supported file changed`() {
        val file = "k8s-deployment.yaml"
        val filePath = "/src/$file"
        createFakeIacResultInCache(file, filePath)

        myFixture.configureByText(file, "some text")

        await().atMost(2, TimeUnit.SECONDS).until { iacCacheInvalidatedForFilePath(filePath) }
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
            iacCacheInvalidatedForFilePath(null)
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
            iacCacheInvalidatedForFilePath(null)
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
            iacCacheInvalidatedForFilePath(originalFilePath)
        )
    }

    @Test
    fun `test current IacResults should drop cache and mark IacScanNeeded when IaC supported file RENAMED`() {
        val originalFileName = "existing.yaml"
        val originalFile = myFixture.addFileToProject(originalFileName, "some text")
        val originalFilePath = originalFile.virtualFile.path
        createFakeIacResultInCache(originalFileName, originalFilePath)

        assertFalse(isIacUpdateNeeded())

        ApplicationManager.getApplication().runWriteAction {
            originalFile.virtualFile.rename(null, "renamed_filename.txt")
        }

        assertTrue(isIacUpdateNeeded())
        assertTrue(
            "Renamed IaC file with issues should been marked as obsolete here",
            iacCacheInvalidatedForFilePath(originalFilePath)
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
            iacCacheInvalidatedForFilePath(originalFile.virtualFile.path)
        )
    }

    @Test
    fun `test currentIacResults should keep it when out-of-project IaC supported file content changed`() {
        val fakeIacResult = IacResult(null)

        val file = myFixture.addFileToProject("exclude/k8s-deployment.yaml", "")
        val module = ProjectRootManager.getInstance(project).fileIndex.getModuleForFile(file.virtualFile)!!
        PsiTestUtil.addExcludedRoot(module, file.virtualFile.parent)

        getSnykCachedResults(project)?.currentIacResult = fakeIacResult

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
            getSnykCachedResults(project)?.currentIacResult
        )
    }
}
