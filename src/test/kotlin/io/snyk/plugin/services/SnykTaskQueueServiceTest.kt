package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
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
import io.snyk.plugin.services.download.SnykCliDownloaderService
import io.snyk.plugin.setupDummyCliFile
import org.awaitility.Awaitility.await
import snyk.common.lsp.LanguageServerWrapper
import snyk.container.ContainerResult
import snyk.iac.IacResult
import snyk.oss.OssResult
import snyk.oss.OssService
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import java.util.concurrent.TimeUnit

class SnykTaskQueueServiceTest : LightPlatformTestCase() {

    private lateinit var ossServiceMock: OssService
    private lateinit var downloaderServiceMock: SnykCliDownloaderService

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        ossServiceMock = mockk(relaxed = true)
        project.replaceService(OssService::class.java, ossServiceMock, project)

        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic("snyk.trust.TrustedProjectsKt")

        downloaderServiceMock = spyk(SnykCliDownloaderService())
        every { downloaderServiceMock.requestLatestReleasesInformation() } returns "testTag"

        every { getSnykCliDownloaderService() } returns downloaderServiceMock
        every { downloaderServiceMock.isFourDaysPassedSinceLastCheck() } returns false
        every { confirmScanningAndSetWorkspaceTrustedStateIfNeeded(any()) } returns true

        mockkObject(LanguageServerWrapper.Companion)
        every { LanguageServerWrapper.getInstance() } returns mockk(relaxed = true)
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        removeDummyCliFile()
        super.tearDown()
    }

    fun testSnykTaskQueueService() {
        setupDummyCliFile()
        val snykTaskQueueService = project.service<SnykTaskQueueService>()

        snykTaskQueueService.scan(false)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        snykTaskQueueService.downloadLatestRelease()
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)
        assertNull(snykTaskQueueService.ossScanProgressIndicator)
    }
    fun testCliDownloadBeforeScanIfNeeded() {
        setupAppSettingsForDownloadTests()
        every { isCliInstalled() } returns true

        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        snykTaskQueueService.scan(false)

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)
        verify { isCliInstalled() }
    }

    fun testDontDownloadCLIIfUpdatesDisabled() {
        val downloaderMock = setupMockForDownloadTest()
        val settings = setupAppSettingsForDownloadTests()
        settings.manageBinariesAutomatically = false
        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        every { isCliInstalled() } returns true

        snykTaskQueueService.scan(false)

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
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

    fun testProjectClosedWhileTaskRunning() {
        val snykTaskQueueService = project.service<SnykTaskQueueService>()

        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
        setProject(null) // to avoid double disposing effort in tearDown

        // the Task should roll out gracefully without any Exception or Error
        snykTaskQueueService.downloadLatestRelease()
    }

    fun `test LCE should be unknown in initial settings state`() {
        val settings = pluginSettings()

        assertNull(settings.localCodeEngineEnabled)
    }

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

        snykTaskQueueService.scan(false)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        assertEquals(fakeIacResult, getSnykCachedResults(project)?.currentIacResult)
    }

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

        snykTaskQueueService.scan(false)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        await().atMost(2, TimeUnit.SECONDS).until {
            getSnykCachedResults(project)?.currentContainerResult != null
        }
    }
}
