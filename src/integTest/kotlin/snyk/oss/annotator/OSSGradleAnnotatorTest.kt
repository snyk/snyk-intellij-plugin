package snyk.oss.annotator

import com.google.gson.Gson
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.junit.Before
import org.junit.Test
import snyk.common.SnykCachedResults
import snyk.common.intentionactions.AlwaysAvailableReplacementIntentionAction
import snyk.oss.OssResult
import snyk.oss.OssVulnerabilitiesForFile
import snyk.oss.Vulnerability
import java.nio.file.Paths

@Suppress("DuplicatedCode", "FunctionName")
class OSSGradleAnnotatorTest : BasePlatformTestCase() {
    private val cut by lazy { OSSGradleAnnotator() }
    private val annotationHolderMock = mockk<AnnotationHolder>(relaxed = true)

    private val ossResultGradleKtsJson =
        javaClass.classLoader.getResource("oss-test-results/oss-result-gradle-kts.json")!!.readText(Charsets.UTF_8)
    private val buildGradleKts: KtFile by lazy {
        myFixture.configureByFile("build.gradle.kts")!! as KtFile
    }
    private val ossResultGradleJson =
        javaClass.classLoader.getResource("oss-test-results/oss-result-gradle.json")!!.readText(Charsets.UTF_8)
    private val buildGradle: GroovyFile by lazy {
        val buildGradleText = OSSGradleAnnotator::class.java
            .getResource("/test-fixtures/oss/annotator/build.gradle")!!.readText(Charsets.UTF_8)
        val groovyFile = myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, buildGradleText)!! as GroovyFile
        groovyFile.name = "build.gradle"
        return@lazy groovyFile
    }

    private val snykCachedResults: SnykCachedResults = mockk(relaxed = true)

    override fun getTestDataPath(): String {
        val resource = OSSGradleAnnotator::class.java.getResource("/test-fixtures/oss/annotator")
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
    }

    override fun tearDown() {
        unmockkAll()
        project.replaceService(SnykCachedResults::class.java, SnykCachedResults(project), project)
        pluginSettings().fileListenerEnabled = true
        super.tearDown()
    }

    @Test
    fun `test gradle-kts apply should trigger newAnnotation call`() {
        every { snykCachedResults.currentOssResults } returns createGradleKtsOssResultWithIssues()

        cut.apply(buildGradleKts, Unit, annotationHolderMock)

        verify(exactly = 5) { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test gradle apply should trigger newAnnotation call`() {
        every { snykCachedResults.currentOssResults } returns createGradleOssResultWithIssues()

        cut.apply(buildGradle, Unit, annotationHolderMock)

        verify(exactly = 6) { annotationHolderMock.newAnnotation(any(), any()) }
    }

    @Test
    fun `test textRange for Gradle Kts pom`() {
        val ossResult = createGradleKtsOssResultWithIssues()
        val vulnerabilities = ossResult.allCliIssues!![0].vulnerabilities

        var issue = vulnerabilities[0] // org.jetbrains.kotlin:kotlin-test-junit
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

    @Test
    fun `test textRange for Gradle pom`() {
        val ossResult = createGradleOssResultWithIssues()
        val vulnerabilities = ossResult.allCliIssues!![0].vulnerabilities

        // 'com.google.guava:guava:29.0-jre'
        checkRangeFinding(
            expectedStart = 720,
            expectedEnd = 753,
            issue = vulnerabilities[0],
            psiFile = buildGradle
        )
        // 'junit:junit:4.13'
        checkRangeFinding(
            expectedStart = 874,
            expectedEnd = 892,
            issue = vulnerabilities[1],
            psiFile = buildGradle
        )
        // 'org.apache.logging.log4j:log4j-core:2.14.1'
        checkRangeFinding(
            expectedStart = 773,
            expectedEnd = 817,
            issue = vulnerabilities[2],
            psiFile = buildGradle
        )
        // 'org.apache.logging.log4j:log4j-core:2.14.1'
        checkRangeFinding(
            expectedStart = 773,
            expectedEnd = 817,
            issue = vulnerabilities[3],
            psiFile = buildGradle
        )
        // 'org.apache.logging.log4j:log4j-core:2.14.1'
        checkRangeFinding(
            expectedStart = 773,
            expectedEnd = 817,
            issue = vulnerabilities[4],
            psiFile = buildGradle
        )
        // 'org.apache.logging.log4j:log4j-core:2.14.1'
        checkRangeFinding(
            expectedStart = 773,
            expectedEnd = 817,
            issue = vulnerabilities[5],
            psiFile = buildGradle
        )
    }

    private fun checkRangeFinding(
        expectedStart: Int,
        expectedEnd: Int,
        issue: Vulnerability,
        psiFile: PsiFile
    ) {
        val expectedRange = TextRange(expectedStart, expectedEnd)
        val actualRange = cut.textRange(psiFile, issue)
        assertEquals(expectedRange, actualRange)
    }

    @Test
    fun `test annotation message should contain issue title`() {
        val vulnerability = createGradleKtsOssResultWithIssues().allCliIssues!!.first().vulnerabilities[0]

        val actual = cut.annotationMessage(vulnerability)

        assertTrue(actual.contains(vulnerability.title) && actual.contains(vulnerability.name))
    }

    @Test
    fun `test gradle kts apply should add a quickfix if upgradePath available and introducing dep is in gradle kts`() {
        val builderMock = mockk<AnnotationBuilder>(relaxed = true)
        val result = createGradleKtsOssResultWithIssues()
        val capturedIntentionSlot = slot<AlwaysAvailableReplacementIntentionAction>()
        every { snykCachedResults.currentOssResults } returns result
        every { annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>()) } returns builderMock
        every { builderMock.withFix(capture(capturedIntentionSlot)) } returns builderMock

        cut.apply(buildGradleKts, Unit, annotationHolderMock)

        val action = capturedIntentionSlot.captured
        assertEquals(TextRange(700, 706), action.range)
        assertEquals("2.17.1", action.replacementText)
        verify {
            annotationHolderMock.newAnnotation(any(), any()).range(any<TextRange>())
        }
        verify(exactly = 4) { builderMock.withFix(ofType(AlwaysAvailableReplacementIntentionAction::class)) }
    }

    private fun createGradleKtsOssResultWithIssues(): OssResult =
        OssResult(listOf(Gson().fromJson(ossResultGradleKtsJson, OssVulnerabilitiesForFile::class.java)))

    private fun createGradleOssResultWithIssues(): OssResult =
        OssResult(listOf(Gson().fromJson(ossResultGradleJson, OssVulnerabilitiesForFile::class.java)))
}
