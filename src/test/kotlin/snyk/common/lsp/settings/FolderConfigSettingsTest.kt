package snyk.common.lsp.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.FolderConfig
import java.io.File
import java.nio.file.Paths

class FolderConfigSettingsTest {

    private lateinit var settings: FolderConfigSettings

    @Before
    fun setUp() {
        settings = FolderConfigSettings()
        settings.clear()
    }

    @Test
    fun `addFolderConfig stores and getFolderConfig retrieves with simple path`() {
        val path = "/test/projectA"
        val normalizedPath = Paths.get(path).normalize().toAbsolutePath().toString()
        val config = FolderConfig(
            folderPath = path,
            baseBranch = "main",
            additionalParameters = listOf("--scan-all-unmanaged")
        )

        settings.addFolderConfig(config)

        val retrievedConfig = settings.getFolderConfig(path)
        assertNotNull("Retrieved config should not be null", retrievedConfig)
        assertEquals("Normalized path should match", normalizedPath, retrievedConfig.folderPath)
        assertEquals("Base branch should match", "main", retrievedConfig.baseBranch)
        assertEquals(
            "Additional parameters should match",
            listOf("--scan-all-unmanaged"),
            retrievedConfig.additionalParameters
        )

        assertEquals("Settings map size should be 1", 1, settings.getAll().size)
        assertTrue("Settings map should contain normalized path key", settings.getAll().containsKey(normalizedPath))
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
            retrievedAgain.folderPath
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
        val expectedNormalizedPath =
            Paths.get(rawPath).normalize().toAbsolutePath().toString()

        assertTrue("Settings should be empty initially", settings.getAll().isEmpty())

        val newConfig = settings.getFolderConfig(rawPath)
        assertNotNull("New config should not be null", newConfig)
        assertEquals("FolderPath in new config should be normalized", expectedNormalizedPath, newConfig.folderPath)
        assertEquals("New config should have default baseBranch", "main", newConfig.baseBranch)
        assertEquals(
            "New config additionalParameters should be emptyList",
            emptyList<String>(),
            newConfig.additionalParameters
        )
        assertEquals("New config localBranches should be emptyList", emptyList<String>(), newConfig.localBranches)
        assertEquals("New config referenceFolderPath should be empty string", "", newConfig.referenceFolderPath)
        assertEquals(
            "New config scanCommandConfig should be emptyMap",
            emptyMap<String, Any>(),
            newConfig.scanCommandConfig
        )

        val allConfigs = settings.getAll()
        assertEquals("A new config should have been added", 1, allConfigs.size)
        assertTrue(
            "Internal map should contain the new config with normalized path as key",
            allConfigs.containsKey(expectedNormalizedPath)
        )
        assertEquals(
            "Stored config folderPath should match",
            expectedNormalizedPath,
            allConfigs[expectedNormalizedPath]?.folderPath
        )
    }

