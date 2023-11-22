package io.snyk.plugin.analytics

import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getArch
import io.snyk.plugin.getOS
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper
import snyk.pluginInfo
import java.util.concurrent.CompletableFuture

class AnalyticsScanListenerTest {
    private lateinit var cut: AnalyticsScanListener
    private val projectMock: Project = mockk()
    private val lsMock: LanguageServer = mockk()
    private val settings = SnykApplicationSettingsStateService()

    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { pluginSettings() } returns settings
        mockkStatic("snyk.PluginInformationKt")
        every { pluginInfo } returns mockk(relaxed = true)
        every { pluginInfo.integrationName } returns "Snyk Intellij Plugin"
        every { pluginInfo.integrationVersion } returns "2.4.61"
        every { pluginInfo.integrationEnvironment } returns "IntelliJ IDEA"
        every { pluginInfo.integrationEnvironmentVersion } returns "2020.3.2"
        cut = AnalyticsScanListener(projectMock)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetScanDone() {
        val scanDoneEvent = cut.getScanDoneEvent(
            1,
            "product",
            1,
            1,
            1,
            1
        )
        assertEquals("1", scanDoneEvent.data.attributes.durationMs)
        assertEquals("product", scanDoneEvent.data.attributes.scanType)
        assertEquals(1, scanDoneEvent.data.attributes.uniqueIssueCount.critical)
        assertEquals(1, scanDoneEvent.data.attributes.uniqueIssueCount.high)
        assertEquals(1, scanDoneEvent.data.attributes.uniqueIssueCount.medium)
        assertEquals(1, scanDoneEvent.data.attributes.uniqueIssueCount.low)

        assertEquals("IntelliJ IDEA", scanDoneEvent.data.attributes.application)
        assertEquals("2020.3.2", scanDoneEvent.data.attributes.applicationVersion)
        assertEquals("IntelliJ IDEA", scanDoneEvent.data.attributes.integrationEnvironment)
        assertEquals("2020.3.2", scanDoneEvent.data.attributes.integrationEnvironmentVersion)
        assertEquals(getOS(), scanDoneEvent.data.attributes.os)
        assertEquals(getArch(), scanDoneEvent.data.attributes.arch)

        assertEquals("analytics", scanDoneEvent.data.type)
        assertEquals("Scan done", scanDoneEvent.data.attributes.eventType)
        assertEquals("Succeeded", scanDoneEvent.data.attributes.status)
        assertEquals(settings.userAnonymousId, scanDoneEvent.data.attributes.deviceId)

        assertNotNull(scanDoneEvent.data.attributes.timestampFinished)
    }

    @Test
    fun `testScanListener scanningIacFinished should call language server to report analytics`() {
        val languageServerWrapper = LanguageServerWrapper("dummy")
        languageServerWrapper.languageServer = lsMock
        every { getSnykTaskQueueService(projectMock)?.ls } returns languageServerWrapper
        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(null)

        cut.snykScanListener.scanningIacFinished(mockk(relaxed = true))

        verify { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
    }

    @Test
    fun `testScanListener scanningOssFinished should call language server to report analytics`() {
        val languageServerWrapper = LanguageServerWrapper("dummy")
        languageServerWrapper.languageServer = lsMock
        every { getSnykTaskQueueService(projectMock)?.ls } returns languageServerWrapper
        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(null)

        cut.snykScanListener.scanningOssFinished(mockk(relaxed = true))

        verify { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
    }

    @Test
    fun `testScanListener scanningCodeFinished should call language server to report analytics`() {
        val languageServerWrapper = LanguageServerWrapper("dummy")
        languageServerWrapper.languageServer = lsMock
        every { getSnykTaskQueueService(projectMock)?.ls } returns languageServerWrapper
        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(null)

        cut.snykScanListener.scanningSnykCodeFinished(mockk(relaxed = true))

        verify { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
    }

    @Test
    fun `testScanListener scanningContainerFinished should call language server to report analytics`() {
        val languageServerWrapper = LanguageServerWrapper("dummy")
        languageServerWrapper.languageServer = lsMock
        every { getSnykTaskQueueService(projectMock)?.ls } returns languageServerWrapper
        every {
            lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>())
        } returns CompletableFuture.completedFuture(null)

        cut.snykScanListener.scanningContainerFinished(mockk(relaxed = true))

        verify { lsMock.workspaceService.executeCommand(any<ExecuteCommandParams>()) }
    }
}
