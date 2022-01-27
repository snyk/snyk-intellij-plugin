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
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.hamcrest.collection.IsCollectionWithSize
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import snyk.oss.OssResult
import snyk.oss.OssVulnerabilitiesForFile
import java.nio.file.Paths

@Suppress("DuplicatedCode", "FunctionName")
class OSSDefaultAnnotatorTest : BasePlatformTestCase() {
    private var cut = OSSDefaultAnnotator()
    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val fileName = "build.gradle.kts"
    private val ossResult =
        javaClass.classLoader.getResource("oss-test-results/oss-result-gradle-kts.json")!!.readText(Charsets.UTF_8)

    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    private val toolWindowPanel: SnykToolWindowPanel = mockk(relaxed = true)

    override fun getTestDataPath(): String {
        val resource = OSSDefaultAnnotator::class.java.getResource("/test-fixtures/oss/annotator")
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
        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
        cut = OSSDefaultAnnotator()
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
    fun `test getIssues should not return any issue if no oss issue exists`() {
        every { toolWindowPanel.currentOssResults } returns null

        val issues = cut.getIssuesForFile(psiFile)

        assertEquals(null, issues)
    }

    @Test
    fun `test getIssues should return issues if they exist`() {
        every { toolWindowPanel.currentOssResults } returns createOssResultWithIssues()

        val issues = cut.getIssuesForFile(psiFile)

        assertNotEquals(null, issues)
        assertThat(issues!!.vulnerabilities, IsCollectionWithSize.hasSize(6))
    }

    @Test
    fun `test apply should trigger newAnnotation call`() {
        every { toolWindowPanel.currentOssResults } returns createOssResultWithIssues()

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test textRange`() {
        val ossResult = createOssResultWithIssues()
        val issue = ossResult.allCliIssues!!.first().vulnerabilities[0]
        val expectedStart = 1115
        val expectedEnd = 1158
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
        every { toolWindowPanel.currentOssResults } returns result
        every { annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>()) } returns builderMock

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify {
            annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>())
        }
        verify(exactly = 0) { builderMock.withFix(any()) }
    }

    private fun createOssResultWithIssues(): OssResult =
        OssResult(listOf(Gson().fromJson(ossResult, OssVulnerabilitiesForFile::class.java)), null)
}
