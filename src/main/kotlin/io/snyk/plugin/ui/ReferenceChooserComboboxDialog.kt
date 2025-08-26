package io.snyk.plugin.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
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

    private val originalBaseBranches: MutableMap<FolderConfig, String> = mutableMapOf()
    private val originalReferenceFolders: MutableMap<FolderConfig, String> = mutableMapOf()
    private val changedComponents: MutableSet<FolderConfig> = mutableSetOf()

    init {
        init()
        title = "Choose base branch for net-new issue scanning"
        isOKActionEnabled = true
    }

    override fun createCenterPanel(): JComponent {
        val folderConfigs = service<FolderConfigSettings>().getAllForProject(project)
        folderConfigs.forEach { folderConfig ->
            val comboBox = ComboBox(folderConfig.localBranches?.sorted()?.toTypedArray() ?: emptyArray())
            comboBox.selectedItem = folderConfig.baseBranch
            comboBox.name = folderConfig.folderPath

            // Store original values for change detection
            originalBaseBranches[folderConfig] = folderConfig.baseBranch

            baseBranches[folderConfig] = comboBox
            referenceFolders[folderConfig] = configureReferenceFolder(folderConfig)

            // Add change listeners to track modifications
            comboBox.addActionListener { onComponentChanged(folderConfig) }
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

        // Store original value for change detection
        originalReferenceFolders[folderConfig] = folderConfig.referenceFolderPath ?: ""

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

        // Track changes by adding a document listener to the text field
        referenceFolder.textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) { onComponentChanged(folderConfig) }
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) { onComponentChanged(folderConfig) }
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) { onComponentChanged(folderConfig) }
        })

        return referenceFolder
    }

    private fun onComponentChanged(folderConfig: FolderConfig) {
        changedComponents.add(folderConfig)
    }

    private fun hasComponentChanged(folderConfig: FolderConfig): Boolean {
        val baseBranch = baseBranches[folderConfig]
        val refFolder = referenceFolders[folderConfig]

        if (baseBranch == null || refFolder == null) {
            return false
        }

        val currentBaseBranch = getSelectedItem(baseBranch) ?: ""
        val currentRefFolder = refFolder.text
        val originalBaseBranch = originalBaseBranches[folderConfig] ?: ""
        val originalRefFolder = originalReferenceFolders[folderConfig] ?: ""

        return currentBaseBranch != originalBaseBranch || currentRefFolder != originalRefFolder
    }

    public override fun doOKAction() {
        // Always allow OK action, validation is now advisory
        super.doOKAction()
        execute()
    }

    fun execute() {
        val folderConfigSettings = service<FolderConfigSettings>()
        baseBranches.forEach {
            val folderConfig: FolderConfig = it.key

            val baseBranch = getSelectedItem(it.value) ?: ""
            val referenceFolderControl = referenceFolders[folderConfig]
            val referenceFolder = referenceFolderControl?.text ?: ""

            // Only update if there are actual changes or if the component was modified
            if (hasComponentChanged(folderConfig) && (referenceFolder.isNotBlank() || baseBranch.isNotBlank())) {
                folderConfigSettings.addFolderConfig(
                    folderConfig.copy(
                        baseBranch = baseBranch,
                        referenceFolderPath = referenceFolder
                    )
                )
            }
        }

        // Always try to update configuration, but don't block dialog closure
        runInBackground("Snyk: updating configuration") {
            LanguageServerWrapper.getInstance(project).updateConfiguration(true)
        }
    }

    override fun doValidate(): ValidationInfo? {
        // Only validate components that have been changed by the user
        changedComponents.forEach { folderConfig ->
            val baseBranch = baseBranches[folderConfig]
            val refFolder = referenceFolders[folderConfig]

            if (baseBranch != null && refFolder != null) {
                val baseBranchSelected = !getSelectedItem(baseBranch).isNullOrBlank()
                val refFolderSelected = refFolder.text.isNotBlank()

                // Only validate if the user has made changes to this component
                if (hasComponentChanged(folderConfig) && !baseBranchSelected && !refFolderSelected) {
                    return ValidationInfo(
                        "Please select a base branch for ${baseBranch.name} or a reference folder",
                        baseBranch
                    )
                }
            }
        }
        return null
    }

    private fun getSelectedItem(baseBranch: ComboBox<String>) = baseBranch.selectedItem?.toString()
}
