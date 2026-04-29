package io.snyk.plugin.services

import io.snyk.plugin.Severity
import java.time.LocalDateTime
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import snyk.common.lsp.settings.LsFolderSettingsKeys

class SnykApplicationSettingsStateServiceTest {

  @Test
  fun hasSeverityEnabled_allSeveritiesEnabledByDefault() {
    val target = SnykApplicationSettingsStateService()

    assertTrue(target.hasSeverityEnabled(Severity.CRITICAL))
    assertTrue(target.hasSeverityEnabled(Severity.HIGH))
    assertTrue(target.hasSeverityEnabled(Severity.MEDIUM))
    assertTrue(target.hasSeverityEnabled(Severity.LOW))
  }

  @Test
  fun hasSeverityEnabled_someDisabled() {
    val target = SnykApplicationSettingsStateService()
    target.lowSeverityEnabled = false
    target.criticalSeverityEnabled = false

    assertFalse(target.hasSeverityEnabled(Severity.CRITICAL))
    assertFalse(target.hasSeverityEnabled(Severity.LOW))
  }

  @Test
  fun requiredLsProtocolVersion_shouldBe25() {
    val target = SnykApplicationSettingsStateService()
    assertEquals(25, target.requiredLsProtocolVersion)
  }

  @Test
  fun getState_returnsSelf() {
    val target = SnykApplicationSettingsStateService()
    val state = target.getState()
    assertTrue(state === target)
  }

  @Test
  fun loadState_copiesBeanProperties() {
    val source = SnykApplicationSettingsStateService()
    source.token = "copied-token"
    source.organization = "copied-org"

    val target = SnykApplicationSettingsStateService()
    target.loadState(source)

    assertEquals("copied-token", target.token)
    assertEquals("copied-org", target.organization)
  }

  @Suppress("DEPRECATION")
  @Test
  fun initializeComponent_migratesCliScanEnable() {
    val target = SnykApplicationSettingsStateService()
    target.cliScanEnable = false
    target.ossScanEnable = true

    target.initializeComponent()

    assertFalse(target.ossScanEnable)
    assertTrue(target.cliScanEnable)
  }

  @Suppress("DEPRECATION")
  @Test
  fun initializeComponent_migratesUseTokenAuthentication() {
    val target = SnykApplicationSettingsStateService()
    target.useTokenAuthentication = true
    target.authenticationType = AuthenticationType.OAUTH2

    target.initializeComponent()

    assertEquals(AuthenticationType.API_TOKEN, target.authenticationType)
    assertFalse(target.useTokenAuthentication)
  }

  @Test
  fun hasSeverityEnabled_unknownSeverityReturnsFalse() {
    val target = SnykApplicationSettingsStateService()
    assertFalse(target.hasSeverityEnabled(Severity.UNKNOWN))
  }

  @Test
  fun hasSeverityEnabledAndFiltered_delegatesToHasSeverityEnabled() {
    val target = SnykApplicationSettingsStateService()

    assertTrue(target.hasSeverityEnabledAndFiltered(Severity.CRITICAL))

    target.criticalSeverityEnabled = false
    assertFalse(target.hasSeverityEnabledAndFiltered(Severity.CRITICAL))
  }

  @Test
  fun hasOnlyOneSeverityEnabled_trueWhenExactlyOne() {
    val target = SnykApplicationSettingsStateService()

    target.criticalSeverityEnabled = false
    target.highSeverityEnabled = false
    target.mediumSeverityEnabled = false
    target.lowSeverityEnabled = true

    assertTrue(target.hasOnlyOneSeverityEnabled())
  }

  @Test
  fun hasOnlyOneSeverityEnabled_falseWhenMultiple() {
    val target = SnykApplicationSettingsStateService()

    assertTrue(target.lowSeverityEnabled)
    assertTrue(target.highSeverityEnabled)
    assertFalse(target.hasOnlyOneSeverityEnabled())
  }

