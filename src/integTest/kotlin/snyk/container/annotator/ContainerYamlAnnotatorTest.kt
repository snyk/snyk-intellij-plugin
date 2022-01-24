@file:Suppress("FunctionName")

package snyk.container.annotator

import com.google.gson.Gson
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import snyk.container.BaseImageInfo
import snyk.container.BaseImageRemediationInfo
import snyk.container.BaseImageVulnerabilities
import snyk.container.ContainerIssue
import snyk.container.ContainerIssuesForImage
import snyk.container.ContainerResult
import snyk.container.Docker
import snyk.container.KubernetesWorkloadImage
import java.nio.file.Paths

class ContainerYamlAnnotatorTest : BasePlatformTestCase() {

    private lateinit var cut: ContainerYamlAnnotator
    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val kubernetesManifestFile = "kubernetes-deployment.yaml"
    private val containerResultWithRemediationJson =
        javaClass.classLoader.getResource("container-test-results/nginx-with-remediation.json")!!
            .readText(Charsets.UTF_8)

    lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    val toolWindowPanel: SnykToolWindowPanel = mockk(relaxed = true)

    override fun getTestDataPath(): String {
        val resource = ContainerYamlAnnotator::class.java.getResource("/test-fixtures/container/annotator")
        requireNotNull(resource) { "Make sure that the resource $resource exists!" }
        return Paths.get(resource.toURI()).toString()
    }

    override fun isWriteActionRequired(): Boolean = true

    @Before
    override fun setUp() {
        super.setUp()
        unmockkAll()
        project.replaceService(SnykToolWindowPanel::class.java, toolWindowPanel, project)
        pluginSettings().fileListenerEnabled = false
        file = myFixture.copyFileToProject(kubernetesManifestFile)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
        cut = ContainerYamlAnnotator()
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    override fun tearDown() {
        unmockkAll()
        try {
            super.tearDown()
            pluginSettings().fileListenerEnabled = true
        } catch (e: Exception) {
            // when tearing down the test case, our File Listener is trying to react on the deletion of the test
            // files and tries to access the file index that isn't there anymore
        }
    }

    @Test
    fun `test getIssues should not return any issue if no container issue exists`() {
        every { toolWindowPanel.currentContainerResult } returns null

        val issues = cut.getContainerIssuesForImages(psiFile)

        assertThat(issues, hasSize(0))
    }

    @Test
    fun `test getIssues should return one issue if only one container issue exists`() {
        every { toolWindowPanel.currentContainerResult } returns createContainerResultWithIssueOnLine21()

        val issues = cut.getContainerIssuesForImages(psiFile)

        assertThat(issues, hasSize(1))
    }

    @Test
    fun `test apply should trigger newAnnotation call`() {
        every { toolWindowPanel.currentContainerResult } returns createContainerResultWithIssueOnLine21()

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test severity with at least one critical should return error`() {
        val severity = cut.severity(createContainerImageForIssuesWithSeverity(ContainerYamlAnnotator.SEVERITY_CRITICAL))
        assertEquals(HighlightSeverity.ERROR.javaClass, severity.javaClass)
    }

    @Test
    fun `test severity with at least one high should return warning`() {
        val severity = cut.severity(createContainerImageForIssuesWithSeverity(ContainerYamlAnnotator.SEVERITY_HIGH))
        assertEquals(HighlightSeverity.WARNING.javaClass, severity.javaClass)
    }

    @Test
    fun `test severity with at least one medium should return weak warning`() {
        val severity = cut.severity(createContainerImageForIssuesWithSeverity(ContainerYamlAnnotator.SEVERITY_MEDIUM))
        assertEquals(HighlightSeverity.WEAK_WARNING.javaClass, severity.javaClass)
    }

    @Test
    fun `test severity with at least one low should return info`() {
        val severity = cut.severity(createContainerImageForIssuesWithSeverity(ContainerYamlAnnotator.SEVERITY_LOW))
        assertEquals(HighlightSeverity.INFORMATION.javaClass, severity.javaClass)
    }

    @Test
    fun `test textRange`() {
        val containerResult = createContainerResultWithIssueOnLine21()
        val line = 21
        val imageName = containerResult.allCliIssues!!.first().imageName
        val expectedStart = 333
        val expectedEnd = expectedStart + imageName.length
        val expectedRange = TextRange(expectedStart, expectedEnd)

        val actualRange = cut.textRange(psiFile, line, imageName)

        assertEquals(expectedRange, actualRange)
    }

    @Test
    fun `test annotation message should display vulnerabiltiy count 1 for severity critical and remediation`() {
        val expected = "Snyk found 1 vulnerability. Upgrade image to a newer version"
        val image = createContainerImageForIssuesWithSeverity(ContainerYamlAnnotator.SEVERITY_CRITICAL)
            .copy(baseImageRemediationInfo = dummyBaseRemediationInfo())

        val actual = cut.annotationMessage(image)

        assertEquals(expected, actual)
    }

    @Test
    fun `test annotation message should display vulnerabiltiy count and no remediation`() {
        val expected = "Snyk found 1 vulnerability. "
        val actual =
            cut.annotationMessage(createContainerImageForIssuesWithSeverity(ContainerYamlAnnotator.SEVERITY_LOW))
        assertEquals(expected, actual)
    }

    private fun dummyBaseRemediationInfo() = BaseImageRemediationInfo(
        currentImage = BaseImageInfo(
            "nginx",
            BaseImageVulnerabilities(1, 0, 0, 0)
        ),
        majorUpgrades = null,
        alternativeUpgrades = null,
        minorUpgrades = BaseImageInfo("nginx", BaseImageVulnerabilities(0, 0, 0, 0))
    )

    private fun createContainerResultWithIssueOnLine21(): ContainerResult {
        val containerResult = ContainerResult(
            listOf(Gson().fromJson(containerResultWithRemediationJson, ContainerIssuesForImage::class.java)), null
        )

        val firstContainerIssuesForImage = containerResult.allCliIssues!![0]

        val workloadImages = listOf(KubernetesWorkloadImage("nginx:1.16.0", psiFile, 21))
        containerResult.allCliIssues = listOf(firstContainerIssuesForImage.copy(workloadImages = workloadImages))
        return containerResult
    }

    private fun createContainerImageForIssuesWithSeverity(
        severity: String = ContainerYamlAnnotator.SEVERITY_CRITICAL
    ): ContainerIssuesForImage {
        val containerIssue = ContainerIssue("", "", "", severity, packageManager = "npm", from = emptyList())
        val vulnerabilities = listOf(containerIssue, containerIssue.copy()) // force the tests to filter duplicates
        val docker = Docker(null)
        return ContainerIssuesForImage(vulnerabilities, "test", docker, null, "nginx:1.16.0", null)
    }
}
