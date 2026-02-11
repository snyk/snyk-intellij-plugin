package snyk.common.lsp.settings

import com.intellij.openapi.project.Project
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.snyk.plugin.fromPathToUriString
import java.nio.file.Paths
import org.eclipse.lsp4j.WorkspaceFolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.FolderConfig
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
      FolderConfig(
        folderPath = path,
        baseBranch = "main",
        additionalParameters = listOf("--scan-all-unmanaged"),
      )

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals("Normalized path should match", normalizedPath, retrievedConfig.folderPath)
    assertEquals("Base branch should match", "main", retrievedConfig.baseBranch)
    assertEquals(
      "Additional parameters should match",
      listOf("--scan-all-unmanaged"),
      retrievedConfig.additionalParameters,
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

    val config = FolderConfig(folderPath = rawPath, baseBranch = "develop")
    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(rawPath)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals("Normalized path should match", expectedNormalizedPath, retrievedConfig.folderPath)
    assertEquals("Base branch should match", "develop", retrievedConfig.baseBranch)

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

    val config = FolderConfig(folderPath = path1, baseBranch = "feature-branch")
    settings.addFolderConfig(config)

    val retrievedConfig1 = settings.getFolderConfig(path1)
    assertEquals("Path1 normalized path should match", normalizedPath1, retrievedConfig1.folderPath)
    assertEquals("Path1 base branch should match", "feature-branch", retrievedConfig1.baseBranch)

    val retrievedConfig2 = settings.getFolderConfig(path2)
    assertEquals("Path2 normalized path should match", normalizedPath1, retrievedConfig2.folderPath)
    assertEquals("Path2 base branch should match", "feature-branch", retrievedConfig2.baseBranch)

    val retrievedConfig3 = settings.getFolderConfig(path3)
    assertEquals("Path3 normalized path should match", normalizedPath1, retrievedConfig3.folderPath)
    assertEquals("Path3 base branch should match", "feature-branch", retrievedConfig3.baseBranch)
  }

  @Test
  fun `addFolderConfig ignores empty or blank folderPaths`() {
    settings.addFolderConfig(FolderConfig(folderPath = "", baseBranch = "main"))
    assertEquals("Config with empty path should be ignored", 0, settings.getAll().size)

    settings.addFolderConfig(FolderConfig(folderPath = "   ", baseBranch = "main"))
    assertEquals("Config with blank path should be ignored", 0, settings.getAll().size)
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
    assertEquals("New config should have default baseBranch", "main", newConfig.baseBranch)
    assertEquals(
      "New config additionalParameters should be emptyList",
      emptyList<String>(),
      newConfig.additionalParameters,
    )
    assertEquals(
      "New config localBranches should be emptyList",
      emptyList<String>(),
      newConfig.localBranches,
    )
    assertEquals(
      "New config referenceFolderPath should be empty string",
      "",
      newConfig.referenceFolderPath,
    )
    assertEquals(
      "New config scanCommandConfig should be emptyMap",
      emptyMap<String, Any>(),
      newConfig.scanCommandConfig,
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
      FolderConfig(folderPath = path, baseBranch = "v1", additionalParameters = listOf("param1"))
    settings.addFolderConfig(config1)

    var retrieved = settings.getFolderConfig(path)
    assertEquals("Retrieved v1 baseBranch", "v1", retrieved.baseBranch)
    assertEquals(
      "Retrieved v1 additionalParameters",
      listOf("param1"),
      retrieved.additionalParameters,
    )
    assertEquals("Retrieved v1 normalizedPath", normalizedPath, retrieved.folderPath)

    val config2 =
      FolderConfig(
        folderPath = equivalentPath,
        baseBranch = "v2",
        additionalParameters = listOf("param2"),
      )
    settings.addFolderConfig(config2)

    retrieved = settings.getFolderConfig(path)
    assertEquals("BaseBranch should be from the overriding config", "v2", retrieved.baseBranch)
    assertEquals(
      "AdditionalParameters should be from the overriding config",
      listOf("param2"),
      retrieved.additionalParameters,
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

    val configUpper = FolderConfig(folderPath = pathUpper, baseBranch = "upper")
    settings.addFolderConfig(configUpper)

    val configLower = FolderConfig(folderPath = pathLower, baseBranch = "lower")
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
        settings.getFolderConfig(pathUpper).baseBranch,
      )
      assertEquals(
        "BaseBranch for lower case path",
        "lower",
        settings.getFolderConfig(pathLower).baseBranch,
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
        settings.getFolderConfig(pathUpper).baseBranch,
      )
      assertEquals(
        "Lower should overwrite if normalized paths are identical (lower retrieval)",
        "lower",
        settings.getFolderConfig(pathLower).baseBranch,
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
        settings.getFolderConfig(pathUpper).baseBranch,
      )
      assertEquals(
        "BaseBranch for lower case path (distinct)",
        "lower",
        settings.getFolderConfig(pathLower).baseBranch,
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
    val config1 = FolderConfig(folderPath = pathWithSlash, baseBranch = "main")
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
    val config2 = FolderConfig(folderPath = pathWithoutSlash, baseBranch = "develop")
    settings.addFolderConfig(config2)

    val retrieved2With = settings.getFolderConfig(pathWithSlash)
    val retrieved2Without = settings.getFolderConfig(pathWithoutSlash)

    assertNotNull("Config (added without slash) should be retrievable with slash", retrieved2With)
    assertEquals(
      "Retrieved (with slash) path should be normalized (added without)",
      expectedNormalizedPath,
      retrieved2With.folderPath,
    )
    assertEquals("develop", retrieved2With.baseBranch) // Ensure correct config is retrieved
    assertNotNull(
      "Config (added without slash) should be retrievable without slash",
      retrieved2Without,
    )
    assertEquals(
      "Retrieved (without slash) path should be normalized (added without)",
      expectedNormalizedPath,
      retrieved2Without.folderPath,
    )
    assertEquals("develop", retrieved2Without.baseBranch)
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

    val config = FolderConfig(folderPath = rootPathWithSlash, baseBranch = "rootBranch")
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
    val config = FolderConfig(folderPath = path, baseBranch = "main", preferredOrg = "my-org-uuid")

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals("Preferred org should match", "my-org-uuid", retrievedConfig.preferredOrg)
  }

  @Test
  fun `addFolderConfig with empty preferredOrg uses default`() {
    val path = "/test/project"
    val config = FolderConfig(folderPath = path, baseBranch = "main")

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals(
      "Preferred org should be empty string by default",
      "",
      retrievedConfig.preferredOrg,
    )
  }

  @Test
  fun `getFolderConfig creates new config with empty preferredOrg by default`() {
    val path = "/new/project"

    val newConfig = settings.getFolderConfig(path)
    assertNotNull("New config should not be null", newConfig)
    assertEquals("New config preferredOrg should be empty string", "", newConfig.preferredOrg)
  }

  @Test
  fun `addFolderConfig overwrites preferredOrg when config is updated`() {
    val path = "/test/project"
    val config1 = FolderConfig(folderPath = path, baseBranch = "main", preferredOrg = "first-org")
    settings.addFolderConfig(config1)

    val retrieved1 = settings.getFolderConfig(path)
    assertEquals("First preferredOrg should match", "first-org", retrieved1.preferredOrg)

    val config2 = FolderConfig(folderPath = path, baseBranch = "main", preferredOrg = "second-org")
    settings.addFolderConfig(config2)

    val retrieved2 = settings.getFolderConfig(path)
    assertEquals("PreferredOrg should be updated", "second-org", retrieved2.preferredOrg)
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
    val config1 = FolderConfig(folderPath = path1, baseBranch = "main", preferredOrg = "org-uuid-1")
    val config2 = FolderConfig(folderPath = path2, baseBranch = "main", preferredOrg = "org-uuid-2")
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

    val config1 = FolderConfig(folderPath = path1, baseBranch = "main", preferredOrg = "")
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

    val config1 = FolderConfig(folderPath = path1, baseBranch = "main", preferredOrg = "org-uuid-1")
    val config2 = FolderConfig(folderPath = path2, baseBranch = "main", preferredOrg = "org-uuid-2")
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
    val config = FolderConfig(folderPath = path, baseBranch = "main")

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals("orgSetByUser should default to false", false, retrievedConfig.orgSetByUser)
  }

  @Test
  fun `addFolderConfig stores and retrieves orgSetByUser set to true`() {
    val path = "/test/project"
    val config = FolderConfig(folderPath = path, baseBranch = "main", orgSetByUser = true)

    settings.addFolderConfig(config)

    val retrievedConfig = settings.getFolderConfig(path)
    assertNotNull("Retrieved config should not be null", retrievedConfig)
    assertEquals("orgSetByUser should be true", true, retrievedConfig.orgSetByUser)
  }

  @Test
  fun `addFolderConfig overwrites orgSetByUser when config is updated`() {
    val path = "/test/project"
    val config1 = FolderConfig(folderPath = path, baseBranch = "main", orgSetByUser = false)
    settings.addFolderConfig(config1)

    val retrieved1 = settings.getFolderConfig(path)
    assertEquals("First orgSetByUser should be false", false, retrieved1.orgSetByUser)

    val config2 = FolderConfig(folderPath = path, baseBranch = "main", orgSetByUser = true)
    settings.addFolderConfig(config2)

    val retrieved2 = settings.getFolderConfig(path)
    assertEquals("orgSetByUser should be updated to true", true, retrieved2.orgSetByUser)
  }

  @Test
  fun `getFolderConfig creates new config with orgSetByUser false by default`() {
    val path = "/new/project"

    val newConfig = settings.getFolderConfig(path)
    assertNotNull("New config should not be null", newConfig)
    assertEquals("New config orgSetByUser should be false", false, newConfig.orgSetByUser)
  }

  @Test
  fun `isAutoOrganizationEnabled returns true when orgSetByUser is false`() {
    val projectMock = mockk<Project>(relaxed = true)
    val lsWrapperMock = mockk<LanguageServerWrapper>(relaxed = true)

    val path = "/test/project"
    val workspaceFolder = WorkspaceFolder().apply { uri = path.fromPathToUriString() }

    val config = FolderConfig(folderPath = path, baseBranch = "main", orgSetByUser = false)

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

    val config = FolderConfig(folderPath = path, baseBranch = "main", orgSetByUser = true)

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

    val config = FolderConfig(folderPath = path, baseBranch = "main", orgSetByUser = true)

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

    val config = FolderConfig(folderPath = path, baseBranch = "main", preferredOrg = "old-org")

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
}
