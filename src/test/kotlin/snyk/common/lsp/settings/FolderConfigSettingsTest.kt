package snyk.common.lsp.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.snyk.plugin.Severity
import io.snyk.plugin.fromPathToUriString
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import java.nio.file.Paths
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.ProductType
import snyk.common.lsp.LanguageServerWrapper

class FolderConfigSettingsTest {

  private lateinit var settings: FolderConfigSettings

  @Before
  fun setUp() {
    settings = FolderConfigSettings()
    settings.clear()
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `addFolderConfig stores and getFolderConfig retrieves with simple path`() {
    val path = "/test/projectA"
    val normalizedPath = Paths.get(path).normalize().toAbsolutePath().toString()
    val config =
      folderConfig(
        folderPath = path,
        baseBranch = "main",
        additionalParameters = listOf("--scan-all-unmanaged"),
      )

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals("Normalized path should match", normalizedPath, retrievedConfig.folderPath)
    assertEquals(
      "Base branch should match",
      "main",
      retrievedConfig.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
    assertEquals(
      "Additional parameters should match",
      listOf("--scan-all-unmanaged"),
      retrievedConfig.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )

    assertEquals("Settings map size should be 1", 1, settings.getAll().size)
    assertTrue(
      "Settings map should contain normalized path key",
      settings.getAll().containsKey(normalizedPath),
    )
  }

  @Test
  fun `addFolderConfig normalizes path with dot and double-dot segments`() {
    val rawPath = "/test/projectB/./subfolder/../othersubfolder"
    val expectedNormalizedPath = Paths.get(rawPath).normalize().toAbsolutePath().toString()

    val config = folderConfig(folderPath = rawPath, baseBranch = "develop")
    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(rawPath)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals("Normalized path should match", expectedNormalizedPath, retrievedConfig.folderPath)
    assertEquals(
      "Base branch should match",
      "develop",
      retrievedConfig.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )

    val retrievedAgain = settings.getFolderConfig(expectedNormalizedPath)
    assertNotNull("Retrieved again config should not be null", retrievedAgain)
    assertEquals(
      "Normalized path should match when retrieved again",
      expectedNormalizedPath,
      retrievedAgain.folderPath,
    )
  }

  @Test
  fun `getFolderConfig retrieves config using equivalent normalized paths`() {
    val path1 = "/my/project/folder"
    val path2 = "/my/project/./folder"
    val path3 = "/my/project/../project/folder"
    val normalizedPath1 = Paths.get(path1).normalize().toAbsolutePath().toString()

    val config = folderConfig(folderPath = path1, baseBranch = "feature-branch")
    settings.addFolderConfig(config)

    val retrievedConfig1 = settings.getFolderConfig(path1)
    assertEquals("Path1 normalized path should match", normalizedPath1, retrievedConfig1.folderPath)
    assertEquals(
      "Path1 base branch should match",
      "feature-branch",
      retrievedConfig1.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )

    val retrievedConfig2 = settings.getFolderConfig(path2)
    assertEquals("Path2 normalized path should match", normalizedPath1, retrievedConfig2.folderPath)
    assertEquals(
      "Path2 base branch should match",
      "feature-branch",
      retrievedConfig2.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )

    val retrievedConfig3 = settings.getFolderConfig(path3)
    assertEquals("Path3 normalized path should match", normalizedPath1, retrievedConfig3.folderPath)
    assertEquals(
      "Path3 base branch should match",
      "feature-branch",
      retrievedConfig3.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
  }

  @Test
  fun `addFolderConfig ignores empty or blank folderPaths`() {
    settings.addFolderConfig(folderConfig(folderPath = "", baseBranch = "main"))
    assertEquals("Config with empty path should be ignored", 0, settings.getAll().size)

    settings.addFolderConfig(folderConfig(folderPath = "   ", baseBranch = "main"))
    assertEquals("Config with blank path should be ignored", 0, settings.getAll().size)
  }

  @Test
  fun `addFolderConfig handles null additionalParameters by not setting key`() {
    val path = "/test/project"
    val config = folderConfig(folderPath = path, baseBranch = "main", additionalParameters = null)

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals(
      "additionalParameters should be null when not set",
      null,
      retrievedConfig.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )
  }

  @Test
  fun `addFolderConfig handles null additionalEnv by not setting key`() {
    val path = "/test/project"
    val config = folderConfig(folderPath = path, baseBranch = "main", additionalEnv = null)

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals(
      "additionalEnv should be null when not set",
      null,
      retrievedConfig.settings?.get(LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT)?.value,
    )
  }

  @Test
  fun `getFolderConfig creates and stores new config if not found, with normalized path`() {
    val rawPath = "/new/folder/./for/creation"
    val expectedNormalizedPath = Paths.get(rawPath).normalize().toAbsolutePath().toString()

    assertTrue("Settings should be empty initially", settings.getAll().isEmpty())

    val newConfig = settings.getFolderConfig(rawPath)
    assertNotNull("New config should not be null", newConfig)
    assertEquals(
      "FolderPath in new config should be normalized",
      expectedNormalizedPath,
      newConfig.folderPath,
    )
    assertEquals(
      "New config should have default baseBranch",
      "main",
      newConfig.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
    assertEquals(
      "New config additionalParameters should be emptyList",
      emptyList<String>(),
      newConfig.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )
    assertEquals(
      "New config localBranches should be emptyList",
      emptyList<String>(),
      newConfig.settings?.get(LsFolderSettingsKeys.LOCAL_BRANCHES)?.value,
    )
    assertEquals(
      "New config referenceFolderPath should be empty string",
      "",
      newConfig.settings?.get(LsFolderSettingsKeys.REFERENCE_FOLDER)?.value,
    )
    assertEquals(
      "New config scanCommandConfig should be emptyMap",
      emptyMap<String, Any>(),
      newConfig.settings?.get(LsFolderSettingsKeys.SCAN_COMMAND_CONFIG)?.value,
    )

    val allConfigs = settings.getAll()
    assertEquals("A new config should have been added", 1, allConfigs.size)
    assertTrue(
      "Internal map should contain the new config with normalized path as key",
      allConfigs.containsKey(expectedNormalizedPath),
    )
    assertEquals(
      "Stored config folderPath should match",
      expectedNormalizedPath,
      allConfigs[expectedNormalizedPath]?.folderPath,
    )
  }

  @Test
  fun `addFolderConfig overwrites existing config with same normalized path`() {
    val path = "/my/overwritable/folder"
    val equivalentPath = "/my/overwritable/./folder/../folder"
    val normalizedPath = Paths.get(path).normalize().toAbsolutePath().toString()

    val config1 =
      folderConfig(folderPath = path, baseBranch = "v1", additionalParameters = listOf("param1"))
    settings.addFolderConfig(config1)

    var retrieved = settings.getFolderConfig(path)
    assertEquals(
      "Retrieved v1 baseBranch",
      "v1",
      retrieved.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
    assertEquals(
      "Retrieved v1 additionalParameters",
      listOf("param1"),
      retrieved.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )
    assertEquals("Retrieved v1 normalizedPath", normalizedPath, retrieved.folderPath)

    val config2 =
      folderConfig(
        folderPath = equivalentPath,
        baseBranch = "v2",
        additionalParameters = listOf("param2"),
      )
    settings.addFolderConfig(config2)

    retrieved = settings.getFolderConfig(path)
    assertEquals(
      "BaseBranch should be from the overriding config",
      "v2",
      retrieved.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
    assertEquals(
      "AdditionalParameters should be from the overriding config",
      listOf("param2"),
      retrieved.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )
    assertEquals(
      "NormalizedPath should remain the same after overwrite",
      normalizedPath,
      retrieved.folderPath,
    )

    assertEquals("Should still be only one entry in settings map", 1, settings.getAll().size)
  }

  @Test
  fun `paths are treated as case-sensitive by default by the underlying map`() {
    val pathUpper = "/Case/Sensitive/Path"
    val pathLower = "/case/sensitive/path"

    val normalizedUpper = Paths.get(pathUpper).normalize().toAbsolutePath().toString()
    val normalizedLower = Paths.get(pathLower).normalize().toAbsolutePath().toString()

    val configUpper = folderConfig(folderPath = pathUpper, baseBranch = "upper")
    settings.addFolderConfig(configUpper)

    val configLower = folderConfig(folderPath = pathLower, baseBranch = "lower")
    settings.addFolderConfig(configLower)

    if (
      normalizedUpper.equals(normalizedLower, ignoreCase = true) &&
        normalizedUpper != normalizedLower
    ) {
      assertEquals(
        "Configs with paths differing only in case should be distinct if normalized strings differ",
        2,
        settings.getAll().size,
      )
      assertEquals(
        "BaseBranch for upper case path",
        "upper",
        settings.getFolderConfig(pathUpper).settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
      )
      assertEquals(
        "BaseBranch for lower case path",
        "lower",
        settings.getFolderConfig(pathLower).settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
      )
    } else if (normalizedUpper == normalizedLower) {
      assertEquals(
        "If normalized paths are identical, one should overwrite the other",
        1,
        settings.getAll().size,
      )
      assertEquals(
        "Lower should overwrite if normalized paths are identical (upper retrieval)",
        "lower",
        settings.getFolderConfig(pathUpper).settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
      )
      assertEquals(
        "Lower should overwrite if normalized paths are identical (lower retrieval)",
        "lower",
        settings.getFolderConfig(pathLower).settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
      )
    } else {
      assertEquals(
        "Distinct normalized paths should result in distinct entries",
        2,
        settings.getAll().size,
      )
      assertEquals(
        "BaseBranch for upper case path (distinct)",
        "upper",
        settings.getFolderConfig(pathUpper).settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
      )
      assertEquals(
        "BaseBranch for lower case path (distinct)",
        "lower",
        settings.getFolderConfig(pathLower).settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
      )
    }
  }

  @Test
  fun `addFolderConfig with trailing slash is equivalent to without trailing slash`() {
    val pathWithSlash = "/test/trailing/"
    val pathWithoutSlash = "/test/trailing"
    // For non-root paths, Paths.get().normalize() typically removes trailing slashes.
    val expectedNormalizedPath = Paths.get(pathWithoutSlash).normalize().toAbsolutePath().toString()

    // Add with slash
    val config1 = folderConfig(folderPath = pathWithSlash, baseBranch = "main")
    settings.addFolderConfig(config1)

    // Retrieve with and without slash
    val retrieved1With = settings.getFolderConfig(pathWithSlash)
    val retrieved1Without = settings.getFolderConfig(pathWithoutSlash)

    assertNotNull("Config should be retrievable with slash", retrieved1With)
    assertEquals(
      "Retrieved (with slash) path should be normalized",
      expectedNormalizedPath,
      retrieved1With.folderPath,
    )
    assertNotNull("Config should be retrievable without slash", retrieved1Without)
    assertEquals(
      "Retrieved (without slash) path should be normalized",
      expectedNormalizedPath,
      retrieved1Without.folderPath,
    )
    assertEquals(
      "Both retrievals should yield the same object instance",
      retrieved1With,
      retrieved1Without,
    )
    assertEquals("Only one config should be stored", 1, settings.getAll().size)
    assertTrue(
      "Map key should be the normalized path",
      settings.getAll().containsKey(expectedNormalizedPath),
    )

    // Clear and test adding without slash first
    settings.clear()
    val config2 = folderConfig(folderPath = pathWithoutSlash, baseBranch = "develop")
    settings.addFolderConfig(config2)

    val retrieved2With = settings.getFolderConfig(pathWithSlash)
    val retrieved2Without = settings.getFolderConfig(pathWithoutSlash)

    assertNotNull("Config (added without slash) should be retrievable with slash", retrieved2With)
    assertEquals(
      "Retrieved (with slash) path should be normalized (added without)",
      expectedNormalizedPath,
      retrieved2With.folderPath,
    )
    assertEquals(
      "develop",
      retrieved2With.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    ) // Ensure correct config is retrieved
    assertNotNull(
      "Config (added without slash) should be retrievable without slash",
      retrieved2Without,
    )
    assertEquals(
      "Retrieved (without slash) path should be normalized (added without)",
      expectedNormalizedPath,
      retrieved2Without.folderPath,
    )
    assertEquals(
      "develop",
      retrieved2Without.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
    assertEquals(
      "Both retrievals should yield the same object instance",
      retrieved2With,
      retrieved2Without,
    )
    assertEquals(
      "Only one config should be stored when adding without slash",
      1,
      settings.getAll().size,
    )
  }

  @Test
  fun `addFolderConfig with trailing slash on root path`() {
    // Behavior of Paths.get for root might differ slightly, e.g. "/" vs "/."
    // Note: Windows root "C:\\" vs "C:\" might also be relevant if testing on Windows.
    // For simplicity, this test uses POSIX root.
    val rootPathWithSlash = "/"
    val rootPathNormalized = Paths.get(rootPathWithSlash).normalize().toAbsolutePath().toString()

    val config = folderConfig(folderPath = rootPathWithSlash, baseBranch = "rootBranch")
    settings.addFolderConfig(config)
    val retrieved = settings.getFolderConfig(rootPathWithSlash)
    assertNotNull("Retrieved config for root path should not be null", retrieved)
    assertEquals(
      "Retrieved root path should be normalized",
      rootPathNormalized,
      retrieved.folderPath,
    )
    assertEquals("Settings map size for root path should be 1", 1, settings.getAll().size)

    // Test with a path that might normalize to root, e.g., "/."
    val retrievedDot = settings.getFolderConfig("/.")
    assertNotNull("Retrieved config for '/.' should not be null", retrievedDot)
    assertEquals(
      "Retrieved path for '/.' should be normalized to root",
      rootPathNormalized,
      retrievedDot.folderPath,
    )
    assertEquals(
      "Settings map size should still be 1 after retrieving '/.'",
      1,
      settings.getAll().size,
    ) // Still one config
  }

  @Test
  fun `addFolderConfig stores and retrieves preferredOrg`() {
    val path = "/test/project"
    val config = folderConfig(folderPath = path, baseBranch = "main", preferredOrg = "my-org-uuid")

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals(
      "Preferred org should match",
      "my-org-uuid",
      retrievedConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
  }

  @Test
  fun `addFolderConfig with empty preferredOrg uses default`() {
    val path = "/test/project"
    val config = folderConfig(folderPath = path, baseBranch = "main")

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals(
      "Preferred org should be empty string by default",
      "",
      retrievedConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
  }

  @Test
  fun `getFolderConfig creates new config with empty preferredOrg by default`() {
    val path = "/new/project"

    val newConfig = settings.getFolderConfig(path)
    assertNotNull("New config should not be null", newConfig)
    assertEquals(
      "New config preferredOrg should be empty string",
      "",
      newConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
  }

  @Test
  fun `addFolderConfig overwrites preferredOrg when config is updated`() {
    val path = "/test/project"
    val config1 = folderConfig(folderPath = path, baseBranch = "main", preferredOrg = "first-org")
    settings.addFolderConfig(config1)

    val retrieved1 = settings.getFolderConfig(path)
    assertEquals(
      "First preferredOrg should match",
      "first-org",
      retrieved1.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )

    val config2 = folderConfig(folderPath = path, baseBranch = "main", preferredOrg = "second-org")
    settings.addFolderConfig(config2)

    val retrieved2 = settings.getFolderConfig(path)
    assertEquals(
      "PreferredOrg should be updated",
      "second-org",
      retrieved2.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
  }

  @Test
  fun `getPreferredOrg returns empty string when no configs exist`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns emptySet()
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf()

    val result = settings.getPreferredOrg(projectMock)
    assertEquals("PreferredOrg should be empty string when no configs exist", "", result)
  }

  @Test
  fun `getPreferredOrg returns preferredOrg from first folder config with non-empty value`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path1 = "/test/project1"
    val path2 = "/test/project2"
    val normalizedPath1 = Paths.get(path1).normalize().toAbsolutePath().toString()
    val normalizedPath2 = Paths.get(path2).normalize().toAbsolutePath().toString()

    // Add configs with preferredOrg
    val config1 = folderConfig(folderPath = path1, baseBranch = "main", preferredOrg = "org-uuid-1")
    val config2 = folderConfig(folderPath = path2, baseBranch = "main", preferredOrg = "org-uuid-2")
    settings.addFolderConfig(config1)
    settings.addFolderConfig(config2)

    val workspaceFolder1 =
      WorkspaceFolder().apply {
        uri = normalizedPath1.fromPathToUriString()
        name = "project1"
      }
    val workspaceFolder2 =
      WorkspaceFolder().apply {
        uri = normalizedPath2.fromPathToUriString()
        name = "project2"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder1, workspaceFolder2)
    every { lsWrapperMock.configuredWorkspaceFolders } returns
      mutableSetOf(workspaceFolder1, workspaceFolder2)

    val result = settings.getPreferredOrg(projectMock)
    assertEquals("PreferredOrg should return first non-empty value", "org-uuid-1", result)
  }

  @Test
  fun `getPreferredOrg returns preferredOrg even when empty`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path1 = "/test/project1"
    val normalizedPath1 = Paths.get(path1).normalize().toAbsolutePath().toString()

    val config1 = folderConfig(folderPath = path1, baseBranch = "main", preferredOrg = "")
    settings.addFolderConfig(config1)

    val workspaceFolder1 =
      WorkspaceFolder().apply {
        uri = normalizedPath1.fromPathToUriString()
        name = "project1"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder1)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder1)

    val result = settings.getPreferredOrg(projectMock)
    assertEquals("PreferredOrg should be empty string when value is empty", "", result)
  }

  @Test
  fun `getPreferredOrg only includes configured workspace folders`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path1 = "/test/project1"
    val path2 = "/test/project2"
    val normalizedPath1 = Paths.get(path1).normalize().toAbsolutePath().toString()
    val normalizedPath2 = Paths.get(path2).normalize().toAbsolutePath().toString()

    val config1 = folderConfig(folderPath = path1, baseBranch = "main", preferredOrg = "org-uuid-1")
    val config2 = folderConfig(folderPath = path2, baseBranch = "main", preferredOrg = "org-uuid-2")
    settings.addFolderConfig(config1)
    settings.addFolderConfig(config2)

    val workspaceFolder1 =
      WorkspaceFolder().apply {
        uri = normalizedPath1.fromPathToUriString()
        name = "project1"
      }
    val workspaceFolder2 =
      WorkspaceFolder().apply {
        uri = normalizedPath2.fromPathToUriString()
        name = "project2"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder1, workspaceFolder2)
    // Only workspaceFolder2 is configured
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder2)

    val result = settings.getPreferredOrg(projectMock)
    assertEquals("PreferredOrg should only use configured workspace folders", "org-uuid-2", result)
  }

  @Test
  fun `addFolderConfig stores and retrieves orgSetByUser with default false`() {
    val path = "/test/project"
    val config = folderConfig(folderPath = path, baseBranch = "main")

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals(
      "orgSetByUser should default to false",
      false,
      retrievedConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value,
    )
  }

  @Test
  fun `addFolderConfig stores and retrieves orgSetByUser set to true`() {
    val path = "/test/project"
    val config = folderConfig(folderPath = path, baseBranch = "main", orgSetByUser = true)

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals(
      "orgSetByUser should be true",
      true,
      retrievedConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value,
    )
  }

  @Test
  fun `addFolderConfig overwrites orgSetByUser when config is updated`() {
    val path = "/test/project"
    val config1 = folderConfig(folderPath = path, baseBranch = "main", orgSetByUser = false)
    settings.addFolderConfig(config1)

    val retrieved1 = settings.getFolderConfig(path)
    assertEquals(
      "First orgSetByUser should be false",
      false,
      retrieved1.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value,
    )

    val config2 = folderConfig(folderPath = path, baseBranch = "main", orgSetByUser = true)
    settings.addFolderConfig(config2)

    val retrieved2 = settings.getFolderConfig(path)
    assertEquals(
      "orgSetByUser should be updated to true",
      true,
      retrieved2.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value,
    )
  }

  @Test
  fun `getFolderConfig creates new config with orgSetByUser false by default`() {
    val path = "/new/project"

    val newConfig = settings.getFolderConfig(path)
    assertNotNull("New config should not be null", newConfig)
    assertEquals(
      "New config orgSetByUser should be false",
      false,
      newConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value,
    )
  }

  @Test
  fun `isAutoOrganizationEnabled returns true when orgSetByUser is false`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path = "/test/project"
    val workspaceFolder = WorkspaceFolder().apply { uri = path.fromPathToUriString() }

    val config = folderConfig(folderPath = path, baseBranch = "main", orgSetByUser = false)

    settings.addFolderConfig(config)

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

    val result = settings.isAutoOrganizationEnabled(projectMock)
    assertTrue("Should return true when orgSetByUser is false", result)
  }

  @Test
  fun `isAutoOrganizationEnabled returns false when orgSetByUser is true`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path = "/test/project"
    val workspaceFolder = WorkspaceFolder().apply { uri = path.fromPathToUriString() }

    val config = folderConfig(folderPath = path, baseBranch = "main", orgSetByUser = true)

    settings.addFolderConfig(config)

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

    val result = settings.isAutoOrganizationEnabled(projectMock)
    assertFalse("Should return false when orgSetByUser is true", result)
  }

  @Test
  fun `setAutoOrganization updates orgSetByUser flag correctly`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path = "/test/project"
    val workspaceFolder = WorkspaceFolder().apply { uri = path.fromPathToUriString() }

    val config = folderConfig(folderPath = path, baseBranch = "main", orgSetByUser = true)

    settings.addFolderConfig(config)

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

    // Enable auto-organization
    settings.setAutoOrganization(projectMock, true)

    val result = settings.isAutoOrganizationEnabled(projectMock)
    assertTrue("Should return true after enabling auto-organization", result)

    // Disable auto-organization
    settings.setAutoOrganization(projectMock, false)

    val result2 = settings.isAutoOrganizationEnabled(projectMock)
    assertFalse("Should return false after disabling auto-organization", result2)
  }

  @Test
  fun `setOrganization updates preferredOrg correctly`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path = "/test/project"
    val workspaceFolder = WorkspaceFolder().apply { uri = path.fromPathToUriString() }

    val config = folderConfig(folderPath = path, baseBranch = "main", preferredOrg = "old-org")

    settings.addFolderConfig(config)

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

    // Set new organization
    settings.setOrganization(projectMock, "new-org")

    val result = settings.getPreferredOrg(projectMock)
    assertEquals("Should return new organization", "new-org", result)

    // Clear organization
    settings.setOrganization(projectMock, null)

    val result2 = settings.getPreferredOrg(projectMock)
    assertEquals("Should return empty string after clearing", "", result2)
  }

  @Test
  fun `migrateNestedFolderConfigs removes nested folder configs with same values silently`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    // Workspace folder is the parent
    val workspacePath = "/test/project"
    val nestedPath = "/test/project/submodule"
    val normalizedWorkspacePath = Paths.get(workspacePath).normalize().toAbsolutePath().toString()
    val normalizedNestedPath = Paths.get(nestedPath).normalize().toAbsolutePath().toString()

    // Add both configs with SAME values (nested has default/same values as parent)
    val workspaceConfig = folderConfig(folderPath = workspacePath, baseBranch = "main")
    val nestedConfig = folderConfig(folderPath = nestedPath, baseBranch = "main") // Same as parent
    settings.addFolderConfig(workspaceConfig)
    settings.addFolderConfig(nestedConfig)

    assertEquals("Should have 2 configs before migration", 2, settings.getAll().size)

    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = normalizedWorkspacePath.fromPathToUriString()
        name = "project"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)

