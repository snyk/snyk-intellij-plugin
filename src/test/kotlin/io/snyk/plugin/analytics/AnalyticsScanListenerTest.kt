package io.snyk.plugin.analytics

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getArch
import io.snyk.plugin.getOS
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper
import snyk.pluginInfo
import java.io.File
import java.nio.file.Path

class AnalyticsScanListenerTest {
    private lateinit var cut: AnalyticsScanListener
    private val projectMock: Project = mockk()
    private val settings = SnykApplicationSettingsStateService()
    private val languageServerWrapper: LanguageServerWrapper = mockk()
    private val applicationMock: Application = mockk(relaxed = true)

    @Before
    fun setUp() {
        unmockkAll()

        mockkStatic(ApplicationManager::class)
        every { ApplicationManager.getApplication() } returns applicationMock
        every { applicationMock.getService(SnykPluginDisposable::class.java) } returns mockk(relaxed = true)

        mockkStatic("io.snyk.plugin.UtilsKt")
        every { pluginSettings() } returns settings

        mockkObject(LanguageServerWrapper.Companion)
        every { LanguageServerWrapper.getInstance(projectMock) } returns languageServerWrapper
        every { languageServerWrapper.notAuthenticated() } returns false
        justRun { languageServerWrapper.sendReportAnalyticsCommand(any()) }

        mockkStatic("snyk.PluginInformationKt")
        every { pluginInfo } returns mockk(relaxed = true)
        every { pluginInfo.integrationName } returns "Snyk Intellij Plugin"
        every { pluginInfo.integrationVersion } returns "2.4.61"
        every { pluginInfo.integrationEnvironment } returns "IntelliJ IDEA"
        every { pluginInfo.integrationEnvironmentVersion } returns "2020.3.2"

        val virtualFile: VirtualFile = mockk()
        val nioPath: Path = mockk()
        val file: File = mockk()
        every { projectMock.basePath } returns "/home/user/project"
        every { projectMock.getService(SnykPluginDisposable::class.java) } returns mockk(relaxed = true)
        every { any<String>().toVirtualFile() } returns virtualFile
        every { virtualFile.toNioPath() } returns nioPath
        every { nioPath.toFile() } returns file
        every { file.absolutePath } returns "/home/user/project"


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
        assertEquals("/home/user/project", scanDoneEvent.data.attributes.path)

        assertEquals(getOS(), scanDoneEvent.data.attributes.os)
        assertEquals(getArch(), scanDoneEvent.data.attributes.arch)

        assertEquals("analytics", scanDoneEvent.data.type)
        assertEquals("Scan done", scanDoneEvent.data.attributes.eventType)
        assertEquals("Success", scanDoneEvent.data.attributes.status)
        assertEquals(settings.userAnonymousId, scanDoneEvent.data.attributes.deviceId)

        assertNotNull(scanDoneEvent.data.attributes.timestampFinished)
    }

    @Test
    fun `testScanListener scanningContainerFinished should call language server to report analytics`() {
        cut.snykScanListener.scanningContainerFinished(mockk(relaxed = true))

        verify(timeout = 3000) { languageServerWrapper.sendReportAnalyticsCommand(any()) }
    }
}
