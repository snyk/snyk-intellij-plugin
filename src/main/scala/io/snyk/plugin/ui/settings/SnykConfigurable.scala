package io.snyk.plugin.ui.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

case class SnykConfigurable(project: Project) extends SearchableConfigurable {

  private val settingsDialog: SettingsDialog = new SettingsDialog(project)

  override def getId: String = "preference.SnykConfigurable"

  override def getDisplayName: String = "Snyk"

  override def createComponent(): JComponent = {
    settingsDialog.getRootPanel
  }

  override def isModified: Boolean = settingsDialog.isModified

  override def apply(): Unit = settingsDialog.apply
}
