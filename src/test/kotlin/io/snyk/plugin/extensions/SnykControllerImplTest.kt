package io.snyk.plugin.extensions

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.download.SnykCliDownloaderService
import snyk.common.lsp.LanguageServerWrapper
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded

class SnykControllerImplTest : LightPlatformTestCase() {
    private val languageServerWrapper = mockk<LanguageServerWrapper>()
    private lateinit var downloaderServiceMock: SnykCliDownloaderService

    override fun setUp() {
        super.setUp()
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        mockkStatic("snyk.trust.TrustedProjectsKt")
        downloaderServiceMock = spyk(SnykCliDownloaderService())
        every { downloaderServiceMock.requestLatestReleasesInformation() } returns "testTag"
        every { getSnykCliDownloaderService() } returns downloaderServiceMock
        every { downloaderServiceMock.isFourDaysPassedSinceLastCheck() } returns false
        every { confirmScanningAndSetWorkspaceTrustedStateIfNeeded(any()) } returns true
        mockkObject(LanguageServerWrapper.Companion)
        every { LanguageServerWrapper.getInstance() } returns languageServerWrapper
        every { languageServerWrapper.isInitialized } returns true
        justRun { languageServerWrapper.sendReportAnalyticsCommand(any()) }
        justRun { languageServerWrapper.sendScanCommand(any()) }
    }

    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    fun testControllerCanTriggerScan() {
        val settings = pluginSettings()
        settings.ossScanEnable = true
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = false
        settings.containerScanEnabled = false

        val controller = SnykControllerImpl(project)
        controller.scan()

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        verify { languageServerWrapper.sendScanCommand(project) }
    }
}
