package io.snyk.plugin

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before

open class SnykBaseTest {
    val project = mockk<Project>()
    val application = mockk<Application>(relaxed = true)

    @Before
    open fun setUp() {
        unmockkAll()
        mockkStatic(ApplicationManager::class)
        replaceApplicationWithMockAndSetUnitTestMode()
    }

    @After
    open fun tearDown() {
        unmockkAll()
    }

    private fun replaceApplicationWithMockAndSetUnitTestMode() {
        every { ApplicationManager.getApplication() } returns application
        every { application.isUnitTestMode } returns true
        every { application.isHeadlessEnvironment } returns true
    }

    inline fun <reified T : Any> replaceServiceWithMock(relaxed: Boolean = false): T {
        val mock: T = mockk(relaxed = relaxed)
        every { application.getService(T::class.java) } returns mock
        every { project.getService(T::class.java) } returns mock
        return mock
    }

    inline fun <reified T : Any> replaceServiceWithSpy(): T {
        val spy: T = spyk()
        every { application.getService(T::class.java) } returns spy
        every { project.getService(T::class.java) } returns spy
        return spy
    }
}
