package snyk.iac.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import snyk.iac.IacIssue
import snyk.iac.IacIssuesForFile
import snyk.iac.IacResult

class IacJsonAnnotatorTest : IacBaseAnnotatorCase() {

    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val cloudformationManifestFile = "cloudformation-deployment.yaml"
    lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    @Before
    override fun setUp() {
        super.setUp()
        file = myFixture.copyFileToProject(cloudformationManifestFile)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
    }

    @Test
    fun `test getIssues should not return any annotations if no iac issue exists`() {
        every { toolWindowPanel.currentIacResult } returns null

        val issues = IacJsonAnnotator().getIssues(psiFile)

        assertThat(issues, hasSize(0))
    }

    @Test
    fun `test getIssues should return one annotations if only one iac issue exists`() {
        every { toolWindowPanel.currentIacResult } returns createIacResultWithIssueOnLine2()

        val issues = IacJsonAnnotator().getIssues(psiFile)

        assertThat(issues, hasSize(1))
    }

    @Test
    fun `test apply should trigger newAnnotation call`() {
        every { toolWindowPanel.currentIacResult } returns createIacResultWithIssueOnLine2()

        IacJsonAnnotator().apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    private fun createIacResultWithIssueOnLine2(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-AWS-426",
            title = "Snyk - EC2 API termination protection is not enabled",
            lineNumber = 2,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile =
            IacIssuesForFile(listOf(iacIssue), cloudformationManifestFile, file.path, "cloudformation")
        return IacResult(listOf(iacIssuesForFile), null)
    }
}
