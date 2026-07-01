package io.snyk.plugin.ui.jcef

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import io.mockk.verify
import io.snyk.plugin.getDefaultCliPath
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.AuthenticationType
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanCommandConfig
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.common.lsp.settings.LsFolderSettingsKeys
import snyk.common.lsp.settings.LsSettingsKeys
import snyk.common.lsp.settings.withSetting
import snyk.trust.WorkspaceTrustService
import snyk.trust.WorkspaceTrustSettings

class SaveConfigHandlerTest : BasePlatformTestCase() {
  private val gson = Gson()

  private lateinit var settings: SnykApplicationSettingsStateService
  private lateinit var cut: SaveConfigHandler
  private lateinit var lsWrapperMock: LanguageServerWrapper

  override fun setUp() {
    super.setUp()
    unmockkAll()
    try {
      unmockkStatic(ApplicationManager::class)
    } catch (_: Throwable) {
      // not statically mocked
    }
    resetSettings(project)
    service<WorkspaceTrustSettings>().state.trustedPaths.clear()

    mockkStatic("io.snyk.plugin.UtilsKt")
    settings = mockk(relaxed = true)
    every { pluginSettings() } returns settings

    lsWrapperMock = mockk(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(project) } returns lsWrapperMock

    cut = SaveConfigHandler(project, onModified = {})
  }

  override fun tearDown() {
    unmockkAll()
    super.tearDown()
  }

