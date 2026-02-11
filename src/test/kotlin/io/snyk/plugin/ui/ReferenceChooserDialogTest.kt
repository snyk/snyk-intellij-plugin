package io.snyk.plugin.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.fromPathToUriString
import io.snyk.plugin.getContentRootPaths
import javax.swing.JTextField
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.Test
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.common.lsp.settings.LanguageServerSettings
import snyk.trust.WorkspaceTrustSettings

class ReferenceChooserDialogTest : LightPlatform4TestCase() {
  private val lsMock: LanguageServer = mockk(relaxed = true)
  private lateinit var folderConfig: FolderConfig
  private lateinit var cut: ReferenceChooserDialog

  override fun setUp() {
    super.setUp()
    unmockkAll()
    val languageServerWrapper = LanguageServerWrapper.getInstance(project)
    languageServerWrapper.isInitialized = true
    languageServerWrapper.languageServer = lsMock

    project.getContentRootPaths().forEach {
      val absolutePathString = it.toAbsolutePath().normalize().toString()
      service<WorkspaceTrustSettings>().addTrustedPath(absolutePathString)

      // Create a folder config with local branches for the original tests
      folderConfig =
        FolderConfig(
          absolutePathString,
          baseBranch = "testBranch",
          localBranches = listOf("main", "dev"),
        )
      service<FolderConfigSettings>().addFolderConfig(folderConfig)

      languageServerWrapper.configuredWorkspaceFolders.add(
        WorkspaceFolder(absolutePathString.fromPathToUriString(), "test")
      )
      languageServerWrapper.updateFolderConfigRefresh(absolutePathString, true)
    }
    cut = ReferenceChooserDialog(project)

    // Initialize the dialog's internal state to match the test setup
    // This ensures that change tracking works correctly in tests
    cut.baseBranches = mutableMapOf()
    cut.referenceFolders = mutableMapOf()
  }

  override fun tearDown() {
    super.tearDown()
    unmockkAll()
  }

  /** Helper method to create a folder config with no local branches for testing */
  private fun createFolderConfigWithNoBranches(): FolderConfig {
    val folderConfigSettings = service<FolderConfigSettings>()
    val existingConfig = folderConfigSettings.getFolderConfig(folderConfig.folderPath)
    val modifiedConfig = existingConfig.copy(localBranches = emptyList())
    folderConfigSettings.addFolderConfig(modifiedConfig)
    return modifiedConfig
  }

  @Test
  fun `test execute transmits the folder config to language server when changes are made`() {
    // setup selected item to main
    val comboBox =
      ComboBox(arrayOf("main", "dev")).apply {
        name = folderConfig.folderPath
        selectedItem = "main"
      }

    // Create a reference folder control to ensure change tracking works
    val referenceFolder = JTextField().apply { text = "/some/reference/path" }
    val referenceFolderControl = TextFieldWithBrowseButton(referenceFolder)

    cut.baseBranches = mutableMapOf(Pair(folderConfig, comboBox))
    cut.referenceFolders = mutableMapOf(Pair(folderConfig, referenceFolderControl))

    // Manually set hasChanges since we're bypassing the UI change listeners
    cut.hasChanges = true

    cut.execute()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val capturedParam = CapturingSlot<DidChangeConfigurationParams>()
    verify { lsMock.workspaceService.didChangeConfiguration(capture(capturedParam)) }
    val transmittedSettings = capturedParam.captured.settings as LanguageServerSettings
    // we expect the selected item
    assertEquals("main", transmittedSettings.folderConfigs[0].baseBranch)
    // we also expect the reference folder to be transmitted
    assertEquals("/some/reference/path", transmittedSettings.folderConfigs[0].referenceFolderPath)
  }

