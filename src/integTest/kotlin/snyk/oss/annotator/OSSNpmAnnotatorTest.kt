package snyk.oss.annotator

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
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import org.hamcrest.collection.IsCollectionWithSize
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import snyk.common.SnykCachedResults
import snyk.common.intentionactions.AlwaysAvailableReplacementIntentionAction
import snyk.oss.OssResult
import snyk.oss.OssVulnerabilitiesForFile
import java.nio.file.Paths

@Suppress("DuplicatedCode", "FunctionName")
class OSSNpmAnnotatorTest : BasePlatformTestCase() {
    private var cut = OSSNpmAnnotator()
    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val fileName = "package.json"
    private val ossResult =
        javaClass.classLoader.getResource("oss-test-results/oss-result-package.json")!!.readText(Charsets.UTF_8)

    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    private val snykCachedResults: SnykCachedResults = mockk(relaxed = true)

    override fun getTestDataPath(): String {
        val resource = OSSNpmAnnotator::class.java.getResource("/test-fixtures/oss/annotator")
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
        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
        cut = OSSNpmAnnotator()
    }

    override fun tearDown() {
        unmockkAll()
        project.replaceService(SnykCachedResults::class.java, SnykCachedResults(project), project)
        resetSettings(project)
        super.tearDown()
    }

    @Test
    fun `test getIssues should not return any issue if no oss issue exists`() {
        every { snykCachedResults.currentOssResults } returns null

        val issues = cut.getIssuesForFile(psiFile)

        assertEquals(null, issues)
    }

    @Test
    fun `test getIssues should return issues if they exist`() {
        every { snykCachedResults.currentOssResults } returns createOssResultWithIssues()

        val issues = cut.getIssuesForFile(psiFile)

        assertNotEquals(null, issues)
        assertThat(issues!!.vulnerabilities.distinctBy { it.id }, IsCollectionWithSize.hasSize(1))
    }

    @Test
    fun `test apply should trigger newAnnotation call`() {
        every { snykCachedResults.currentOssResults } returns createOssResultWithIssues()

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test apply for disabled Severity should not trigger newAnnotation call`() {
        every { snykCachedResults.currentOssResults } returns createOssResultWithIssues()
        pluginSettings().mediumSeverityEnabled = false

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify(exactly = 0) { annotationHolderMock.newAnnotation(HighlightSeverity.WEAK_WARNING, any()) }
    }

    @Test
    fun `test textRange`() {
        val ossResult = createOssResultWithIssues()
        val issue = ossResult.allCliIssues!!.first().vulnerabilities[0]
        val expectedStart = 156
        val expectedEnd = 174
        val expectedRange = TextRange(expectedStart, expectedEnd)

        val actualRange = cut.textRange(psiFile, issue)

        assertEquals(expectedRange, actualRange)
    }

    @Test
    fun `test annotation message should contain issue title`() {
        val vulnerability = createOssResultWithIssues().allCliIssues!!.first().vulnerabilities[0]
        val expected = "Snyk: ${vulnerability.title} in ${vulnerability.name}"

        val actual = cut.annotationMessage(vulnerability)

        assertEquals(expected, actual)
    }

    @Test
    fun `test apply should not add a quickfix when upgrade empty`() {
        val builderMock = mockk<AnnotationBuilder>(relaxed = true)
        val result = createOssResultWithIssues()
        result.allCliIssues?.first()?.remediation = null
        every { snykCachedResults.currentOssResults } returns result
        every { annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>()) } returns builderMock

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify(exactly = 0) { builderMock.withFix(any()) }
    }

    @Test
    fun `test apply should add a quickfix with message`() {
        val builderMock = mockk<AnnotationBuilder>(relaxed = true)
        val result = createOssResultWithIssues()
        every { snykCachedResults.currentOssResults } returns result
        every { annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>()) } returns builderMock
        val intentionActionCapturingSlot = slot<AlwaysAvailableReplacementIntentionAction>()
        every { builderMock.withFix(capture(intentionActionCapturingSlot)) } returns builderMock
        myFixture.configureByText("package-lock.json", "test")

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify(exactly = 1) { builderMock.withFix(any()) }
        val expected = "Please update your package-lock.json to finish fixing the vulnerability."
        assertEquals(expected, intentionActionCapturingSlot.captured.message)
    }

    private fun createOssResultWithIssues(): OssResult =
        OssResult(listOf(Gson().fromJson(ossResult, OssVulnerabilitiesForFile::class.java)))
}
