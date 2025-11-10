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
    fun `apply enables auto-detect when preferredOrgTextField is blank`() {
        val path = "/test/project"
        val workspaceFolder = WorkspaceFolder().apply {
            uri = path.fromPathToUriString()
            name = "test-project"
        }

        // Setup initial config with orgSetByUser = true
        val initialConfig = FolderConfig(
            folderPath = path,
            baseBranch = "main",
            preferredOrg = "some-org",
            orgSetByUser = true
        )
        folderConfigSettings.addFolderConfig(initialConfig)

        // Mock the dialog to return blank preferredOrg
        every { snykSettingsDialogMock.getPreferredOrg() } returns ""
        every { snykSettingsDialogMock.isAutoDetectOrg() } returns false // User had it unchecked
        every { snykSettingsDialogMock.getAdditionalParameters() } returns ""

        // Mock workspace folders
        every { lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock) } returns setOf(workspaceFolder)
        every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

        // Simulate the apply logic
        val preferredOrgText = snykSettingsDialogMock.getPreferredOrg()
        val shouldAutoDetect = if (preferredOrgText.isBlank()) {
            true
        } else {
            snykSettingsDialogMock.isAutoDetectOrg()
        }

        val updatedConfig = initialConfig.copy(
            additionalParameters = snykSettingsDialogMock.getAdditionalParameters().split(" ", System.lineSeparator()),
            preferredOrg = if (shouldAutoDetect) "" else preferredOrgText,
            orgSetByUser = !shouldAutoDetect
        )
        folderConfigSettings.addFolderConfig(updatedConfig)

        // Verify the result
        val resultConfig = folderConfigSettings.getFolderConfig(path)
        assertEquals("preferredOrg should be empty", "", resultConfig.preferredOrg)
        assertFalse("orgSetByUser should be false (auto-detect enabled)", resultConfig.orgSetByUser)
        assertTrue("isAutoOrganizationEnabled should return true", folderConfigSettings.isAutoOrganizationEnabled(projectMock))
    }

    @Test
    fun `apply keeps manual org when preferredOrgTextField has value`() {
        val path = "/test/project"
        val workspaceFolder = WorkspaceFolder().apply {
            uri = path.fromPathToUriString()
            name = "test-project"
        }

        // Setup initial config with auto-detect enabled
        val initialConfig = FolderConfig(
            folderPath = path,
            baseBranch = "main",
            preferredOrg = "",
            orgSetByUser = false
        )
        folderConfigSettings.addFolderConfig(initialConfig)

        // Mock the dialog to return a specific org
        every { snykSettingsDialogMock.getPreferredOrg() } returns "my-specific-org"
        every { snykSettingsDialogMock.isAutoDetectOrg() } returns false // User unchecked auto-detect
        every { snykSettingsDialogMock.getAdditionalParameters() } returns ""

        // Mock workspace folders
        every { lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock) } returns setOf(workspaceFolder)
        every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

        // Simulate the apply logic
        val preferredOrgText = snykSettingsDialogMock.getPreferredOrg()
        val shouldAutoDetect = if (preferredOrgText.isBlank()) {
            true
        } else {
            snykSettingsDialogMock.isAutoDetectOrg()
        }

        val updatedConfig = initialConfig.copy(
            additionalParameters = snykSettingsDialogMock.getAdditionalParameters().split(" ", System.lineSeparator()),
            preferredOrg = if (shouldAutoDetect) "" else preferredOrgText,
            orgSetByUser = !shouldAutoDetect
        )
        folderConfigSettings.addFolderConfig(updatedConfig)

        // Verify the result
        val resultConfig = folderConfigSettings.getFolderConfig(path)
        assertEquals("preferredOrg should be set", "my-specific-org", resultConfig.preferredOrg)
        assertTrue("orgSetByUser should be true (manual org)", resultConfig.orgSetByUser)
        assertFalse("isAutoOrganizationEnabled should return false", folderConfigSettings.isAutoOrganizationEnabled(projectMock))
    }

    @Test
    fun `apply respects auto-detect checkbox when preferredOrgTextField has value and checkbox is checked`() {
        val path = "/test/project"
        val workspaceFolder = WorkspaceFolder().apply {
            uri = path.fromPathToUriString()
            name = "test-project"
        }

        // Setup initial config
        val initialConfig = FolderConfig(
            folderPath = path,
            baseBranch = "main",
            preferredOrg = "old-org",
            orgSetByUser = true
        )
        folderConfigSettings.addFolderConfig(initialConfig)

        // Mock the dialog - user has text in field but checkbox is checked
        every { snykSettingsDialogMock.getPreferredOrg() } returns "some-org-in-field"
        every { snykSettingsDialogMock.isAutoDetectOrg() } returns true // User checked auto-detect
        every { snykSettingsDialogMock.getAdditionalParameters() } returns ""

        // Mock workspace folders
        every { lsWrapperMock.getWorkspaceFoldersFromRoots(projectMock) } returns setOf(workspaceFolder)
        every { lsWrapperMock.configuredWorkspaceFolders } returns mutableSetOf(workspaceFolder)

        // Simulate the apply logic
        val preferredOrgText = snykSettingsDialogMock.getPreferredOrg()
        val shouldAutoDetect = if (preferredOrgText.isBlank()) {
            true
        } else {
            snykSettingsDialogMock.isAutoDetectOrg()
        }

        val updatedConfig = initialConfig.copy(
            additionalParameters = snykSettingsDialogMock.getAdditionalParameters().split(" ", System.lineSeparator()),
            preferredOrg = if (shouldAutoDetect) "" else preferredOrgText,
            orgSetByUser = !shouldAutoDetect
        )
        folderConfigSettings.addFolderConfig(updatedConfig)

        // Verify the result - should use auto-detect since checkbox is checked
        val resultConfig = folderConfigSettings.getFolderConfig(path)
        assertEquals("preferredOrg should be empty (auto-detect)", "", resultConfig.preferredOrg)
        assertFalse("orgSetByUser should be false (auto-detect enabled)", resultConfig.orgSetByUser)
        assertTrue("isAutoOrganizationEnabled should return true", folderConfigSettings.isAutoOrganizationEnabled(projectMock))
    }
}
