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
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Before
import org.junit.Test
import snyk.common.intentionactions.AlwaysAvailableReplacementIntentionAction
import snyk.oss.OssResult
import snyk.oss.OssVulnerabilitiesForFile
import snyk.oss.Vulnerability
import java.nio.file.Paths

@Suppress("DuplicatedCode", "FunctionName")
class OSSGradleKtsAnnotatorTest : BasePlatformTestCase() {
    private var cut = OSSGradleKtsAnnotator()
    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)
    private val buildGradleKtsFilename = "build.gradle.kts"
    private val ossResultGradleKts =
        javaClass.classLoader.getResource("oss-test-results/oss-result-gradle-kts.json")!!.readText(Charsets.UTF_8)

    private lateinit var virtualFileBuildGradleKts: VirtualFile
    private lateinit var buildGradleKts: KtFile

    private val toolWindowPanel: SnykToolWindowPanel = mockk(relaxed = true)

    override fun getTestDataPath(): String {
        val resource = OSSGradleKtsAnnotator::class.java.getResource("/test-fixtures/oss/annotator")
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
        virtualFileBuildGradleKts = myFixture.copyFileToProject(buildGradleKtsFilename)
        buildGradleKts = WriteAction.computeAndWait<KtFile, Throwable> {
            psiManager.findFile(virtualFileBuildGradleKts)!! as KtFile
        }
        cut = OSSGradleKtsAnnotator()
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
    fun `test gradle-kts apply should trigger newAnnotation call`() {
        every { toolWindowPanel.currentOssResults } returns createGradleKtsOssResultWithIssues()

        cut.apply(buildGradleKts, Unit, annotationHolderMock)

        verify(exactly = 5) { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test textRange for Gradle Kts pom`() {
        val ossResult = createGradleKtsOssResultWithIssues()
        val vulnerabilities = ossResult.allCliIssues!![0].vulnerabilities

        var issue = vulnerabilities[0] // org.jetbrains.kotlin:kotlin-test-junit")
        var expectedStart = 980
        var expectedEnd = 1018
        checkRangeFinding(expectedStart, expectedEnd, issue, buildGradleKts)

        issue = vulnerabilities[1] // org.apache.logging.log4j:log4j-core:2.14.1
        expectedStart = 664
        expectedEnd = 706
        checkRangeFinding(expectedStart, expectedEnd, issue, buildGradleKts)

        issue = vulnerabilities[2] // org.apache.logging.log4j:log4j-core:2.14.1
        expectedStart = 664
        expectedEnd = 706
        checkRangeFinding(expectedStart, expectedEnd, issue, buildGradleKts)

        issue = vulnerabilities[3]
        expectedStart = 664 // org.apache.logging.log4j:log4j-core:2.14.1
        expectedEnd = 706
        checkRangeFinding(expectedStart, expectedEnd, issue, buildGradleKts)

        issue = vulnerabilities[4]
        expectedStart = 664 // org.apache.logging.log4j:log4j-core:2.14.1
        expectedEnd = 706
        checkRangeFinding(expectedStart, expectedEnd, issue, buildGradleKts)
    }

    private fun checkRangeFinding(
        expectedStart: Int,
        expectedEnd: Int,
        issue: Vulnerability,
        psiFile: PsiFile = buildGradleKts
    ) {
        val expectedRange = TextRange(expectedStart, expectedEnd)
        val actualRange = cut.textRange(psiFile, issue)
        assertEquals(expectedRange, actualRange)
    }

    @Test
    fun `test annotation message should contain issue title`() {
        val vulnerability = createGradleKtsOssResultWithIssues().allCliIssues!!.first().vulnerabilities[0]
        val expected = "Snyk: ${vulnerability.title} in ${vulnerability.name}"

        val actual = cut.annotationMessage(vulnerability)

        assertEquals(expected, actual)
    }

    @Test
    fun `test gradle kts apply should add a quickfix if upgradePath available and introducing dep is in gradle kts`() {
        val builderMock = mockk<AnnotationBuilder>(relaxed = true)
        val result = createGradleKtsOssResultWithIssues()
        val capturedIntentionSlot = slot<IntentionAction>()
        every { toolWindowPanel.currentOssResults } returns result
        every { annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>()) } returns builderMock
        every { builderMock.withFix(capture(capturedIntentionSlot)) } returns builderMock

        cut.apply(buildGradleKts, Unit, annotationHolderMock)

        assertTrue(capturedIntentionSlot.captured is AlwaysAvailableReplacementIntentionAction)
        val action = capturedIntentionSlot.captured as AlwaysAvailableReplacementIntentionAction
        assertEquals(TextRange(700, 706), action.range)
        assertEquals("2.17.1", action.replacementText)
        verify {
            annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>())
        }
        verify(exactly = 4) { builderMock.withFix(any()) }
    }

    private fun createGradleKtsOssResultWithIssues(): OssResult =
        OssResult(listOf(Gson().fromJson(ossResultGradleKts, OssVulnerabilitiesForFile::class.java)), null)
}
