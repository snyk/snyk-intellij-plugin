package io.snyk.plugin.settings

import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.snyk.plugin.fromPathToUriString
import io.snyk.plugin.ui.SnykSettingsDialog
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings

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
      FolderConfig(
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
    assertEquals("preferredOrg should be empty", "", resultConfig.preferredOrg)
    assertFalse("orgSetByUser should be false (auto-detect enabled)", resultConfig.orgSetByUser)
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
      FolderConfig(
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
      resultConfig.preferredOrg,
    )
    assertTrue(
      "orgSetByUser should be true (manual mode, empty preferredOrg falls back to global)",
      resultConfig.orgSetByUser,
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
      FolderConfig(folderPath = path, baseBranch = "main", preferredOrg = "", orgSetByUser = false)
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
    assertEquals("preferredOrg should be set", "my-specific-org", resultConfig.preferredOrg)
    assertTrue("orgSetByUser should be true (manual org)", resultConfig.orgSetByUser)
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
      fcs.addFolderConfig(FolderConfig(folderPath = path, baseBranch = "main"))

      applyFolderConfigChanges(
        fcs = fcs,
        folderPath = path,
        preferredOrgText = "",
        autoSelectOrgEnabled = true,
        additionalParameters = case.input,
      )

      val result = fcs.getFolderConfig(path).additionalParameters
      assertEquals("Case '${case.description}': unexpected parsed tokens", case.expected, result)
    }
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
      FolderConfig(
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
    assertEquals("preferredOrg should be empty (auto-detect)", "", resultConfig.preferredOrg)
    assertFalse("orgSetByUser should be false (auto-detect enabled)", resultConfig.orgSetByUser)
    assertTrue(
      "isAutoOrganizationEnabled should return true",
      folderConfigSettings.isAutoOrganizationEnabled(projectMock),
    )
  }
}
