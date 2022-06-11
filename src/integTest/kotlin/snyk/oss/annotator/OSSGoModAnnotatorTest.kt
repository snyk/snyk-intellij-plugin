package snyk.oss.annotator

import com.google.gson.Gson
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
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
import org.hamcrest.collection.IsCollectionWithSize
import org.junit.Assert
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import snyk.common.SnykCachedResults
import snyk.oss.OssResult
import snyk.oss.OssVulnerabilitiesForFile
import java.nio.file.Paths

@Suppress("DuplicatedCode", "FunctionName")
class OSSGoModAnnotatorTest : BasePlatformTestCase() {
    private val cut by lazy { OSSGoModAnnotator() }
    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val fileName = "go.mod"
    private val ossResult =
        javaClass.classLoader.getResource("oss-test-results/oss-result-go-mod.json")!!.readText(Charsets.UTF_8)

    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    private val snykCachedResults: SnykCachedResults = mockk(relaxed = true)

    override fun getTestDataPath(): String {
        val resource = OSSGoModAnnotator::class.java.getResource("/test-fixtures/oss/annotator")
        requireNotNull(resource) { "Make sure that the resource $resource exists!" }
        return Paths.get(resource.toURI()).toString()
    }

    override fun isWriteActionRequired(): Boolean = true

    @Before
    override fun setUp() {
        super.setUp()
        unmockkAll()
        project.replaceService(SnykCachedResults::class.java, snykCachedResults, project)
        pluginSettings().fileListenerEnabled = false
        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
    }

    override fun tearDown() {
        unmockkAll()
        project.replaceService(SnykCachedResults::class.java, SnykCachedResults(project), project)
        pluginSettings().fileListenerEnabled = true
        super.tearDown()
    }

    @Test
    fun `test getPackageName`() {
        val issue = createOssResultWithIssues().allCliIssues!!.first().vulnerabilities[0]
        val actualPackageName = cut.getIntroducingPackage(issue)
        assertEquals("github.com/gin-gonic/gin", actualPackageName)
    }

    @Test
    fun `test getIssues should not return any issue if no oss issue exists`() {
        every { snykCachedResults.currentOssResults } returns null

        val issues = cut.getIssuesForFile(psiFile)

        Assert.assertEquals(null, issues)
    }

    @Test
    fun `test getIssues should return issues if they exist`() {
        every { snykCachedResults.currentOssResults } returns createOssResultWithIssues()

        val issues = cut.getIssuesForFile(psiFile)

        assertNotEquals(null, issues)
        assertThat(issues!!.vulnerabilities, IsCollectionWithSize.hasSize(11))
    }

    @Test
    fun `test apply should trigger newAnnotation call`() {
        every { snykCachedResults.currentOssResults } returns createOssResultWithIssues()

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test textRange`() {
        val ossResult = createOssResultWithIssues()
        val issue = ossResult.allCliIssues!!.first().vulnerabilities[0]
        val expectedStart = 301
        val expectedEnd = 332
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
    fun `test apply should not add a quickfix`() {
        val builderMock = mockk<AnnotationBuilder>(relaxed = true)
        val result = createOssResultWithIssues()
        every { snykCachedResults.currentOssResults } returns result
        every { annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>()) } returns builderMock

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify {
            annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>())
        }
        verify(exactly = 0) { builderMock.withFix(any()) }
    }

    private fun createOssResultWithIssues(): OssResult =
        OssResult(listOf(Gson().fromJson(ossResult, OssVulnerabilitiesForFile::class.java)))
}
