package io.snyk.plugin.services

import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
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
  fun markExplicitlyChanged_cancelsPendingResetForSameKey() {
    // Race fix (IDE-2149, mirrors vscode ExplicitLspConfigurationChangeTracker): a reset queues a
    // pending null, then the user sets a concrete value -> markExplicitlyChanged must cancel the
    // pending reset so consumePendingResets() no longer returns it (the concrete value wins).
    val target = SnykApplicationSettingsStateService()
    target.addPendingReset("some_key")

    target.markExplicitlyChanged("some_key")

    assertTrue(target.isExplicitlyChanged("some_key"))
    assertFalse(target.consumePendingResets().contains("some_key"))
  }

  @Test
  fun markExplicitlyChanged_forDifferentKey_doesNotCancelPendingReset() {
    val target = SnykApplicationSettingsStateService()
    target.addPendingReset("reset_key")

    target.markExplicitlyChanged("other_key")

    assertTrue(target.consumePendingResets().contains("reset_key"))
  }

  @Test
  fun clearExplicitlyChanged_doesNotCancelPendingReset() {
    // Only markExplicitlyChanged (a concrete user assertion) cancels a pending reset.
    // clearExplicitlyChanged is part of the reset itself and must leave the pending reset intact.
    val target = SnykApplicationSettingsStateService()
    target.addPendingReset("reset_key")

    target.clearExplicitlyChanged("reset_key")

    assertTrue(target.consumePendingResets().contains("reset_key"))
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
  fun initializeComponent_marksOnlyDeviatingProductKeyExplicit() {
    // Fix 2 (IDE-2149 PR review): per-product independent checks — only the deviating key is
    // marked.
    // Previously, the OR+markAll block marked all four keys when any one deviated, which would
    // re-assert a reset OSS override if Secrets (or any other product) still deviates on startup.
    val target = SnykApplicationSettingsStateService()
    target.iacScanEnabled = false // only IaC deviates from its default (true)

    target.initializeComponent()

    assertFalse(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_OSS_ENABLED))
    assertFalse(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
    assertTrue(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
    assertFalse(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED))
  }

  @Test
  fun initializeComponent_marksAllDeviatingProductKeysExplicit() {
    // When multiple products deviate, all of their keys are marked — but not the non-deviating
    // ones.
    val target = SnykApplicationSettingsStateService()
    target.iacScanEnabled = false
    target.secretsEnabled = true // secrets default is false, so this deviates

    target.initializeComponent()

    assertFalse(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_OSS_ENABLED))
    assertFalse(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
    assertTrue(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_IAC_ENABLED))
    assertTrue(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_SECRETS_ENABLED))
  }

  @Test
  fun snykCodeDefault_matchesLanguageServerDefault_false() {
    // An unset Code value resolves to the plugin default, kept in sync with the LS flagset default
    // (false). The raw persisted value is null until something writes it.
    val target = SnykApplicationSettingsStateService()
    assertNull(target.snykCodeSecurityIssuesScanEnableRaw)
    assertFalse(target.snykCodeSecurityIssuesScanEnable)
  }

  @Test
  fun codeEnablement_unsetValueOmittedFromPersistedXml_setValueWrittenUnderLegacyName() {
    // The 2.21.0-enabled recovery depends on the enabled value being ABSENT from persisted state.
    // Lock the serialization contract using the same skip-defaults filter PersistentStateComponent
    // applies: an unset (null) raw value is not written; a set value is, under the legacy element
    // name so old configs still deserialize.
    val filter = SkipDefaultsSerializationFilter()
    val unsetXml = XmlSerializer.serialize(SnykApplicationSettingsStateService(), filter)
    assertFalse(
      unsetXml.getChildren("option").any {
        it.getAttributeValue("name") == "snykCodeSecurityIssuesScanEnable"
      }
    )

    val enabled = SnykApplicationSettingsStateService()
    enabled.snykCodeSecurityIssuesScanEnable = true
    val enabledXml = XmlSerializer.serialize(enabled, filter)
    assertTrue(
      enabledXml.getChildren("option").any {
        it.getAttributeValue("name") == "snykCodeSecurityIssuesScanEnable" &&
          it.getAttributeValue("value") == "true"
      }
    )
  }

  @Test
  fun migration_enabledCodeFrom221_recoveredWhenNoValuePersisted() {
    // 2.21.0 user who had Code enabled (the old default): the value equalled the default and so was
    // omitted from persisted state -> loads as null. On an existing install it must be recovered as
    // enabled AND asserted, so the LS (default false) honors it.
    val persisted =
      SnykApplicationSettingsStateService().apply {
        pluginFirstRun = false // existing install upgrading
        // snykCodeSecurityIssuesScanEnableRaw stays null == absent from persisted XML
      }
    val target = SnykApplicationSettingsStateService()
    target.loadState(persisted)

    target.initializeComponent()

    assertTrue(target.snykCodeSecurityIssuesScanEnable)
    assertTrue(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
    assertTrue(target.codeEnablementUpgradeMigratedV2221)
  }

  @Test
  fun migration_enabledCodeVia222Straggler_recoveredWhenConfigUnchanged() {
    // 2.21.0 -> 2.22.0 -> 2.22.1 with no config changes: 2.22.0 silently disabled Code, but that
    // disabled value equalled 2.22.0's own default (false), so it was omitted from persisted state
    // and never entered explicitChanges. The migration marker did not exist in 2.22.0 either. So
    // the loaded 2.22.1 state is indistinguishable from a direct 2.21.0 -> 2.22.1 upgrade, and
    // Code is recovered the same way.
    val persisted =
      SnykApplicationSettingsStateService().apply {
        pluginFirstRun = false // already existed since 2.21.0
        // raw == null (no code element ever persisted), SNYK_CODE_ENABLED not explicit,
        // codeEnablementUpgradeMigratedV2221 == false (absent in <= 2.22.0)
      }
    val target = SnykApplicationSettingsStateService()
    target.loadState(persisted)

    target.initializeComponent()

    assertTrue(target.snykCodeSecurityIssuesScanEnable)
    assertTrue(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
  }

  @Test
  fun migration_explicitlyDisabledCodeFrom221_preserved() {
    // 2.21.0 user who explicitly disabled Code: persisted as false (deviated from the old true
    // default), so raw loads as false. Must stay disabled and NOT be re-enabled.
    val persisted =
      SnykApplicationSettingsStateService().apply {
        pluginFirstRun = false
        snykCodeSecurityIssuesScanEnableRaw = false
      }
    val target = SnykApplicationSettingsStateService()
    target.loadState(persisted)

    target.initializeComponent()

    assertFalse(target.snykCodeSecurityIssuesScanEnable)
    assertFalse(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
  }

  @Test
  fun migration_freshInstall_defersToLanguageServer() {
    // Fresh 2.22.1 install (pluginFirstRun == true) with no persisted Code value must not be
    // seeded: LS default applies.
    val target = SnykApplicationSettingsStateService()
    assertTrue(target.pluginFirstRun)

    target.initializeComponent()

    assertFalse(target.snykCodeSecurityIssuesScanEnable)
    assertFalse(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
    assertTrue(target.codeEnablementUpgradeMigratedV2221)
  }

  @Test
  fun migration_2220UserExplicitlyDisabledCode_preserved() {
    // Users who already upgraded to 2.22.0 and explicitly disabled Code: the value equals the
    // 2.22.0 default (false) so it was omitted (raw null), but the toggle is tracked in
    // explicitChanges, which persists. The explicit-change guard must keep them disabled.
    val persisted =
      SnykApplicationSettingsStateService().apply {
        pluginFirstRun = false
        markExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED)
      }
    val target = SnykApplicationSettingsStateService()
    target.loadState(persisted)

    target.initializeComponent()

    assertFalse(target.snykCodeSecurityIssuesScanEnable)
    assertTrue(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
  }

  @Test
  fun migration_isOneShot_doesNotReAssertAfterUserResetsCode() {
    // Once migrated, a later reset of Code back to default must not be re-enabled on next startup.
    val persisted =
      SnykApplicationSettingsStateService().apply {
        pluginFirstRun = false
        codeEnablementUpgradeMigratedV2221 = true // migration already ran previously
        // Code since reset to default: raw null, not explicitly changed
      }
    val target = SnykApplicationSettingsStateService()
    target.loadState(persisted)

    target.initializeComponent()

    assertFalse(target.snykCodeSecurityIssuesScanEnable)
    assertFalse(target.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_CODE_ENABLED))
  }

  @Test
  fun clearAllExplicitlyChanged_clearsGlobalChanges() {
    val target = SnykApplicationSettingsStateService()
    target.markExplicitlyChanged("global_key_a")
    target.markExplicitlyChanged("global_key_b")
    assertTrue(target.isExplicitlyChanged("global_key_a"))
    assertTrue(target.isExplicitlyChanged("global_key_b"))

    target.clearAllExplicitlyChanged()

    assertFalse(target.isExplicitlyChanged("global_key_a"))
    assertFalse(target.isExplicitlyChanged("global_key_b"))
  }
}
