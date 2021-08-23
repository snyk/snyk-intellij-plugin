package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getOssService
import io.snyk.plugin.setupDummyCliFile
import org.junit.Test
import org.mockito.Mockito

class SnykTaskQueueServiceTest : LightPlatformTestCase() {

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

        val settings = getApplicationSettingsStateService()
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
        val settings = getApplicationSettingsStateService()
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
}
