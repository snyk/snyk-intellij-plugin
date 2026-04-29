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

    val jsonConfig = """{"scanningMode": "auto"}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(realSettings.scanOnSave)
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

  fun `test parseAndSaveConfig handles null values gracefully`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.organization = "original-org"
    every { pluginSettings() } returns realSettings

    // JSON with explicit null value
    val jsonConfig = """{"organization": null, "activateSnykOpenSource": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    // Organization should remain unchanged since null is not a valid String
    assertEquals("original-org", realSettings.organization)
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

  fun `test applyGlobalSettings marks explicit only for fields whose values differ from previous state`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    val jsonConfig =
      """
        {
            "activateSnykOpenSource": true,
            "activateSnykCode": true,
            "activateSnykIac": true,
            "activateSnykSecrets": true,
            "scanningMode": "auto",
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

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_OSS_ENABLED))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))

    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SCAN_AUTOMATIC))

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM))
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_LOW))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH))

    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES))

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SCAN_NET_NEW))

    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.RISK_SCORE_THRESHOLD))
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

  fun `test applyGlobalSettings with identical values does not mark any key as changed`() {
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
      assertFalse(
        "Key '$key' should NOT be marked as changed when value is identical",
        realSettings.isExplicitlyChanged(key),
      )
    }
  }

  fun `test applyGlobalSettings with changed manageBinariesAutomatically marks only AUTOMATIC_DOWNLOAD`() {
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
      "AUTOMATIC_DOWNLOAD should be marked as changed",
      realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD),
    )
    assertFalse(
      "PROXY_INSECURE should NOT be marked (value unchanged)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.PROXY_INSECURE),
    )
    assertFalse(realSettings.manageBinariesAutomatically)
  }

  fun `test applyGlobalSettings with changed token marks only TOKEN`() {
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
    assertFalse(
      "ORGANIZATION should NOT be marked (value unchanged)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.ORGANIZATION),
    )
    assertEquals("new-token", realSettings.token)
    assertEquals("my-org", realSettings.organization)
  }

  fun `test applyGlobalSettings with mix of changed and unchanged marks only changed keys`() {
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

    // Change manageBinariesAutomatically, endpoint, and authenticationMethod; keep the rest
    val jsonConfig =
      """
        {
            "manageBinariesAutomatically": false,
            "cliPath": "/original/path",
            "cliBaseDownloadURL": "https://downloads.snyk.io",
            "insecure": false,
            "organization": "keep-org",
            "endpoint": "https://new-api.snyk.io",
            "token": "keep-token",
            "authenticationMethod": "token"
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    // Changed keys
    assertTrue(
      "AUTOMATIC_DOWNLOAD should be marked (value changed)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD),
    )
    assertTrue(
      "API_ENDPOINT should be marked (value changed)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.API_ENDPOINT),
    )
    assertTrue(
      "AUTHENTICATION_METHOD should be marked (value changed)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.AUTHENTICATION_METHOD),
    )

    // Unchanged keys
    assertFalse(
      "CLI_PATH should NOT be marked (value unchanged)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_PATH),
    )
    assertFalse(
      "BINARY_BASE_URL should NOT be marked (value unchanged)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.BINARY_BASE_URL),
    )
    assertFalse(
      "PROXY_INSECURE should NOT be marked (value unchanged)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.PROXY_INSECURE),
    )
    assertFalse(
      "ORGANIZATION should NOT be marked (value unchanged)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.ORGANIZATION),
    )
    assertFalse(
      "TOKEN should NOT be marked (value unchanged)",
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
    assertTrue(
      realSettings.isExplicitlyChanged(folderPath, LsFolderSettingsKeys.RISK_SCORE_THRESHOLD)
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
    assertTrue(realSettings.isExplicitlyChanged(folderPath, LsFolderSettingsKeys.SCAN_AUTOMATIC))
    assertTrue(realSettings.isExplicitlyChanged(folderPath, LsFolderSettingsKeys.SNYK_OSS_ENABLED))
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
    assertFalse(
      realSettings.isExplicitlyChanged(folderPath, LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)
    )
    assertTrue(realSettings.isExplicitlyChanged(folderPath, LsFolderSettingsKeys.SCAN_AUTOMATIC))
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
    assertTrue(
      realSettings.isExplicitlyChanged(folderPath, LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)
    )
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

  fun `test parseAndSaveConfig scanningMode manual sets scanOnSave false and marks SCAN_AUTOMATIC`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.scanOnSave = true
    every { pluginSettings() } returns realSettings

    invokeParseAndSaveConfig("""{"scanningMode": "manual"}""")

    assertFalse(realSettings.scanOnSave)
    assertTrue(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SCAN_AUTOMATIC))
  }

  fun `test parseAndSaveConfig severity filters matching previous does not mark changed`() {
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

    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM))
    assertFalse(realSettings.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_LOW))
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
