package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getIacService
import io.snyk.plugin.isIacEnabled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.setupDummyCliFile
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import org.junit.Test
import snyk.iac.IacResult

class SnykTaskQueueServiceTest : LightPlatformTestCase() {

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
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

        val snykTaskQueueService = project!!.service<SnykTaskQueueService>()

        snykTaskQueueService.scan()
        // needed due to luck of disposing services by Idea test framework (bug?)
        Disposer.dispose(service<SnykApiService>())

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        snykTaskQueueService.downloadLatestRelease()

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        assertNull(snykTaskQueueService.getOssScanProgressIndicator())
    }

    @Test
    fun testCliDownloadBeforeScanIfNeeded() {
        val cliFile = getCliFile()
        if (cliFile.exists()) cliFile.delete()
        assertFalse(cliFile.exists())

        val snykTaskQueueService = project!!.service<SnykTaskQueueService>()

        val settings = pluginSettings()
        settings.ossScanEnable = true

        snykTaskQueueService.scan()
        // needed due to luck of disposing services by Idea test framework (bug?)
        Disposer.dispose(service<SnykApiService>())

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        assertTrue(cliFile.exists())
        cliFile.delete()
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
    fun testIacScanTriggeredAndProduceResults() {
        val snykTaskQueueService = project.service<SnykTaskQueueService>()
        val settings = pluginSettings()
        settings.ossScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = true

        val fakeIacResult = IacResult(null, null)

        mockkStatic("io.snyk.plugin.UtilsKt")
        every { isIacEnabled() } returns true
        every { getIacService(project).isCliInstalled() } returns true
        every { getIacService(project).scan() } returns fakeIacResult

        snykTaskQueueService.scan()

        val toolWindowPanel = project.service<SnykToolWindowPanel>()

        assertEquals(fakeIacResult, toolWindowPanel.currentIacResult)

        unmockkStatic("io.snyk.plugin.UtilsKt")
    }
}
