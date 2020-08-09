package io.snyk.plugin.ui.settings

import io.snyk.plugin.Utils.isUrlValid

import java.util.Objects.nonNull
import java.awt.{Dimension, Insets}

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.{ComponentValidator, ValidationInfo}
import com.intellij.ui.DocumentAdapter
import com.intellij.uiDesigner.core.{Spacer, GridConstraints => UIGridConstraints, GridLayoutManager => UIGridLayoutManager}
import javax.swing._
import javax.swing.event.DocumentEvent

class SettingsDialog(applicationSettings: SnykApplicationSettingsStateService,
  projectSettings: Option[SnykProjectSettingsStateService]) {

  private val customEndpointTextField = new JTextField() with Disposable {
    override def dispose(): Unit = {}
  }

  private val organizationTextField: JTextField = new JTextField()
  private val ignoreUnknownCACheckBox: JCheckBox = new JCheckBox()
  private val additionalParametersTextField: JTextField = new JTextField("")

  private val rootPanel: JPanel = new JPanel()

  setupUI()
  setupValidation()

  if (nonNull(applicationSettings)) {
    reset()
  }

  def getRootPanel: JComponent = rootPanel

  def reset(): Unit = {
    customEndpointTextField.setText(applicationSettings.getCustomEndpointUrl)
    organizationTextField.setText(applicationSettings.getOrganization)
    ignoreUnknownCACheckBox.setSelected(applicationSettings.isIgnoreUnknownCA)

    if (projectSettings.nonEmpty) {
      additionalParametersTextField.setText(projectSettings.get.getAdditionalParameters)
    }
  }

  private def setupUI(): Unit = {
    val gridLayoutManager = new UIGridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1)

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
      5,
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

    if (projectSettings.nonEmpty) {
      val additionalParametersLabel = new JLabel
      additionalParametersLabel.setText("Additional parameters:")

      rootPanel.add(additionalParametersLabel, new UIGridConstraints(
        3,
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

      rootPanel.add(additionalParametersTextField, new UIGridConstraints(
        3,
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
    }
  }

  private def setupValidation(): Unit = {
    new ComponentValidator(customEndpointTextField).withValidator(() => {

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

  def organization: String = organizationTextField.getText

  def customEndpoint: String = customEndpointTextField.getText

  def isIgnoreUnknownCA: Boolean = ignoreUnknownCACheckBox.isSelected

  def additionalParameters: String = additionalParametersTextField.getText
}
