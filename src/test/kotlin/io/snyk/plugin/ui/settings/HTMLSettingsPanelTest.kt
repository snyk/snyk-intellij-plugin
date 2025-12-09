package io.snyk.plugin.ui.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.jcef.JBCefApp
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import snyk.common.lsp.LanguageServerWrapper

class HTMLSettingsPanelTest : BasePlatformTestCase() {
    private lateinit var settings: SnykApplicationSettingsStateService
    private lateinit var lsWrapperMock: LanguageServerWrapper

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        mockkStatic("io.snyk.plugin.UtilsKt")
        settings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns settings

        lsWrapperMock = mockk(relaxed = true)
        mockkObject(LanguageServerWrapper)
        every { LanguageServerWrapper.getInstance(project) } returns lsWrapperMock

        mockkStatic(ApplicationManager::class)
        val applicationMock = mockk<com.intellij.openapi.application.Application>(relaxed = true)
        every { ApplicationManager.getApplication() } returns applicationMock

        val snykPluginDisposable = mockk<SnykPluginDisposable>(relaxed = true)
        every { project.getService(SnykPluginDisposable::class.java) } returns snykPluginDisposable
    }

    override fun tearDown() {
        unmockkAll()
        super.tearDown()
    }

    fun `test isModified returns false initially`() {
        // Skip if JCEF is not supported
        if (!JBCefApp.isSupported()) {
            return
        }

        every { lsWrapperMock.getConfigHtml() } returns "<html><body>Test</body></html>"

        val panel = HTMLSettingsPanel(project)

        assertFalse(panel.isModified())

        panel.dispose()
    }

    fun `test isModified returns false after apply`() {
        // Skip if JCEF is not supported
        if (!JBCefApp.isSupported()) {
            return
        }

        every { lsWrapperMock.getConfigHtml() } returns "<html><body>Test</body></html>"

        val panel = HTMLSettingsPanel(project)
        panel.apply()

        assertFalse(panel.isModified())

        panel.dispose()
    }

    fun `test panel loads fallback when LS returns null`() {
        // Skip if JCEF is not supported
        if (!JBCefApp.isSupported()) {
            return
        }

        every { lsWrapperMock.getConfigHtml() } returns null

        // Set up settings for fallback template
        settings.manageBinariesAutomatically = true
        settings.cliBaseDownloadURL = "https://downloads.snyk.io"
        settings.cliPath = "/usr/local/bin/snyk"
        settings.cliReleaseChannel = "stable"

        val panel = HTMLSettingsPanel(project)

        // Panel should be created successfully with fallback
        assertNotNull(panel)

        panel.dispose()
    }

    fun `test panel loads fallback when LS returns blank string`() {
        // Skip if JCEF is not supported
        if (!JBCefApp.isSupported()) {
            return
        }

        every { lsWrapperMock.getConfigHtml() } returns "   "

        val panel = HTMLSettingsPanel(project)

        // Panel should be created successfully with fallback
        assertNotNull(panel)

        panel.dispose()
    }

    fun `test dispose cleans up browser`() {
        // Skip if JCEF is not supported
        if (!JBCefApp.isSupported()) {
            return
        }

        every { lsWrapperMock.getConfigHtml() } returns "<html><body>Test</body></html>"

        val panel = HTMLSettingsPanel(project)
        panel.dispose()

        // After disposal, calling isModified should still work (return false)
        assertFalse(panel.isModified())
    }
}
