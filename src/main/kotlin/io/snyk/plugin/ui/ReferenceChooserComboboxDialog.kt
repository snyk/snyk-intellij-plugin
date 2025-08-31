package io.snyk.plugin.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBScrollPane
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


class ReferenceChooserDialog(val project: Project) : DialogWrapper(true) {
    var baseBranches: MutableMap<FolderConfig, ComboBox<String>> = mutableMapOf()
    internal var referenceFolders: MutableMap<FolderConfig, TextFieldWithBrowseButton> = mutableMapOf()
    internal var hasChanges = false

    init {
        init()
        title = "Choose base branch for net-new issue scanning"
    }

    override fun isOKActionEnabled(): Boolean = hasChanges

    override fun createCenterPanel(): JComponent {
        val folderConfigs = service<FolderConfigSettings>().getAllForProject(project)

        val gridBagLayout = GridBagLayout()
        val dialogPanel = JPanel(gridBagLayout)
        val gridBag = GridBag()
        gridBag.defaultFill = GridBagConstraints.BOTH
        gridBag.insets = JBUI.insets(20)
        gridBag.defaultPaddingX = 20
        gridBag.defaultPaddingY = 20

        folderConfigs.forEach { folderConfig ->
            // Only create combo box if there are local branches
            folderConfig.localBranches?.takeIf { it.isNotEmpty() }?.let { localBranches ->
                val comboBox = ComboBox(localBranches.sorted().toTypedArray())
                comboBox.selectedItem = folderConfig.baseBranch
                comboBox.name = folderConfig.folderPath

                // Add change listener to track modifications
                comboBox.addActionListener { hasChanges = true }

                baseBranches[folderConfig] = comboBox

                // Add base branch components to dialog
                dialogPanel.add(JLabel("Base Branch for ${comboBox.name}: "), gridBag.nextLine())
                dialogPanel.add(comboBox, gridBag.nextLine())
            }

            // Always create reference folder
            val referenceFolder = configureReferenceFolder(folderConfig)
            referenceFolders[folderConfig] = referenceFolder

            // Add reference folder components to dialog
            dialogPanel.add(JLabel("Reference Folder for ${referenceFolder.name}: "), gridBag.nextLine())
            dialogPanel.add(referenceFolder, gridBag.nextLine())
        }

        // Wrap the content panel in a scroll pane
        val scrollPane = JBScrollPane(dialogPanel)
        scrollPane.verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        scrollPane.border = JBUI.Borders.empty()

        return scrollPane
    }

    private fun configureReferenceFolder(folderConfig: FolderConfig): TextFieldWithBrowseButton {
        val referenceFolder = TextFieldWithBrowseButton()
        referenceFolder.text = folderConfig.referenceFolderPath ?: ""
        referenceFolder.name = folderConfig.folderPath

        referenceFolder.toolTipText =
            "Optional. Here you can specify a reference directory to be used for scanning."

        // Add change listener to track modifications
        referenceFolder.addPropertyChangeListener("text") { hasChanges = true }

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

    public override fun doOKAction() {
        super.doOKAction()
        execute()
    }

    fun execute() {
        // Only proceed if there are actual changes
        if (!hasChanges) {
            return
        }

        val folderConfigSettings = service<FolderConfigSettings>()

        // Iterate over the baseBranches keys to ensure we process the correct folder configs
        baseBranches.keys.forEach { folderConfig ->
            val comboBox = baseBranches[folderConfig]
            val baseBranch = comboBox?.let { getSelectedItem(it) } ?: ""
            val referenceFolderControl = referenceFolders[folderConfig]

            // Update if either base branch or reference folder is provided
            if (baseBranch.isNotBlank() || referenceFolderControl?.text?.isNotBlank() == true) {
                folderConfigSettings.addFolderConfig(
                    folderConfig.copy(
                        baseBranch = baseBranch,
                        referenceFolderPath = referenceFolderControl?.text ?: ""
                    )
                )
            }
        }

        // Also process any folder configs that only have reference folders (no base branches)
        val allFolderConfigs = service<FolderConfigSettings>().getAllForProject(project)
        allFolderConfigs.forEach { folderConfig ->
            if (!baseBranches.containsKey(folderConfig)) {
                referenceFolders[folderConfig]?.text?.let { referenceFolder ->
                    if (referenceFolder.isNotBlank()) {
                        folderConfigSettings.addFolderConfig(
                            folderConfig.copy(
                                baseBranch = "",
                                referenceFolderPath = referenceFolder
                            )
                        )
                    }
                }
            }
        }

        // Only update configuration if there are changes
        runInBackground("Snyk: updating configuration") {
            LanguageServerWrapper.getInstance(project).updateConfiguration(true)
        }
    }

    private fun getSelectedItem(baseBranch: ComboBox<String>) = baseBranch.selectedItem?.toString()
}
