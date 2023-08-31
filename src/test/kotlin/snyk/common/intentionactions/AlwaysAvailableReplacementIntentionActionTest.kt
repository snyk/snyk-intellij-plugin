package snyk.common.intentionactions

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykAnalyticsService
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths

@Suppress("FunctionName")
class AlwaysAvailableReplacementIntentionActionTest : BasePlatformTestCase() {
    private lateinit var file: VirtualFile
    private val replacementText = "b"
    private val range = TextRange(/* startOffset = */ 0,/* endOffset = */ 4)
    private val familyName = "Snyk"
    private lateinit var cut: AlwaysAvailableReplacementIntentionAction

    private val fileName = "package.json"
    private val analyticsMock = mockk<SnykAnalyticsService>(relaxed = true)

    private lateinit var psiFile: PsiFile

    override fun getTestDataPath(): String {
        val resource = AlwaysAvailableReplacementIntentionAction::class.java
            .getResource("/test-fixtures/oss/annotator")
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
        cut = AlwaysAvailableReplacementIntentionAction(
            range = range,
            replacementText = replacementText,
            analyticsService = analyticsMock
        )
    }

    override fun tearDown() {
        unmockkAll()
        pluginSettings().fileListenerEnabled = true
        super.tearDown()
    }

    @Test
    fun `test getText`() {
        assertTrue(cut.text.contains(replacementText))
    }

    @Test
    fun `test familyName`() {
        assertEquals(familyName, cut.familyName)
    }

    @Test
    fun `test invoke`() {
        val editor = mockk<Editor>()
        val doc = psiFile.viewProvider.document
        doc!!.setText("abcd")
        every { editor.document } returns doc

        cut.invoke(project, editor, psiFile)

        assertEquals("b", doc.text)
        verify { analyticsMock.logQuickFixIsTriggered(any()) }
    }
}
