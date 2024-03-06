package snyk.iac.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase
import snyk.iac.IacIssue
import snyk.iac.IacIssuesForFile
import snyk.iac.IacResult

class IacHclAnnotatorTest : IacBaseAnnotatorCase() {

    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val terraformManifestFile = "terraform-main.tf"
    lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    override fun setUp() {
        super.setUp()
        file = myFixture.copyFileToProject(terraformManifestFile)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
    }

    fun `test doAnnotation should not return any annotations if no iac issue exists`() {
        every { snykCachedResults.currentIacResult } returns null

        val annotations = IacHclAnnotator().getIssues(psiFile)

        assertEquals(0, annotations.size)
    }

    fun `test getIssues should return one annotation if only one iac issue exists`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine1()

        val annotations = IacHclAnnotator().getIssues(psiFile)

        TestCase.assertEquals(1, annotations.size)
    }

    fun `test apply should trigger newAnnotation call`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine1()

        IacHclAnnotator().apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    fun `test textRange without leading whitespace for HCLIdentifier`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine1()

        val expectedRange = TextRange.create(0, 8)
        val actualRange = IacHclAnnotator().textRange(
            psiFile,
            snykCachedResults.currentIacResult?.allCliIssues!!.first().infrastructureAsCodeIssues.first()
        )

        assertEquals(expectedRange, actualRange)
    }

    fun `test textRange with leading whitespace for HCLIdentifier`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine8()

        val expectedRange = TextRange.create(201, 209)
        val actualRange = IacHclAnnotator().textRange(
            psiFile,
            snykCachedResults.currentIacResult?.allCliIssues!!.first().infrastructureAsCodeIssues.first()
        )

        assertEquals(expectedRange, actualRange)
    }

    fun `test textRange for leading whitespace for HCLProperty`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine18()

        val expectedRange = TextRange.create(446, 453)
        val actualRange = IacHclAnnotator().textRange(
            psiFile,
            snykCachedResults.currentIacResult?.allCliIssues!!.first().infrastructureAsCodeIssues.first()
        )

        assertEquals(expectedRange, actualRange)
    }

    private fun createIacResultWithIssueOnLine1(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-TF-74",
            title = "Credentials are configured via provider attributes",
            lineNumber = 1,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile =
            IacIssuesForFile(listOf(iacIssue), terraformManifestFile, file.path, "terraform", null, project)
        return IacResult(listOf(iacIssuesForFile))
    }

    private fun createIacResultWithIssueOnLine8(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-TF-8",
            title = "Snyk - IAM password should contain lowercase",
            lineNumber = 8,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile =
            IacIssuesForFile(listOf(iacIssue), terraformManifestFile, file.path, "terraform", null, project)
        return IacResult(listOf(iacIssuesForFile))
    }

    private fun createIacResultWithIssueOnLine18(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-TF-1",
            title = "Snyk - Security Group allows open ingress",
            lineNumber = 18,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile =
            IacIssuesForFile(listOf(iacIssue), terraformManifestFile, file.path, "terraform", null, project)
        return IacResult(listOf(iacIssuesForFile))
    }
}
