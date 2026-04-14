package io.snyk.plugin.services

import io.snyk.plugin.Severity
import java.time.LocalDateTime
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

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
  fun hasSeverityTreeFiltered_allSeveritiesEnabledByDefault() {
    val target = SnykApplicationSettingsStateService()

    assertTrue(target.hasSeverityTreeFiltered(Severity.CRITICAL))
    assertTrue(target.hasSeverityTreeFiltered(Severity.HIGH))
    assertTrue(target.hasSeverityTreeFiltered(Severity.MEDIUM))
    assertTrue(target.hasSeverityTreeFiltered(Severity.LOW))
  }

  @Test
  fun hasSeverityTreeFiltered_unknownSeverityReturnsFalse() {
    val target = SnykApplicationSettingsStateService()
    assertFalse(target.hasSeverityTreeFiltered(Severity.UNKNOWN))
  }

  @Test
  fun setSeverityTreeFiltered_setsEachSeverity() {
    val target = SnykApplicationSettingsStateService()

    target.setSeverityTreeFiltered(Severity.CRITICAL, false)
    assertFalse(target.treeFiltering.criticalSeverity)

    target.setSeverityTreeFiltered(Severity.HIGH, false)
    assertFalse(target.treeFiltering.highSeverity)

    target.setSeverityTreeFiltered(Severity.MEDIUM, false)
    assertFalse(target.treeFiltering.mediumSeverity)

    target.setSeverityTreeFiltered(Severity.LOW, false)
    assertFalse(target.treeFiltering.lowSeverity)
  }

  @Test(expected = IllegalArgumentException::class)
  fun setSeverityTreeFiltered_unknownSeverityThrows() {
    val target = SnykApplicationSettingsStateService()
    target.setSeverityTreeFiltered(Severity.UNKNOWN, true)
  }

  @Test
  fun hasSeverityEnabledAndFiltered_requiresBoth() {
    val target = SnykApplicationSettingsStateService()

    assertTrue(target.hasSeverityEnabledAndFiltered(Severity.CRITICAL))

    target.criticalSeverityEnabled = false
    assertFalse(target.hasSeverityEnabledAndFiltered(Severity.CRITICAL))

    target.criticalSeverityEnabled = true
    target.setSeverityTreeFiltered(Severity.CRITICAL, false)
    assertFalse(target.hasSeverityEnabledAndFiltered(Severity.CRITICAL))
  }

  @Test
  fun hasOnlyOneSeverityEnabled_trueWhenExactlyOne() {
    val target = SnykApplicationSettingsStateService()

    target.setSeverityTreeFiltered(Severity.CRITICAL, false)
    target.setSeverityTreeFiltered(Severity.HIGH, false)
    target.setSeverityTreeFiltered(Severity.MEDIUM, false)
    target.setSeverityTreeFiltered(Severity.LOW, true)

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
  fun matchFilteringWithEnablement_syncsSeveritiesAndProducts() {
    val target = SnykApplicationSettingsStateService()
    target.criticalSeverityEnabled = false
    target.highSeverityEnabled = false
    target.mediumSeverityEnabled = true
    target.lowSeverityEnabled = true
    target.ossScanEnable = false
    target.snykCodeSecurityIssuesScanEnable = true
    target.iacScanEnabled = false

    target.matchFilteringWithEnablement()

    assertFalse(target.treeFiltering.criticalSeverity)
    assertFalse(target.treeFiltering.highSeverity)
    assertTrue(target.treeFiltering.mediumSeverity)
    assertTrue(target.treeFiltering.lowSeverity)
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
  fun markAndCheckExplicitlyChanged_folder() {
    val target = SnykApplicationSettingsStateService()
    assertFalse(target.isExplicitlyChanged("/folder", "key"))

    target.markExplicitlyChanged("/folder", "key")
    assertTrue(target.isExplicitlyChanged("/folder", "key"))
    assertFalse(target.isExplicitlyChanged("/folder", "other"))
    assertFalse(target.isExplicitlyChanged("/other", "key"))
  }
}
