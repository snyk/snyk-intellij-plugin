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
import io.snyk.plugin.pluginSettings
import org.junit.Before
import org.junit.Test
import java.nio.file.Paths

@Suppress("FunctionName")
class AlwaysAvailableReplacementIntentionActionTest : BasePlatformTestCase() {
    private lateinit var file: VirtualFile
    private val intentionText = "a"
    private val replacementText = "b"
    private val range = TextRange(/* startOffset = */ 0,/* endOffset = */ 4)
    private val familyName = "SnykySnyky"
    private lateinit var cut: AlwaysAvailableReplacementIntentionAction

    private val fileName = "package.json"

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
        cut = AlwaysAvailableReplacementIntentionAction(range, replacementText, intentionText, familyName)
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
    fun `test getText`() {
        assertEquals(intentionText + replacementText, cut.text)
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
    }
}
