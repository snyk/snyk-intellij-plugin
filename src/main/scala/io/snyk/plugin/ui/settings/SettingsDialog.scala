package io.snyk.plugin.ui.settings

import java.net.URL

import com.intellij.openapi.ui.{ComponentValidator, ValidationInfo}
import javax.swing.{JCheckBox, JComponent, JLabel, JPanel, JTextField}
import java.awt.{Dimension, Insets}

import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.uiDesigner.core.{GridConstraints => UIGridConstraints, GridLayoutManager => UIGridLayoutManager}
import com.intellij.uiDesigner.core.Spacer
import javax.swing.event.DocumentEvent

class SettingsDialog(project: Project) {

  private val persistentState: SnykPersistentStateComponent = SnykPersistentStateComponent.getInstance(project)

  private val customEndpointTextField: JTextField = new JTextField()
  private val organizationTextField: JTextField = new JTextField()
  private val ignoreUnknownCACheckBox: JCheckBox = new JCheckBox()

  private val rootPanel: JPanel = new JPanel()

  setupUI()
  setupValidation()

  if (persistentState != null) {
    reset()
  }

  def getRootPanel: JComponent = rootPanel

  def apply(): Unit = {
    val customEndpoint = customEndpointTextField.getText

    if (!isUrlValid(customEndpoint)) {
      return
    }

    persistentState.setCustomEndpointUrl(customEndpoint)
    persistentState.setOrganization(organizationTextField.getText)
    persistentState.setIgnoreUnknownCA(ignoreUnknownCACheckBox.isSelected)
  }

  def reset(): Unit = {
    customEndpointTextField.setText(persistentState.customEndpointUrl)
    organizationTextField.setText(persistentState.organization)
    ignoreUnknownCACheckBox.setSelected(persistentState.isIgnoreUnknownCA)
  }

  def isModified: Boolean =
     isCustomEndpointModified || isOrganizationModified || isIgnoreUnknownCAModified

  private def isCustomEndpointModified =
    customEndpointTextField.getText != persistentState.customEndpointUrl

  private def isOrganizationModified =
    organizationTextField.getText() != persistentState.organization

  private def isIgnoreUnknownCAModified =
    ignoreUnknownCACheckBox.isSelected != persistentState.isIgnoreUnknownCA

  private def setupUI(): Unit = {
    val gridLayoutManager = new UIGridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1)

    rootPanel.setLayout(gridLayoutManager)

    val customEndpointLabel = new JLabel
    customEndpointLabel.setText("Custom endpoint:")

    rootPanel.add(customEndpointLabel, new UIGridConstraints(
      0,
      0,
      1,
      1,
      UIGridConstraints.ANCHOR_WEST,
      UIGridConstraints.FILL_NONE,
      UIGridConstraints.SIZEPOLICY_FIXED,
      UIGridConstraints.SIZEPOLICY_FIXED,
      null,
      new Dimension(110, 16),
      null,
      0,
      false))

    val spacer = new Spacer()

    rootPanel.add(spacer, new UIGridConstraints(
      4,
      0,
      1,
      2,
      UIGridConstraints.ANCHOR_CENTER,
      UIGridConstraints.FILL_VERTICAL,
      1,
      UIGridConstraints.SIZEPOLICY_WANT_GROW,
      null,
      new Dimension(110, 14),
      null,
      0,
      false))

    rootPanel.add(customEndpointTextField, new UIGridConstraints(
      0,
      1,
      1,
      1,
      UIGridConstraints.ANCHOR_WEST,
      UIGridConstraints.FILL_HORIZONTAL,
      UIGridConstraints.SIZEPOLICY_WANT_GROW,
      UIGridConstraints.SIZEPOLICY_FIXED,
      null,
      new Dimension(150, -1),
      null,
      0,
      false))

    val organizationLabel = new JLabel
    organizationLabel.setText("Organization:")

    rootPanel.add(organizationLabel, new UIGridConstraints(
      2,
      0,
      1,
      1,
      UIGridConstraints.ANCHOR_WEST,
      UIGridConstraints.FILL_NONE,
      UIGridConstraints.SIZEPOLICY_FIXED,
      UIGridConstraints.SIZEPOLICY_FIXED,
      null,
      null,
      null,
      0,
      false))
    rootPanel.add(organizationTextField, new UIGridConstraints(
      2,
      1,
      1,
      1,
      UIGridConstraints.ANCHOR_WEST,
      UIGridConstraints.FILL_HORIZONTAL,
      UIGridConstraints.SIZEPOLICY_WANT_GROW,
      UIGridConstraints.SIZEPOLICY_FIXED,
      null,
      new Dimension(150, -1),
      null,
      0,
      false))

    ignoreUnknownCACheckBox.setText("Ignore unknown CA")

    rootPanel.add(ignoreUnknownCACheckBox, new UIGridConstraints(
      1,
      1,
      1,
      1,
      UIGridConstraints.ANCHOR_WEST,
      UIGridConstraints.FILL_NONE,
      UIGridConstraints.SIZEPOLICY_CAN_SHRINK | UIGridConstraints.SIZEPOLICY_CAN_GROW,
      UIGridConstraints.SIZEPOLICY_FIXED,
      null,
      null,
      null,
      0,
      false))
  }

  private def setupValidation(): Unit = {
    new ComponentValidator(project).withValidator(() => {

      if (isUrlValid(customEndpointTextField.getText)) {
        null
      } else {
        new ValidationInfo("Invalid custom enpoint URL.", customEndpointTextField)
      }
    }).installOn(customEndpointTextField)

    customEndpointTextField.getDocument.addDocumentListener(new DocumentAdapter() {
      protected def textChanged(documentEvent: DocumentEvent): Unit =
        ComponentValidator
          .getInstance(customEndpointTextField)
          .ifPresent((componentValidator: ComponentValidator) => componentValidator.revalidate())
    })
  }

  private def isUrlValid(url: String): Boolean = try {
    if (url.nonEmpty) {
      new URL(url).toURI

      true
    } else {
      true
    }
  } catch {
    case _: Throwable => false
  }
}
