package io.snyk.plugin.ui.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import snyk.common.ProductType

class ScanTypesPanel(cliScanComments: String? = null) {
  private val settings
    get() = pluginSettings()

  private var currentOSSScanEnabled = settings.ossScanEnable
  private var currentSnykCodeSecurityScanEnabled = settings.snykCodeSecurityIssuesScanEnable
  private var currentIaCScanEnabled = settings.iacScanEnabled
  private var codeSecurityCheckbox: JBCheckBox? = null
  private var snykCodeComment: JLabel? = null

  val scanTypesPanel =
    panel {
        getOSSCheckbox(cliScanComments)
        getIaCCheckbox()
        getCodeCheckbox()
      }
      .apply {
        name = "scanTypesPanel"
        border = JBUI.Borders.empty(2)
      }

  private fun Panel.getCodeCheckbox() {
    row {
      checkBox(ProductType.CODE_SECURITY.productSelectionName)
        .applyToComponent {
          name = text
          codeSecurityCheckbox = this
          label("").component.convertIntoHelpHintLabel(ProductType.CODE_SECURITY.description)
          isSelected = settings.snykCodeSecurityIssuesScanEnable
        }
        .actionListener { _, it ->
          val isSelected = it.isSelected
          if (canBeChanged(it)) {
            currentSnykCodeSecurityScanEnabled = isSelected
            snykCodeComment?.isVisible = shouldSnykCodeCommentBeVisible()
          }
        }
        .bindSelected(settings::snykCodeSecurityIssuesScanEnable)
    }
  }

  private fun Panel.getIaCCheckbox() {
    row {
      checkBox(ProductType.IAC.productSelectionName)
        .applyToComponent {
          name = text
          label("").component.convertIntoHelpHintLabel(ProductType.IAC.description)
        }
        .actionListener { _, it ->
          val isSelected = it.isSelected
          if (canBeChanged(it)) {
            currentIaCScanEnabled = isSelected
          }
        }
        .bindSelected(settings::iacScanEnabled)
    }
  }

  private fun Panel.getOSSCheckbox(cliScanComments: String?) {
    row {
      checkBox(ProductType.OSS.productSelectionName)
        .applyToComponent {
          name = text
          cliScanComments?.let { comment(it) }
          label("").component.convertIntoHelpHintLabel(ProductType.OSS.description)
        }
        .actionListener { _, it ->
          val isSelected = it.isSelected
          if (canBeChanged(it)) {
            // we need to change the settings in here in order for the validation to work pre-apply
            currentOSSScanEnabled = isSelected
          }
        }
        // bindSelected is needed to trigger isModified() and then apply() on the settings dialog
        // that this panel is rendered in
        // that way we trigger the re-rendering of the Tree Nodes
        .bindSelected(settings::ossScanEnable)
    }
  }

  private fun JLabel.convertIntoHelpHintLabel(text: String) {
    icon = AllIcons.General.ContextHelp
    addMouseListener(ShowHintMouseAdapter(this, text))
  }

  private var currentHint: Balloon? = null

  inner class ShowHintMouseAdapter(val component: Component, val text: String) : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent?) {
      currentHint?.hide()
      currentHint = SnykBalloonNotificationHelper.showInfoBalloonForComponent(text, component, true)
    }
  }

  private fun shouldSnykCodeCommentBeVisible() = codeSecurityCheckbox?.isSelected == true

  private fun canBeChanged(component: JBCheckBox): Boolean {
    val onlyOneEnabled =
      arrayOf(currentOSSScanEnabled, currentSnykCodeSecurityScanEnabled, currentIaCScanEnabled)
        .count { it } == 1

    if (onlyOneEnabled && !component.isSelected) {
      SnykBalloonNotificationHelper.showWarnBalloonForComponent(
        "At least one Scan type should be enabled",
        component,
      )
      component.isSelected = true
      return false
    }
    return true
  }

  fun reset() {
    currentOSSScanEnabled = settings.ossScanEnable
    currentSnykCodeSecurityScanEnabled = settings.snykCodeSecurityIssuesScanEnable
    currentIaCScanEnabled = settings.iacScanEnabled
    codeSecurityCheckbox?.isSelected = settings.snykCodeSecurityIssuesScanEnable
    snykCodeComment?.isVisible = shouldSnykCodeCommentBeVisible()
    scanTypesPanel.reset()
  }
}
