package snyk.oss.annotator

import com.google.gson.Gson
import com.intellij.codeInsight.intention.IntentionAction
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
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.hamcrest.collection.IsCollectionWithSize
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import snyk.common.intentionactions.AlwaysAvailableReplacementIntentionAction
import snyk.oss.OssResult
import snyk.oss.OssVulnerabilitiesForFile
import java.nio.file.Paths

@Suppress("DuplicatedCode", "FunctionName")
class OSSMavenAnnotatorTest : BasePlatformTestCase() {
    private var cut = OSSMavenAnnotator()
    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val fileName = "pom.xml"
    private val ossResult =
        javaClass.classLoader.getResource("oss-test-results/oss-result-maven.json")!!.readText(Charsets.UTF_8)

    private lateinit var file: VirtualFile
    private lateinit var psiFile: PsiFile

    private val toolWindowPanel: SnykToolWindowPanel = mockk(relaxed = true)

    override fun getTestDataPath(): String {
        val resource = OSSMavenAnnotator::class.java.getResource("/test-fixtures/oss/annotator")
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
        cut = OSSMavenAnnotator()
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
        assertThat(issues!!.vulnerabilities, IsCollectionWithSize.hasSize(4))
    }

    @Test
    fun `test apply should trigger newAnnotation call`() {
        every { toolWindowPanel.currentOssResults } returns createOssResultWithIssues()

        cut.apply(psiFile, Unit, annotationHolderMock)

        verify { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test textRange for maven pom`() {
        val ossResult = createOssResultWithIssues()
        val issue = ossResult.allCliIssues!!.first().vulnerabilities[0]
        val expectedStart = 757
        val expectedEnd = 763
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
    fun `test apply should add a quickfix if upgradePath available and introducing dep is in pom`() {
        val builderMock = mockk<AnnotationBuilder>(relaxed = true)
        val result = createOssResultWithIssues()
        val capturedIntentionSlot = slot<IntentionAction>()
        every { toolWindowPanel.currentOssResults } returns result
        every { annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>()) } returns builderMock
        every { builderMock.withFix(capture(capturedIntentionSlot)) } returns builderMock

        cut.apply(psiFile, Unit, annotationHolderMock)

        assertTrue(capturedIntentionSlot.captured is AlwaysAvailableReplacementIntentionAction)
        val action = capturedIntentionSlot.captured as AlwaysAvailableReplacementIntentionAction
        assertEquals(TextRange(757, 763), action.range)
        assertEquals("2.17.1", action.replacementText)
        verify {
            annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>())
        }
    }

    private fun createOssResultWithIssues(): OssResult =
        OssResult(listOf(Gson().fromJson(ossResult, OssVulnerabilitiesForFile::class.java)), null)
}
