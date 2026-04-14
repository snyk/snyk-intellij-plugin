package io.snyk.plugin.settings

import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.snyk.plugin.fromPathToUriString
import io.snyk.plugin.fromUriToPath
import io.snyk.plugin.ui.SnykSettingsDialog
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.common.lsp.settings.LsFolderSettingsKeys
import snyk.common.lsp.settings.folderConfig

class SnykProjectSettingsConfigurableTest {

  private lateinit var folderConfigSettings: FolderConfigSettings
  private lateinit var projectMock: Project
  private lateinit var lsWrapperMock: LanguageServerWrapper
  private lateinit var snykSettingsDialogMock: SnykSettingsDialog

  @Before
  fun setUp() {
    folderConfigSettings = FolderConfigSettings()
    folderConfigSettings.clear()
    projectMock = mockk(relaxed = true)
    lsWrapperMock = mockk(relaxed = true)
    snykSettingsDialogMock = mockk(relaxed = true)

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `apply enables auto-detect when checkbox is checked regardless of preferredOrgTextField`() {
    val path = "/test/project"
    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = path.fromPathToUriString()
        name = "test-project"
      }

    // Setup initial config with orgSetByUser = true
    val initialConfig =
      folderConfig(
        folderPath = path,
        baseBranch = "main",
        preferredOrg = "some-org",
        orgSetByUser = true,
      )
    folderConfigSettings.addFolderConfig(initialConfig)

    // Mock the dialog - checkbox is checked (auto-detect enabled)
    every { snykSettingsDialogMock.getPreferredOrg() } returns ""
    every { snykSettingsDialogMock.isAutoSelectOrgEnabled() } returns
      true // User checked auto-detect
    every { snykSettingsDialogMock.getAdditionalParameters() } returns ""

    // Mock workspace folders
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

    // Use the real apply logic
    applyFolderConfigChanges(
      folderConfigSettings,
      path,
      snykSettingsDialogMock.getPreferredOrg(),
      snykSettingsDialogMock.isAutoSelectOrgEnabled(),
      snykSettingsDialogMock.getAdditionalParameters(),
    )

    // Verify the result
    val resultConfig = folderConfigSettings.getFolderConfig(path)
    assertEquals(
      "preferredOrg should be empty",
      "",
      resultConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
    assertFalse(
      "orgSetByUser should be false (auto-detect enabled)",
      resultConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value as? Boolean ?: false,
    )
    assertTrue(
      "isAutoOrganizationEnabled should return true",
      folderConfigSettings.isAutoOrganizationEnabled(projectMock),
    )
  }

  @Test
  fun `apply allows empty preferredOrg when checkbox is unchecked to enable global org fallback`() {
    val path = "/test/project"
    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = path.fromPathToUriString()
        name = "test-project"
      }

    // Setup initial config with orgSetByUser = true
    val initialConfig =
      folderConfig(
        folderPath = path,
        baseBranch = "main",
        preferredOrg = "some-org",
        orgSetByUser = true,
      )
    folderConfigSettings.addFolderConfig(initialConfig)

    // Mock the dialog - checkbox is unchecked, preferredOrg is empty (user wants global org
    // fallback)
    every { snykSettingsDialogMock.getPreferredOrg() } returns ""
    every { snykSettingsDialogMock.isAutoSelectOrgEnabled() } returns
      false // User unchecked auto-detect
    every { snykSettingsDialogMock.getAdditionalParameters() } returns ""

    // Mock workspace folders
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

    // Use the real apply logic
    applyFolderConfigChanges(
      folderConfigSettings,
      path,
      snykSettingsDialogMock.getPreferredOrg(),
      snykSettingsDialogMock.isAutoSelectOrgEnabled(),
      snykSettingsDialogMock.getAdditionalParameters(),
    )

    // Verify the result - should keep manual mode with empty preferredOrg for global org fallback
    val resultConfig = folderConfigSettings.getFolderConfig(path)
    assertEquals(
      "preferredOrg should be empty to enable global org fallback",
      "",
      resultConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
    assertTrue(
      "orgSetByUser should be true (manual mode, empty preferredOrg falls back to global)",
      resultConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value as? Boolean ?: false,
    )
    assertFalse(
      "isAutoOrganizationEnabled should return false",
      folderConfigSettings.isAutoOrganizationEnabled(projectMock),
    )
  }

  @Test
  fun `apply keeps manual org when preferredOrgTextField has value`() {
    val path = "/test/project"
    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = path.fromPathToUriString()
        name = "test-project"
      }

    // Setup initial config with auto-detect enabled
    val initialConfig =
      folderConfig(folderPath = path, baseBranch = "main", preferredOrg = "", orgSetByUser = false)
    folderConfigSettings.addFolderConfig(initialConfig)

    // Mock the dialog to return a specific org
    every { snykSettingsDialogMock.getPreferredOrg() } returns "my-specific-org"
    every { snykSettingsDialogMock.isAutoSelectOrgEnabled() } returns
      false // User unchecked auto-detect
    every { snykSettingsDialogMock.getAdditionalParameters() } returns ""

    // Mock workspace folders
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

    // Use the real apply logic
    applyFolderConfigChanges(
      folderConfigSettings,
      path,
      snykSettingsDialogMock.getPreferredOrg(),
      snykSettingsDialogMock.isAutoSelectOrgEnabled(),
      snykSettingsDialogMock.getAdditionalParameters(),
    )

    // Verify the result
    val resultConfig = folderConfigSettings.getFolderConfig(path)
    assertEquals(
      "preferredOrg should be set",
      "my-specific-org",
      resultConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
    assertTrue(
      "orgSetByUser should be true (manual org)",
      resultConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value as? Boolean ?: false,
    )
    assertFalse(
      "isAutoOrganizationEnabled should return false",
      folderConfigSettings.isAutoOrganizationEnabled(projectMock),
    )
  }

  @Test
  fun `applyFolderConfigChanges parses additionalParameters correctly`() {
    data class Case(val description: String, val input: String, val expected: List<String>)

    val cases =
      listOf(
        Case(
          description = "simple space-separated flags",
          input = "--json --all-projects",
          expected = listOf("--json", "--all-projects"),
        ),
        Case(
          description = "single-quoted argument with spaces is split (single quotes not supported)",
          input = "'project sample'",
          expected = listOf("'project", "sample'"),
        ),
        Case(
          description = "double-quoted argument with spaces is kept as one token",
          input = "\"project sample\"",
          expected = listOf("project sample"),
        ),
        Case(
          description = "newline-separated flags are split correctly",
          input = "--json\n--all-projects",
          expected = listOf("--json", "--all-projects"),
        ),
        Case(description = "empty string produces empty list", input = "", expected = emptyList()),
        Case(
          description =
            "real-world complex input (double-quoted tokens preserved, single-quoted split)",
          input =
            "--json --org=00000000-0000-0000-0000-000000000000 --all-projects " +
              "/Users/infra-services --package-manager=sbt " +
              "-debug -- -Dprojects=sample \"project sample\" -DignoreLinter=true clean compile",
          expected =
            listOf(
              "--json",
              "--org=00000000-0000-0000-0000-000000000000",
              "--all-projects",
              "/Users/infra-services",
              "--package-manager=sbt",
              "-debug",
              "--",
              "-Dprojects=sample",
              "project sample",
              "-DignoreLinter=true",
              "clean",
              "compile",
            ),
        ),
      )

    val path = "/test/project"
    val fcs = FolderConfigSettings()

    for (case in cases) {
      fcs.clear()
      fcs.addFolderConfig(folderConfig(folderPath = path, baseBranch = "main"))

      applyFolderConfigChanges(
        fcs = fcs,
        folderPath = path,
        preferredOrgText = "",
        autoSelectOrgEnabled = true,
        additionalParameters = case.input,
      )

      val result =
        fcs.getFolderConfig(path).settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value
      assertEquals("Case '${case.description}': unexpected parsed tokens", case.expected, result)
    }
  }

  @Test
  fun `apply writes additionalParameters to folder config correctly`() {
    val path = "/test/project"

    // Setup initial config with empty additional parameters
    val initialConfig =
      folderConfig(folderPath = path, baseBranch = "main", additionalParameters = emptyList())
    folderConfigSettings.addFolderConfig(initialConfig)

    // Apply with additional parameters containing multiple flags
    applyFolderConfigChanges(
      fcs = folderConfigSettings,
      folderPath = path,
      preferredOrgText = "",
      autoSelectOrgEnabled = true,
      additionalParameters = "--severity-threshold=high --json",
    )

    // Verify the result
    val resultConfig = folderConfigSettings.getFolderConfig(path)
    val additionalParamsSetting =
      resultConfig.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)
    assertEquals(
      "additionalParameters should be parsed into a list",
      listOf("--severity-threshold=high", "--json"),
      additionalParamsSetting?.value,
    )
    assertTrue(
      "additionalParameters ConfigSetting should have changed=true",
      additionalParamsSetting?.changed == true,
    )
  }

