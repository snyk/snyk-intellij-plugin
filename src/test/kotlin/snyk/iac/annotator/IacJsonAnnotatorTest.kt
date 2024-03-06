package snyk.iac.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import snyk.iac.IacIssue
import snyk.iac.IacIssuesForFile
import snyk.iac.IacResult

@Suppress("FunctionName")
class IacJsonAnnotatorTest : IacBaseAnnotatorCase() {

    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val cloudformationManifestFile = "cloudformation-deployment.yaml"
    lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    override fun setUp() {
        super.setUp()
        file = myFixture.copyFileToProject(cloudformationManifestFile)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
    }

    fun `test getIssues should not return any annotations if no iac issue exists`() {
        every { snykCachedResults.currentIacResult } returns null

        val issues = IacJsonAnnotator().getIssues(psiFile)

        assertEquals(0, issues.size)
    }

    fun `test getIssues should return one annotations if only one iac issue exists`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine2()

        val issues = IacJsonAnnotator().getIssues(psiFile)

        assertEquals(1, issues.size)
    }

    fun `test apply should trigger newAnnotation call`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine2()

        IacJsonAnnotator().apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    fun `test apply for disabled Severity should not trigger newAnnotation call`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine2()
        pluginSettings().mediumSeverityEnabled = false

        IacJsonAnnotator().apply(psiFile, Unit, annotationHolderMock)

        verify(exactly = 0) { annotationHolderMock.newAnnotation(HighlightSeverity.WARNING, any()) }
    }

    private fun createIacResultWithIssueOnLine2(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-AWS-426",
            title = "Snyk - EC2 API termination protection is not enabled",
            lineNumber = 2,
            severity = "medium", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile =
            IacIssuesForFile(listOf(iacIssue), cloudformationManifestFile, file.path, "cloudformation", null, project)
        return IacResult(listOf(iacIssuesForFile))
    }
}
