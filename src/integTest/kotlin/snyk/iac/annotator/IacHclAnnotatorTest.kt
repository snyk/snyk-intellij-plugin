package snyk.iac.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import snyk.iac.IacIssue
import snyk.iac.IacIssuesForFile
import snyk.iac.IacResult

class IacHclAnnotatorTest : IacBaseAnnotatorCase() {

    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val terraformManifestFile = "terraform-main.tf"
    lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    @Before
    override fun setUp() {
        super.setUp()
        file = myFixture.copyFileToProject(terraformManifestFile)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
    }

    @Test
    fun `test doAnnotation should not return any annotations if no iac issue exists`() {
        every { snykCachedResults.currentIacResult } returns null

        val annotations = IacHclAnnotator().getIssues(psiFile)

        assertThat(annotations, hasSize(0))
    }

    @Test
    fun `test getIssues should return one annotation if only one iac issue exists`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine1()

        val annotations = IacHclAnnotator().getIssues(psiFile)

        assertThat(annotations, hasSize(1))
    }

    @Test
    fun `test apply should trigger newAnnotation call`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine1()

        IacHclAnnotator().apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test textRange without leading whitespace for HCLIdentifier`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine1()

        val expectedRange = TextRange.create(0, 8)
        val actualRange = IacHclAnnotator().textRange(
            psiFile,
            snykCachedResults.currentIacResult?.allCliIssues!!.first().infrastructureAsCodeIssues.first()
        )

        assertThat(actualRange, equalTo(expectedRange))
    }

    @Test
    fun `test textRange with leading whitespace for HCLIdentifier`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine8()

        val expectedRange = TextRange.create(201, 209)
        val actualRange = IacHclAnnotator().textRange(
            psiFile,
            snykCachedResults.currentIacResult?.allCliIssues!!.first().infrastructureAsCodeIssues.first()
        )

        assertThat(actualRange, equalTo(expectedRange))
    }

    @Test
    fun `test textRange for leading whitespace for HCLProperty`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine18()

        val expectedRange = TextRange.create(446, 453)
        val actualRange = IacHclAnnotator().textRange(
            psiFile,
            snykCachedResults.currentIacResult?.allCliIssues!!.first().infrastructureAsCodeIssues.first()
        )

        assertThat(actualRange, equalTo(expectedRange))
    }

    private fun createIacResultWithIssueOnLine1(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-TF-74",
            title = "Credentials are configured via provider attributes",
            lineNumber = 1,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile = IacIssuesForFile(listOf(iacIssue), terraformManifestFile, file.path, "terraform")
        return IacResult(listOf(iacIssuesForFile), null)
    }

    private fun createIacResultWithIssueOnLine8(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-TF-8",
            title = "Snyk - IAM password should contain lowercase",
            lineNumber = 8,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile = IacIssuesForFile(listOf(iacIssue), terraformManifestFile, file.path, "terraform")
        return IacResult(listOf(iacIssuesForFile), null)
    }

    private fun createIacResultWithIssueOnLine18(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-TF-1",
            title = "Snyk - Security Group allows open ingress",
            lineNumber = 18,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile = IacIssuesForFile(listOf(iacIssue), terraformManifestFile, file.path, "terraform")
        return IacResult(listOf(iacIssuesForFile), null)
    }
}
