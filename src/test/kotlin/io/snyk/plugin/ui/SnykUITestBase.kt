package io.snyk.plugin.ui

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.replaceService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.setupDummyCliFile
import org.eclipse.lsp4j.services.LanguageServer
import snyk.common.UITestUtils
import snyk.common.lsp.LanguageServerWrapper
import snyk.trust.WorkspaceTrustService
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import io.snyk.plugin.getCliFile

/**
 * Base class for Snyk UI tests providing common setup and utilities
 * Uses LightPlatform4TestCase for faster test execution
 */
abstract class SnykUITestBase : LightPlatform4TestCase() {
    protected lateinit var languageServerWrapper: LanguageServerWrapper
    protected lateinit var languageServer: LanguageServer
    protected lateinit var settings: SnykApplicationSettingsStateService
    protected lateinit var taskQueueService: SnykTaskQueueService
    protected lateinit var trustService: WorkspaceTrustService

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)

        // Mock trust service to avoid trust dialogs
        mockkStatic("snyk.trust.TrustedProjectsKt")
        every { confirmScanningAndSetWorkspaceTrustedStateIfNeeded(any()) } returns true
        
        // Make CLI executable if present
        makeCliExecutable()

        // Setup settings
        settings = pluginSettings()
        settings.token = "fake-token-for-tests"
        settings.pluginFirstRun = false

        // Disable all scan types by default - tests should enable what they need
        settings.ossScanEnable = false
        settings.iacScanEnabled = false
        settings.snykCodeSecurityIssuesScanEnable = false

        // Setup dummy CLI file
        setupDummyCliFile()

        // Mock services
        setupMockServices()
    }

    private fun setupMockServices() {
        // Mock Language Server
        languageServerWrapper = UITestUtils.createMockLanguageServerWrapper()
        languageServer = languageServerWrapper.languageServer

        // Mock task queue service
        taskQueueService = mockk(relaxed = true)
        project.replaceService(SnykTaskQueueService::class.java, taskQueueService, project)

        // Mock trust service
        trustService = mockk(relaxed = true)
        every { trustService.isPathTrusted(any()) } returns true
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        try {
            super.tearDown()
        } catch (_: Exception) {
            // Ignore teardown errors in tests
        }
    }

    /**
     * Enable OSS scanning for tests that need it
     */
    protected fun enableOssScan() {
        settings.ossScanEnable = true
    }

    /**
     * Enable Code scanning for tests that need it
     */
    protected fun enableCodeScan() {
        settings.snykCodeSecurityIssuesScanEnable = true
    }

    /**
     * Enable IaC scanning for tests that need it
     */
    protected fun enableIacScan() {
        settings.iacScanEnabled = true
    }

    /**
     * Wait for UI updates and dispatch events
     */
    protected fun waitForUiUpdates() {
        UITestUtils.waitForUiUpdates()
    }
    
    private fun makeCliExecutable() {
        try {
            val cliPath = getCliFile()
            if (cliPath != null && cliPath.exists()) {
                cliPath.setExecutable(true)
            }
        } catch (e: Exception) {
            // Ignore permission errors in tests
        }
    }
}