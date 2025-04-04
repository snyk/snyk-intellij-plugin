package io.snyk.plugin.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.toURI
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
    lateinit var cut: ReferenceChooserDialog

    override fun setUp() {
        super.setUp()
        unmockkAll()
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        languageServerWrapper.isInitialized = true
        languageServerWrapper.languageServer = lsMock

        project.getContentRootPaths().forEach {
            val absolutePathString = it.toString()
            service<WorkspaceTrustSettings>().addTrustedPath(absolutePathString)
            folderConfig = FolderConfig(absolutePathString, "testBranch")
            service<FolderConfigSettings>().addFolderConfig(folderConfig)
            languageServerWrapper.configuredWorkspaceFolders.add(
                WorkspaceFolder(
                    absolutePathString.toURI().toASCIIString(), "test"
                )
            )
            languageServerWrapper.folderConfigsRefreshed[folderConfig.folderPath] = true
        }
        cut = ReferenceChooserDialog(project)
    }

    override fun tearDown() {
        super.tearDown()
        unmockkAll()
    }

    @Test
    fun `test execute transmits the folder config to language server`() {
        // setup selected item to main
        val comboBox = ComboBox(arrayOf("main", "master")).apply {
            name = folderConfig.folderPath
            selectedItem = "main"
        }

        cut.baseBranches = mutableMapOf(Pair(folderConfig, comboBox))

        cut.execute()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()


        val capturedParam = CapturingSlot<DidChangeConfigurationParams>()
        verify { lsMock.workspaceService.didChangeConfiguration(capture(capturedParam)) }
        val transmittedSettings = capturedParam.captured.settings as LanguageServerSettings
        // we expect the selected item
        assertEquals("main", transmittedSettings.folderConfigs[0].baseBranch)
    }

    @Test
    fun `test execute does not transmit the folder config to language server`() {
        // setup selected item to main
        val comboBox = ComboBox(arrayOf("main", "master")).apply {
            name = folderConfig.folderPath
            selectedItem = "main"
        }
        cut.baseBranches = mutableMapOf(Pair(folderConfig, comboBox))

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
        val comboBox = ComboBox(arrayOf("main", "master")).apply {
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

    @Test
    fun `enforce either reference folder or base branch is set`() {
        val comboBox = ComboBox(emptyArray<String>())
        cut.baseBranches = mutableMapOf(Pair(folderConfig, comboBox))

        cut.doOKAction()

        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val capturedParam = CapturingSlot<DidChangeConfigurationParams>()
        verify(exactly = 0) {
            lsMock.workspaceService.didChangeConfiguration(capture(capturedParam))
        }
    }
}