  @Test
  fun `apply with auto-detect disabled writes manual org to folder config`() {
    val path = "/test/project"

    // Setup initial config with auto-detect enabled
    val initialConfig =
      folderConfig(folderPath = path, baseBranch = "main", preferredOrg = "", orgSetByUser = false)
    folderConfigSettings.addFolderConfig(initialConfig)

    // Apply with manual org "my-org"
    applyFolderConfigChanges(
      fcs = folderConfigSettings,
      folderPath = path,
      preferredOrgText = "my-org",
      autoSelectOrgEnabled = false,
      additionalParameters = "",
    )

    // Verify the result
    val resultConfig = folderConfigSettings.getFolderConfig(path)
    val preferredOrgSetting = resultConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)
    val orgSetByUserSetting = resultConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)
    assertEquals("preferredOrg should be 'my-org'", "my-org", preferredOrgSetting?.value)
    assertTrue("orgSetByUser should be true", orgSetByUserSetting?.value as? Boolean ?: false)
    assertTrue(
      "preferredOrg ConfigSetting should have changed=true",
      preferredOrgSetting?.changed == true,
    )
    assertTrue(
      "orgSetByUser ConfigSetting should have changed=true",
      orgSetByUserSetting?.changed == true,
    )
  }

  @Test
  fun `apply updates multiple workspace folders independently`() {
    val path1 = "/test/project1"
    val path2 = "/test/project2"

    // Setup initial configs for both folders
    folderConfigSettings.addFolderConfig(
      folderConfig(folderPath = path1, baseBranch = "main", preferredOrg = "", orgSetByUser = false)
    )
    folderConfigSettings.addFolderConfig(
      folderConfig(folderPath = path2, baseBranch = "main", preferredOrg = "", orgSetByUser = false)
    )

    // Apply folder config changes independently to each folder
    applyFolderConfigChanges(
      fcs = folderConfigSettings,
      folderPath = path1,
      preferredOrgText = "org-alpha",
      autoSelectOrgEnabled = false,
      additionalParameters = "--json",
    )
    applyFolderConfigChanges(
      fcs = folderConfigSettings,
      folderPath = path2,
      preferredOrgText = "org-beta",
      autoSelectOrgEnabled = false,
      additionalParameters = "--all-projects",
    )

    // Verify folder 1
    val result1 = folderConfigSettings.getFolderConfig(path1)
    assertEquals(
      "folder1 preferredOrg should be 'org-alpha'",
      "org-alpha",
      result1.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
    assertEquals(
      "folder1 additionalParameters should be ['--json']",
      listOf("--json"),
      result1.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )

    // Verify folder 2 is independent
    val result2 = folderConfigSettings.getFolderConfig(path2)
    assertEquals(
      "folder2 preferredOrg should be 'org-beta'",
      "org-beta",
      result2.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
    assertEquals(
      "folder2 additionalParameters should be ['--all-projects']",
      listOf("--all-projects"),
      result2.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )

    // Verify all three keys are present in each folder
    for ((label, result) in listOf("folder1" to result1, "folder2" to result2)) {
      assertTrue(
        "$label must have ADDITIONAL_PARAMETERS key",
        result.settings?.containsKey(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS) == true,
      )
      assertTrue(
        "$label must have PREFERRED_ORG key",
        result.settings?.containsKey(LsFolderSettingsKeys.PREFERRED_ORG) == true,
      )
      assertTrue(
        "$label must have ORG_SET_BY_USER key",
        result.settings?.containsKey(LsFolderSettingsKeys.ORG_SET_BY_USER) == true,
      )
    }
  }

  @Test
  fun `apply propagation chain produces correct LspConfigurationParam`() {
    val path = "/test/project"
    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = path.fromPathToUriString()
        name = "test-project"
      }

    // Setup initial config
    folderConfigSettings.addFolderConfig(folderConfig(folderPath = path, baseBranch = "main"))

    // Mock workspace folders
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

    // Simulate what apply() does: iterate workspace folders and apply changes
    val folderPaths =
      lsWrapperMock
        .getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
        .asSequence()
        .filter { lsWrapperMock.configuredWorkspaceFolders.contains(it) }
        .map { it.uri.fromUriToPath().toString() }
        .toList()

    folderPaths.forEach { folderPath ->
      applyFolderConfigChanges(
        fcs = folderConfigSettings,
        folderPath = folderPath,
        preferredOrgText = "test-org",
        autoSelectOrgEnabled = false,
        additionalParameters = "--json --all-projects",
      )
    }

    // Verify the stored config matches what getSettings() would read from FolderConfigSettings
    val storedConfig = folderConfigSettings.getFolderConfig(path)

    // All three folder-scope keys must be present
    assertTrue(
      "stored config must have ADDITIONAL_PARAMETERS",
      storedConfig.settings?.containsKey(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS) == true,
    )
    assertTrue(
      "stored config must have PREFERRED_ORG",
      storedConfig.settings?.containsKey(LsFolderSettingsKeys.PREFERRED_ORG) == true,
    )
    assertTrue(
      "stored config must have ORG_SET_BY_USER",
      storedConfig.settings?.containsKey(LsFolderSettingsKeys.ORG_SET_BY_USER) == true,
    )

    // Values must match what was applied
    assertEquals(
      listOf("--json", "--all-projects"),
      storedConfig.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )
    assertEquals("test-org", storedConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value)
    assertEquals(true, storedConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value)

    // All ConfigSetting objects must have changed=true
    assertTrue(
      "ADDITIONAL_PARAMETERS must be marked changed",
      storedConfig.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.changed == true,
    )
    assertTrue(
      "PREFERRED_ORG must be marked changed",
      storedConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.changed == true,
    )
    assertTrue(
      "ORG_SET_BY_USER must be marked changed",
      storedConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.changed == true,
    )
  }

  @Test
  fun `autoDeterminedOrg is not clobbered by applyFolderConfigChanges when auto-detect enabled`() {
    val path = "/test/project"

    // Setup initial config simulating LS sending auto-determined org
    val initialConfig =
      folderConfig(
        folderPath = path,
        baseBranch = "main",
        autoDeterminedOrg = "auto-org-from-ls",
        orgSetByUser = false,
        preferredOrg = "",
      )
    folderConfigSettings.addFolderConfig(initialConfig)

    // Apply folder config changes with auto-detect enabled (simulating settings UI save)
    applyFolderConfigChanges(
      fcs = folderConfigSettings,
      folderPath = path,
      preferredOrgText = "",
      autoSelectOrgEnabled = true,
      additionalParameters = "",
    )

    // Verify autoDeterminedOrg is preserved
    val resultConfig = folderConfigSettings.getFolderConfig(path)
    assertEquals(
      "autoDeterminedOrg should not be clobbered",
      "auto-org-from-ls",
      resultConfig.settings?.get(LsFolderSettingsKeys.AUTO_DETERMINED_ORG)?.value,
    )
    assertFalse(
      "orgSetByUser should remain false",
      resultConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value as? Boolean ?: false,
    )
    assertEquals(
      "preferredOrg should be empty when auto-detect enabled",
      "",
      resultConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
  }

  @Test
  fun `getSettings output preserves autoDeterminedOrg in folder config settings`() {
    val path = "/test/project"

    // Setup initial config with autoDeterminedOrg set
    val config =
      folderConfig(
        folderPath = path,
        baseBranch = "main",
        autoDeterminedOrg = "auto-org-preserved",
        orgSetByUser = false,
        preferredOrg = "",
      )
    folderConfigSettings.addFolderConfig(config)

    // Verify autoDeterminedOrg is in the stored config that getSettings() would read
    val storedConfig = folderConfigSettings.getFolderConfig(path)
    assertTrue(
      "stored config must have AUTO_DETERMINED_ORG key",
      storedConfig.settings?.containsKey(LsFolderSettingsKeys.AUTO_DETERMINED_ORG) == true,
    )
    assertEquals(
      "autoDeterminedOrg should be preserved in folder config settings",
      "auto-org-preserved",
      storedConfig.settings?.get(LsFolderSettingsKeys.AUTO_DETERMINED_ORG)?.value,
    )

    // Verify all org-related keys are present together
    assertTrue(
      "stored config must have PREFERRED_ORG key",
      storedConfig.settings?.containsKey(LsFolderSettingsKeys.PREFERRED_ORG) == true,
    )
    assertTrue(
      "stored config must have ORG_SET_BY_USER key",
      storedConfig.settings?.containsKey(LsFolderSettingsKeys.ORG_SET_BY_USER) == true,
    )
  }

  @Test
  fun `apply respects auto-detect checkbox when preferredOrgTextField has value and checkbox is checked`() {
    val path = "/test/project"
    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = path.fromPathToUriString()
        name = "test-project"
      }

    // Setup initial config
    val initialConfig =
      folderConfig(
        folderPath = path,
        baseBranch = "main",
        preferredOrg = "old-org",
        orgSetByUser = true,
      )
    folderConfigSettings.addFolderConfig(initialConfig)

    // Mock the dialog - user has text in field but checkbox is checked
    every { snykSettingsDialogMock.getPreferredOrg() } returns "some-org-in-field"
    every { snykSettingsDialogMock.isAutoSelectOrgEnabled() } returns
      true // User checked auto-detect
    every { snykSettingsDialogMock.getAdditionalParameters() } returns ""

    // Mock workspace folders
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

    // Use the real apply logic
    applyFolderConfigChanges(
      folderConfigSettings,
      path,
      snykSettingsDialogMock.getPreferredOrg(),
      snykSettingsDialogMock.isAutoSelectOrgEnabled(),
      snykSettingsDialogMock.getAdditionalParameters(),
    )

    // Verify the result - should use auto-detect since checkbox is checked
    val resultConfig = folderConfigSettings.getFolderConfig(path)
    assertEquals(
      "preferredOrg should be empty (auto-detect)",
      "",
      resultConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
    assertFalse(
      "orgSetByUser should be false (auto-detect enabled)",
      resultConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value as? Boolean ?: false,
    )
    assertTrue(
      "isAutoOrganizationEnabled should return true",
      folderConfigSettings.isAutoOrganizationEnabled(projectMock),
    )
  }

  @Test
  fun `applyFolderConfigChanges with auto-detect clears preferredOrg`() {
    val path = "/test/auto-clear"
    folderConfigSettings.addFolderConfig(
      folderConfig(folderPath = path, baseBranch = "main", preferredOrg = "old-org")
    )

    applyFolderConfigChanges(
      fcs = folderConfigSettings,
      folderPath = path,
      preferredOrgText = "ignored-text",
      autoSelectOrgEnabled = true,
      additionalParameters = "",
    )

    val result = folderConfigSettings.getFolderConfig(path)
    assertEquals(
      "preferredOrg should be empty with auto-detect",
      "",
      result.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
    assertFalse(
      "orgSetByUser should be false",
      result.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value as? Boolean ?: false,
    )
  }

  @Test
  fun `applyFolderConfigChanges trims preferredOrg whitespace`() {
    val path = "/test/trim-org"
    folderConfigSettings.addFolderConfig(folderConfig(folderPath = path, baseBranch = "main"))

    applyFolderConfigChanges(
      fcs = folderConfigSettings,
      folderPath = path,
      preferredOrgText = "  my-org  ",
      autoSelectOrgEnabled = false,
      additionalParameters = "--json",
    )

    val result = folderConfigSettings.getFolderConfig(path)
    assertEquals(
      "preferredOrg should be trimmed",
      "my-org",
      result.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
    assertTrue(
      "orgSetByUser should be true",
      result.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value as? Boolean ?: false,
    )
    assertEquals(
      "additionalParameters should be parsed",
      listOf("--json"),
      result.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )
  }

  @Test
  fun `applyFolderConfigChanges with empty params and auto-detect`() {
    val path = "/test/empty-auto"
    folderConfigSettings.addFolderConfig(
      folderConfig(
        folderPath = path,
        baseBranch = "main",
        preferredOrg = "old",
        orgSetByUser = true,
      )
    )

    applyFolderConfigChanges(
      fcs = folderConfigSettings,
      folderPath = path,
      preferredOrgText = "",
      autoSelectOrgEnabled = true,
      additionalParameters = "",
    )

    val result = folderConfigSettings.getFolderConfig(path)
    assertEquals(
      "preferredOrg should be empty",
      "",
      result.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
    assertFalse(
      "orgSetByUser should be false",
      result.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value as? Boolean ?: false,
    )
    assertEquals(
      "additionalParameters should be empty list",
      emptyList<String>(),
      result.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )
  }
}
