package snyk.code.annotator

import ai.deepcode.javaclient.core.MyTextRange
import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths

@Suppress("DuplicatedCode", "FunctionName")
class SnykCodeAnnotatorTest : BasePlatformTestCase() {
    private var cut = SnykCodeAnnotator()
    private val fileName = "app.js"

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
        pluginSettings().fileListenerEnabled = false
        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
        cut = SnykCodeAnnotator()
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
    fun `test textRange for maven pom`() {
        val issue = createSnykCodeResultWithIssues()[0]
        val expectedStart = 132
        val expectedEnd = 203
        val expectedRange = TextRange(expectedStart, expectedEnd)

        val actualRange = cut.textRange(psiFile, issue.ranges[0])

        assertEquals(expectedRange, actualRange)
    }

    @Test
    fun `test annotation message should contain issue title`() {
        val issue = createSnykCodeResultWithIssues()[0]
        val expected = "Snyk Code: ${issue.title}. ${issue.message}"

        val actual = cut.annotationMessage(issue)

        assertEquals(expected, actual)
    }

    private fun createSnykCodeResultWithIssues(): List<SuggestionForFile> {
        val myRange = mockk<MyTextRange>()
        every { myRange.startCol } returns 10
        every { myRange.endCol } returns 20
        every { myRange.startRow } returns 3
        every { myRange.endRow } returns 4

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
            listOf(myRange),
            emptyList(),
            emptyList(),
            emptyList()
        )
        return listOf(suggestionForFile)
    }
}
