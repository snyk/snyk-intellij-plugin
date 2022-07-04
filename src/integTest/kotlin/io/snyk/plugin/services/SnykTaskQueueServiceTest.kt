package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getContainerService
import io.snyk.plugin.getIacService
import io.snyk.plugin.getOssService
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.isContainerEnabled
import io.snyk.plugin.isIacEnabled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.download.CliDownloader
import io.snyk.plugin.services.download.LatestReleaseInfo
import io.snyk.plugin.services.download.SnykCliDownloaderService
import io.snyk.plugin.setupDummyCliFile
import org.awaitility.Awaitility.await
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import snyk.container.ContainerResult
import snyk.iac.IacResult
import snyk.oss.OssResult
import java.util.concurrent.TimeUnit

@Suppress("FunctionName")
class SnykTaskQueueServiceTest : LightPlatformTestCase() {

    private lateinit var downloaderServiceMock: SnykCliDownloaderService

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        mockkStatic("io.snyk.plugin.UtilsKt")
        downloaderServiceMock = spyk(SnykCliDownloaderService())
        every { downloaderServiceMock.requestLatestReleasesInformation() } returns LatestReleaseInfo(
            "http://testUrl",
            "testReleaseInfo",
            "testTag"
        )
        every { getSnykCliDownloaderService() } returns downloaderServiceMock
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        removeDummyCliFile()
        super.tearDown()
    }

    @Test
    fun testSnykTaskQueueService() {
        setupDummyCliFile()

        val snykTaskQueueService = project.service<SnykTaskQueueService>()

        snykTaskQueueService.scan()
        // needed due to luck of disposing services by Idea test framework (bug?)
        Disposer.dispose(service<SnykApiService>())

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        snykTaskQueueService.downloadLatestRelease()

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        assertNull(snykTaskQueueService.ossScanProgressIndicator)
    }

    @Test
    fun testCliDownloadBeforeScanIfNeeded() {
        val cliFile = getCliFile()
        val downloaderMock = setupMockForDownloadTest()
        every { downloaderMock.expectedSha() } returns "test"
        every { downloaderMock.downloadFile(any(), any(), any()) } returns cliFile
        setupAppSettingsForDownloadTests()

        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        snykTaskQueueService.scan()
        // needed due to luck of disposing services by Idea test framework (bug?)
        Disposer.dispose(service<SnykApiService>())

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        verify { downloaderMock.downloadFile(any(), any(), any()) }
    }

    @Test
    fun testDontDownloadCLIIfUpdatesDisabled() {
        val downloaderMock = setupMockForDownloadTest()
        val settings = setupAppSettingsForDownloadTests()
        settings.automaticCLIUpdatesEnabled = false

        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        snykTaskQueueService.scan()
        // needed due to luck of disposing services by Idea test framework (bug?)
        Disposer.dispose(service<SnykApiService>())

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        verify(exactly = 0) { downloaderMock.downloadFile(any(), any(), any()) }
    }

    private fun setupAppSettingsForDownloadTests(): SnykApplicationSettingsStateService {
        every { getOssService(project)?.scan() } returns OssResult(null)

        val settings = pluginSettings()
        settings.ossScanEnable = true
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = false
        settings.containerScanEnabled = false
        return settings
    }

    private fun setupMockForDownloadTest(): CliDownloader {
        every { getCliFile().exists() } returns false
        every { isCliInstalled() } returns false

        val downloaderMock = mockk<CliDownloader>()
        getSnykCliDownloaderService().downloader = downloaderMock
        return downloaderMock
    }

    @Test
    fun testProjectClosedWhileTaskRunning() {
        val snykTaskQueueService = project.service<SnykTaskQueueService>()

        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
        setProject(null) // to avoid double disposing effort in tearDown

        // the Task should roll out gracefully without any Exception or Error
        snykTaskQueueService.downloadLatestRelease()
    }

    @Test
    fun testSastEnablementCheckInScan() {
        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        val settings = pluginSettings()
        settings.ossScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = true
        settings.snykCodeQualityIssuesScanEnable = true

        snykTaskQueueService.scan()
        // needed due to luck of disposing services by Idea test framework (bug?)
        Disposer.dispose(service<SnykApiService>())

        assertNull(settings.sastOnServerEnabled)
        assertFalse(settings.snykCodeSecurityIssuesScanEnable)
        assertFalse(settings.snykCodeQualityIssuesScanEnable)
    }

    @Test
    fun `test reportFalsePositivesEnabled should be unknown in initial settings state`() {
        val settings = pluginSettings()

        assertNull(settings.reportFalsePositivesEnabled)
    }

    @Test
    fun `test LCE should be unknown in initial settings state`() {
        val settings = pluginSettings()

        assertNull(settings.localCodeEngineEnabled)
    }

    @Test
    fun `test should disable Code settings when LCE is enabled`() {
        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        val settings = pluginSettings()
        settings.sastOnServerEnabled = true
        settings.localCodeEngineEnabled = true

        snykTaskQueueService.scan()
        Disposer.dispose(service<SnykApiService>())

        assertThat(settings.snykCodeSecurityIssuesScanEnable, equalTo(false))
        assertThat(settings.snykCodeQualityIssuesScanEnable, equalTo(false))
    }

    @Test
    fun testIacScanTriggeredAndProduceResults() {
        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        val settings = pluginSettings()
        settings.ossScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = true
        getSnykCachedResults(project)?.currentIacResult = null

        val fakeIacResult = IacResult(emptyList())

        mockkStatic("io.snyk.plugin.UtilsKt")
        every { isIacEnabled() } returns true
        every { isCliInstalled() } returns true
        every { getIacService(project)?.scan() } returns fakeIacResult

        snykTaskQueueService.scan()

        assertEquals(fakeIacResult, getSnykCachedResults(project)?.currentIacResult)
    }

    @Test
    fun testContainerScanTriggeredAndProduceResults() {
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { isContainerEnabled() } returns true
        every { isCliInstalled() } returns true
        val fakeContainerResult = ContainerResult(emptyList())
        every { getContainerService(project)?.scan() } returns fakeContainerResult

        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        val settings = pluginSettings()
        settings.ossScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = false
        settings.containerScanEnabled = true

        getSnykCachedResults(project)?.currentContainerResult = null

        snykTaskQueueService.scan()

        await().atMost(2, TimeUnit.SECONDS).until {
            getSnykCachedResults(project)?.currentContainerResult != null
        }
    }
}