  @Test
  fun `test execute does not transmit config to language server when no changes are made`() {
    // setup selected item to match the original folderConfig (no changes)
    val comboBox =
      ComboBox(arrayOf("main", "dev")).apply {
        name = folderConfig.folderPath
        selectedItem = folderConfig.baseBranch // Use original value, not "main"
      }

    // Create a reference folder control with original value (no changes)
    val referenceFolder =
      JTextField().apply {
        text = folderConfig.referenceFolderPath ?: "" // Use original value
      }
    val referenceFolderControl = TextFieldWithBrowseButton(referenceFolder)

    cut.baseBranches = mutableMapOf(Pair(folderConfig, comboBox))
    cut.referenceFolders = mutableMapOf(Pair(folderConfig, referenceFolderControl))

    cut.execute()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Since no changes were made, nothing should be transmitted
    verify(exactly = 0) { lsMock.workspaceService.didChangeConfiguration(any()) }
  }

  @Test
  fun `test doCancelAction does not transmit the folder config to language server`() {
    // setup selected item to main
    val comboBox =
      ComboBox(arrayOf("main", "dev")).apply {
        name = folderConfig.folderPath
        selectedItem = "main"
      }

    cut.baseBranches = mutableMapOf(Pair(folderConfig, comboBox))

    cut.doCancelAction()

    val capturedParam = CapturingSlot<DidChangeConfigurationParams>()

    // we need the config update before the scan
    verify(exactly = 0) { lsMock.workspaceService.didChangeConfiguration(capture(capturedParam)) }
  }

  @Test
  fun `test dialog shows only reference folder when local branches is null`() {
    // Create a folder config with null local branches
    val folderConfigSettings = service<FolderConfigSettings>()
    val existingConfig = folderConfigSettings.getFolderConfig(folderConfig.folderPath)
    val configNullBranches = existingConfig.copy(localBranches = null)
    folderConfigSettings.addFolderConfig(configNullBranches)

    // Create new dialog instance
    val dialog = ReferenceChooserDialog(project)

    // Verify that only reference folder components are created
    assertFalse(dialog.baseBranches.containsKey(configNullBranches))
    assertTrue(dialog.referenceFolders.containsKey(configNullBranches))
  }

  @Test
  fun `test dialog shows only reference folder when local branches is empty`() {
    // Create a folder config with empty local branches
    val configEmptyBranches = createFolderConfigWithNoBranches()

    // Create new dialog instance
    val dialog = ReferenceChooserDialog(project)

    // Verify that only reference folder components are created
    assertFalse(dialog.baseBranches.containsKey(configEmptyBranches))
    assertTrue(dialog.referenceFolders.containsKey(configEmptyBranches))
  }

  @Test
  fun `test execute handles folder configs with no base branches correctly`() {
    // Create a folder config with no local branches
    val configNoBranches = createFolderConfigWithNoBranches()

    // Create new dialog instance
    val dialog = ReferenceChooserDialog(project)

    // Set up reference folder with text
    val referenceFolder = JTextField().apply { text = "/some/reference/path" }
    val referenceFolderControl = TextFieldWithBrowseButton(referenceFolder)
    dialog.referenceFolders[configNoBranches] = referenceFolderControl

    // Manually set hasChanges since we're bypassing the UI change listeners
    dialog.hasChanges = true

    // Execute - should not crash and should process reference folder
    dialog.execute()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Verify that configuration was updated
    val capturedParam = CapturingSlot<DidChangeConfigurationParams>()
    verify { lsMock.workspaceService.didChangeConfiguration(capture(capturedParam)) }

    val transmittedSettings = capturedParam.captured.settings as LanguageServerSettings
    val transmittedConfig =
      transmittedSettings.folderConfigs.find { it.folderPath == configNoBranches.folderPath }

    assertNotNull(transmittedConfig)
    assertEquals("", transmittedConfig!!.baseBranch) // Should be empty string
    assertEquals("/some/reference/path", transmittedConfig.referenceFolderPath)
  }

  @Test
  fun `test OK button is disabled initially and enabled after changes`() {
    // Create new dialog instance
    val dialog = ReferenceChooserDialog(project)

    // Initially, OK button should be disabled (no changes)
    assertFalse(dialog.isOKActionEnabled)

    // Simulate a change by modifying a combo box
    val comboBox = dialog.baseBranches.values.firstOrNull()
    if (comboBox != null) {
      comboBox.selectedItem = "dev" // Change selection
      assertTrue(dialog.isOKActionEnabled)
    }
  }
}
