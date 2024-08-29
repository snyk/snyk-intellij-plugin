package io.snyk.plugin.extensions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.download.SnykCliDownloaderService
import org.eclipse.lsp4j.services.LanguageServer
import snyk.common.lsp.LanguageServerWrapper
import snyk.trust.WorkspaceTrustService
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded

class SnykControllerImplTest : LightPlatformTestCase() {
    private val lsMock = mockk<LanguageServer>(relaxed = true)
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
        val lsMock = mockk<LanguageServer>()
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        languageServerWrapper.languageServer = lsMock
        languageServerWrapper.isInitialized = true
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

        getSnykCachedResults(project)?.currentContainerResult = null

        val controller = SnykControllerImpl(project)
        controller.scan()

        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()

        verify { lsMock.workspaceService.executeCommand(any()) }
    }
}
