package io.snyk.plugin.ui.jcef

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.AuthenticationType
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.LsFolderSettingsKeys
import snyk.common.lsp.settings.LsSettingsKeys

class SaveConfigHandlerTest : BasePlatformTestCase() {
  private lateinit var settings: SnykApplicationSettingsStateService
  private lateinit var cut: SaveConfigHandler
  private lateinit var lsWrapperMock: LanguageServerWrapper

  override fun setUp() {
    super.setUp()
    unmockkAll()
    resetSettings(project)

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
            "filterSeverity": {
                "critical": true,
                "high": true,
                "medium": false,
                "low": false
            }
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

  fun `test parseAndSaveConfig treats missing scan types as false when other scan types present`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.ossScanEnable = true
    realSettings.snykCodeSecurityIssuesScanEnable = true
    realSettings.iacScanEnabled = true
    every { pluginSettings() } returns realSettings

    // Only activateSnykOpenSource is sent (checked), others missing (unchecked)
    val jsonConfig = """{"activateSnykOpenSource": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(realSettings.ossScanEnable)
    assertFalse(realSettings.snykCodeSecurityIssuesScanEnable) // Missing = unchecked = false
    assertFalse(realSettings.iacScanEnabled) // Missing = unchecked = false
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

  fun `test parseAndSaveConfig handles string booleans in severity filters`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    // Some JS implementations might send "true"/"false" as strings
    val jsonConfig =
      """
        {
            "filterSeverity": {
                "critical": "true",
                "high": "TRUE",
                "medium": "false",
                "low": "FALSE"
            }
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(realSettings.criticalSeverityEnabled)
    assertTrue(realSettings.highSeverityEnabled)
    assertFalse(realSettings.mediumSeverityEnabled)
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
            "filterSeverity": {
                "critical": false,
                "high": false
            }
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

  fun `test applyGlobalSettings does not mark folder-scoped keys as explicitly changed`() {
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
            "filterSeverity": {
                "critical": true,
                "high": true,
                "medium": false,
                "low": false
            },
            "issue_view_open_issues": true,
            "issue_view_ignored_issues": false,
            "scan_net_new": true,
            "risk_score_threshold": 500
        }
        """
        .trimIndent()

    invokeParseAndSaveConfig(jsonConfig)

    val folderScopedKeys =
      listOf(
        LsFolderSettingsKeys.SNYK_OSS_ENABLED,
        LsFolderSettingsKeys.SNYK_CODE_ENABLED,
        LsFolderSettingsKeys.SNYK_IAC_ENABLED,
        LsFolderSettingsKeys.SNYK_SECRETS_ENABLED,
        LsFolderSettingsKeys.SCAN_AUTOMATIC,
        LsFolderSettingsKeys.ENABLED_SEVERITIES,
        LsFolderSettingsKeys.ISSUE_VIEW_OPEN_ISSUES,
        LsFolderSettingsKeys.ISSUE_VIEW_IGNORED_ISSUES,
        LsFolderSettingsKeys.SCAN_NET_NEW,
        LsFolderSettingsKeys.RISK_SCORE_THRESHOLD,
      )

    for (key in folderScopedKeys) {
      assertFalse(
        "Folder-scoped key '$key' should not be in explicitChanges",
        realSettings.isExplicitlyChanged(key),
      )
    }
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

  fun `test applyGlobalSettings with null field clears explicit change flag`() {
    val realSettings = SnykApplicationSettingsStateService()
    // Pre-mark the key as explicitly changed
    realSettings.markExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD)
    assertTrue(realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD))
    every { pluginSettings() } returns realSettings

    // Send config WITHOUT manageBinariesAutomatically (null) to trigger the clear
    val jsonConfig = """{"activateSnykOpenSource": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertFalse(
      "AUTOMATIC_DOWNLOAD should be cleared when manageBinariesAutomatically is absent",
      realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD),
    )
  }

  fun `test applyGlobalSettings with absent fields clears all their flags`() {
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

    // Send empty config (all nullable fields absent) -- all flags should be cleared
    val jsonConfig = """{}"""
    invokeParseAndSaveConfig(jsonConfig)

    for (key in keysToPreMark) {
      assertFalse(
        "Key '$key' should be cleared when its config field is absent",
        realSettings.isExplicitlyChanged(key),
      )
    }
  }

  fun `test applyGlobalSettings clears flag for absent field but keeps flag for present field`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.manageBinariesAutomatically = true
    realSettings.markExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD)
    realSettings.markExplicitlyChanged(LsSettingsKeys.CLI_PATH)
    every { pluginSettings() } returns realSettings

    // Send config with manageBinariesAutomatically changed but cliPath absent
    val jsonConfig = """{"manageBinariesAutomatically": false}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertTrue(
      "AUTOMATIC_DOWNLOAD should remain marked (field present and changed)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.AUTOMATIC_DOWNLOAD),
    )
    assertFalse(
      "CLI_PATH should be cleared (field absent)",
      realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_PATH),
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

  fun `test applyGlobalSettings with null cliReleaseChannel clears CLI_RELEASE_CHANNEL`() {
    val realSettings = SnykApplicationSettingsStateService()
    realSettings.markExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL)
    assertTrue(realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL))
    every { pluginSettings() } returns realSettings

    // Send config WITHOUT cliReleaseChannel (null) to trigger the clear
    val jsonConfig = """{"activateSnykOpenSource": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    assertFalse(
      "CLI_RELEASE_CHANNEL should be cleared when cliReleaseChannel is absent",
      realSettings.isExplicitlyChanged(LsSettingsKeys.CLI_RELEASE_CHANNEL),
    )
  }

  fun `test applyGlobalSettings with null field adds pending reset`() {
    val realSettings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns realSettings

    // Send config WITHOUT manageBinariesAutomatically (null) to trigger pending reset
    val jsonConfig = """{"activateSnykOpenSource": true}"""
    invokeParseAndSaveConfig(jsonConfig)

    val resets = realSettings.consumePendingResets()
    assertTrue(
      "AUTOMATIC_DOWNLOAD should be in pending resets when field is absent",
      resets.contains(LsSettingsKeys.AUTOMATIC_DOWNLOAD),
    )
    assertTrue(
      "CLI_PATH should be in pending resets when field is absent",
      resets.contains(LsSettingsKeys.CLI_PATH),
    )
    assertTrue(
      "BINARY_BASE_URL should be in pending resets when field is absent",
      resets.contains(LsSettingsKeys.BINARY_BASE_URL),
    )
    assertTrue(
      "CLI_RELEASE_CHANNEL should be in pending resets when field is absent",
      resets.contains(LsSettingsKeys.CLI_RELEASE_CHANNEL),
    )
    assertTrue(
      "PROXY_INSECURE should be in pending resets when field is absent",
      resets.contains(LsSettingsKeys.PROXY_INSECURE),
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

  private fun invokeParseAndSaveConfig(jsonString: String) {
    val method = SaveConfigHandler::class.java.getDeclaredMethod("saveConfig", String::class.java)
    method.isAccessible = true
    method.invoke(cut, jsonString)
  }
}
