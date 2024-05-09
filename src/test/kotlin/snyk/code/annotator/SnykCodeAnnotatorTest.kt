package snyk.code.annotator

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.SnykFile
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths

@Suppress("DuplicatedCode", "FunctionName")
class SnykCodeAnnotatorTest : BasePlatformTestCase() {
    private var cut = SnykCodeAnnotator()
    private val fileName = "app.js"
    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)

    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    override fun getTestDataPath(): String {
        val resource = SnykCodeAnnotator::class.java.getResource("/test-fixtures/code/annotator")
        requireNotNull(resource) { "Make sure that the resource $resource exists!" }
        return Paths.get(resource.toURI()).toString()
    }

    override fun isWriteActionRequired(): Boolean = true

    @Before
    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        pluginSettings().fileListenerEnabled = false
        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
        cut = SnykCodeAnnotator()
    }

    override fun tearDown() {
        unmockkAll()
        super.tearDown()
        resetSettings(null)
    }

    @Test
    fun `test textRange for maven pom`() {
        val issue = createSnykCodeResultWithIssues()[0]
        val expectedStart = 132
        val expectedEnd = 203
        val expectedRange = TextRange(expectedStart, expectedEnd)

        val actualRange = cut.textRange(psiFile, issue.ranges[0])

        assertEquals(expectedRange, actualRange)
    }

    @Test
    fun `test textRange should use document to determine boundary limits`() {
        val range = mockRange(-1, Int.MAX_VALUE, -1, Int.MAX_VALUE)
        val issue = createSnykCodeResultWithIssues(range)[0]
        val expectedStart = 0
        val expectedEnd = 0
        val expectedRange = TextRange(expectedStart, expectedEnd)

        val actualRange = cut.textRange(psiFile, issue.ranges[0])

        assertEquals(expectedRange, actualRange)
    }

    @Test
    fun `test textRange should use document to determine boundary limits with startRow above document limit`() {
        val range = mockRange(Int.MAX_VALUE, -1, Int.MAX_VALUE, Int.MAX_VALUE)
        val issue = createSnykCodeResultWithIssues(range)[0]
        val expectedStart = 0
        val expectedEnd = 0
        val expectedRange = TextRange(expectedStart, expectedEnd)

        val actualRange = cut.textRange(psiFile, issue.ranges[0])

        assertEquals(expectedRange, actualRange)
    }

    @Test
    fun `test annotation message should contain issue title or message`() {
        val issue = createSnykCodeResultWithIssues()[0]

        val actual = cut.annotationMessage(issue)

        assertTrue(actual.contains(issue.title) || actual.contains(issue.message))
    }

    @Test
    fun `test apply should trigger newAnnotation call`() {
        mockkObject(AnalysisData)
        every { AnalysisData.instance.getAnalysis(SnykFile(psiFile.project, psiFile.virtualFile)) } returns
            createSnykCodeResultWithIssues()

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test apply for disabled Severity should not trigger newAnnotation call`() {
        mockkObject(AnalysisData)
        every { AnalysisData.instance.getAnalysis(SnykFile(psiFile.project, psiFile.virtualFile)) } returns
            createSnykCodeResultWithIssues()
        pluginSettings().highSeverityEnabled = false

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify(exactly = 0) { annotationHolderMock.newAnnotation(HighlightSeverity.ERROR, any()) }
    }

    private fun createSnykCodeResultWithIssues(range: MyTextRange = mockRange()): List<SuggestionForFile> {
        val suggestionForFile = SuggestionForFile(
            "testId",
            "testRule",
            "testMessage",
            "testTitle",
            "textText",
            3,
            1,
            emptyList(),
            emptyList(),
            listOf(range),
            emptyList(),
            emptyList(),
            emptyList()
        )
        return listOf(suggestionForFile)
    }

    private fun mockRange(startRow: Int = 3, endRow: Int = 4, startCol: Int = 10, endCol: Int = 20): MyTextRange {
        val myRange = mockk<MyTextRange>()
        every { myRange.startRow } returns startRow
        every { myRange.endRow } returns endRow
        every { myRange.startCol } returns startCol
        every { myRange.endCol } returns endCol
        return myRange
    }
}
