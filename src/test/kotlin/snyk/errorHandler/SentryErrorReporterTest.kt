package snyk.errorHandler

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import junit.framework.TestCase
import org.junit.After
import org.junit.Before
import org.junit.Test
import snyk.PluginInformation
import snyk.common.isAnalyticsPermitted
import snyk.pluginInfo
import java.net.URI

class SentryErrorReporterTest {
    @Before
    fun setUp() {
        unmockkAll()
        mockkStatic(Sentry::class)
        // never send to Sentry
        every { Sentry.captureException(any()) } returns SentryId()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `captureException should send exceptions to Sentry when crashReportingEnabled is true`() {
        val settings = mockPluginInformation()
        setUnitTesting(false)
        settings.crashReportingEnabled = true

        SentryErrorReporter.captureException(RuntimeException("test"))

        verify(exactly = 1) { Sentry.captureException(any()) }
    }

    @Test
    fun `captureException should only report if permitted env`() {
        val settings = mockPluginInformation()
        setUnitTesting(false)
        settings.crashReportingEnabled = true

        val uris = listOf(
            "https://app.fedramp.snykgov.io",
            "https://app.eu.snyk.io/api",
            "https://app.au.snyk.io/api"
        )

        uris.forEach { uri ->
            settings.customEndpointUrl = uri

            SentryErrorReporter.captureException(RuntimeException("test"))

            verify(exactly = 0) { Sentry.captureException(any()) }
        }
    }
    @Test
    fun `captureException should send exceptions to Sentry when URL is a permitted environment`() {
        val settings = mockPluginInformation()
        setUnitTesting(false)
        settings.crashReportingEnabled = true
        settings.customEndpointUrl = "https://app.snyk.io"

        SentryErrorReporter.captureException(RuntimeException("test"))

        verify(exactly = 1) { Sentry.captureException(any()) }
    }

    @Test
    fun `captureException should not send exceptions to Sentry when crashReportingEnabled is false`() {
        val settings = mockPluginInformation()
        setUnitTesting(false)
        settings.crashReportingEnabled = false

        val e = RuntimeException("test")
        SentryErrorReporter.captureException(e)

        verify(exactly = 0) { Sentry.captureException(any()) }
    }

    @Test
    fun `captureException should not send exceptions to Sentry when testing detected`() {
        val settings = mockPluginInformation()
        settings.crashReportingEnabled = true
        setUnitTesting(true)
        SentryErrorReporter.captureException(RuntimeException("test"))

        verify(exactly = 0) { Sentry.captureException(any()) }
    }

    private fun mockPluginInformation(): SnykApplicationSettingsStateService {
        mockkStatic("io.snyk.plugin.UtilsKt")
        val settings = SnykApplicationSettingsStateService()
        every { pluginSettings() } returns settings

        mockkStatic("snyk.PluginInformationKt")
        val pluginInformation = PluginInformation(
            "testIntegrationName",
            "testIntegrationVersion",
            "testIntegrationEnvironment",
            "testIntegrationEnvironmentVersion"
        )
        every { pluginInfo } returns pluginInformation
        return settings
    }

    private fun setUnitTesting(boolean: Boolean) {
        mockkStatic(ApplicationManager::class)
        val applicationMock = mockk<Application>()
        every { ApplicationManager.getApplication() } returns applicationMock
        every { applicationMock.isUnitTestMode } returns boolean
    }
}