    // Run migration - should remove silently without prompting (same values)
    val removedCount = settings.migrateNestedFolderConfigs(projectMock)

    assertEquals("Should have removed 1 nested config", 1, removedCount)
    assertEquals("Should have 1 config after migration", 1, settings.getAll().size)
    assertTrue(
      "Workspace folder config should remain",
      settings.getAll().containsKey(normalizedWorkspacePath),
    )
    assertFalse(
      "Nested folder config should be removed",
      settings.getAll().containsKey(normalizedNestedPath),
    )
  }

  @Test
  fun `migrateNestedFolderConfigs keeps non-nested configs`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    // Two separate workspace folders (not nested)
    val path1 = "/test/project1"
    val path2 = "/test/project2"
    val normalizedPath1 = Paths.get(path1).normalize().toAbsolutePath().toString()
    val normalizedPath2 = Paths.get(path2).normalize().toAbsolutePath().toString()

    val config1 = folderConfig(folderPath = path1, baseBranch = "main")
    val config2 = folderConfig(folderPath = path2, baseBranch = "develop")
    settings.addFolderConfig(config1)
    settings.addFolderConfig(config2)

    assertEquals("Should have 2 configs before migration", 2, settings.getAll().size)

    val workspaceFolder1 =
      WorkspaceFolder().apply {
        uri = normalizedPath1.fromPathToUriString()
        name = "project1"
      }
    val workspaceFolder2 =
      WorkspaceFolder().apply {
        uri = normalizedPath2.fromPathToUriString()
        name = "project2"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder1, workspaceFolder2)

    // Run migration
    val removedCount = settings.migrateNestedFolderConfigs(projectMock)

    assertEquals("Should not remove any configs", 0, removedCount)
    assertEquals("Should still have 2 configs after migration", 2, settings.getAll().size)
  }

  @Test
  fun `migrateNestedFolderConfigs does nothing when no workspace folders`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path = "/test/project"
    settings.addFolderConfig(folderConfig(folderPath = path, baseBranch = "main"))

    assertEquals("Should have 1 config before migration", 1, settings.getAll().size)

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns emptySet()

    val removedCount = settings.migrateNestedFolderConfigs(projectMock)

    assertEquals("Should not remove any configs when no workspace folders", 0, removedCount)
    assertEquals("Should still have 1 config after migration", 1, settings.getAll().size)
  }

  @Test
  fun `getAllForProject returns only workspace folder configs not nested`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val workspacePath = "/test/project"
    val nestedPath = "/test/project/submodule"
    val normalizedWorkspacePath = Paths.get(workspacePath).normalize().toAbsolutePath().toString()

    // Add both configs
    settings.addFolderConfig(folderConfig(folderPath = workspacePath, baseBranch = "main"))
    settings.addFolderConfig(folderConfig(folderPath = nestedPath, baseBranch = "develop"))

    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = normalizedWorkspacePath.fromPathToUriString()
        name = "project"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

    // getAllForProject should only return workspace folder configs (not nested)
    val result = settings.getAllForProject(projectMock)

    assertEquals("Should only return 1 config (workspace folder only)", 1, result.size)
    assertEquals(
      "Should return workspace folder config",
      normalizedWorkspacePath,
      result[0].folderPath,
    )
  }

  @Test
  fun `hasNonDefaultValues returns true when config differs from parent`() {
    val parentConfig = folderConfig(folderPath = "/parent", baseBranch = "main")
    val childConfig = folderConfig(folderPath = "/child", baseBranch = "develop")

    assertTrue(
      "Should detect different baseBranch",
      settings.hasNonDefaultValues(childConfig, parentConfig),
    )
  }

  @Test
  fun `hasNonDefaultValues returns false when config matches parent`() {
    val parentConfig = folderConfig(folderPath = "/parent", baseBranch = "main")
    val childConfig = folderConfig(folderPath = "/child", baseBranch = "main")

    assertFalse(
      "Should not detect differences when configs match",
      settings.hasNonDefaultValues(childConfig, parentConfig),
    )
  }

  @Test
  fun `hasConflictingConfigs returns true when configs differ`() {
    val config1 = folderConfig(folderPath = "/path1", baseBranch = "main")
    val config2 = folderConfig(folderPath = "/path2", baseBranch = "develop")

    assertTrue(
      "Should detect conflicting configs",
      settings.hasConflictingConfigs(listOf(config1, config2)),
    )
  }

  @Test
  fun `hasConflictingConfigs returns false when configs match`() {
    val config1 = folderConfig(folderPath = "/path1", baseBranch = "main")
    val config2 = folderConfig(folderPath = "/path2", baseBranch = "main")

    assertFalse(
      "Should not detect conflicts when configs match",
      settings.hasConflictingConfigs(listOf(config1, config2)),
    )
  }

  @Test
  fun `mergeConfigs copies sub-config values into parent`() {
    val parentConfig =
      folderConfig(
        folderPath = "/parent",
        baseBranch = "main",
        referenceFolderPath = "/old/ref",
        additionalParameters = listOf("--old"),
      )
    val subConfig =
      folderConfig(
        folderPath = "/child",
        baseBranch = "develop",
        referenceFolderPath = "/new/ref",
        additionalParameters = listOf("--new"),
      )

    val merged = settings.mergeConfigs(parentConfig, subConfig)

    assertEquals("Should keep parent folderPath", "/parent", merged.folderPath)
    assertEquals(
      "Should use sub-config baseBranch",
      "develop",
      merged.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
    assertEquals(
      "Should use sub-config referenceFolderPath",
      "/new/ref",
      merged.settings?.get(LsFolderSettingsKeys.REFERENCE_FOLDER)?.value,
    )
    assertEquals(
      "Should use sub-config additionalParameters",
      listOf("--new"),
      merged.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value,
    )
  }

  @Test
  fun `isPathNestedUnder correctly identifies nested paths`() {
    assertTrue(settings.isPathNestedUnder("/parent/child", "/parent"))
    assertTrue(settings.isPathNestedUnder("/parent/child/deep", "/parent"))
    assertFalse(settings.isPathNestedUnder("/parent", "/parent"))
    assertFalse(settings.isPathNestedUnder("/other/path", "/parent"))
    assertFalse(settings.isPathNestedUnder("/parentExtra/child", "/parent"))
  }

  @Test
  fun `migrateNestedFolderConfigs with custom sub-config merges when user chooses merge`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val workspacePath = "/test/project"
    val nestedPath = "/test/project/submodule"
    val normalizedWorkspacePath = Paths.get(workspacePath).normalize().toAbsolutePath().toString()
    val normalizedNestedPath = Paths.get(nestedPath).normalize().toAbsolutePath().toString()

    // Parent and nested have DIFFERENT values
    settings.addFolderConfig(folderConfig(folderPath = workspacePath, baseBranch = "main"))
    settings.addFolderConfig(
      folderConfig(
        folderPath = nestedPath,
        baseBranch = "develop",
        referenceFolderPath = "/custom/ref",
      )
    )

    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = normalizedWorkspacePath.fromPathToUriString()
        name = "project"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)

    // Use spyk to mock the prompt method
    val settingsSpy = spyk(settings)
    every { settingsSpy.promptForSingleSubConfigMigration(any(), any(), any()) } returns
      FolderConfigSettings.MigrationChoice.MERGE_INTO_PARENT

    val removedCount = settingsSpy.migrateNestedFolderConfigs(projectMock)

    assertEquals("Should have removed 1 nested config", 1, removedCount)
    assertEquals("Should have 1 config after migration", 1, settingsSpy.getAll().size)

    // Verify parent was updated with sub-config values
    val parentConfig = settingsSpy.getAll()[normalizedWorkspacePath]
    assertEquals(
      "Parent should have merged baseBranch",
      "develop",
      parentConfig?.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
    assertEquals(
      "Parent should have merged referenceFolderPath",
      "/custom/ref",
      parentConfig?.settings?.get(LsFolderSettingsKeys.REFERENCE_FOLDER)?.value,
    )
  }

  @Test
  fun `migrateNestedFolderConfigs with custom sub-config discards when user chooses discard`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val workspacePath = "/test/project"
    val nestedPath = "/test/project/submodule"
    val normalizedWorkspacePath = Paths.get(workspacePath).normalize().toAbsolutePath().toString()
    val normalizedNestedPath = Paths.get(nestedPath).normalize().toAbsolutePath().toString()

    // Parent and nested have DIFFERENT values
    settings.addFolderConfig(folderConfig(folderPath = workspacePath, baseBranch = "main"))
    settings.addFolderConfig(folderConfig(folderPath = nestedPath, baseBranch = "develop"))

    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = normalizedWorkspacePath.fromPathToUriString()
        name = "project"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)

    // Use spyk to mock the prompt method
    val settingsSpy = spyk(settings)
    every { settingsSpy.promptForSingleSubConfigMigration(any(), any(), any()) } returns
      FolderConfigSettings.MigrationChoice.USE_PARENT_VALUES

    val removedCount = settingsSpy.migrateNestedFolderConfigs(projectMock)

    assertEquals("Should have removed 1 nested config", 1, removedCount)
    assertEquals("Should have 1 config after migration", 1, settingsSpy.getAll().size)

    // Verify parent was NOT updated
    val parentConfig = settingsSpy.getAll()[normalizedWorkspacePath]
    assertEquals(
      "Parent should keep original baseBranch",
      "main",
      parentConfig?.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
  }

  @Test
  fun `migrateNestedFolderConfigs with multiple conflicting sub-configs removes parent when user chooses`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val workspacePath = "/test/project"
    val nestedPath1 = "/test/project/sub1"
    val nestedPath2 = "/test/project/sub2"
    val normalizedWorkspacePath = Paths.get(workspacePath).normalize().toAbsolutePath().toString()
    val normalizedNestedPath1 = Paths.get(nestedPath1).normalize().toAbsolutePath().toString()
    val normalizedNestedPath2 = Paths.get(nestedPath2).normalize().toAbsolutePath().toString()

    // Parent and two nested configs with DIFFERENT values from each other
    settings.addFolderConfig(folderConfig(folderPath = workspacePath, baseBranch = "main"))
    settings.addFolderConfig(folderConfig(folderPath = nestedPath1, baseBranch = "develop"))
    settings.addFolderConfig(folderConfig(folderPath = nestedPath2, baseBranch = "feature"))

    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = normalizedWorkspacePath.fromPathToUriString()
        name = "project"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)

    // Use spyk to mock the prompt method
    val settingsSpy = spyk(settings)
    every { settingsSpy.promptForMultipleConflictingMigration(any(), any(), any()) } returns
      FolderConfigSettings.MigrationChoice.REMOVE_PARENT

    val removedCount = settingsSpy.migrateNestedFolderConfigs(projectMock)

    assertEquals("Should have removed 1 config (the parent)", 1, removedCount)
    assertEquals(
      "Should have 2 configs after migration (the sub-configs)",
      2,
      settingsSpy.getAll().size,
    )
    assertFalse(
      "Parent config should be removed",
      settingsSpy.getAll().containsKey(normalizedWorkspacePath),
    )
    assertTrue(
      "Sub-config 1 should remain",
      settingsSpy.getAll().containsKey(normalizedNestedPath1),
    )
    assertTrue(
      "Sub-config 2 should remain",
      settingsSpy.getAll().containsKey(normalizedNestedPath2),
    )
  }

  @Test
  fun `migrateNestedFolderConfigs with multiple conflicting sub-configs keeps all when user chooses`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val workspacePath = "/test/project"
    val nestedPath1 = "/test/project/sub1"
    val nestedPath2 = "/test/project/sub2"
    val normalizedWorkspacePath = Paths.get(workspacePath).normalize().toAbsolutePath().toString()
    val normalizedNestedPath1 = Paths.get(nestedPath1).normalize().toAbsolutePath().toString()
    val normalizedNestedPath2 = Paths.get(nestedPath2).normalize().toAbsolutePath().toString()

    // Parent and two nested configs with DIFFERENT values from each other
    settings.addFolderConfig(folderConfig(folderPath = workspacePath, baseBranch = "main"))
    settings.addFolderConfig(folderConfig(folderPath = nestedPath1, baseBranch = "develop"))
    settings.addFolderConfig(folderConfig(folderPath = nestedPath2, baseBranch = "feature"))

    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = normalizedWorkspacePath.fromPathToUriString()
        name = "project"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)

    // Use spyk to mock the prompt method
    val settingsSpy = spyk(settings)
    every { settingsSpy.promptForMultipleConflictingMigration(any(), any(), any()) } returns
      FolderConfigSettings.MigrationChoice.KEEP_ALL

    val removedCount = settingsSpy.migrateNestedFolderConfigs(projectMock)

    assertEquals("Should not have removed any configs", 0, removedCount)
    assertEquals("Should have 3 configs after migration", 3, settingsSpy.getAll().size)
    assertTrue(
      "Parent config should remain",
      settingsSpy.getAll().containsKey(normalizedWorkspacePath),
    )
    assertTrue(
      "Sub-config 1 should remain",
      settingsSpy.getAll().containsKey(normalizedNestedPath1),
    )
    assertTrue(
      "Sub-config 2 should remain",
      settingsSpy.getAll().containsKey(normalizedNestedPath2),
    )
  }

  @Test
  fun `addAll with empty list is a no-op`() {
    assertTrue("Settings should be empty initially", settings.getAll().isEmpty())

    settings.addAll(emptyList())

    assertTrue(
      "Settings should still be empty after addAll with empty list",
      settings.getAll().isEmpty(),
    )
  }

  @Test
  fun `addAll stores all configs independently`() {
    val path1 = "/test/projectA"
    val path2 = "/test/projectB"
    val path3 = "/test/projectC"
    val normalizedPath1 = Paths.get(path1).normalize().toAbsolutePath().toString()
    val normalizedPath2 = Paths.get(path2).normalize().toAbsolutePath().toString()
    val normalizedPath3 = Paths.get(path3).normalize().toAbsolutePath().toString()

    val configs =
      listOf(
        folderConfig(folderPath = path1, baseBranch = "main"),
        folderConfig(folderPath = path2, baseBranch = "develop"),
        folderConfig(folderPath = path3, baseBranch = "release"),
      )

    settings.addAll(configs)

    val allConfigs = settings.getAll()
    assertEquals("All three configs should be stored", 3, allConfigs.size)
    assertTrue("Config for path1 should exist", allConfigs.containsKey(normalizedPath1))
    assertTrue("Config for path2 should exist", allConfigs.containsKey(normalizedPath2))
    assertTrue("Config for path3 should exist", allConfigs.containsKey(normalizedPath3))
    assertEquals(
      "Path1 base branch should be main",
      "main",
      allConfigs[normalizedPath1]?.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
    assertEquals(
      "Path2 base branch should be develop",
      "develop",
      allConfigs[normalizedPath2]?.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
    assertEquals(
      "Path3 base branch should be release",
      "release",
      allConfigs[normalizedPath3]?.settings?.get(LsFolderSettingsKeys.BASE_BRANCH)?.value,
    )
  }

  @Test
  fun `autoDeterminedOrg survives addAll and getFolderConfig round-trip`() {
    val path = "/test/project"
    val normalizedPath = Paths.get(path).normalize().toAbsolutePath().toString()

    val config =
      folderConfig(
        folderPath = path,
        baseBranch = "main",
        autoDeterminedOrg = "auto-org",
        orgSetByUser = false,
        preferredOrg = "",
      )

    settings.addAll(listOf(config))

    val retrievedConfig = settings.getFolderConfig(path)
    assertEquals(
      "autoDeterminedOrg should survive addAll and getFolderConfig round-trip",
      "auto-org",
      retrievedConfig.settings?.get(LsFolderSettingsKeys.AUTO_DETERMINED_ORG)?.value,
    )
    assertEquals(
      "orgSetByUser should be false",
      false,
      retrievedConfig.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value,
    )
    assertEquals(
      "preferredOrg should be empty",
      "",
      retrievedConfig.settings?.get(LsFolderSettingsKeys.PREFERRED_ORG)?.value,
    )
    assertEquals("folderPath should be normalized", normalizedPath, retrievedConfig.folderPath)
  }

  @Test
  fun `getAdditionalParameters returns empty string when no workspace folders`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns emptySet()
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf()

    val result = settings.getAdditionalParameters(projectMock)
    assertEquals("Should return empty string when no workspace folders", "", result)
  }

  @Test
  fun `getAdditionalParameters returns parameters from folder configs`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path1 = "/test/project1"
    val normalizedPath1 = Paths.get(path1).normalize().toAbsolutePath().toString()

    val config1 =
      folderConfig(
        folderPath = path1,
        baseBranch = "main",
        additionalParameters = listOf("--json", "--all-projects"),
      )
    settings.addFolderConfig(config1)

    val workspaceFolder1 =
      WorkspaceFolder().apply {
        uri = normalizedPath1.fromPathToUriString()
        name = "project1"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder1)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder1)

    val result = settings.getAdditionalParameters(projectMock)
    assertEquals("Should return joined parameters", "--json --all-projects", result)
  }

  @Test
  fun `getAdditionalParameters returns empty when parameters are empty lists`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path1 = "/test/project1"
    val normalizedPath1 = Paths.get(path1).normalize().toAbsolutePath().toString()

    val config1 =
      folderConfig(folderPath = path1, baseBranch = "main", additionalParameters = emptyList())
    settings.addFolderConfig(config1)

    val workspaceFolder1 =
      WorkspaceFolder().apply {
        uri = normalizedPath1.fromPathToUriString()
        name = "project1"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder1)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder1)

    val result = settings.getAdditionalParameters(projectMock)
    assertEquals("Should return empty string for empty parameters", "", result)
  }

  @Test
  fun `getAdditionalParameters joins parameters from multiple folders`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path1 = "/test/project1"
    val path2 = "/test/project2"
    val normalizedPath1 = Paths.get(path1).normalize().toAbsolutePath().toString()
    val normalizedPath2 = Paths.get(path2).normalize().toAbsolutePath().toString()

    val config1 =
      folderConfig(folderPath = path1, baseBranch = "main", additionalParameters = listOf("--json"))
    val config2 =
      folderConfig(
        folderPath = path2,
        baseBranch = "main",
        additionalParameters = listOf("--all-projects"),
      )
    settings.addFolderConfig(config1)
    settings.addFolderConfig(config2)

    val workspaceFolder1 =
      WorkspaceFolder().apply {
        uri = normalizedPath1.fromPathToUriString()
        name = "project1"
      }
    val workspaceFolder2 =
      WorkspaceFolder().apply {
        uri = normalizedPath2.fromPathToUriString()
        name = "project2"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder1, workspaceFolder2)
    every { lsWrapperMock.configuredWorkspaceFolders } returns
      mutableSetOf(workspaceFolder1, workspaceFolder2)

    val result = settings.getAdditionalParameters(projectMock)
    assertTrue(
      "Should contain parameters from both folders",
      result.contains("--json") && result.contains("--all-projects"),
    )
  }

  @Test
  fun `hasConflictingConfigs returns false for single config`() {
    val config1 = folderConfig(folderPath = "/path1", baseBranch = "main")
    assertFalse(
      "Single config list should not have conflicts",
      settings.hasConflictingConfigs(listOf(config1)),
    )
  }

  @Test
  fun `migrateNestedFolderConfigs keeps sub-config when user chooses keep`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val workspacePath = "/test/project"
    val nestedPath = "/test/project/submodule"
    val normalizedWorkspacePath = Paths.get(workspacePath).normalize().toAbsolutePath().toString()

    settings.addFolderConfig(folderConfig(folderPath = workspacePath, baseBranch = "main"))
    settings.addFolderConfig(folderConfig(folderPath = nestedPath, baseBranch = "develop"))

    val workspaceFolder =
      WorkspaceFolder().apply {
        uri = normalizedWorkspacePath.fromPathToUriString()
        name = "project"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(workspaceFolder)

    val settingsSpy = spyk(settings)
    every { settingsSpy.promptForSingleSubConfigMigration(any(), any(), any()) } returns
      FolderConfigSettings.MigrationChoice.KEEP_AS_IS

    val removedCount = settingsSpy.migrateNestedFolderConfigs(projectMock)

    assertEquals("Should not have removed any configs", 0, removedCount)
    assertEquals("Should still have 2 configs", 2, settingsSpy.getAll().size)
  }

  @Test
  fun `autoDeterminedOrg is preserved in settings map after store and retrieve`() {
    val path = "/test/project"

    val config =
      folderConfig(
        folderPath = path,
        baseBranch = "main",
        autoDeterminedOrg = "auto-org-from-ls",
        orgSetByUser = false,
      )
    settings.addFolderConfig(config)

    // Retrieve and verify the autoDeterminedOrg is present in the settings map
    val retrievedConfig = settings.getFolderConfig(path)
    val settingsMap = retrievedConfig.settings
    assertNotNull("Settings map should not be null", settingsMap)
    assertTrue(
      "Settings map should contain AUTO_DETERMINED_ORG key",
      settingsMap!!.containsKey(LsFolderSettingsKeys.AUTO_DETERMINED_ORG),
    )
    assertEquals(
      "autoDeterminedOrg value should match what was stored",
      "auto-org-from-ls",
      settingsMap[LsFolderSettingsKeys.AUTO_DETERMINED_ORG]?.value,
    )
  }

  @Test
  fun `getSeverityFilterForFile returns stored value when file path is under workspace folder`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    val pathWs = "/tmp/snyk_sev_ws"
    val normalizedWs = Paths.get(pathWs).normalize().toAbsolutePath().toString()
    val cfg =
      folderConfig(folderPath = normalizedWs, baseBranch = "main")
        .withSetting(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH, false)
    settings.addFolderConfig(cfg)

    val wf =
      WorkspaceFolder().apply {
        uri = normalizedWs.fromPathToUriString()
        name = "ws"
      }
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(wf)

    val virtualFile = mockk<VirtualFile>()
    every { virtualFile.path } returns "$normalizedWs/src/Foo.java"

    assertEquals(false, settings.getSeverityFilterForFile(Severity.HIGH, virtualFile, projectMock))
    assertNull(settings.getSeverityFilterForFile(Severity.CRITICAL, virtualFile, projectMock))
  }

  @Test
  fun `getSeverityFilterForFile resolves when file path needs normalization to match workspace folder`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    val normalizedWs = Paths.get("/tmp/snyk_pf", "ws").normalize().toAbsolutePath().toString()
    val cfg =
      folderConfig(folderPath = normalizedWs, baseBranch = "main")
        .withSetting(LsFolderSettingsKeys.SEVERITY_FILTER_HIGH, false)
    settings.addFolderConfig(cfg)

    val wf =
      WorkspaceFolder().apply {
        uri = normalizedWs.fromPathToUriString()
        name = "ws"
      }
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(wf)

    val rawFilePath = "/tmp/snyk_pf/../snyk_pf/ws/src/Foo.java"
    assertFalse(
      "precondition: raw paths must not naive-prefix-match so normalization is required",
      rawFilePath.startsWith(normalizedWs),
    )

    val virtualFile = mockk<VirtualFile>()
    every { virtualFile.path } returns rawFilePath

    assertEquals(false, settings.getSeverityFilterForFile(Severity.HIGH, virtualFile, projectMock))
  }

  @Test
  fun `getSeverityFilterForFile returns null when file is not under any configured workspace folder`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    val wf =
      WorkspaceFolder().apply {
        uri = Paths.get("/tmp/other").normalize().toAbsolutePath().toString().fromPathToUriString()
        name = "other"
      }
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(wf)

    val virtualFile = mockk<VirtualFile>()
    every { virtualFile.path } returns "/unrelated/path/Foo.java"

    assertNull(settings.getSeverityFilterForFile(Severity.HIGH, virtualFile, projectMock))
  }

  @Test
  fun `isSeverityEnabledForProjectToolWindow is true when any folder enables severity`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    val path1 = "/tmp/snyk_sev_p1"
    val path2 = "/tmp/snyk_sev_p2"
    val n1 = Paths.get(path1).normalize().toAbsolutePath().toString()
    val n2 = Paths.get(path2).normalize().toAbsolutePath().toString()

    val cfg1 =
      folderConfig(folderPath = n1, baseBranch = "main")
        .withSetting(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL, true)
    val cfg2 = folderConfig(folderPath = n2, baseBranch = "main")
    settings.addFolderConfig(cfg1)
    settings.addFolderConfig(cfg2)

    val wf1 =
      WorkspaceFolder().apply {
        uri = n1.fromPathToUriString()
        name = "p1"
      }
    val wf2 =
      WorkspaceFolder().apply {
        uri = n2.fromPathToUriString()
        name = "p2"
      }

    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(wf1, wf2)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(wf1, wf2)

    assertTrue(
      settings.isSeverityEnabledForProjectToolWindow(
        Severity.CRITICAL,
        projectMock,
        globalSeverityEnabled = false,
      )
    )
    assertFalse(
      settings.isSeverityEnabledForProjectToolWindow(
        Severity.HIGH,
        projectMock,
        globalSeverityEnabled = false,
      )
    )
  }

  @Test
  fun `setSeverityEnabledForProject writes severity_filter_ to all workspace folders for all severities and states`() {
    val cases =
      listOf(
        Severity.CRITICAL to LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL,
        Severity.HIGH to LsFolderSettingsKeys.SEVERITY_FILTER_HIGH,
        Severity.MEDIUM to LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM,
        Severity.LOW to LsFolderSettingsKeys.SEVERITY_FILTER_LOW,
      )
    val states = listOf(true, false)

    for ((severity, key) in cases) {
      for (state in states) {
        settings.clear()
        val ps = SnykApplicationSettingsStateService()
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { pluginSettings() } returns ps
        try {
          val projectMock = mockk<Project>(relaxed = true)
          val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

          val n1 = Paths.get("/tmp/snyk_set_sev_p1").normalize().toAbsolutePath().toString()
          val n2 = Paths.get("/tmp/snyk_set_sev_p2").normalize().toAbsolutePath().toString()
          settings.addFolderConfig(folderConfig(folderPath = n1, baseBranch = "main"))
          settings.addFolderConfig(folderConfig(folderPath = n2, baseBranch = "main"))

          val wf1 =
            WorkspaceFolder().apply {
              uri = n1.fromPathToUriString()
              name = "p1"
            }
          val wf2 =
            WorkspaceFolder().apply {
              uri = n2.fromPathToUriString()
              name = "p2"
            }
          mockkObject(LanguageServerWrapper.Companion)
          every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
          every {
            lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
          } returns setOf(wf1, wf2)
          every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(wf1, wf2)

          val applied = settings.setSeverityEnabledForProject(projectMock, severity, state)

          assertTrue("$severity=$state should be applied", applied)
          for (path in listOf(n1, n2)) {
            val cfg = settings.getFolderConfig(path)
            val setting = cfg.settings?.get(key)
            assertEquals("$path/$key value", state, setting?.value)
            assertEquals("$path/$key changed flag", true, setting?.changed)
            assertTrue(
              "$path/$key should be marked explicitly changed",
              ps.isExplicitlyChanged(path, key),
            )
          }
        } finally {
          unmockkAll()
        }
      }
    }
  }

  @Test
  fun `setSeverityEnabledForProject returns false when no folder configs and does not touch global`() {
    val ps = SnykApplicationSettingsStateService()
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { pluginSettings() } returns ps
    try {
      val projectMock = mockk<Project>(relaxed = true)
      val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
      mockkObject(LanguageServerWrapper.Companion)
      every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
      every {
        lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
      } returns emptySet()
      every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf()

      val applied = settings.setSeverityEnabledForProject(projectMock, Severity.CRITICAL, false)

      assertFalse(applied)
      assertTrue(
        "global criticalSeverityEnabled must remain default true",
        ps.criticalSeverityEnabled,
      )
      assertFalse(
        "no global explicit change should be recorded",
        ps.isExplicitlyChanged(LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL),
      )
    } finally {
      unmockkAll()
    }
  }

  @Test
  fun `setSeverityEnabledForProject returns false for unknown severity`() {
    val ps = SnykApplicationSettingsStateService()
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { pluginSettings() } returns ps
    try {
      val projectMock = mockk<Project>(relaxed = true)
      val applied = settings.setSeverityEnabledForProject(projectMock, Severity.UNKNOWN, true)
      assertFalse(applied)
    } finally {
      unmockkAll()
    }
  }

  @Test
  fun `setProductEnabledForProject writes snyk_ _enabled to all workspace folders for all products and states`() {
    val cases =
      listOf(
        ProductType.OSS to LsFolderSettingsKeys.SNYK_OSS_ENABLED,
        ProductType.CODE_SECURITY to LsFolderSettingsKeys.SNYK_CODE_ENABLED,
        ProductType.IAC to LsFolderSettingsKeys.SNYK_IAC_ENABLED,
        ProductType.SECRETS to LsFolderSettingsKeys.SNYK_SECRETS_ENABLED,
      )
    val states = listOf(false, true)

    for ((productType, key) in cases) {
      for (state in states) {
        settings.clear()
        val ps = SnykApplicationSettingsStateService()
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { pluginSettings() } returns ps
        try {
          val projectMock = mockk<Project>(relaxed = true)
          val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

          val n1 = Paths.get("/tmp/snyk_set_prod_p1").normalize().toAbsolutePath().toString()
          val n2 = Paths.get("/tmp/snyk_set_prod_p2").normalize().toAbsolutePath().toString()
          settings.addFolderConfig(folderConfig(folderPath = n1, baseBranch = "main"))
          settings.addFolderConfig(folderConfig(folderPath = n2, baseBranch = "main"))

          val wf1 =
            WorkspaceFolder().apply {
              uri = n1.fromPathToUriString()
              name = "p1"
            }
          val wf2 =
            WorkspaceFolder().apply {
              uri = n2.fromPathToUriString()
              name = "p2"
            }
          mockkObject(LanguageServerWrapper.Companion)
          every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
          every {
            lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
          } returns setOf(wf1, wf2)
          every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(wf1, wf2)

          val applied = settings.setProductEnabledForProject(projectMock, productType, state)

          assertTrue("$productType=$state should be applied", applied)
          for (path in listOf(n1, n2)) {
            val cfg = settings.getFolderConfig(path)
            val setting = cfg.settings?.get(key)
            assertEquals("$path/$key value", state, setting?.value)
            assertEquals("$path/$key changed flag", true, setting?.changed)
            assertTrue(
              "$path/$key should be marked explicitly changed",
              ps.isExplicitlyChanged(path, key),
            )
          }
        } finally {
          unmockkAll()
        }
      }
    }
  }

  @Test
  fun `setProductEnabledForProject returns false when no folder configs and does not touch global`() {
    val ps = SnykApplicationSettingsStateService()
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { pluginSettings() } returns ps
    try {
      val projectMock = mockk<Project>(relaxed = true)
      val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
      mockkObject(LanguageServerWrapper.Companion)
      every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
      every {
        lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
      } returns emptySet()
      every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf()

      val applied = settings.setProductEnabledForProject(projectMock, ProductType.OSS, false)

      assertFalse(applied)
      assertTrue("global ossScanEnable must remain default true", ps.ossScanEnable)
      assertFalse(
        "no global explicit change should be recorded",
        ps.isExplicitlyChanged(LsFolderSettingsKeys.SNYK_OSS_ENABLED),
      )
    } finally {
      unmockkAll()
    }
  }

  @Test
  fun `isProductEnabledForProjectToolWindow is true when any folder enables product`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    val path1 = "/tmp/snyk_prod_p1"
    val path2 = "/tmp/snyk_prod_p2"
    val n1 = Paths.get(path1).normalize().toAbsolutePath().toString()
    val n2 = Paths.get(path2).normalize().toAbsolutePath().toString()
    settings.addFolderConfig(
      folderConfig(folderPath = n1, baseBranch = "main")
        .withSetting(LsFolderSettingsKeys.SNYK_OSS_ENABLED, true, changed = true)
    )
    settings.addFolderConfig(
      folderConfig(folderPath = n2, baseBranch = "main")
        .withSetting(LsFolderSettingsKeys.SNYK_OSS_ENABLED, false, changed = true)
    )
    val wf1 =
      WorkspaceFolder().apply {
        uri = n1.fromPathToUriString()
        name = "p1"
      }
    val wf2 =
      WorkspaceFolder().apply {
        uri = n2.fromPathToUriString()
        name = "p2"
      }
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns setOf(wf1, wf2)
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(wf1, wf2)

    assertTrue(
      settings.isProductEnabledForProjectToolWindow(
        ProductType.OSS,
        projectMock,
        globalProductEnabled = false,
      )
    )
    assertFalse(
      settings.isProductEnabledForProjectToolWindow(
        ProductType.IAC,
        projectMock,
        globalProductEnabled = false,
      )
    )
  }

  @Test
  fun `isProductEnabledForProjectToolWindow falls back to global when no folder configs`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)
    mockkObject(LanguageServerWrapper.Companion)
    every { LanguageServerWrapper.getInstance(projectMock) } returns lsWrapperMock
    every {
      lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock, promptForTrust = false)
    } returns emptySet()
    every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf()

    assertTrue(
      settings.isProductEnabledForProjectToolWindow(
        ProductType.OSS,
        projectMock,
        globalProductEnabled = true,
      )
    )
    assertFalse(
      settings.isProductEnabledForProjectToolWindow(
        ProductType.OSS,
        projectMock,
        globalProductEnabled = false,
      )
    )
  }
}