  @Test
  fun matchFilteringWithEnablement_syncsProductsOnly() {
    val target = SnykApplicationSettingsStateService()
    target.ossScanEnable = false
    target.snykCodeSecurityIssuesScanEnable = true
    target.iacScanEnabled = false

    target.matchFilteringWithEnablement()

    assertFalse(target.treeFiltering.ossResults)
    assertTrue(target.treeFiltering.codeSecurityResults)
    assertFalse(target.treeFiltering.iacResults)
  }

  @Test
  fun isDeltaFindingsEnabled_basedOnIssuesToDisplay() {
    val target = SnykApplicationSettingsStateService()
    assertFalse(target.isDeltaFindingsEnabled())

    target.setDeltaEnabled(true)
    assertTrue(target.isDeltaFindingsEnabled())
    assertEquals(SnykApplicationSettingsStateService.DISPLAY_NEW_ISSUES, target.issuesToDisplay)

    target.setDeltaEnabled(false)
    assertFalse(target.isDeltaFindingsEnabled())
    assertEquals(SnykApplicationSettingsStateService.DISPLAY_ALL_ISSUES, target.issuesToDisplay)
  }

  @Test
  fun getLastCheckDate_returnsNullWhenNotSet() {
    val target = SnykApplicationSettingsStateService()
    target.lastCheckDate = null
    assertNull(target.getLastCheckDate())
  }

  @Test
  fun setAndGetLastCheckDate_roundTrips() {
    val target = SnykApplicationSettingsStateService()
    val now = LocalDateTime.of(2025, 1, 15, 10, 30)
    target.setLastCheckDate(now)

    val result = target.getLastCheckDate()
    assertNotNull(result)
    assertEquals(2025, result!!.year)
    assertEquals(1, result.monthValue)
    assertEquals(15, result.dayOfMonth)
  }

  @Test
  fun markAndCheckExplicitlyChanged_global() {
    val target = SnykApplicationSettingsStateService()
    assertFalse(target.isExplicitlyChanged("some_key"))

    target.markExplicitlyChanged("some_key")
    assertTrue(target.isExplicitlyChanged("some_key"))
    assertFalse(target.isExplicitlyChanged("other_key"))
  }

  @Test
  fun clearExplicitlyChanged_removesKeyFromSet() {
    val target = SnykApplicationSettingsStateService()
    target.markExplicitlyChanged("key_a")
    target.markExplicitlyChanged("key_b")
    assertTrue(target.isExplicitlyChanged("key_a"))

    target.clearExplicitlyChanged("key_a")

    assertFalse(target.isExplicitlyChanged("key_a"))
    assertTrue(target.isExplicitlyChanged("key_b"))
  }

  @Test
  fun clearExplicitlyChanged_noOpForAbsentKey() {
    val target = SnykApplicationSettingsStateService()
    // Should not throw when removing a key that was never added
    target.clearExplicitlyChanged("nonexistent")
    assertFalse(target.isExplicitlyChanged("nonexistent"))
  }

  @Test
  fun clearAllExplicitlyChanged_emptiesTheSet() {
    val target = SnykApplicationSettingsStateService()
    target.markExplicitlyChanged("key_a")
    target.markExplicitlyChanged("key_b")
    target.markExplicitlyChanged("key_c")
    assertTrue(target.isExplicitlyChanged("key_a"))
    assertTrue(target.isExplicitlyChanged("key_b"))
    assertTrue(target.isExplicitlyChanged("key_c"))

    target.clearAllExplicitlyChanged()

    assertFalse(target.isExplicitlyChanged("key_a"))
    assertFalse(target.isExplicitlyChanged("key_b"))
    assertFalse(target.isExplicitlyChanged("key_c"))
  }

