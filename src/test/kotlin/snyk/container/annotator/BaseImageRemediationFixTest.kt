package snyk.container.annotator

import com.google.gson.Gson
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.snyk.plugin.getContainerService
import io.snyk.plugin.pluginSettings
import org.junit.Test
import snyk.container.ContainerIssuesForImage
import snyk.container.ContainerResult
import java.nio.file.Paths

@Suppress("FunctionName")
class BaseImageRemediationFixTest : BasePlatformTestCase() {
    private lateinit var file: VirtualFile
    private val range = TextRange(/* startOffset = */ 0,/* endOffset = */ 4)
    private val familyName = "Snyk"
    private lateinit var cut: BaseImageRemediationFix

    private val fileName = "package.json"

    private lateinit var psiFile: PsiFile

    override fun getTestDataPath(): String {
        val resource = BaseImageRemediationFixTest::class.java
            .getResource("/test-fixtures/oss/annotator")
        requireNotNull(resource) { "Make sure that the resource $resource exists!" }
        return Paths.get(resource.toURI()).toString()
    }

    override fun isWriteActionRequired(): Boolean = true

    private lateinit var languageServerWrapperMock: snyk.common.lsp.LanguageServerWrapper // Add field

    override fun setUp() {
        super.setUp()
        unmockkAll()

        // Mock LanguageServerWrapper to prevent issues during teardown
        mockkObject(snyk.common.lsp.LanguageServerWrapper.Companion)
        languageServerWrapperMock = mockk(relaxed = true)
        every { snyk.common.lsp.LanguageServerWrapper.getInstance(project) } returns languageServerWrapperMock
        justRun { languageServerWrapperMock.dispose() }
        justRun { languageServerWrapperMock.shutdown() }
        pluginSettings().fileListenerEnabled = false
        file = myFixture.copyFileToProject(fileName)
        psiFile = WriteAction.computeAndWait<PsiFile, Throwable> { psiManager.findFile(file)!! }
        val containerIssuesForImage = createContainerIssuesForImage()
        cut = BaseImageRemediationFix(
            containerIssuesForImage = containerIssuesForImage,
            range = range
        )
    }

    override fun tearDown() {
        unmockkAll()
        pluginSettings().fileListenerEnabled = true
        super.tearDown()
    }

    @Test
    fun `test getText`() {
        assertEquals("Upgrade Image to nginx:1.21.4", cut.text)
    }

    @Test
    fun `test familyName`() {
        assertEquals(familyName, cut.familyName)
    }

    @Test
    fun `test invoke`() {
        val doc = psiFile.viewProvider.document
        doc!!.setText("abcd")
        val editor = mockk<Editor>()
        every { editor.document } returns doc

        cut.invoke(project, editor, psiFile)

        assertEquals("nginx:1.21.4", doc.text)
    }

    private val containerResultWithRemediationJson =
        javaClass.classLoader.getResource("container-test-results/nginx-with-remediation.json")!!
            .readText(Charsets.UTF_8)

    private fun createContainerIssuesForImage(): ContainerIssuesForImage {
        val containerResult = ContainerResult(
            listOf(Gson().fromJson(containerResultWithRemediationJson, ContainerIssuesForImage::class.java))
        )

        val firstContainerIssuesForImage = containerResult.allCliIssues!![0]
        val baseImageRemediationInfo =
            getContainerService(project)?.convertRemediation(firstContainerIssuesForImage)
        return firstContainerIssuesForImage.copy(
            baseImageRemediationInfo = baseImageRemediationInfo,
            workloadImages = emptyList()
        )
    }
}
