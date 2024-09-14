package io.snyk.plugin.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import org.jetbrains.concurrency.runAsync
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.FolderConfigSettings
import snyk.common.lsp.LanguageServerWrapper
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel


class BranchChooserComboBoxDialog(val project: Project) : DialogWrapper(true) {
    var comboBoxes: MutableList<ComboBox<String>> = mutableListOf()

    init {
        init()
        title = "Choose base branch for net-new issue scanning"
    }

    override fun createCenterPanel(): JComponent {
        val folderConfigs = service<FolderConfigSettings>().getAllForProject(project)
        folderConfigs.forEach {
            val comboBox = ComboBox(it.localBranches.sorted().toTypedArray())
            comboBox.selectedItem = it.baseBranch
            comboBox.name = it.folderPath
            comboBoxes.add(comboBox)
        }
        val gridBagLayout = GridBagLayout()
        val dialogPanel = JPanel(gridBagLayout)
        val gridBag = GridBag()
        gridBag.defaultFill = GridBagConstraints.HORIZONTAL
        gridBag.insets = JBUI.insets(20)
        comboBoxes.forEach {
            dialogPanel.add(JLabel("Base Branch for ${it.name}: "))
            dialogPanel.add(it, gridBag.nextLine())
        }
        return dialogPanel
    }

    override fun doOKAction() {
        execute()
        super.doOKAction()
    }

    fun execute() {
        val folderConfigSettings = service<FolderConfigSettings>()
        comboBoxes.forEach {
            val folderConfig: FolderConfig? = folderConfigSettings.getFolderConfig(it.name)
            if (folderConfig == null) {
                SnykBalloonNotificationHelper.showError(
                    "Unexpectedly cannot retrieve folder config for ${it.name} for base branch updating.",
                    project
                )
                return@forEach
            }

            val baseBranch = it.selectedItem!!.toString() // validation makes sure it is not null and not empty
            folderConfigSettings.addFolderConfig(folderConfig.copy(baseBranch = baseBranch))
        }
        runAsync {
            LanguageServerWrapper.getInstance().updateConfiguration()
        }
    }

    override fun doValidate(): ValidationInfo? {
        comboBoxes.forEach {
            if (it.selectedItem == null || it.selectedItem?.toString()?.isEmpty() == true) {
                return ValidationInfo("Please select a base branch for ${it.name}", it)
            }
        }
        return null
    }
}
