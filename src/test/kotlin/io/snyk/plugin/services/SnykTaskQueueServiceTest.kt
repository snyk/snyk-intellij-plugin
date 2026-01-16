package io.snyk.plugin.services

import com.intellij.openapi.application.ApplicationManager
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
import io.snyk.plugin.events.SnykCliDownloadListener
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

        // The scan() method waits for checkCliExistsFinished event, so we need to
        // simulate it being fired after downloadLatestRelease is called
        val latch = CountDownLatch(1)
        ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(
            SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
            object : SnykCliDownloadListener {
                override fun checkCliExistsStarted() {
                    // When download check starts, immediately signal completion
                    // This simulates the CLI already being installed
                    ApplicationManager.getApplication().messageBus
                        .syncPublisher(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC)
                        .checkCliExistsFinished()
                }
                override fun checkCliExistsFinished() {
                    latch.countDown()
                }
            }
        )

        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        snykTaskQueueService.downloadLatestRelease()

        // Wait for the download check to complete with timeout
        assertTrue("Download check should complete", latch.await(10, TimeUnit.SECONDS))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        verify { isCliInstalled() }
    }

    fun testDontDownloadCLIIfUpdatesDisabled() {
        val downloaderMock = setupMockForDownloadTest()
        val settings = setupAppSettingsForDownloadTests()
        settings.manageBinariesAutomatically = false
        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        every { isCliInstalled() } returns true

        // When manageBinariesAutomatically is false, downloadLatestRelease returns early
        // without triggering actual download
        snykTaskQueueService.downloadLatestRelease()

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        verify(exactly = 0) { downloaderMock.downloadFile(any(), any(), any()) }
    }

    fun testCheckCliExistsFinishedPublishedWhenManageBinariesDisabled() {
        val settings = setupAppSettingsForDownloadTests()
        settings.manageBinariesAutomatically = false
        every { isCliInstalled() } returns true

        val latch = CountDownLatch(1)
        var eventReceived = false

        ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(
            SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
            object : SnykCliDownloadListener {
                override fun checkCliExistsFinished() {
                    eventReceived = true
                    latch.countDown()
                }
            }
        )

        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        snykTaskQueueService.downloadLatestRelease()

        // Wait for the event with a short timeout - it should be published immediately
        // when manageBinariesAutomatically is false
        assertTrue(
            "checkCliExistsFinished should be published when manageBinariesAutomatically is false",
            latch.await(5, TimeUnit.SECONDS)
        )
        assertTrue("Event should have been received", eventReceived)
    }

    fun testWaitUntilCliDownloadedDoesNotHangWhenManageBinariesDisabled() {
        val settings = setupAppSettingsForDownloadTests()
        settings.manageBinariesAutomatically = false
        every { isCliInstalled() } returns true

        val snykTaskQueueService = project.service<SnykTaskQueueService>()

        // This should complete quickly (not wait 20 minutes) because
        // checkCliExistsFinished is now published when manageBinariesAutomatically is false
        val startTime = System.currentTimeMillis()
        snykTaskQueueService.waitUntilCliDownloadedIfNeeded()
        val elapsed = System.currentTimeMillis() - startTime

        // Should complete in under 10 seconds (not the 20-minute timeout)
        assertTrue(
            "waitUntilCliDownloadedIfNeeded should complete quickly when manageBinariesAutomatically is false, " +
                "but took ${elapsed}ms",
            elapsed < 10_000
        )
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
        // Setup: ensure manageBinariesAutomatically is false so downloadLatestRelease
        // returns early without trying to run background tasks
        pluginSettings().manageBinariesAutomatically = false
        every { isCliInstalled() } returns true

        val snykTaskQueueService = project.service<SnykTaskQueueService>()

        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
        setProject(null) // to avoid double disposing effort in tearDown

        // the Task should roll out gracefully without any Exception or Error
        // With project disposed and manageBinariesAutomatically=false, this should return immediately
        snykTaskQueueService.downloadLatestRelease()
    }
}
