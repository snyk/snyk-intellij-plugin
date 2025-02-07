package io.snyk.plugin.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import io.snyk.plugin.runInBackground
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


class BranchChooserComboBoxDialog(val project: Project) : DialogWrapper(true) {
    val baseBranches: MutableMap<FolderConfig, ComboBox<String>> = mutableMapOf()
    val referenceFolders: MutableMap<FolderConfig, TextFieldWithBrowseButton> = mutableMapOf()

    init {
        init()
        title = "Choose base branch for net-new issue scanning"
    }

    override fun createCenterPanel(): JComponent {
        val folderConfigs = service<FolderConfigSettings>().getAllForProject(project)
        folderConfigs.forEach { folderConfig ->
            val comboBox = ComboBox(folderConfig.localBranches?.sorted()?.toTypedArray()?: emptyArray())
            comboBox.selectedItem = folderConfig.baseBranch
            comboBox.name = folderConfig.folderPath
            baseBranches[folderConfig] = comboBox
            referenceFolders[folderConfig] = configureReferenceFolder(folderConfig)
        }
        val gridBagLayout = GridBagLayout()
        val dialogPanel = JPanel(gridBagLayout)
        val gridBag = GridBag()
        gridBag.defaultFill = GridBagConstraints.BOTH
        gridBag.insets = JBUI.insets(20)
        gridBag.defaultPaddingX = 20
        gridBag.defaultPaddingY = 20

        baseBranches.forEach {
            dialogPanel.add(JLabel("Base Branch for ${it.value.name}: "), gridBag.nextLine())
            dialogPanel.add(it.value, gridBag.nextLine())
            val referenceFolder = referenceFolders[it.key]
            dialogPanel.add(JLabel("Reference Folder for ${referenceFolder!!.name}: "), gridBag.nextLine())
            dialogPanel.add(referenceFolder, gridBag.nextLine())
        }
        return dialogPanel
    }

    private fun configureReferenceFolder(folderConfig: FolderConfig): TextFieldWithBrowseButton {
        val referenceFolder = TextFieldWithBrowseButton()
        referenceFolder.text = folderConfig.referenceFolderPath ?: ""
        referenceFolder.name = folderConfig.folderPath

        referenceFolder.toolTipText =
            "Optional. Here you can specify a reference directory to be used for scanning."

        val descriptor = FileChooserDescriptor(
            false,
            true,
            false,
            false,
            false,
            false
        )

        referenceFolder.addBrowseFolderListener(
            "",
            "Please choose the reference folder you want to use:",
            null,
            descriptor,
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
        )
        return referenceFolder
    }

    override fun doOKAction() {
        execute()
        super.doOKAction()
    }

    fun execute() {
        val folderConfigSettings = service<FolderConfigSettings>()
        baseBranches.forEach {
            val folderConfig: FolderConfig = it.key

            val baseBranch = getSelectedItem(it.value) ?: ""
            val referenceFolder = referenceFolders[folderConfig]!!.text
            if (baseBranch.isNotBlank()) {
                folderConfigSettings.addFolderConfig(folderConfig.copy(baseBranch = baseBranch))
            }
            if (referenceFolder.isNotBlank()){
                folderConfigSettings.addFolderConfig(folderConfig.copy(referenceFolderPath = referenceFolder))
            }
        }
        runInBackground("Snyk: updating configuration") {
            LanguageServerWrapper.getInstance().updateConfiguration(true)
        }
    }

    override fun doValidate(): ValidationInfo? {
        baseBranches.forEach { entry ->
            val baseBranch = entry.value
            val refFolder = referenceFolders[entry.key]

            val baseBranchSelected = !getSelectedItem(baseBranch).isNullOrBlank()
            val refFolderSelected = refFolder != null && refFolder.text.isNotBlank()
            if (!baseBranchSelected && !refFolderSelected) {
                return ValidationInfo(
                    "Please select a base branch for ${baseBranch.name} or a reference folder",
                    baseBranch
                )
            }
        }
        return null
    }

    private fun getSelectedItem(baseBranch: ComboBox<String>) = baseBranch.selectedItem?.toString()
}
