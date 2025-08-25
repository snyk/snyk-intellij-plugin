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
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.Test
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings
import snyk.common.lsp.settings.LanguageServerSettings
import snyk.trust.WorkspaceTrustSettings
import javax.swing.JTextField

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
            folderConfig = FolderConfig(absolutePathString, "testBranch")
            service<FolderConfigSettings>().addFolderConfig(folderConfig)
            languageServerWrapper.configuredWorkspaceFolders.add(
                WorkspaceFolder(
                    absolutePathString.fromPathToUriString(),
                    "test"
                )
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

    @Test
    fun `test execute transmits the folder config to language server when changes are made`() {
        // setup selected item to main
        val comboBox = ComboBox(arrayOf("main", "dev")).apply {
            name = folderConfig.folderPath
            selectedItem = "main"
        }

        // Create a reference folder control to ensure change tracking works
        val referenceFolder = JTextField().apply {
            text = "/some/reference/path"
        }
        val referenceFolderControl = TextFieldWithBrowseButton(referenceFolder)

        cut.baseBranches = mutableMapOf(Pair(folderConfig, comboBox))
        cut.referenceFolders = mutableMapOf(Pair(folderConfig, referenceFolderControl))

        cut.execute()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val capturedParam = CapturingSlot<DidChangeConfigurationParams>()
        verify { lsMock.workspaceService.didChangeConfiguration(capture(capturedParam)) }
        val transmittedSettings = capturedParam.captured.settings as LanguageServerSettings
        // we expect the selected item
        assertEquals("main", transmittedSettings.folderConfigs[0].baseBranch)
    }

    @Test
    fun `test execute transmits config to language server even when no changes are made`() {
        // setup selected item to main
        val comboBox = ComboBox(arrayOf("main", "dev")).apply {
            name = folderConfig.folderPath
            selectedItem = "main"
        }

        // Create a reference folder control to ensure change tracking works
        val referenceFolder = JTextField().apply {
            text = "/some/reference/path"
        }
        val referenceFolderControl = TextFieldWithBrowseButton(referenceFolder)

        cut.baseBranches = mutableMapOf(Pair(folderConfig, comboBox))
        cut.referenceFolders = mutableMapOf(Pair(folderConfig, referenceFolderControl))

        cut.execute()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val capturedParam = CapturingSlot<DidChangeConfigurationParams>()
        // we need the config update before the scan
        verify(exactly = 1, timeout = 2000) {
            lsMock.workspaceService.didChangeConfiguration(capture(capturedParam))
        }

        val transmittedSettings = capturedParam.captured.settings as LanguageServerSettings

        // we expect the selected item
        assertEquals("main", transmittedSettings.folderConfigs[0].baseBranch)
    }

    @Test
    fun `test doCancelAction does not transmit the folder config to language server`() {
        // setup selected item to main
        val comboBox = ComboBox(arrayOf("main", "dev")).apply {
            name = folderConfig.folderPath
            selectedItem = "main"
        }

        cut.baseBranches = mutableMapOf(Pair(folderConfig, comboBox))

        cut.doCancelAction()

        val capturedParam = CapturingSlot<DidChangeConfigurationParams>()

        // we need the config update before the scan
        verify(exactly = 0) {
            lsMock.workspaceService.didChangeConfiguration(capture(capturedParam))
        }
    }
}