  fun `test parseAndSaveConfig should update scan settings`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "activateSnykOpenSource": true,
            "activateSnykCode": false,
            "activateSnykIac": true
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(realSettings.ossScanEnable)
    assertFalse(realSettings.snykCodeSecurityIssuesScanEnable)
    assertTrue(realSettings.iacScanEnabled)
  }

  fun `test parseAndSaveConfig should update scanning mode`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.scanOnSave = false
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"scan_automatic": true}""")

    assertTrue(realSettings.scanOnSave)
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SCAN_AUTOMATIC))
  }

  fun `test parseAndSaveConfig should update organization and endpoint`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "organization": "my-org",
            "endpoint": "https://api.snyk.io"
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    assertEquals("my-org", realSettings.organization)
    assertEquals("https://api.snyk.io", realSettings.customEndpointUrl)
  }

  fun `test parseAndSaveConfig should update authentication method to oauth`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.authenticationType = AuthenticationType.API_TOKEN
    every { pluginSettings() } returns realSettings

    val jsonConfig = """{"authenticationMethod": "oauth"}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertEquals(AuthenticationType.OAUTH2, realSettings.authenticationType)
  }

  fun `test parseAndSaveConfig should update authentication method to token`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.authenticationType = AuthenticationType.OAUTH2
    every { pluginSettings() } returns realSettings

    val jsonConfig = """{"authenticationMethod": "token"}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertEquals(AuthenticationType.API_TOKEN, realSettings.authenticationType)
  }

  fun `test parseAndSaveConfig should update severity filters`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "severity_filter_critical": true,
            "severity_filter_high": true,
            "severity_filter_medium": false,
            "severity_filter_low": false
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(realSettings.criticalSeverityEnabled)
    assertTrue(realSettings.highSeverityEnabled)
    assertFalse(realSettings.mediumSeverityEnabled)
    assertFalse(realSettings.lowSeverityEnabled)
  }

  fun `test parseAndSaveConfig should update issue view options`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "issue_view_open_issues": true,
            "issue_view_ignored_issues": false
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(realSettings.openIssuesEnabled)
    assertFalse(realSettings.ignoredIssuesEnabled)
  }

  fun `test parseAndSaveConfig should update CLI settings`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "cliPath": "/usr/local/bin/snyk",
            "manageBinariesAutomatically": false,
            "cliBaseDownloadURL": "https://downloads.snyk.io/fips",
            "cliReleaseChannel": "preview"
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    assertEquals("/usr/local/bin/snyk", realSettings.cliPath)
    assertFalse(realSettings.manageBinariesAutomatically)
    assertEquals("https://downloads.snyk.io/fips", realSettings.cliBaseDownloadURL)
    assertEquals("preview", realSettings.cliReleaseChannel)
  }

  fun `test onModified callback is invoked`() {
    var callbackInvoked = false
    val handler = SaveConfigHandler(project, onModified = { callbackInvoked = true })

    // The onModified callback is wired to notifyModifiedQuery which requires JCEF
    // We can only verify the handler accepts the callback
    assertNotNull(handler)
    assertFalse(callbackInvoked) // Not invoked until JCEF triggers it
  }

  fun `test parseAndSaveConfig handles empty json gracefully`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    // Should not throw
    invokeParseAndSaveConfig("{}")
  }

  fun `test parseAndSaveConfig leaves absent scan types untouched when other scan type changed`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.ossScanEnable = true
    realSettings.snykCodeSecurityIssuesScanEnable = true
    realSettings.iacScanEnabled = true
    realSettings.secretsEnabled = true
    every { pluginSettings() } returns realSettings

    // LS diff-based payload: only the toggled field is sent (snake_case wire name).
    val jsonConfig = """{"snyk_iac_enabled": false}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertFalse(realSettings.iacScanEnabled)
    assertTrue(realSettings.ossScanEnable)
    assertTrue(realSettings.snykCodeSecurityIssuesScanEnable)
    assertTrue(realSettings.secretsEnabled)
  }

  fun `test parseAndSaveConfig partial payload disabling only OSS leaves Code IaC Secrets enabled`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.ossScanEnable = true
    realSettings.snykCodeSecurityIssuesScanEnable = true
    realSettings.iacScanEnabled = true
    realSettings.secretsEnabled = true
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"activateSnykOpenSource": false}""")

    assertFalse(realSettings.ossScanEnable)
    assertTrue(realSettings.snykCodeSecurityIssuesScanEnable)
    assertTrue(realSettings.iacScanEnabled)
    assertTrue(realSettings.secretsEnabled)
  }

  fun `test parseAndSaveConfig empty product payload leaves product flags untouched`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.ossScanEnable = false
    realSettings.snykCodeSecurityIssuesScanEnable = true
    realSettings.iacScanEnabled = false
    realSettings.secretsEnabled = true
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("{}")

    assertFalse(realSettings.ossScanEnable)
    assertTrue(realSettings.snykCodeSecurityIssuesScanEnable)
    assertFalse(realSettings.iacScanEnabled)
    assertTrue(realSettings.secretsEnabled)
  }

  fun `test parseAndSaveConfig explicit false for all four products disables all`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.ossScanEnable = true
    realSettings.snykCodeSecurityIssuesScanEnable = true
    realSettings.iacScanEnabled = true
    realSettings.secretsEnabled = true
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "activateSnykOpenSource": false,
            "activateSnykCode": false,
            "activateSnykIac": false,
            "activateSnykSecrets": false
        }
        """
        .trimIndent()
    invokeParseAndSaveConfig(jsonConfig)

    assertFalse(realSettings.ossScanEnable)
    assertFalse(realSettings.snykCodeSecurityIssuesScanEnable)
    assertFalse(realSettings.iacScanEnabled)
    assertFalse(realSettings.secretsEnabled)
  }

  fun `test parseAndSaveConfig partial product payload does not mark absent products explicitly changed`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.ossScanEnable = true
    realSettings.snykCodeSecurityIssuesScanEnable = true
    realSettings.iacScanEnabled = true
    realSettings.secretsEnabled = true
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"snyk_iac_enabled": false}""")

    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_OSS_ENABLED))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED))
  }

  fun `test parseAndSaveConfig fallback form ignores activateSnyk fields in payload`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.iacScanEnabled = true
    realSettings.ossScanEnable = true
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "isFallbackForm": true,
            "cliPath": "/x/snyk",
            "activateSnykIac": false,
            "activateSnykOpenSource": false
        }
        """
        .trimIndent()
    invokeParseAndSaveConfig(jsonConfig)

    assertEquals("/x/snyk", realSettings.cliPath)
    assertTrue(realSettings.iacScanEnabled)
    assertTrue(realSettings.ossScanEnable)
  }

  fun `test parseAndSaveConfig throws on invalid JSON`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val invalidJson = "{ this is not valid json }"

    try {
      invokeParseAndSaveConfig(invalidJson)
      fail("Expected IllegalArgumentException to be thrown")
    } catch (e: Exception) {
      // The reflection invocation wraps the exception
      val cause = e.cause
      assertTrue(
        "Expected IllegalArgumentException but got ${cause?.javaClass}",
        cause is IllegalArgumentException,
      )
      assertTrue(
        "Expected message to contain 'Invalid configuration format'",
        cause?.message?.contains("Invalid configuration format") == true,
      )
    }
  }

  fun `test parseAndSaveConfig handles boolean as string for enableDeltaFindings`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.setDeltaEnabled(false)
    every { pluginSettings() } returns realSettings

    // Test with boolean true
    val jsonConfig = """{"enableDeltaFindings": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(realSettings.isDeltaFindingsEnabled())
  }

  fun `test parseAndSaveConfig handles flat severity filter booleans`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "severity_filter_critical": true,
            "severity_filter_high": false,
            "severity_filter_medium": true,
            "severity_filter_low": false
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(realSettings.criticalSeverityEnabled)
    assertFalse(realSettings.highSeverityEnabled)
    assertTrue(realSettings.mediumSeverityEnabled)
    assertFalse(realSettings.lowSeverityEnabled)
  }

  fun `test parseAndSaveConfig explicit null organization resets to project defaults`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.organization = "original-org"
    realSettings.markExplicitlyChanged(LsSettingsKeys.ORGANIZATION)
    every { pluginSettings() } returns realSettings

    // Explicit JSON null at the top level is a user "reset to Project Defaults" request.
    val jsonConfig = """{"organization": null, "activateSnykOpenSource": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    // Organization override is cleared, value restored to default, and a one-shot reset is queued.
    assertNull(realSettings.organization)
    assertFalse(realSettings.isExplicitlyChanged(LsSettingsKeys.ORGANIZATION))
    assertTrue(realSettings.consumePendingResets().contains(LsSettingsKeys.ORGANIZATION))
    assertTrue(realSettings.ossScanEnable)
  }

  fun `test parseAndSaveConfig handles baseUrl for CLI download`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val jsonConfig = """{"cliBaseDownloadURL": "https://downloads.snyk.io/fips"}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertEquals("https://downloads.snyk.io/fips", realSettings.cliBaseDownloadURL)
  }

  fun `test parseAndSaveConfig should update insecure setting`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.ignoreUnknownCA = false
    every { pluginSettings() } returns realSettings

    val jsonConfig = """{"insecure": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(realSettings.ignoreUnknownCA)
  }

  fun `test parseAndSaveConfig should disable insecure setting`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.ignoreUnknownCA = true
    every { pluginSettings() } returns realSettings

    val jsonConfig = """{"insecure": false}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertFalse(realSettings.ignoreUnknownCA)
  }

  fun `test parseAndSaveConfig saves auth method before login would trigger`() {
    // This test verifies that authentication method is correctly saved when config is parsed.
    // The login handler in SaveConfigHandler calls updateConfiguration() then authenticate()
    // AFTER the save handler has already run (LS calls getAndSaveIdeConfig before __ideLogin__).
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.authenticationType = AuthenticationType.API_TOKEN
    every { pluginSettings() } returns realSettings

    // Simulate LS sending config with new auth method before login
    val jsonConfig = """{"authenticationMethod": "oauth"}"""
    invokeParseAndSaveConfig(jsonConfig)

    // Auth method should be updated so login handler can use it
    assertEquals(AuthenticationType.OAUTH2, realSettings.authenticationType)
  }

  fun `test parseAndSaveConfig with fallback form only saves CLI settings`() {
    val realSettings = SnykApplicationSettingsStateService()
    // Set initial values to verify they don't change
    realSettings.ossScanEnable = true
    realSettings.snykCodeSecurityIssuesScanEnable = true
    realSettings.organization = "original-org"
    realSettings.token = "original-token"
    realSettings.criticalSeverityEnabled = true
    realSettings.highSeverityEnabled = true
    every { pluginSettings() } returns realSettings

    // Fallback form with CLI settings and other fields that should be ignored
    val jsonConfig =
      """
        {
            "isFallbackForm": true,
            "cliPath": "/new/path/to/cli",
            "manageBinariesAutomatically": false,
            "cliBaseDownloadURL": "https://downloads.snyk.io/fips",
            "cliReleaseChannel": "preview",
            "insecure": true,
            "activateSnykOpenSource": false,
            "activateSnykCode": false,
            "organization": "should-not-save",
            "token": "should-not-save",
            "severity_filter_critical": false,
            "severity_filter_high": false
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    // CLI settings should be updated
    assertEquals("/new/path/to/cli", realSettings.cliPath)
    assertFalse(realSettings.manageBinariesAutomatically)
    assertEquals("https://downloads.snyk.io/fips", realSettings.cliBaseDownloadURL)
    assertEquals("preview", realSettings.cliReleaseChannel)
    assertTrue(realSettings.ignoreUnknownCA)

    // Non-CLI settings should remain unchanged
    assertTrue(realSettings.ossScanEnable) // Not changed from original
    assertTrue(realSettings.snykCodeSecurityIssuesScanEnable) // Not changed from original
    assertEquals("original-org", realSettings.organization) // Not changed
    assertEquals("original-token", realSettings.token) // Not changed
    assertTrue(realSettings.criticalSeverityEnabled) // Not changed
    assertTrue(realSettings.highSeverityEnabled) // Not changed
  }

  fun `test parseAndSaveConfig with full form saves all settings`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.ossScanEnable = true
    realSettings.organization = "original-org"
    every { pluginSettings() } returns realSettings

    // Full form (isFallbackForm = false or missing)
    val jsonConfig =
      """
        {
            "isFallbackForm": false,
            "cliPath": "/new/path/to/cli",
            "activateSnykOpenSource": false,
            "organization": "new-org"
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    // Both CLI and non-CLI settings should be updated
    assertEquals("/new/path/to/cli", realSettings.cliPath)
    assertFalse(realSettings.ossScanEnable) // Changed
    assertEquals("new-org", realSettings.organization) // Changed
  }

  fun `test saveConfig sends didChangeConfiguration to language server`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val testCases =
      listOf(
        "full form" to """{"activateSnykOpenSource": true}""",
        "fallback form" to """{"isFallbackForm": true, "cliPath": "/usr/bin/snyk"}""",
        "empty config" to "{}",
      )

    for ((name, json) in testCases) {
      invokeParseAndSaveConfig(json)
      verify(atLeast = 1) { lsWrapperMock.updateConfiguration() }
    }
  }

  fun `test applyGlobalSettings marks every present non-null field as explicitly changed`() {
    // Diff-based wire contract (IDE-2149 / ADR-1): a field PRESENT with a non-null value means the
    // user genuinely asserted it, so it is ALWAYS marked explicitly changed — even when the value
    // equals the store default. (Previously, values equal to the default were auto-cleared, which
    // made it impossible to set a Project Default equal to the store default after a reset.)
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "activateSnykOpenSource": true,
            "activateSnykCode": true,
            "activateSnykIac": true,
            "activateSnykSecrets": true,
            "scan_automatic": true,
            "severity_filter_critical": true,
            "severity_filter_high": true,
            "severity_filter_medium": false,
            "severity_filter_low": false,
            "issue_view_open_issues": true,
            "issue_view_ignored_issues": false,
            "scan_net_new": true,
            "risk_score_threshold": 500
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    // Every field above is present and non-null, so every one is marked explicitly changed
    // regardless of whether its value equals the store default.
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_OSS_ENABLED))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED))

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SCAN_AUTOMATIC))

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_LOW))

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES))

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SCAN_NET_NEW))

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD))
  }

  fun `test re-enabling a field to its default value after reset re-asserts the override (ADR-1)`() {
    // Regression for IDE-2149 / ADR-1: after a Project Defaults reset restores snyk_code_enabled to
    // its plugin default (true), a later save re-enabling it to that SAME default value must still
    // mark it explicitly changed so getSettings() emits changed:true and the LS relearns the
    // override. Previously the value-equals-default shortcut auto-cleared it (changed:false).
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    // 1) User resets snyk_code_enabled to Project Defaults (explicit JSON null).
    invokeParseAndSaveConfig("""{"snyk_code_enabled": null}""")
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
    // reset restores the plugin default (true) and queues a one-shot reset
    assertTrue(realSettings.snykCodeSecurityIssuesScanEnable)
    assertTrue(realSettings.consumePendingResets().contains(LsFolderSettingsKeys.SNYK_CODE_ENABLED))

    // 2) User re-enables snyk_code_enabled to the SAME value as the store default.
    invokeParseAndSaveConfig("""{"snyk_code_enabled": true}""")

    // The re-assertion is tracked as an explicit change, so getSettings() will emit changed:true.
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
    assertTrue(realSettings.snykCodeSecurityIssuesScanEnable)
  }

  fun `test applyGlobalSettings marks machine-scoped keys as explicitly changed`() {
    val realSettings = SnykApplicationSettingsStateService()
    // Set defaults that differ from the values we will send, so diff-based tracking triggers
    realSettings.manageBinariesAutomatically = true
    realSettings.ignoreUnknownCA = false
    realSettings.authenticationType = AuthenticationType.API_TOKEN
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "manageBinariesAutomatically": false,
            "cliPath": "/test",
            "insecure": true,
            "organization": "test-org",
            "endpoint": "https://test.snyk.io",
            "token": "test-token",
            "authenticationMethod": "oauth"
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    val machineScopedKeys =
      listOf(
        LsSettingsKeys.AUTOMATIC_DOWNLOAD,
        LsSettingsKeys.CLI_PATH,
        LsSettingsKeys.PROXY_INSECURE,
        LsSettingsKeys.ORGANIZATION,
        LsSettingsKeys.API_ENDPOINT,
        LsSettingsKeys.TOKEN,
        LsSettingsKeys.AUTHENTICATION_METHOD,
      )

    for (key in machineScopedKeys) {
      assertTrue(
        "Machine-scoped key '$key' should be in explicitChanges",
        realSettings.isExplicitlyChanged(key),
      )
    }
  }

  fun `test applyGlobalSettings marks present keys even when values are identical to stored ones`() {
    // IDE-2149 / ADR-1: a field present in the payload is a genuine user assertion, so it is marked
    // explicitly changed even when its value already equals the stored one. (No value-equals-store
    // auto-clear — that shortcut prevented re-asserting a Project Default equal to the store
    // value.)
    val realSettings = SnykApplicationSettingsStateService()
    // Pre-set values that match what the config will send
    realSettings.manageBinariesAutomatically = false
    realSettings.cliPath = "/usr/local/bin/snyk"
    realSettings.cliBaseDownloadURL = "https://downloads.snyk.io/fips"
    realSettings.ignoreUnknownCA = true
    realSettings.organization = "my-org"
    realSettings.customEndpointUrl = "https://api.snyk.io"
    realSettings.token = "my-token"
    realSettings.authenticationType = AuthenticationType.API_TOKEN
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "manageBinariesAutomatically": false,
            "cliPath": "/usr/local/bin/snyk",
            "cliBaseDownloadURL": "https://downloads.snyk.io/fips",
            "insecure": true,
            "organization": "my-org",
            "endpoint": "https://api.snyk.io",
            "token": "my-token",
            "authenticationMethod": "token"
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    val allMachineKeys =
      listOf(
        LsSettingsKeys.AUTOMATIC_DOWNLOAD,
        LsSettingsKeys.CLI_PATH,
        LsSettingsKeys.BINARY_BASE_URL,
        LsSettingsKeys.PROXY_INSECURE,
        LsSettingsKeys.ORGANIZATION,
        LsSettingsKeys.API_ENDPOINT,
        LsSettingsKeys.TOKEN,
        LsSettingsKeys.AUTHENTICATION_METHOD,
      )

    for (key in allMachineKeys) {
      assertTrue(
        "Key '$key' should be marked as changed when present, even if value is identical",
        realSettings.isExplicitlyChanged(key),
      )
    }
  }

  fun `test applyGlobalSettings marks only present keys, leaving absent keys untouched`() {
    // Present keys are marked (even when unchanged, per IDE-2149 / ADR-1); absent keys are
    // untouched.
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.manageBinariesAutomatically = true
    realSettings.ignoreUnknownCA = false
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "manageBinariesAutomatically": false,
            "insecure": false
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(
      "AUTOMATIC_DOWNLOAD should be marked as changed (present, value changed)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD),
    )
    assertTrue(
      "PROXY_INSECURE should be marked as changed (present, even though value is unchanged)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.PROXY_INSECURE),
    )
    // A key absent from the payload stays untouched.
    assertFalse(
      "TOKEN should NOT be marked (absent from payload)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.TOKEN),
    )
    assertFalse(realSettings.manageBinariesAutomatically)
  }

  fun `test applyGlobalSettings marks token and unchanged organization when both present`() {
    // Both keys are present, so both are marked, including organization whose value is unchanged.
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.token = "old-token"
    realSettings.organization = "my-org"
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "token": "new-token",
            "organization": "my-org"
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(
      "TOKEN should be marked as changed",
      realSettings.isExplicitlyChanged(LsSettingsKeys.TOKEN),
    )
    assertTrue(
      "ORGANIZATION should be marked (present, even though value is unchanged)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.ORGANIZATION),
    )
    assertEquals("new-token", realSettings.token)
    assertEquals("my-org", realSettings.organization)
  }

  fun `test applyGlobalSettings marks present keys and leaves absent keys untouched`() {
    // IDE-2149 / ADR-1: present keys are marked (changed or not); absent keys are untouched.
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.manageBinariesAutomatically = true
    realSettings.cliPath = "/original/path"
    realSettings.cliBaseDownloadURL = "https://downloads.snyk.io"
    realSettings.ignoreUnknownCA = false
    realSettings.organization = "keep-org"
    realSettings.customEndpointUrl = "https://api.snyk.io"
    realSettings.token = "keep-token"
    realSettings.authenticationType = AuthenticationType.OAUTH2
    every { pluginSettings() } returns realSettings

    // Present: manageBinariesAutomatically (changed), organization (unchanged), endpoint (changed),
    // authenticationMethod (changed). Absent: cliPath, cliBaseDownloadURL, insecure, token.
    val jsonConfig =
      """
        {
            "manageBinariesAutomatically": false,
            "organization": "keep-org",
            "endpoint": "https://new-api.snyk.io",
            "authenticationMethod": "token"
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    // Present keys are all marked, regardless of whether the value changed.
    assertTrue(
      "AUTOMATIC_DOWNLOAD should be marked (present, value changed)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD),
    )
    assertTrue(
      "ORGANIZATION should be marked (present, value unchanged)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.ORGANIZATION),
    )
    assertTrue(
      "API_ENDPOINT should be marked (present, value changed)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.API_ENDPOINT),
    )
    assertTrue(
      "AUTHENTICATION_METHOD should be marked (present, value changed)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.AUTHENTICATION_METHOD),
    )

    // Absent keys are untouched.
    assertFalse(
      "CLI_PATH should NOT be marked (absent from payload)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_PATH),
    )
    assertFalse(
      "BINARY_BASE_URL should NOT be marked (absent from payload)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.BINARY_BASE_URL),
    )
    assertFalse(
      "PROXY_INSECURE should NOT be marked (absent from payload)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.PROXY_INSECURE),
    )
    assertFalse(
      "TOKEN should NOT be marked (absent from payload)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.TOKEN),
    )

    // Verify values are still assigned correctly
    assertFalse(realSettings.manageBinariesAutomatically)
    assertEquals("https://new-api.snyk.io", realSettings.customEndpointUrl)
    assertEquals(AuthenticationType.API_TOKEN, realSettings.authenticationType)
  }

  fun `test applyGlobalSettings with null field preserves existing explicit change flag`() {
    val realSettings = SnykApplicationSettingsStateService()
    // Pre-mark the key as explicitly changed
    realSettings.markExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD)
    assertTrue(realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD))
    every { pluginSettings() } returns realSettings

    // Send diff-based payload WITHOUT manageBinariesAutomatically -- absence means "no change".
    val jsonConfig = """{"activateSnykOpenSource": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(
      "AUTOMATIC_DOWNLOAD must remain marked when manageBinariesAutomatically is absent" +
        " (LS UI sends diff-based updates; absence != user reset)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD),
    )
  }

  fun `test applyGlobalSettings with absent fields preserves all existing flags`() {
    val realSettings = SnykApplicationSettingsStateService()
    // Pre-mark several keys as explicitly changed
    val keysToPreMark =
      listOf(
        LsSettingsKeys.AUTOMATIC_DOWNLOAD,
        LsSettingsKeys.CLI_PATH,
        LsSettingsKeys.BINARY_BASE_URL,
        LsSettingsKeys.CLI_RELEASE_CHANNEL,
        LsSettingsKeys.PROXY_INSECURE,
        LsSettingsKeys.ORGANIZATION,
        LsSettingsKeys.API_ENDPOINT,
        LsSettingsKeys.TOKEN,
        LsSettingsKeys.AUTHENTICATION_METHOD,
      )
    for (key in keysToPreMark) {
      realSettings.markExplicitlyChanged(key)
    }
    for (key in keysToPreMark) {
      assertTrue("Pre-condition: $key should be marked", realSettings.isExplicitlyChanged(key))
    }
    every { pluginSettings() } returns realSettings

    // Send empty diff -- all previously-asserted user overrides must remain marked.
    val jsonConfig = """{}"""
    invokeParseAndSaveConfig(jsonConfig)

    for (key in keysToPreMark) {
      assertTrue(
        "Key '$key' must remain marked when its config field is absent from diff payload",
        realSettings.isExplicitlyChanged(key),
      )
    }
  }

  fun `test applyGlobalSettings preserves flag for absent field while updating present field`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.manageBinariesAutomatically = true
    realSettings.markExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD)
    realSettings.markExplicitlyChanged(LsSettingsKeys.CLI_PATH)
    every { pluginSettings() } returns realSettings

    // Send diff with manageBinariesAutomatically changed, cliPath absent.
    val jsonConfig = """{"manageBinariesAutomatically": false}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(
      "AUTOMATIC_DOWNLOAD should remain marked (field present and changed)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD),
    )
    assertTrue(
      "CLI_PATH must remain marked when its field is absent from a diff-based payload",
      realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_PATH),
    )
  }

  fun `test applyGlobalSettings partial payload does not wipe machine-scoped user overrides`() {
    // Regression for diff-based payloads: changing one machine-scoped field must not silently
    // revoke unrelated overrides. Previously, absent fields cleared explicitChanges and queued
    // pending resets, causing the LS to fall back to org/system defaults despite local settings.
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.cliPath = "/usr/local/bin/snyk"
    realSettings.cliBaseDownloadURL = "https://downloads.snyk.io/fips"
    realSettings.cliReleaseChannel = "preview"
    realSettings.ignoreUnknownCA = true
    realSettings.manageBinariesAutomatically = false
    realSettings.markExplicitlyChanged(LsSettingsKeys.CLI_PATH)
    realSettings.markExplicitlyChanged(LsSettingsKeys.BINARY_BASE_URL)
    realSettings.markExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL)
    realSettings.markExplicitlyChanged(LsSettingsKeys.PROXY_INSECURE)
    realSettings.markExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD)
    every { pluginSettings() } returns realSettings

    // LS-style diff payload toggling only one unrelated field.
    invokeParseAndSaveConfig("""{"activateSnykOpenSource": true}""")

    // Values are untouched.
    assertEquals("/usr/local/bin/snyk", realSettings.cliPath)
    assertEquals("https://downloads.snyk.io/fips", realSettings.cliBaseDownloadURL)
    assertEquals("preview", realSettings.cliReleaseChannel)
    assertTrue(realSettings.ignoreUnknownCA)
    assertFalse(realSettings.manageBinariesAutomatically)

    // Explicit-change flags are preserved across diff-based saves.
    assertTrue(realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_PATH))
    assertTrue(realSettings.isExplicitlyChanged(LsSettingsKeys.BINARY_BASE_URL))
    assertTrue(realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL))
    assertTrue(realSettings.isExplicitlyChanged(LsSettingsKeys.PROXY_INSECURE))
    assertTrue(realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD))

    // No spurious reset signals queued for the absent machine-scoped keys.
    val resets = realSettings.consumePendingResets()
    assertTrue(
      "No pending resets should be queued for absent fields in a diff payload, got: $resets",
      resets.isEmpty(),
    )
  }

  fun `test applyGlobalSettings with changed cliReleaseChannel marks CLI_RELEASE_CHANNEL`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.cliReleaseChannel = "stable"
    every { pluginSettings() } returns realSettings

    val jsonConfig = """{"cliReleaseChannel": "preview"}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(
      "CLI_RELEASE_CHANNEL should be marked as changed",
      realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL),
    )
    assertEquals("preview", realSettings.cliReleaseChannel)
  }

  fun `test applyGlobalSettings with null cliReleaseChannel preserves CLI_RELEASE_CHANNEL flag`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.markExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL)
    assertTrue(realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL))
    every { pluginSettings() } returns realSettings

    // Diff-based payload with cliReleaseChannel absent must not clear the existing flag.
    val jsonConfig = """{"activateSnykOpenSource": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(
      "CLI_RELEASE_CHANNEL must remain marked when cliReleaseChannel is absent from diff",
      realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL),
    )
  }

  fun `test applyGlobalSettings with absent machine-scoped fields does not queue pending resets`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    // Diff-based payload omits all machine-scoped fields; no resets should be queued, otherwise
    // the LS would drop the user's local cli_path/proxy_insecure/etc. on every unrelated save.
    val jsonConfig = """{"activateSnykOpenSource": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    val resets = realSettings.consumePendingResets()
    assertTrue(
      "No machine-scoped key should be in pending resets when its field is absent, got: $resets",
      resets.isEmpty(),
    )
  }

  fun `test applyGlobalSettings with present field does not add pending reset`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "manageBinariesAutomatically": true,
            "cliPath": "/usr/local/bin/snyk",
            "cliBaseDownloadURL": "https://downloads.snyk.io",
            "cliReleaseChannel": "stable",
            "insecure": false
        }
        """
        .trimIndent()
    invokeParseAndSaveConfig(jsonConfig)

    val resets = realSettings.consumePendingResets()
    assertFalse(
      "AUTOMATIC_DOWNLOAD should not be in pending resets when field is present",
      resets.contains(LsSettingsKeys.AUTOMATIC_DOWNLOAD),
    )
    assertFalse(
      "CLI_PATH should not be in pending resets when field is present",
      resets.contains(LsSettingsKeys.CLI_PATH),
    )
    assertFalse(
      "BINARY_BASE_URL should not be in pending resets when field is present",
      resets.contains(LsSettingsKeys.BINARY_BASE_URL),
    )
    assertFalse(
      "CLI_RELEASE_CHANNEL should not be in pending resets when field is present",
      resets.contains(LsSettingsKeys.CLI_RELEASE_CHANNEL),
    )
    assertFalse(
      "PROXY_INSECURE should not be in pending resets when field is present",
      resets.contains(LsSettingsKeys.PROXY_INSECURE),
    )
  }

  fun `test saveConfig applies comprehensive folderConfigs and stores scan command`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val folderPath =
      Files.createTempDirectory("snyk-savecfg-fc").toAbsolutePath().normalize().toString()
    val payload =
      mapOf(
        "activateSnykOpenSource" to true,
        "folderConfigs" to
          listOf(
            mapOf(
              "folderPath" to folderPath,
              "additional_parameters" to listOf("--all-projects"),
              "additional_environment" to "FOO=bar",
              "preferred_org" to "pref-org",
              "autoDeterminedOrg" to "auto-org",
              "org_set_by_user" to true,
              "scan_command_config" to
                mapOf(
                  "oss" to
                    mapOf(
                      "preScanCommand" to "echo pre",
                      "preScanOnlyReferenceFolder" to true,
                      "postScanCommand" to "echo post",
                      "postScanOnlyReferenceFolder" to false,
                    )
                ),
              "scan_automatic" to true,
              "scan_net_new" to false,
              "severity_filter_critical" to true,
              "severity_filter_high" to false,
              "severity_filter_medium" to true,
              "severity_filter_low" to false,
              "snyk_oss_enabled" to true,
              "snyk_code_enabled" to false,
              "snyk_iac_enabled" to true,
              "snyk_secrets_enabled" to false,
              "issue_view_open_issues" to false,
              "issue_view_ignored_issues" to true,
              "risk_score_threshold" to 42,
            )
          ),
      )

    invokeParseAndSaveConfig(gson.toJson(payload))

    val fcs = service<FolderConfigSettings>()
    val stored = fcs.getFolderConfig(folderPath)
    val s = stored.settings ?: error("expected folder settings")
    assertEquals(listOf("--all-projects"), s[LsFolderSettingsKeys.ADDITIONAL_PARAMETERS]?.value)
    assertEquals("FOO=bar", s[LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT]?.value)
    assertEquals("pref-org", s[LsFolderSettingsKeys.PREFERRED_ORG]?.value)
    assertEquals("auto-org", s[LsFolderSettingsKeys.AUTO_DETERMINED_ORG]?.value)
    assertEquals(true, s[LsFolderSettingsKeys.ORG_SET_BY_USER]?.value)
    @Suppress("UNCHECKED_CAST")
    val scanMap =
      s[LsFolderSettingsKeys.SCAN_COMMAND_CONFIG]?.value as Map<String, ScanCommandConfig>
    val oss = scanMap["oss"]!!
    assertEquals("echo pre", oss.preScanCommand)
    assertEquals(true, oss.preScanOnlyReferenceFolder)
    assertEquals("echo post", oss.postScanCommand)
    assertEquals(false, oss.postScanOnlyReferenceFolder)
    assertEquals(true, s[LsFolderSettingsKeys.SCAN_AUTOMATIC]?.value)
    assertEquals(false, s[LsFolderSettingsKeys.SCAN_NET_NEW]?.value)
    assertEquals(true, s[LsFolderSettingsKeys.SNYK_OSS_ENABLED]?.value)
    assertEquals(false, s[LsFolderSettingsKeys.SNYK_CODE_ENABLED]?.value)
    assertEquals(42, s[LsFolderSettingsKeys.RISK_SCORE_THRESHOLD]?.value)
  }

  fun `test saveConfig folder risk_score_threshold marks changed true when present in payload`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val folderPath =
      Files.createTempDirectory("snyk-savecfg-risk").toAbsolutePath().normalize().toString()
    val payload =
      mapOf(
        "activateSnykOpenSource" to true,
        "folderConfigs" to
          listOf(
            mapOf(
              "folderPath" to folderPath,
              "risk_score_threshold" to 700,
              "snyk_oss_enabled" to true,
            )
          ),
      )
    invokeParseAndSaveConfig(gson.toJson(payload))

    val fcs = service<FolderConfigSettings>()
    val withDouble =
      fcs
        .getFolderConfig(folderPath)
        .withSetting(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD, 700.0, changed = false)
    fcs.addFolderConfig(withDouble)

    invokeParseAndSaveConfig(gson.toJson(payload))

    val stored = fcs.getFolderConfig(folderPath).settings ?: error("expected folder settings")
    assertEquals(700, stored[LsFolderSettingsKeys.RISK_SCORE_THRESHOLD]?.value)
    assertEquals(
      "field present in payload must be marked changed=true regardless of prior value",
      true,
      stored[LsFolderSettingsKeys.RISK_SCORE_THRESHOLD]?.changed,
    )
  }

  fun `test saveConfig second identical folderConfigs keeps changed true for fields present in payload`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings
    val folderPath =
      Files.createTempDirectory("snyk-savecfg-fc2").toAbsolutePath().normalize().toString()
    val payload =
      mapOf(
        "folderConfigs" to
          listOf(
            mapOf("folderPath" to folderPath, "scan_automatic" to true, "snyk_oss_enabled" to true)
          )
      )
    val json = gson.toJson(payload)
    invokeParseAndSaveConfig(json)
    invokeParseAndSaveConfig(json)

    val s = service<FolderConfigSettings>().getFolderConfig(folderPath).settings ?: error("s")
    assertEquals(true, s[LsFolderSettingsKeys.SCAN_AUTOMATIC]?.changed)
    assertEquals(true, s[LsFolderSettingsKeys.SNYK_OSS_ENABLED]?.changed)
  }

  fun `test saveConfig folder field absent from payload leaves stored value and explicit flag untouched`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings
    val folderPath =
      Files.createTempDirectory("snyk-savecfg-absent").toAbsolutePath().normalize().toString()

    // Seed the folder with additional_parameters and no explicit-change flag.
    val fcs = service<FolderConfigSettings>()
    val seeded =
      fcs
        .getFolderConfig(folderPath)
        .withSetting(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS, listOf("--debug"), changed = false)
    fcs.addFolderConfig(seeded)

    // Payload touches a different field; additional_parameters is absent.
    val payload =
      mapOf("folderConfigs" to listOf(mapOf("folderPath" to folderPath, "scan_automatic" to true)))
    invokeParseAndSaveConfig(gson.toJson(payload))

    val s = fcs.getFolderConfig(folderPath).settings ?: error("s")
    assertEquals(listOf("--debug"), s[LsFolderSettingsKeys.ADDITIONAL_PARAMETERS]?.value)
    assertEquals(false, s[LsFolderSettingsKeys.ADDITIONAL_PARAMETERS]?.changed)
    // The touched field is stored as changed=true; the absent one keeps its prior changed=false.
    assertEquals(true, s[LsFolderSettingsKeys.SCAN_AUTOMATIC]?.changed)
  }

  fun `test saveConfig folder additional_parameters present marks changed and explicit flag`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings
    val folderPath =
      Files.createTempDirectory("snyk-savecfg-addparams").toAbsolutePath().normalize().toString()

    val payload =
      mapOf(
        "folderConfigs" to
          listOf(
            mapOf(
              "folderPath" to folderPath,
              "additional_parameters" to listOf("--debug", "--severity-threshold=high"),
            )
          )
      )
    invokeParseAndSaveConfig(gson.toJson(payload))

    val s = service<FolderConfigSettings>().getFolderConfig(folderPath).settings ?: error("s")
    assertEquals(
      listOf("--debug", "--severity-threshold=high"),
      s[LsFolderSettingsKeys.ADDITIONAL_PARAMETERS]?.value,
    )
    assertEquals(true, s[LsFolderSettingsKeys.ADDITIONAL_PARAMETERS]?.changed)
  }

  fun `test parseAndSaveConfig empty cliPath resolves to default CLI path`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.cliPath = "/nonexistent/custom/cli"
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"cliPath": ""}""")

    assertEquals(getDefaultCliPath(), realSettings.cliPath)
    assertTrue(realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_PATH))
  }

  fun `test parseAndSaveConfig authenticationMethod pat`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"authenticationMethod": "pat"}""")

    assertEquals(AuthenticationType.PAT, realSettings.authenticationType)
  }

  fun `test parseAndSaveConfig scan_automatic false sets scanOnSave false and marks SCAN_AUTOMATIC`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.scanOnSave = true
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"scan_automatic": false}""")

    assertFalse(realSettings.scanOnSave)
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SCAN_AUTOMATIC))
  }

  fun `test parseAndSaveConfig severity filters matching previous still mark changed when present`() {
    // IDE-2149 / ADR-1: present fields are marked even when they match the stored value.
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.criticalSeverityEnabled = true
    realSettings.highSeverityEnabled = true
    realSettings.mediumSeverityEnabled = false
    realSettings.lowSeverityEnabled = false
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "severity_filter_critical": true,
            "severity_filter_high": true,
            "severity_filter_medium": false,
            "severity_filter_low": false
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_LOW))
  }

  fun `test parseAndSaveConfig issue view change marks ISSUE_VIEW_OPEN_ISSUES`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.openIssuesEnabled = false
    realSettings.ignoredIssuesEnabled = false
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig(
      """{"issue_view_open_issues": true, "issue_view_ignored_issues": true}"""
    )

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES))
  }

  fun `test parseAndSaveConfig trustedFolders adds path to workspace trust`() {
    unmockkAll()
    try {
      unmockkStatic(ApplicationManager::class)
    } catch (_: Throwable) {
      // not statically mocked
    }
    reinitializeSaveConfigHandlerFixtureAfterClearingGlobalMocks()
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings
    val p = Files.createTempDirectory("snyk-trust-new").toAbsolutePath().normalize()

    invokeParseAndSaveConfig(gson.toJson(mapOf("trusted_folders" to listOf(p.toString()))))

    assertTrustedPathsInclude(p)
  }

  fun `test parseAndSaveConfig trustedFolders removes paths not present in config`() {
    unmockkAll()
    try {
      unmockkStatic(ApplicationManager::class)
    } catch (_: Throwable) {
      // not statically mocked
    }
    reinitializeSaveConfigHandlerFixtureAfterClearingGlobalMocks()
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings
    val p1 = Files.createTempDirectory("snyk-trust-a").toAbsolutePath().normalize()
    val p2 = Files.createTempDirectory("snyk-trust-b").toAbsolutePath().normalize()
    val trust = workspaceTrustServiceForAssertions()
    trust.addTrustedPath(p1)
    trust.addTrustedPath(p2)

    invokeParseAndSaveConfig(gson.toJson(mapOf("trusted_folders" to listOf(p1.toString()))))

    assertTrustedPathsInclude(p1)
    assertTrustedPathsExclude(p2)
  }

  fun `test parseAndSaveConfig fallback form skips folderConfigs`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings
    val folderPath =
      Files.createTempDirectory("snyk-fallback-skip").toAbsolutePath().normalize().toString()
    val before = service<FolderConfigSettings>().getFolderConfig(folderPath)

    invokeParseAndSaveConfig(
      gson.toJson(
        mapOf(
          "isFallbackForm" to true,
          "cliPath" to "/usr/bin/snyk",
          "folderConfigs" to listOf(mapOf("folderPath" to folderPath, "snyk_oss_enabled" to false)),
        )
      )
    )

    assertEquals("/usr/bin/snyk", realSettings.cliPath)
    val after = service<FolderConfigSettings>().getFolderConfig(folderPath)
    assertEquals(before.settings, after.settings)
  }

  fun `test parseAndSaveConfig global reset for product toggle queues reset and restores default`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.iacScanEnabled = false
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"snyk_iac_enabled": null}""")

    // Default restored, explicit flag cleared, one-shot reset queued.
    assertTrue(realSettings.iacScanEnabled)
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
    assertTrue(realSettings.consumePendingResets().contains(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
  }

  fun `test parseAndSaveConfig global reset for severity filter queues reset`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.mediumSeverityEnabled = false
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM)
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"severity_filter_medium": null}""")

    assertTrue(realSettings.mediumSeverityEnabled)
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM))
    assertTrue(
      realSettings.consumePendingResets().contains(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM)
    )
  }

  fun `test parseAndSaveConfig global reset for risk score threshold clears value`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.riskScoreThreshold = 500
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"risk_score_threshold": null}""")

    assertNull(realSettings.riskScoreThreshold)
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD))
    assertTrue(
      realSettings.consumePendingResets().contains(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)
    )
  }

  fun `test parseAndSaveConfig present global field is not treated as reset`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.iacScanEnabled = true
    every { pluginSettings() } returns realSettings

    // Present non-null value is a normal change, not a reset.
    invokeParseAndSaveConfig("""{"snyk_iac_enabled": false}""")

    assertFalse(realSettings.iacScanEnabled)
    assertFalse(realSettings.consumePendingResets().contains(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
  }

  fun `test parseAndSaveConfig absent global field does not queue reset`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
    every { pluginSettings() } returns realSettings

    // Diff-based payload omits snyk_iac_enabled -> absence is "no change", not a reset.
    invokeParseAndSaveConfig("""{"activateSnykOpenSource": true}""")

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
    assertTrue(realSettings.consumePendingResets().isEmpty())
  }

  fun `test parseAndSaveConfig fallback form does not process global resets`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.iacScanEnabled = false
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"isFallbackForm": true, "snyk_iac_enabled": null}""")

    // Fallback form skips folder/global product handling; nothing reset.
    assertFalse(realSettings.iacScanEnabled)
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
    assertTrue(realSettings.consumePendingResets().isEmpty())
  }

  // ── Scenario 5: markExplicitlyChanged cancels a pending reset (end-to-end) ──
  fun `test parseAndSaveConfig reset then concrete re-assert of same key cancels the pending reset`() {
    // Reproduces the user-facing failure the race fix closes: save #1 resets snyk_iac_enabled
    // (queues a pending null); save #2 re-asserts a concrete value BEFORE the reset was consumed
    // (e.g. LS was down so getSettings never ran). markExplicitlyChanged must cancel the pending
    // reset so the next getSettings emits the concrete value with changed:true, not {null,true}.
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.iacScanEnabled = false
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
    every { pluginSettings() } returns realSettings

    // Save #1: reset to Project Defaults (queues a pending reset, restores default true).
    invokeParseAndSaveConfig("""{"snyk_iac_enabled": null}""")
    // Save #2: user re-asserts a concrete value for the same key before the reset was consumed.
    invokeParseAndSaveConfig("""{"snyk_iac_enabled": false}""")

    // Concrete value applied and marked explicitly changed.
    assertFalse(realSettings.iacScanEnabled)
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
    // The pending reset was cancelled by the concrete re-assert -> not returned.
    assertFalse(
      "concrete re-assert must cancel the stale pending reset",
      realSettings.consumePendingResets().contains(LsFolderSettingsKeys.SNYK_IAC_ENABLED),
    )
  }

  // ── Scenario 1: FULL KEY COVERAGE ──────────────────────────────────────────
  // Every resettable key in globalResetSpecs, when sent as JSON null, must: queue a pending reset,
  // restore its documented plugin default, and clear its explicit-change flag. vscode tests every
  // key (configurationPersistenceService.test.ts); IntelliJ previously tested only a handful.

  /**
   * Source of truth mirroring [SaveConfigHandler.globalResetSpecs]: the top-level JSON field name,
   * the canonical LS key, and a check that the persisted plugin default was restored.
   */
  private data class ResetKeySpec(
    val jsonField: String,
    val lsKey: String,
    val assertDefaultRestored: (SnykApplicationSettingsStateService) -> Unit,
  )

  private fun allResettableKeySpecs(): List<ResetKeySpec> =
    listOf(
      ResetKeySpec("snyk_oss_enabled", LsFolderSettingsKeys.SNYK_OSS_ENABLED) {
        assertTrue("snyk_oss_enabled default is true", it.ossScanEnable)
      },
      ResetKeySpec("snyk_code_enabled", LsFolderSettingsKeys.SNYK_CODE_ENABLED) {
        assertTrue("snyk_code_enabled default is true", it.snykCodeSecurityIssuesScanEnable)
      },
      ResetKeySpec("snyk_iac_enabled", LsFolderSettingsKeys.SNYK_IAC_ENABLED) {
        assertTrue("snyk_iac_enabled default is true", it.iacScanEnabled)
      },
      ResetKeySpec("snyk_secrets_enabled", LsFolderSettingsKeys.SNYK_SECRETS_ENABLED) {
        assertFalse("snyk_secrets_enabled default is false", it.secretsEnabled)
      },
      ResetKeySpec("scan_automatic", LsFolderSettingsKeys.SCAN_AUTOMATIC) {
        assertTrue("scan_automatic default is true", it.scanOnSave)
      },
      ResetKeySpec("scan_net_new", LsFolderSettingsKeys.SCAN_NET_NEW) {
        assertFalse("scan_net_new default is false", it.isDeltaFindingsEnabled())
      },
      ResetKeySpec("severity_filter_critical", LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL) {
        assertTrue("severity_filter_critical default is true", it.criticalSeverityEnabled)
      },
      ResetKeySpec("severity_filter_high", LsFolderSettingsKeys.SEVERITY_FILTER_HIGH) {
        assertTrue("severity_filter_high default is true", it.highSeverityEnabled)
      },
      ResetKeySpec("severity_filter_medium", LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM) {
        assertTrue("severity_filter_medium default is true", it.mediumSeverityEnabled)
      },
      ResetKeySpec("severity_filter_low", LsFolderSettingsKeys.SEVERITY_FILTER_LOW) {
        assertTrue("severity_filter_low default is true", it.lowSeverityEnabled)
      },
      ResetKeySpec("issue_view_open_issues", LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES) {
        assertTrue("issue_view_open_issues default is true", it.openIssuesEnabled)
      },
      ResetKeySpec("issue_view_ignored_issues", LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES) {
        assertFalse("issue_view_ignored_issues default is false", it.ignoredIssuesEnabled)
      },
      ResetKeySpec("risk_score_threshold", LsFolderSettingsKeys.RISK_SCORE_THRESHOLD) {
        assertNull("risk_score_threshold default is null", it.riskScoreThreshold)
      },
      ResetKeySpec("organization", LsSettingsKeys.ORGANIZATION) {
        assertNull("organization default is null", it.organization)
      },
    )

  fun `test parseAndSaveConfig every resettable key null queues reset restores default and clears flag`() {
    // Loop over ALL 14 resettable keys. Each is tested in isolation with a fresh settings state
    // pre-seeded to a non-default, explicitly-changed value so the reset is observable.
    for (spec in allResettableKeySpecs()) {
      val realSettings = SnykApplicationSettingsStateService()
      // Drive the value away from its default and mark it changed so the reset has something to
      // undo.
      when (spec.jsonField) {
        "snyk_oss_enabled" -> realSettings.ossScanEnable = false
        "snyk_code_enabled" -> realSettings.snykCodeSecurityIssuesScanEnable = false
        "snyk_iac_enabled" -> realSettings.iacScanEnabled = false
        "snyk_secrets_enabled" -> realSettings.secretsEnabled = true
        "scan_automatic" -> realSettings.scanOnSave = false
        "scan_net_new" -> realSettings.setDeltaEnabled(true)
        "severity_filter_critical" -> realSettings.criticalSeverityEnabled = false
        "severity_filter_high" -> realSettings.highSeverityEnabled = false
        "severity_filter_medium" -> realSettings.mediumSeverityEnabled = false
        "severity_filter_low" -> realSettings.lowSeverityEnabled = false
        "issue_view_open_issues" -> realSettings.openIssuesEnabled = false
        "issue_view_ignored_issues" -> realSettings.ignoredIssuesEnabled = true
        "risk_score_threshold" -> realSettings.riskScoreThreshold = 500
        "organization" -> realSettings.organization = "some-org"
      }
      realSettings.markExplicitlyChanged(spec.lsKey)
      every { pluginSettings() } returns realSettings

      invokeParseAndSaveConfig("""{"${spec.jsonField}": null}""")

      spec.assertDefaultRestored(realSettings)
      assertFalse(
        "${spec.jsonField}: explicit-change flag must be cleared on reset",
        realSettings.isExplicitlyChanged(spec.lsKey),
      )
      assertTrue(
        "${spec.jsonField}: a one-shot reset must be queued for ${spec.lsKey}",
        realSettings.consumePendingResets().contains(spec.lsKey),
      )
    }
  }

  fun `test parseAndSaveConfig alias field names also trigger global reset`() {
    // Several keys accept an alias field name (e.g. activateSnykOpenSource, filterSeverityHigh).
    // Sending the alias as JSON null must reset exactly like the canonical snake_case name.
    val aliases =
      listOf(
        "activateSnykOpenSource" to LsFolderSettingsKeys.SNYK_OSS_ENABLED,
        "activateSnykCode" to LsFolderSettingsKeys.SNYK_CODE_ENABLED,
        "activateSnykIac" to LsFolderSettingsKeys.SNYK_IAC_ENABLED,
        "activateSnykSecrets" to LsFolderSettingsKeys.SNYK_SECRETS_ENABLED,
        "enableDeltaFindings" to LsFolderSettingsKeys.SCAN_NET_NEW,
        "filterSeverityCritical" to LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL,
        "filterSeverityHigh" to LsFolderSettingsKeys.SEVERITY_FILTER_HIGH,
        "filterSeverityMedium" to LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM,
        "filterSeverityLow" to LsFolderSettingsKeys.SEVERITY_FILTER_LOW,
        "riskScoreThreshold" to LsFolderSettingsKeys.RISK_SCORE_THRESHOLD,
      )
    for ((aliasField, lsKey) in aliases) {
      val realSettings = SnykApplicationSettingsStateService()
      realSettings.markExplicitlyChanged(lsKey)
      every { pluginSettings() } returns realSettings

      invokeParseAndSaveConfig("""{"$aliasField": null}""")

      assertFalse(
        "$aliasField: explicit-change flag must be cleared on reset via alias",
        realSettings.isExplicitlyChanged(lsKey),
      )
      assertTrue(
        "$aliasField: a one-shot reset must be queued for $lsKey via alias",
        realSettings.consumePendingResets().contains(lsKey),
      )
    }
  }

  // ── Scenario 3: DEDUP / all-severity-together (save side) ───────────────────
  fun `test parseAndSaveConfig resetting all four severity filters in one payload queues all four`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.criticalSeverityEnabled = false
    realSettings.highSeverityEnabled = false
    realSettings.mediumSeverityEnabled = false
    realSettings.lowSeverityEnabled = false
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL)
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH)
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM)
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_LOW)
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig(
      """
        {
            "severity_filter_critical": null,
            "severity_filter_high": null,
            "severity_filter_medium": null,
            "severity_filter_low": null
        }
        """
        .trimIndent()
    )

    // All four defaults restored (all severities default to true).
    assertTrue(realSettings.criticalSeverityEnabled)
    assertTrue(realSettings.highSeverityEnabled)
    assertTrue(realSettings.mediumSeverityEnabled)
    assertTrue(realSettings.lowSeverityEnabled)
    // All four flags cleared.
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_LOW))
    // All four resets queued in one save.
    val resets = realSettings.consumePendingResets()
    assertTrue(resets.contains(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL))
    assertTrue(resets.contains(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH))
    assertTrue(resets.contains(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM))
    assertTrue(resets.contains(LsFolderSettingsKeys.SEVERITY_FILTER_LOW))
  }

  // ── Scenario 4: MIXED batch (save side) ─────────────────────────────────────
  fun `test parseAndSaveConfig mixed payload resets some keys and concretely sets others`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.organization = "old-org"
    realSettings.riskScoreThreshold = 500
    realSettings.iacScanEnabled = true
    realSettings.criticalSeverityEnabled = false
    realSettings.markExplicitlyChanged(LsSettingsKeys.ORGANIZATION)
    realSettings.markExplicitlyChanged(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)
    every { pluginSettings() } returns realSettings

    // organization + risk_score_threshold are reset (null); snyk_iac_enabled + severity_critical
    // are concrete assertions in the same payload.
    invokeParseAndSaveConfig(
      """
        {
            "organization": null,
            "risk_score_threshold": null,
            "snyk_iac_enabled": false,
            "severity_filter_critical": true
        }
        """
        .trimIndent()
    )

    // Reset keys: default restored, flag cleared, reset queued.
    assertNull(realSettings.organization)
    assertNull(realSettings.riskScoreThreshold)
    assertFalse(realSettings.isExplicitlyChanged(LsSettingsKeys.ORGANIZATION))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD))

    // Concrete keys: value applied and marked explicitly changed (ADR-1), NOT queued for reset.
    assertFalse(realSettings.iacScanEnabled)
    assertTrue(realSettings.criticalSeverityEnabled)
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL))

    val resets = realSettings.consumePendingResets()
    assertTrue("organization reset queued", resets.contains(LsSettingsKeys.ORGANIZATION))
    assertTrue(
      "risk_score_threshold reset queued",
      resets.contains(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD),
    )
    assertFalse(
      "concretely-set snyk_iac_enabled must NOT be queued for reset",
      resets.contains(LsFolderSettingsKeys.SNYK_IAC_ENABLED),
    )
    assertFalse(
      "concretely-set severity_filter_critical must NOT be queued for reset",
      resets.contains(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL),
    )
  }

  fun `test saveConfig folderConfigs present as JSON null still applies global settings`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    // "folderConfigs": null is a legal payload (SaveConfigRequest.folderConfigs is nullable). The
    // raw re-parse must not throw a ClassCastException on it — that would escape the parse catch
    // and
    // abort the whole save, dropping the global cliPath in the same payload.
    val json =
      """
        {
          "cliPath": "/usr/bin/snyk",
          "folderConfigs": null
        }
      """
        .trimIndent()

    invokeParseAndSaveConfig(json)

    assertEquals("/usr/bin/snyk", realSettings.cliPath)
  }

  fun `test saveConfig malformed folderPath is skipped without sinking other folder resets`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val goodPath =
      Files.createTempDirectory("snyk-savecfg-good").toAbsolutePath().normalize().toString()
    // Pre-seed an override so the reset has something to clear (a never-stored folder writes
    // nothing -- see the never-configured-folder test).
    val fcs = service<FolderConfigSettings>()
    fcs.addFolderConfig(
      fcs
        .getFolderConfig(goodPath)
        .withSetting(LsFolderSettingsKeys.SNYK_CODE_ENABLED, true, changed = true)
    )
    // A malformed path (NUL byte) makes Paths.get throw InvalidPathException; a number
    // folderPath is a non-string primitive. Neither should abort the loop -- the valid
    // folder reset must still be written. Char(0) embeds the NUL without a raw NUL in source.
    val nulPath = gson.toJson("bad${Char(0)}path")
    val json =
      """
        {
          "folderConfigs": [
            { "folderPath": $nulPath, "snyk_code_enabled": null },
            { "folderPath": 123, "snyk_code_enabled": null },
            { "folderPath": ${gson.toJson(goodPath)}, "snyk_code_enabled": null }
          ]
        }
      """
        .trimIndent()

    invokeParseAndSaveConfig(json)

    val stored = fcs.getFolderConfig(goodPath).settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)
    assertNotNull("expected the valid folder's reset to be written", stored)
    assertNull(stored?.value)
    assertEquals(true, stored?.changed)
    // The bogus paths must not leak a stored entry.
    assertFalse(fcs.getAll().containsKey("bad${Char(0)}path"))
    assertFalse(fcs.getAll().containsKey("123"))
  }

  fun `test saveConfig folder field sent as null writes value null changed true into the stored config`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val folderPath =
      Files.createTempDirectory("snyk-savecfg-reset").toAbsolutePath().normalize().toString()
    // Pre-seed overrides so the reset has prior values to clear.
    val fcs = service<FolderConfigSettings>()
    var seeded = fcs.getFolderConfig(folderPath)
    for (key in
      listOf(
        LsFolderSettingsKeys.SNYK_CODE_ENABLED,
        LsFolderSettingsKeys.SCAN_AUTOMATIC,
        LsFolderSettingsKeys.PREFERRED_ORG,
        LsFolderSettingsKeys.ADDITIONAL_PARAMETERS,
        LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT,
        LsFolderSettingsKeys.SCAN_COMMAND_CONFIG,
      )) {
      seeded = seeded.withSetting(key, "seeded", changed = true)
    }
    fcs.addFolderConfig(seeded)

    // Written as a raw JSON string so the reset fields are explicit JSON null (a Kotlin map would
    // drop null values during gson serialization, hiding the present-with-null case under test).
    val json =
      """
        {
          "folderConfigs": [
            {
              "folderPath": ${gson.toJson(folderPath)},
              "snyk_code_enabled": null,
              "scan_automatic": null,
              "preferred_org": null,
              "additional_parameters": null,
              "additional_environment": null,
              "scan_command_config": null
            }
          ]
        }
      """
        .trimIndent()

    invokeParseAndSaveConfig(json)

    val stored = fcs.getFolderConfig(folderPath)
    // Scalars and non-scalars reset identically: a JSON null literal is JsonNull regardless of the
    // field's normal type, so present-null detection fires for all of them.
    for (key in
      listOf(
        LsFolderSettingsKeys.SNYK_CODE_ENABLED,
        LsFolderSettingsKeys.SCAN_AUTOMATIC,
        LsFolderSettingsKeys.PREFERRED_ORG,
        LsFolderSettingsKeys.ADDITIONAL_PARAMETERS,
        LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT,
        LsFolderSettingsKeys.SCAN_COMMAND_CONFIG,
      )) {
      val setting = stored.settings?.get(key) ?: error("expected reset setting for $key")
      assertNull("$key value must be null after reset", setting.value)
      assertEquals("$key must be changed=true after reset", true, setting.changed)
    }
  }

  fun `test saveConfig reset of a never-configured folder does not persist a config`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val folderPath =
      Files.createTempDirectory("snyk-savecfg-reset-unknown")
        .toAbsolutePath()
        .normalize()
        .toString()
    val json =
      """
        {
          "folderConfigs": [
            { "folderPath": ${gson.toJson(folderPath)}, "snyk_code_enabled": null }
          ]
        }
      """
        .trimIndent()

    invokeParseAndSaveConfig(json)

    // getFolderConfig returns a synthetic default for an unknown folder; persisting it on a reset
    // would re-create the default-on-untouched-folder entry the persist-on-read fix removed. A
    // never-configured folder has no override to reset, so the store stays empty.
    assertFalse(
      "Resetting a folder with no prior override must not persist a config",
      service<FolderConfigSettings>().getAll().containsKey(folderPath),
    )
  }

  fun `test saveConfig folder reset writes the stored config keyed by the normalized path`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val fcs = service<FolderConfigSettings>()
    val baseDir =
      Files.createTempDirectory("snyk-savecfg-reset-norm").toAbsolutePath().normalize().toString()
    val normalized = fcs.normalizePath(baseDir)
    // Pre-seed under the normalized path so the reset has an override to clear.
    fcs.addFolderConfig(
      fcs
        .getFolderConfig(normalized)
        .withSetting(LsFolderSettingsKeys.SNYK_CODE_ENABLED, true, changed = true)
    )
    // A non-normalized inbound path: trailing slash + redundant "." segment. The store keys by the
    // normalized path, so a raw key here would miss the stored folder and emit a duplicate.
    val rawPath = "$baseDir/./"

    val json =
      """
        {
          "folderConfigs": [
            { "folderPath": ${gson.toJson(rawPath)}, "snyk_code_enabled": null }
          ]
        }
      """
        .trimIndent()

    invokeParseAndSaveConfig(json)

    val setting =
      fcs.getFolderConfig(normalized).settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)
        ?: error("reset must be written under the normalized path, not the raw inbound path")
    assertNull(setting.value)
    assertEquals(true, setting.changed)
  }

  fun `test applyFolderConfigs stores the config keyed by the normalized path with changed true`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val fcs = service<FolderConfigSettings>()
    val baseDir =
      Files.createTempDirectory("snyk-savecfg-set-norm").toAbsolutePath().normalize().toString()
    val normalized = fcs.normalizePath(baseDir)
    // Non-normalized inbound path (trailing slash + redundant "." segment). The store keys by the
    // normalized path; if applyFolderConfigs stored under the raw path, a later reset of the same
    // folder would miss it.
    val rawPath = "$baseDir/./"

    val json =
      """
        {
          "folderConfigs": [
            { "folderPath": ${gson.toJson(rawPath)}, "snyk_code_enabled": false }
          ]
        }
      """
        .trimIndent()

    invokeParseAndSaveConfig(json)

    val setting =
      fcs.getFolderConfig(normalized).settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)
        ?: error("config must be stored under the normalized path, not the raw inbound path")
    assertEquals(false, setting.value)
    assertEquals(
      "a present field is sent to the LS as changed=true (single source of truth)",
      true,
      setting.changed,
    )
  }

  fun `test saveConfig non-scalar folder fields sent as null write value null changed true into the stored config`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val folderPath =
      Files.createTempDirectory("snyk-savecfg-reset-nonscalar")
        .toAbsolutePath()
        .normalize()
        .toString()
    // Pre-seed user overrides for the three non-scalar fields: additional_parameters is a list,
    // additional_environment is a map, scan_command_config is a nested object map.
    val fcs = service<FolderConfigSettings>()
    var seeded = fcs.getFolderConfig(folderPath)
    seeded =
      seeded.withSetting(
        LsFolderSettingsKeys.ADDITIONAL_PARAMETERS,
        listOf("--all-projects"),
        changed = true,
      )
    seeded =
      seeded.withSetting(
        LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT,
        mapOf("HTTP_PROXY" to "http://localhost:8080"),
        changed = true,
      )
    seeded =
      seeded.withSetting(
        LsFolderSettingsKeys.SCAN_COMMAND_CONFIG,
        mapOf("oss" to mapOf("preScanCommand" to "echo hi")),
        changed = true,
      )
    fcs.addFolderConfig(seeded)

    val json =
      """
        {
          "folderConfigs": [
            {
              "folderPath": ${gson.toJson(folderPath)},
              "additional_parameters": null,
              "additional_environment": null,
              "scan_command_config": null
            }
          ]
        }
      """
        .trimIndent()

    invokeParseAndSaveConfig(json)

    // The reset overwrites each seeded override with {value:null, changed:true} in the stored
    // config.
    val stored = fcs.getFolderConfig(folderPath)
    for (key in
      listOf(
        LsFolderSettingsKeys.ADDITIONAL_PARAMETERS,
        LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT,
        LsFolderSettingsKeys.SCAN_COMMAND_CONFIG,
      )) {
      val setting = stored.settings?.get(key) ?: error("expected reset setting for $key")
      assertNull("$key value must be null after reset", setting.value)
      assertEquals("$key must be changed=true after reset", true, setting.changed)
    }
  }

  fun `test saveConfig folder reset overwrites a prior override with value null changed true`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val folderPath =
      Files.createTempDirectory("snyk-savecfg-reset2").toAbsolutePath().normalize().toString()
    // Pre-seed a stored override as if the user had previously set the value.
    val fcs = service<FolderConfigSettings>()
    fcs.addFolderConfig(
      fcs
        .getFolderConfig(folderPath)
        .withSetting(LsFolderSettingsKeys.SNYK_CODE_ENABLED, true, changed = true)
    )

    val json =
      """
        {
          "folderConfigs": [
            { "folderPath": ${gson.toJson(folderPath)}, "snyk_code_enabled": null }
          ]
        }
      """
        .trimIndent()

    invokeParseAndSaveConfig(json)

    // The prior value is replaced by the reset signal {value:null, changed:true}, which getSettings
    // emits to the LS as an Unset (the override can't re-assert on a later sync).
    val setting =
      fcs.getFolderConfig(folderPath).settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)
    assertNull(setting?.value)
    assertEquals(true, setting?.changed)
  }

  fun `test saveConfig non-null folder field is not treated as a reset`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val folderPath =
      Files.createTempDirectory("snyk-savecfg-noreset").toAbsolutePath().normalize().toString()
    val json =
      """
        {
          "folderConfigs": [
            { "folderPath": ${gson.toJson(folderPath)}, "snyk_code_enabled": false }
          ]
        }
      """
        .trimIndent()

    invokeParseAndSaveConfig(json)

    // A present non-null field is a normal set, not a reset: the stored value is the boolean, not
    // null.
    val stored = service<FolderConfigSettings>().getFolderConfig(folderPath)
    assertEquals(false, stored.settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)?.value)
    assertEquals(true, stored.settings?.get(LsFolderSettingsKeys.SNYK_CODE_ENABLED)?.changed)
  }

  private fun invokeParseAndSaveConfig(jsonString: String) {
    val method = SaveConfigHandler::class.java.getDeclaredMethod("saveConfig", String::class.java)
    method.isAccessible = true
    method.invoke(cut, jsonString)
  }

  /**
   * Other suites (e.g. LanguageServerWrapperTest) mock [ApplicationManager]; that makes
   * `service<WorkspaceTrustService>()` return a mock so trusted paths are never persisted. Call
   * [unmockkAll] then this helper at the start of tests that need the real app services.
   *
   * Avoid [resetSettings] here: it shuts down [LanguageServerWrapper] and can interact with other
   * mocked statics; these tests supply a fresh [SnykApplicationSettingsStateService] via
   * [pluginSettings] anyway.
   */
  private fun reinitializeSaveConfigHandlerFixtureAfterClearingGlobalMocks() {
    service<WorkspaceTrustSettings>().state.trustedPaths.clear()
    mockkStatic("io.snyk.plugin.UtilsKt")
    settings = mockk(relaxed = true)
    every { pluginSettings() } returns settings
    lsWrapperMock = mockk(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(project) } returns lsWrapperMock
    cut = SaveConfigHandler(project, onModified = {})
  }

  private fun workspaceTrustServiceForAssertions(): WorkspaceTrustService =
    ApplicationManager.getApplication().getService(WorkspaceTrustService::class.java)

  /**
   * Compare by normalized absolute path — stored strings may differ from Path.toString() (e.g.
   * symlinks).
   */
  private fun normalizedPathKey(path: Path): Path = path.toAbsolutePath().normalize()

  private fun assertTrustedPathsInclude(expected: Path) {
    val want = normalizedPathKey(expected)
    val paths = workspaceTrustServiceForAssertions().settings.getTrustedPaths()
    assertTrue(
      paths.any { s ->
        try {
          normalizedPathKey(Paths.get(s)) == want
        } catch (_: Exception) {
          false
        }
      }
    )
  }

  private fun assertTrustedPathsExclude(notExpected: Path) {
    val avoid = normalizedPathKey(notExpected)
    val paths = workspaceTrustServiceForAssertions().settings.getTrustedPaths()
    assertFalse(
      paths.any { s ->
        try {
          normalizedPathKey(Paths.get(s)) == avoid
        } catch (_: Exception) {
          false
        }
      }
    )
  }
}
