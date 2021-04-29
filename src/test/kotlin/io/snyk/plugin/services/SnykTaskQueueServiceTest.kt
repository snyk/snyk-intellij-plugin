package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCli
import io.snyk.plugin.getCliFile
import io.snyk.plugin.setupDummyCliFile
import org.junit.Test
import org.mockito.Mockito

class SnykTaskQueueServiceTest : LightPlatformTestCase() {

    @Test
    fun testSnykTaskQueueService() {
        setupDummyCliFile()

        val mockRunner = Mockito.mock(ConsoleCommandRunner::class.java)

        Mockito
            .`when`(mockRunner.execute(listOf(getCliFile().absolutePath, "test", "--json"), project.basePath!!))
            .thenReturn(javaClass.classLoader.getResource("group-vulnerabilities-test.json")!!.readText(Charsets.UTF_8))

        getCli(project).setConsoleCommandRunner(mockRunner)

        val snykTaskQueueService = project!!.service<SnykTaskQueueService>()

        snykTaskQueueService.scan()

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        snykTaskQueueService.downloadLatestRelease()

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        assertNull(snykTaskQueueService.getCurrentProgressIndicator())
    }

    @Test
    fun testProjectClosedWhileTaskRunning() {
        val snykTaskQueueService = project.service<SnykTaskQueueService>()

        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
        setProject(null) // to avoid double disposing effort in tearDown

        // the Task should roll out gracefully without any Exception or Error
        snykTaskQueueService.downloadLatestRelease()
    }
}
