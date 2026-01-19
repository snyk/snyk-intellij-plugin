package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.replaceService
import com.intellij.util.io.RequestBuilder
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykProjectSettingsStateService
import io.snyk.plugin.services.download.HttpRequestHelper
import io.snyk.plugin.services.download.HttpRequestHelper.createRequest
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel
import snyk.common.lsp.LanguageServerWrapper
import java.nio.file.Path

fun setupDummyCliFile() {
    val cliFile = getCliFile()

    if (!cliFile.exists()) {
        if (!cliFile.parentFile.exists()) cliFile.mkdirs()
        cliFile.createNewFile()
    }
}

fun removeDummyCliFile() {
    val cliFile = getCliFile()
    if (cliFile.exists()) {
        cliFile.delete()
    }
}

fun resetSettings(project: Project?) {
    val application = ApplicationManager.getApplication()
    application.replaceService(
        SnykApplicationSettingsStateService::class.java,
        SnykApplicationSettingsStateService(),
        application
    )
    project?.replaceService(
        SnykProjectSettingsStateService::class.java,
        SnykProjectSettingsStateService(),
        project
    )
    try {
        LanguageServerWrapper.getInstance(project!!).shutdown()
    } catch (_: Exception) {
        // ignore
    }
}

/** low level avoiding download the CLI file */
fun mockCliDownload(): RequestBuilder {
    val requestBuilderMockk = mockk<RequestBuilder>(relaxed = true)
    mockkObject(HttpRequestHelper)
    every { createRequest(any()) } returns requestBuilderMockk
    // release version
    every { requestBuilderMockk.readString() } returns "1.2.3"
    // download
    justRun { requestBuilderMockk.saveToFile(any<Path>(), any()) }
    return requestBuilderMockk
}

/**
 * Default timeout for panel initialization in milliseconds.
 * This is used by waitForPanelInit to wait for async panel initialization.
 */
const val DEFAULT_PANEL_INIT_TIMEOUT_MS = 5000L

/**
 * Waits for a SuggestionDescriptionPanel to complete its async initialization.
 * This utility is shared across tests to provide consistent timeout handling.
 *
 * @param panel The panel to wait for
 * @param timeoutMs Maximum time to wait in milliseconds (default: DEFAULT_PANEL_INIT_TIMEOUT_MS)
 * @throws AssertionError if the panel is not initialized within the timeout
 */
fun waitForPanelInit(panel: SuggestionDescriptionPanel, timeoutMs: Long = DEFAULT_PANEL_INIT_TIMEOUT_MS) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!panel.isInitialized() && System.currentTimeMillis() < deadline) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        Thread.sleep(10)
    }
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assert(panel.isInitialized()) { "Panel should be initialized within timeout" }
}
