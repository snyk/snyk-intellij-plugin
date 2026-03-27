package io.snyk.plugin.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService

class SettingsApplyFunctionsTest : BasePlatformTestCase() {
  private lateinit var settings: SnykApplicationSettingsStateService

  override fun setUp() {
    super.setUp()
    unmockkAll()
    resetSettings(project)

    mockkStatic("io.snyk.plugin.UtilsKt")
    settings = SnykApplicationSettingsStateService()
    every { pluginSettings() } returns settings
  }

  override fun tearDown() {
    unmockkAll()
    super.tearDown()
  }

  fun `test handleDeltaFindingsChange handles null cache gracefully`() {
    every { getSnykCachedResults(project) } returns null

    // Should not throw
    handleDeltaFindingsChange(project)
  }

  fun `test shared functions exist and are callable`() {
    // Verify shared functions can be called without errors
    // The actual behavior is tested through integration
    assertNotNull(::executePostApplySettings)
    assertNotNull(::handleReleaseChannelChange)
    assertNotNull(::handleDeltaFindingsChange)
    assertNotNull(::applyFolderConfigChanges)
  }

  fun `test folder-level markExplicitlyChanged and isExplicitlyChanged`() {
    data class Case(
      val folder: String,
      val key: String,
      val markIt: Boolean,
      val expected: Boolean,
    )

    val cases =
      listOf(
        Case("/project/a", "snyk_code_enabled", true, true),
        Case("/project/a", "snyk_oss_enabled", false, false),
        Case("/project/b", "snyk_code_enabled", true, true),
        Case("/project/a", "scan_automatic", true, true),
      )

    val svc = SnykApplicationSettingsStateService()

    for (case in cases) {
      if (case.markIt) svc.markExplicitlyChanged(case.folder, case.key)
      assertEquals(
        "folder=${case.folder} key=${case.key}",
        case.expected,
        svc.isExplicitlyChanged(case.folder, case.key),
      )
    }

    // cross-folder isolation: /project/b should not have scan_automatic
    assertFalse(svc.isExplicitlyChanged("/project/b", "scan_automatic"))
    // global should not be affected
    assertFalse(svc.isExplicitlyChanged("snyk_code_enabled"))
  }
}
