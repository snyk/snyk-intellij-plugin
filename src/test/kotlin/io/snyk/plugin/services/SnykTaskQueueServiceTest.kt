package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.download.CliDownloader
import io.snyk.plugin.services.download.SnykCliDownloaderService
import org.eclipse.lsp4j.services.LanguageServer
import snyk.common.lsp.LanguageServerWrapper
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded

class SnykTaskQueueServiceTest : LightPlatformTestCase() {

    private lateinit var downloaderServiceMock: SnykCliDownloaderService
    private val lsMock = mockk<LanguageServer>()

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic("snyk.trust.TrustedProjectsKt")

        downloaderServiceMock = spyk(SnykCliDownloaderService())
        every { downloaderServiceMock.requestLatestReleasesInformation() } returns "testTag"

        every { getSnykCliDownloaderService() } returns downloaderServiceMock
        every { downloaderServiceMock.isFourDaysPassedSinceLastCheck() } returns false
        every { confirmScanningAndSetWorkspaceTrustedStateIfNeeded(any()) } returns true

        mockkObject(LanguageServerWrapper.Companion)
        val lswMock = mockk<LanguageServerWrapper>(relaxed = true)
        every { LanguageServerWrapper.getInstance(project) } returns lswMock
        every { lswMock.languageServer } returns lsMock
        every { lswMock.isInitialized } returns true
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        removeDummyCliFile()
        super.tearDown()
    }

    fun testCliDownloadBeforeScanIfNeeded() {
        setupAppSettingsForDownloadTests()
        every { isCliInstalled() } returns true

        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        snykTaskQueueService.scan()

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        verify { isCliInstalled() }
    }

    fun testDontDownloadCLIIfUpdatesDisabled() {
        val downloaderMock = setupMockForDownloadTest()
        val settings = setupAppSettingsForDownloadTests()
        settings.manageBinariesAutomatically = false
        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        every { isCliInstalled() } returns true

        snykTaskQueueService.scan()

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        verify(exactly = 0) { downloaderMock.downloadFile(any(), any(), any()) }
    }

    private fun setupAppSettingsForDownloadTests(): SnykApplicationSettingsStateService {
        val settings = pluginSettings()
        settings.ossScanEnable = true
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.iacScanEnabled = false
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
}