    @Test
    fun `addFolderConfig overwrites existing config with same normalized path`() {
        val path = "/my/overwritable/folder"
        val equivalentPath = "/my/overwritable/./folder/../folder"
        val normalizedPath = Paths.get(path).normalize().toAbsolutePath().toString()

        val config1 = FolderConfig(folderPath = path, baseBranch = "v1", additionalParameters = listOf("param1"))
        settings.addFolderConfig(config1)

        var retrieved = settings.getFolderConfig(path)
        assertEquals("Retrieved v1 baseBranch", "v1", retrieved.baseBranch)
        assertEquals("Retrieved v1 additionalParameters", listOf("param1"), retrieved.additionalParameters)
        assertEquals("Retrieved v1 normalizedPath", normalizedPath, retrieved.folderPath)

        val config2 =
            FolderConfig(folderPath = equivalentPath, baseBranch = "v2", additionalParameters = listOf("param2"))
        settings.addFolderConfig(config2)

        retrieved = settings.getFolderConfig(path)
        assertEquals("BaseBranch should be from the overriding config", "v2", retrieved.baseBranch)
        assertEquals(
            "AdditionalParameters should be from the overriding config",
            listOf("param2"),
            retrieved.additionalParameters
        )
        assertEquals("NormalizedPath should remain the same after overwrite", normalizedPath, retrieved.folderPath)

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

        if (normalizedUpper.equals(normalizedLower, ignoreCase = true) && normalizedUpper != normalizedLower) {
            assertEquals(
                "Configs with paths differing only in case should be distinct if normalized strings differ",
                2,
                settings.getAll().size
            )
            assertEquals("BaseBranch for upper case path", "upper", settings.getFolderConfig(pathUpper).baseBranch)
            assertEquals("BaseBranch for lower case path", "lower", settings.getFolderConfig(pathLower).baseBranch)
        } else if (normalizedUpper == normalizedLower) {
            assertEquals("If normalized paths are identical, one should overwrite the other", 1, settings.getAll().size)
            assertEquals(
                "Lower should overwrite if normalized paths are identical (upper retrieval)",
                "lower",
                settings.getFolderConfig(pathUpper).baseBranch
            )
            assertEquals(
                "Lower should overwrite if normalized paths are identical (lower retrieval)",
                "lower",
                settings.getFolderConfig(pathLower).baseBranch
            )
        } else {
            assertEquals("Distinct normalized paths should result in distinct entries", 2, settings.getAll().size)
            assertEquals(
                "BaseBranch for upper case path (distinct)",
                "upper",
                settings.getFolderConfig(pathUpper).baseBranch
            )
            assertEquals(
                "BaseBranch for lower case path (distinct)",
                "lower",
                settings.getFolderConfig(pathLower).baseBranch
            )
        }
    }

    @Test
    fun `addFolderConfig with trailing slash is equivalent to without trailing slash`() {
        val pathWithSlash = "/test/trailing/"
        val pathWithoutSlash = "/test/trailing"
        // For non-root paths, Paths.get().normalize() typically removes trailing slashes.
        val expectedNormalizedPath =
            Paths.get(pathWithoutSlash).normalize().toAbsolutePath().toString()

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
            retrieved1With.folderPath
        )
        assertNotNull("Config should be retrievable without slash", retrieved1Without)
        assertEquals(
            "Retrieved (without slash) path should be normalized",
            expectedNormalizedPath,
            retrieved1Without.folderPath
        )
        assertEquals("Both retrievals should yield the same object instance", retrieved1With, retrieved1Without)
        assertEquals("Only one config should be stored", 1, settings.getAll().size)
        assertTrue("Map key should be the normalized path", settings.getAll().containsKey(expectedNormalizedPath))

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
            retrieved2With.folderPath
        )
        assertEquals("develop", retrieved2With.baseBranch) // Ensure correct config is retrieved
        assertNotNull("Config (added without slash) should be retrievable without slash", retrieved2Without)
        assertEquals(
            "Retrieved (without slash) path should be normalized (added without)",
            expectedNormalizedPath,
            retrieved2Without.folderPath
        )
        assertEquals("develop", retrieved2Without.baseBranch)
        assertEquals("Both retrievals should yield the same object instance", retrieved2With, retrieved2Without)
        assertEquals("Only one config should be stored when adding without slash", 1, settings.getAll().size)
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
        assertEquals("Retrieved root path should be normalized", rootPathNormalized, retrieved.folderPath)
        assertEquals("Settings map size for root path should be 1", 1, settings.getAll().size)

        // Test with a path that might normalize to root, e.g., "/."
        val retrievedDot = settings.getFolderConfig("/.")
        assertNotNull("Retrieved config for '/.' should not be null", retrievedDot)
        assertEquals(
            "Retrieved path for '/.' should be normalized to root",
            rootPathNormalized,
            retrievedDot.folderPath
        )
        assertEquals(
            "Settings map size should still be 1 after retrieving '/.'",
            1,
            settings.getAll().size
        ) // Still one config
    }
}
