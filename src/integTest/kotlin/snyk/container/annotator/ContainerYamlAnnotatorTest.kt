@file:Suppress("FunctionName")

package snyk.container.annotator

import com.google.gson.Gson
import com.intellij.lang.annotation.AnnotationBuilder
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
import io.snyk.plugin.getContainerService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import snyk.common.SnykCachedResults
import snyk.container.BaseImageInfo
import snyk.container.BaseImageRemediation
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

    private val SEVERITY_CRITICAL = "critical"
    private val SEVERITY_HIGH = "high"
    private val SEVERITY_MEDIUM = "medium"
    private val SEVERITY_LOW = "low"

    lateinit var virtualFile: VirtualFile
    private lateinit var psiFile: PsiFile

    val snykCachedResults: SnykCachedResults = mockk(relaxed = true)

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
        project.replaceService(SnykCachedResults::class.java, snykCachedResults, project)
        resetSettings(project)
        pluginSettings().fileListenerEnabled = false
        virtualFile = myFixture.copyFileToProject(kubernetesManifestFile)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(virtualFile)!! }
        cut = ContainerYamlAnnotator()
    }

    override fun tearDown() {
        unmockkAll()
        project.replaceService(SnykCachedResults::class.java, SnykCachedResults(project), project)
        resetSettings(project)
        super.tearDown()
    }

    @Test
    fun `test getIssues should not return any issue if no container issue exists`() {
        every { snykCachedResults.currentContainerResult } returns null

        val issues = cut.getContainerIssuesForImages(psiFile)

        assertThat(issues, hasSize(0))
    }

    @Test
    fun `test getIssues should return one issue if only one container issue exists`() {
        every { snykCachedResults.currentContainerResult } returns createContainerResultWithIssueOnLine21()

        val issues = cut.getContainerIssuesForImages(psiFile)

        assertThat(issues, hasSize(1))
    }

    @Test
    fun `test apply should trigger newAnnotation call`() {
        every { snykCachedResults.currentContainerResult } returns createContainerResultWithIssueOnLine21()

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test apply for disabled Severity should not trigger newAnnotation call`() {
        every { snykCachedResults.currentContainerResult } returns createContainerResultWithIssueOnLine21()
        pluginSettings().mediumSeverityEnabled = false

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify(exactly = 0) { annotationHolderMock.newAnnotation(HighlightSeverity.WARNING, any()) }
    }

    @Test
    fun `test severity with one critical`() {
        val severities = createContainerImageForIssuesWithSeverity(SEVERITY_CRITICAL).getSeverities()
        assertEquals(1, severities.size)
        assertEquals(HighlightSeverity.ERROR, severities.first().getHighlightSeverity())
    }

    @Test
    fun `test severity with one high`() {
        val severities = createContainerImageForIssuesWithSeverity(SEVERITY_HIGH).getSeverities()
        assertEquals(1, severities.size)
        assertEquals(HighlightSeverity.ERROR, severities.first().getHighlightSeverity())
    }

    @Test
    fun `test severity with one medium`() {
        val severities = createContainerImageForIssuesWithSeverity(SEVERITY_MEDIUM).getSeverities()
        assertEquals(1, severities.size)
        assertEquals(HighlightSeverity.WARNING, severities.first().getHighlightSeverity())
    }

    @Test
    fun `test severity with one low`() {
        val severities = createContainerImageForIssuesWithSeverity(SEVERITY_LOW).getSeverities()
        assertEquals(1, severities.size)
        assertEquals(HighlightSeverity.WEAK_WARNING, severities.first().getHighlightSeverity())
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
    fun `test annotation message should display vulnerability count 1 for severity critical and remediation`() {
        val expected = "Snyk found 1 vulnerability. Upgrade image to a newer version"
        val image = createContainerImageForIssuesWithSeverity(SEVERITY_CRITICAL)
            .copy(baseImageRemediationInfo = dummyBaseRemediationInfo())

        val actual = cut.annotationMessage(image)

        assertEquals(expected, actual)
    }

    @Test
    fun `test annotation message should display vulnerability count and no remediation`() {
        val expected = "Snyk found 1 vulnerability. "

        val actual =
            cut.annotationMessage(createContainerImageForIssuesWithSeverity(SEVERITY_LOW))

        assertEquals(expected, actual)
    }

    @Test
    fun `test apply should add a quickfix if remediation advice available`() {
        val builderMock = mockk<AnnotationBuilder>(relaxed = true)
        every { snykCachedResults.currentContainerResult } returns createContainerResultWithIssueOnLine21()
        every { annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>()) } returns builderMock

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify {
            annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>())
            builderMock.withFix(any())
        }
    }

    @Test
    fun `test apply should add annotations for all duplicated images`() {
        val builderMock = mockk<AnnotationBuilder>(relaxed = true)
        every { snykCachedResults.currentContainerResult } returns
            createContainerResultWithIssueOnLine21and23ForSameImageName()
        every { annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>()) } returns builderMock

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify(exactly = 2) {
            annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>())
        }
    }

    @Test
    fun `test apply should not add a quickfix if remediation advice base image different`() {
        val workloadImages = listOf(KubernetesWorkloadImage("nginx:1.16.0", virtualFile, 21))
        val builderMock = mockk<AnnotationBuilder>(relaxed = true)
        val imageForIssues = createContainerImageForIssuesWithSeverity().copy(
            baseImageRemediationInfo = dummyBaseRemediationInfo(imageName = "NotAnImage:0.1.1"),
            docker = dummyDocker(),
            workloadImages = workloadImages
        )
        val containerResult = createContainerResultWithIssueOnLine21()
        containerResult.allCliIssues = listOf(imageForIssues)
        every { snykCachedResults.currentContainerResult } returns containerResult
        every { annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>()) } returns builderMock

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify {
            annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>())
        }
        verify(exactly = 0) { builderMock.withFix(any()) }
    }

    private fun dummyDocker(): Docker {
        return Docker(BaseImageRemediation("REMEDIATION_AVAILABLE", emptyList()))
    }

    private fun dummyBaseRemediationInfo(imageName: String = "nginx") = BaseImageRemediationInfo(
        currentImage = BaseImageInfo(
            "nginx",
            BaseImageVulnerabilities(1, 0, 0, 0)
        ),
        majorUpgrades = null,
        alternativeUpgrades = null,
        minorUpgrades = BaseImageInfo("$imageName:1.21.2", BaseImageVulnerabilities(0, 0, 0, 0))
    )

    private fun createContainerResultWithIssueOnLine21(): ContainerResult {
        val containerResult = ContainerResult(
            listOf(Gson().fromJson(containerResultWithRemediationJson, ContainerIssuesForImage::class.java)), null
        )

        val firstContainerIssuesForImage = containerResult.allCliIssues!![0]
        val baseImageRemediationInfo =
            getContainerService(project)?.convertRemediation(firstContainerIssuesForImage.docker.baseImageRemediation)

        val workloadImages = listOf(KubernetesWorkloadImage("nginx:1.16.0", virtualFile, 21))
        containerResult.allCliIssues = listOf(
            firstContainerIssuesForImage.copy(
                workloadImages = workloadImages,
                baseImageRemediationInfo = baseImageRemediationInfo
            )
        )
        return containerResult
    }

    private fun createContainerResultWithIssueOnLine21and23ForSameImageName(): ContainerResult {
        val containerResult = ContainerResult(
            listOf(Gson().fromJson(containerResultWithRemediationJson, ContainerIssuesForImage::class.java)), null
        )

        val firstContainerIssuesForImage = containerResult.allCliIssues!![0]
        val baseImageRemediationInfo =
            getContainerService(project)?.convertRemediation(firstContainerIssuesForImage.docker.baseImageRemediation)

        val workloadImages1 = KubernetesWorkloadImage("nginx:1.16.0", virtualFile, 21)
        val workloadImages2 = KubernetesWorkloadImage("nginx:1.16.0", virtualFile, 23)
        containerResult.allCliIssues = listOf(
            firstContainerIssuesForImage.copy(
                workloadImages = listOf(workloadImages1, workloadImages2),
                baseImageRemediationInfo = baseImageRemediationInfo
            )
        )
        return containerResult
    }

    private fun createContainerImageForIssuesWithSeverity(
        severity: String = SEVERITY_CRITICAL
    ): ContainerIssuesForImage {
        val containerIssue = ContainerIssue("", "", "", severity, packageManager = "npm", from = emptyList())
        val vulnerabilities = listOf(containerIssue, containerIssue.copy()) // force the tests to filter duplicates
        val docker = Docker(null)
        return ContainerIssuesForImage(vulnerabilities, "test", docker, null, "nginx:1.16.0", null)
    }
}
