package io.snyk.plugin.services

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatformTestCase
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.getCli
import org.junit.Test
import org.mockito.Mockito

class SnykTaskQueueServiceTest : LightPlatformTestCase() {

    @Test
    fun testSnykTaskQueueService() {
        val mockRunner = Mockito.mock(ConsoleCommandRunner::class.java)

        Mockito
            .`when`(mockRunner.execute(listOf("snyk", "--version"), project.basePath!!))
            .thenReturn("1.381.1")

        Mockito
            .`when`(mockRunner.execute(listOf("snyk", "--json", "test"), project.basePath!!))
            .thenReturn(javaClass.classLoader.getResource("group-vulnerabilities-test.json")!!.readText(Charsets.UTF_8))

        getCli(project).setConsoleCommandRunner(mockRunner)

        val snykTaskQueueService = project!!.service<SnykTaskQueueService>()

        snykTaskQueueService.scan()

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        snykTaskQueueService.downloadLatestRelease()

        assertTrue(snykTaskQueueService.getTaskQueue().isEmpty)

        assertNull(snykTaskQueueService.getCurrentProgressIndicator())
    }
}
