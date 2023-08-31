package io.snyk.plugin.services

import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.unmockkAll
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.resetSettings

class SnykTaskQueueServiceHeavyTest : HeavyPlatformTestCase() {

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        super.tearDown()
    }

    fun `test service request for disposed project`() {
        PlatformTestUtil.forceCloseProjectWithoutSaving(project)

        // project.service<>() for disposed project
        // will fail with AlreadyDisposedException under Idea at least 213 and up
        val service = getSnykTaskQueueService(project)

        assertNull("Project Service request should return NULL for disposed project", service)
        myProject = null // to avoid double disposing effort in tearDown
    }
}
