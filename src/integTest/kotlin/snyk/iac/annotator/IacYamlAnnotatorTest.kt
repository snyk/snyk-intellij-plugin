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

class IacYamlAnnotatorTest : IacBaseAnnotatorCase() {

    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val kubernetesManifestFile = "kubernetes-deployment.yaml"
    lateinit var file: VirtualFile
    lateinit var psiFile: PsiFile

    @Before
    override fun setUp() {
        super.setUp()
        file = myFixture.copyFileToProject(kubernetesManifestFile)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
    }

    @Test
    fun `test getIssues should not return any annotations if no iac issue exists`() {
        every { toolWindowPanel.currentIacResult } returns null

        val issues = IacYamlAnnotator().getIssues(psiFile)

        assertThat(issues, hasSize(0))
    }

    @Test
    fun `test getIssues should return one annotations if only one iac issue exists`() {
        every { toolWindowPanel.currentIacResult } returns createIacResultWithIssueOnLine18()

        val issues = IacYamlAnnotator().getIssues(psiFile)

        assertThat(issues, hasSize(1))
    }

    @Test
    fun `test apply should trigger newAnnotation call`() {
        every { toolWindowPanel.currentIacResult } returns createIacResultWithIssueOnLine18()

        IacYamlAnnotator().apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test textRange with leading space`() {
        every { toolWindowPanel.currentIacResult } returns createIacResultWithIssueOnLine18()

        val expectedRange = TextRange.create(262, 269)
        val actualRange = IacYamlAnnotator().textRange(
            psiFile,
            toolWindowPanel.currentIacResult?.allCliIssues!!.first().infrastructureAsCodeIssues.first()
        )

        assertThat(actualRange, equalTo(expectedRange))
    }

    @Test
    fun `test textRange with dash at the begin`() {
        every { toolWindowPanel.currentIacResult } returns createIacResultWithIssueOnLine20()

        val expectedRange = TextRange.create(304, 308)
        val actualRange = IacYamlAnnotator().textRange(
            psiFile,
            toolWindowPanel.currentIacResult?.allCliIssues!!.first().infrastructureAsCodeIssues.first()
        )

        assertThat(actualRange, equalTo(expectedRange))
    }

    private fun createIacResultWithIssueOnLine18(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-K8S-13",
            title = "Container is running in host's IPC namespace",
            lineNumber = 18,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile = IacIssuesForFile()
        iacIssuesForFile.targetFile = kubernetesManifestFile
        iacIssuesForFile.targetFilePath = file.path
        iacIssuesForFile.infrastructureAsCodeIssues = arrayOf(iacIssue)
        return IacResult(arrayOf(iacIssuesForFile), null)
    }

    private fun createIacResultWithIssueOnLine20(): IacResult {
        val iacIssue = IacIssue(
            id = "SNYK-CC-K8S-4",
            title = "Container is running without cpu limit",
            lineNumber = 20,
            severity = "", publicId = "", documentation = "", issue = "", impact = ""
        )
        val iacIssuesForFile = IacIssuesForFile()
        iacIssuesForFile.targetFile = kubernetesManifestFile
        iacIssuesForFile.infrastructureAsCodeIssues = arrayOf(iacIssue)
        return IacResult(arrayOf(iacIssuesForFile), null)
    }
}
