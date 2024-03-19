package snyk.iac.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import snyk.iac.IacIssue
import snyk.iac.IacIssuesForFile
import snyk.iac.IacResult

class IacYamlAnnotatorTest : IacBaseAnnotatorCase() {

    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val kubernetesManifestFile = "kubernetes-deployment.yaml"
    lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    override fun setUp() {
        super.setUp()
        file = myFixture.copyFileToProject(kubernetesManifestFile)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
    }

    fun `test getIssues should not return any annotations if no iac issue exists`() {
        every { snykCachedResults.currentIacResult } returns null

        val issues = IacYamlAnnotator().getIssues(psiFile)

        assertEquals(0, issues.size)
    }

    fun `test getIssues should return one annotations if only one iac issue exists`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine18()

        val issues = IacYamlAnnotator().getIssues(psiFile)

        assertEquals(1, issues.size)
    }

    fun `test apply should trigger newAnnotation call`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine18()

        IacYamlAnnotator().apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    fun `test textRange with leading space`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine18()

        val expectedRange = TextRange.create(262, 269)
        val actualRange = IacYamlAnnotator().textRange(
            psiFile,
            snykCachedResults.currentIacResult?.allCliIssues!!.first().infrastructureAsCodeIssues.first()
        )

        assertEquals(expectedRange, actualRange)
    }

    fun `test textRange with dash at the begin`() {
        every { snykCachedResults.currentIacResult } returns createIacResultWithIssueOnLine20()

        val expectedRange = TextRange.create(304, 308)
        val actualRange = IacYamlAnnotator().textRange(
            psiFile,
            snykCachedResults.currentIacResult?.allCliIssues!!.first().infrastructureAsCodeIssues.first()
        )

        assertEquals(expectedRange, actualRange)
    }

    private fun createIacResultWithIssueOnLine18(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-K8S-13",
            title = "Container is running in host's IPC namespace",
            lineNumber = 18,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile =
            IacIssuesForFile(listOf(iacIssue), kubernetesManifestFile, file.path, "Kubernetes", null, project)
        return IacResult(listOf(iacIssuesForFile))
    }

    private fun createIacResultWithIssueOnLine20(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-K8S-4",
            title = "Container is running without cpu limit",
            lineNumber = 20,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile =
            IacIssuesForFile(listOf(iacIssue), kubernetesManifestFile, file.path, "Kubernetes", null, project)
        return IacResult(listOf(iacIssuesForFile))
    }
}
