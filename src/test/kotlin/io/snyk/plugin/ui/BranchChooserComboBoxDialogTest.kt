package io.snyk.plugin.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.PlatformTestUtil
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.toVirtualFile
import okio.Path.Companion.toPath
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.Test
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.FolderConfigSettings
import snyk.common.lsp.LanguageServerSettings
import snyk.common.lsp.LanguageServerWrapper
import snyk.trust.WorkspaceTrustService
import snyk.trust.WorkspaceTrustSettings
import kotlin.io.path.absolutePathString


class BranchChooserComboBoxDialogTest : LightPlatform4TestCase() {
    private val lsMock: LanguageServer = mockk(relaxed = true)
    private lateinit var folderConfig: FolderConfig
    lateinit var cut: BranchChooserComboBoxDialog

    override fun setUp(): Unit {
        super.setUp()
        unmockkAll()
        folderConfig = FolderConfig(project.basePath.toString(), "testBranch")
        service<FolderConfigSettings>().addFolderConfig(folderConfig)
        project.getContentRootPaths().forEach { service<WorkspaceTrustSettings>().addTrustedPath(it.root.absolutePathString())}
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        languageServerWrapper.isInitialized = true
        languageServerWrapper.languageServer = lsMock
        project.basePath?.let { service<WorkspaceTrustService>().addTrustedPath(it.toPath().parent!!.toNioPath()) }
        cut = BranchChooserComboBoxDialog(project)
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

        cut.comboBoxes = mutableListOf(comboBox)

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
        cut.comboBoxes = mutableListOf(comboBox)

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

        cut.comboBoxes = mutableListOf(comboBox)

        cut.doCancelAction()

        val capturedParam = CapturingSlot<DidChangeConfigurationParams>()

        // we need the config update before the scan
        verify(exactly = 0) {
            lsMock.workspaceService.didChangeConfiguration(capture(capturedParam))
        }
    }
}