  @Test
  fun markAndCheckExplicitlyChanged_folder() {
    val target = SnykApplicationSettingsStateService()
    assertFalse(target.isExplicitlyChanged("/folder", "key"))

    target.markExplicitlyChanged("/folder", "key")
    assertTrue(target.isExplicitlyChanged("/folder", "key"))
    assertFalse(target.isExplicitlyChanged("/folder", "other"))
    assertFalse(target.isExplicitlyChanged("/other", "key"))
  }

  @Test
  fun clearExplicitlyChanged_withFolderPath_removesKeyFromFolderSet() {
    val target = SnykApplicationSettingsStateService()
    target.markExplicitlyChanged("/folder", "key_a")
    target.markExplicitlyChanged("/folder", "key_b")
    assertTrue(target.isExplicitlyChanged("/folder", "key_a"))

    target.clearExplicitlyChanged("/folder", "key_a")

    assertFalse(target.isExplicitlyChanged("/folder", "key_a"))
    assertTrue(target.isExplicitlyChanged("/folder", "key_b"))
  }

  @Test
  fun clearExplicitlyChanged_withFolderPath_removesFolderEntryWhenLastKeyRemoved() {
    val target = SnykApplicationSettingsStateService()
    target.markExplicitlyChanged("/folder", "only_key")
    assertTrue(target.isExplicitlyChanged("/folder", "only_key"))

    target.clearExplicitlyChanged("/folder", "only_key")

    assertFalse(target.isExplicitlyChanged("/folder", "only_key"))
    assertTrue(target.folderExplicitChanges.isEmpty())
  }

  @Test
  fun addPendingReset_addsKeyToPendingSet() {
    val target = SnykApplicationSettingsStateService()
    target.addPendingReset("some_key")

    val resets = target.consumePendingResets()
    assertTrue(resets.contains("some_key"))
  }

  @Test
  fun consumePendingResets_returnsAndClearsPendingSet() {
    val target = SnykApplicationSettingsStateService()
    target.addPendingReset("key_a")
    target.addPendingReset("key_b")

    val first = target.consumePendingResets()
    assertEquals(setOf("key_a", "key_b"), first)

    val second = target.consumePendingResets()
    assertTrue(second.isEmpty())
  }

  @Test
  fun lsUserAssertedChangeForLsConfigurationKey_trueWhenProductDiffersFromDefaults() {
    val target = SnykApplicationSettingsStateService()
    target.iacScanEnabled = false

    assertTrue(
      target.lsUserAssertedChangeForLsConfigurationKey(LsFolderSettingsKeys.SNYK_IAC_ENABLED)
    )
    assertFalse(
      target.lsUserAssertedChangeForLsConfigurationKey(LsFolderSettingsKeys.SNYK_CODE_ENABLED)
    )
  }

  @Test
  fun initializeComponent_marksAllProductKeysExplicitWhenAnyProductDeviates() {
    val target = SnykApplicationSettingsStateService()
    target.iacScanEnabled = false

    target.initializeComponent()

    assertTrue(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_OSS_ENABLED))
    assertTrue(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
    assertTrue(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
    assertTrue(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED))
  }

  @Test
  fun clearAllExplicitlyChanged_clearsBothGlobalAndFolderChanges() {
    val target = SnykApplicationSettingsStateService()
    target.markExplicitlyChanged("global_key")
    target.markExplicitlyChanged("/folder_a", "folder_key")
    target.markExplicitlyChanged("/folder_b", "another_key")
    assertTrue(target.isExplicitlyChanged("global_key"))
    assertTrue(target.isExplicitlyChanged("/folder_a", "folder_key"))
    assertTrue(target.isExplicitlyChanged("/folder_b", "another_key"))

    target.clearAllExplicitlyChanged()

    assertFalse(target.isExplicitlyChanged("global_key"))
    assertFalse(target.isExplicitlyChanged("/folder_a", "folder_key"))
    assertFalse(target.isExplicitlyChanged("/folder_b", "another_key"))
    assertTrue(target.folderExplicitChanges.isEmpty())
  }
}
