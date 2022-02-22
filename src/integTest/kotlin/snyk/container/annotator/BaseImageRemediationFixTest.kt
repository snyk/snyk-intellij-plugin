package snyk.container.annotator

import com.google.gson.Gson
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
import io.snyk.plugin.getContainerService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykAnalyticsService
import org.junit.Before
import org.junit.Test
import snyk.container.ContainerIssuesForImage
import snyk.container.ContainerResult
import java.nio.file.Paths

@Suppress("FunctionName")
class BaseImageRemediationFixTest : BasePlatformTestCase() {
    private lateinit var file: VirtualFile
    private val range = TextRange(/* startOffset = */ 0,/* endOffset = */ 4)
    private val familyName = "Snyk Container"
    private lateinit var cut: BaseImageRemediationFix

    private val fileName = "package.json"
    private val analyticsMock = mockk<SnykAnalyticsService>(relaxed = true)

    private lateinit var psiFile: PsiFile

    override fun getTestDataPath(): String {
        val resource = BaseImageRemediationFixTest::class.java
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
        val containerIssuesForImage = createContainerIssuesForImage()
        cut = BaseImageRemediationFix(
            containerIssuesForImage,
            range,
            analyticsService = analyticsMock
        )
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    override fun tearDown() {
        try {
            unmockkAll()
        } finally {
            super.tearDown()
            pluginSettings().fileListenerEnabled = true
        }
    }

    @Test
    fun `test getText`() {
        assertEquals("Upgrade Image to nginx:1.21.4", cut.text)
        verify { analyticsMock.logQuickFixIsDisplayed(any()) }
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
        verify { analyticsMock.logQuickFixIsTriggered(any()) }
    }

    private val containerResultWithRemediationJson =
        javaClass.classLoader.getResource("container-test-results/nginx-with-remediation.json")!!
            .readText(Charsets.UTF_8)

    private fun createContainerIssuesForImage(): ContainerIssuesForImage {
        val containerResult = ContainerResult(
            listOf(Gson().fromJson(containerResultWithRemediationJson, ContainerIssuesForImage::class.java)), null
        )

        val firstContainerIssuesForImage = containerResult.allCliIssues!![0]
        val baseImageRemediationInfo =
            getContainerService(project)?.convertRemediation(firstContainerIssuesForImage.docker.baseImageRemediation)
        return firstContainerIssuesForImage.copy(
            baseImageRemediationInfo = baseImageRemediationInfo,
            workloadImages = emptyList()
        )
    }
}
