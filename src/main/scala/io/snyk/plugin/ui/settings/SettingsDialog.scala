package io.snyk.plugin.ui.settings

import io.snyk.plugin.Utils.isUrlValid
import java.util.Objects.nonNull
import java.awt.{Dimension, Insets}

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.{ComponentValidator, ValidationInfo}
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.{DocumentAdapter, IdeBorderFactory}
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
  private val additionalParametersTextField: JTextField = new ExpandableTextField()

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
    val defaultTextFieldWidth = 500

    rootPanel.setLayout(
      new UIGridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1))

    val generalSettingsPanel = new JPanel(new UIGridLayoutManager(4, 3, new Insets(0, 0, 0, 0), -1, -1))
    generalSettingsPanel.setBorder(IdeBorderFactory.createTitledBorder("General settings"))

    rootPanel.add(
      generalSettingsPanel,
      new UIGridConstraints(
        0,
        0,
        1,
        1,
        UIGridConstraints.ANCHOR_CENTER,
        UIGridConstraints.FILL_BOTH,
        UIGridConstraints.SIZEPOLICY_CAN_SHRINK | UIGridConstraints.SIZEPOLICY_CAN_GROW,
        UIGridConstraints.SIZEPOLICY_CAN_SHRINK | UIGridConstraints.SIZEPOLICY_CAN_GROW,
        null,
        new Dimension(200, 200),
        null,
        0,
        false
      )
    )

    val customEndpointLabel = new JLabel("Custom endpoint:")

    generalSettingsPanel.add(
      customEndpointLabel,
      new UIGridConstraints(
        0,
        0,
        1,
        1,
        UIGridConstraints.ANCHOR_WEST,
        UIGridConstraints.FILL_NONE,
        UIGridConstraints.SIZEPOLICY_FIXED,
        UIGridConstraints.SIZEPOLICY_FIXED,
        null,
        new Dimension(120, 16),
        null,
        0,
        false
      )
    )

    generalSettingsPanel.add(
      customEndpointTextField,
      new UIGridConstraints(
        0,
        1,
        1,
        1,
        UIGridConstraints.ANCHOR_WEST,
        UIGridConstraints.SIZEPOLICY_FIXED,
        UIGridConstraints.SIZEPOLICY_FIXED,
        UIGridConstraints.SIZEPOLICY_FIXED,
        null,
        new Dimension(defaultTextFieldWidth, -1),
        null,
        0,
        false
      )
    )

    ignoreUnknownCACheckBox.setText("Ignore unknown CA")
    generalSettingsPanel.add(
      ignoreUnknownCACheckBox,
      new UIGridConstraints(
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
        false
      )
    )

    val organizationLabel = new JLabel("Organization:")
    generalSettingsPanel.add(
      organizationLabel,
      new UIGridConstraints(
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
        false
      )
    )

    generalSettingsPanel.add(
      organizationTextField,
      new UIGridConstraints(
        2,
        1,
        1,
        1,
        UIGridConstraints.ANCHOR_WEST,
        UIGridConstraints.SIZEPOLICY_FIXED,
        UIGridConstraints.SIZEPOLICY_FIXED,
        UIGridConstraints.SIZEPOLICY_FIXED,
        null,
        new Dimension(defaultTextFieldWidth, -1),
        null,
        0,
        false
      )
    )

    val generalSettingsSpacer = new Spacer
    generalSettingsPanel.add(
      generalSettingsSpacer,
      new UIGridConstraints(
        3,
        0,
        1,
        1,
        UIGridConstraints.ANCHOR_CENTER,
        UIGridConstraints.FILL_VERTICAL,
        1,
        UIGridConstraints.SIZEPOLICY_WANT_GROW,
        null,
        null,
        null,
        0,
        false
      )
    )

    customEndpointLabel.setLabelFor(customEndpointTextField)
    organizationLabel.setLabelFor(organizationTextField)

    if (projectSettings.nonEmpty) {
      val projectSettingsPanel = new JPanel
      projectSettingsPanel.setLayout(
        new UIGridLayoutManager(2, 3, new Insets(0, 0, 0, 0), -1, -1))
      projectSettingsPanel.setBorder(IdeBorderFactory.createTitledBorder("Project settings"))

      rootPanel.add(
        projectSettingsPanel,
        new UIGridConstraints(
          1,
          0,
          1,
          1,
          UIGridConstraints.ANCHOR_CENTER,
          UIGridConstraints.FILL_BOTH,
          UIGridConstraints.SIZEPOLICY_CAN_SHRINK | UIGridConstraints.SIZEPOLICY_CAN_GROW,
          UIGridConstraints.SIZEPOLICY_CAN_SHRINK | UIGridConstraints.SIZEPOLICY_CAN_GROW,
          null,
          new Dimension(200, 200),
          null,
          0,
          false
        )
      )

      val additionalParametersLabel = new JLabel("Additional parameters:")
      projectSettingsPanel.add(
        additionalParametersLabel,
        new UIGridConstraints(
          0,
          0,
          1,
          1,
          UIGridConstraints.ANCHOR_WEST,
          UIGridConstraints.FILL_NONE,
          UIGridConstraints.SIZEPOLICY_FIXED,
          UIGridConstraints.SIZEPOLICY_FIXED,
          null,
          new Dimension(120, 16),
          null,
          0,
          false
        )
      )

      projectSettingsPanel.add(
        additionalParametersTextField,
        new UIGridConstraints(
          0,
          1,
          1,
          1,
          UIGridConstraints.ANCHOR_WEST,
          UIGridConstraints.SIZEPOLICY_FIXED,
          UIGridConstraints.SIZEPOLICY_FIXED,
          UIGridConstraints.SIZEPOLICY_FIXED,
          null,
          new Dimension(defaultTextFieldWidth, -1),
          null,
          0,
          false
        )
      )

      additionalParametersLabel.setLabelFor(additionalParametersTextField)

      val projectSettingsSpacer = new Spacer
      projectSettingsPanel.add(
        projectSettingsSpacer,
        new UIGridConstraints(
          1,
          0,
          1,
          1,
          UIGridConstraints.ANCHOR_CENTER,
          UIGridConstraints.FILL_VERTICAL,
          1,
          UIGridConstraints.SIZEPOLICY_WANT_GROW,
          null,
          null,
          null,
          0,
          false
        )
      )

      val emptyPanel = new JPanel
      emptyPanel.setLayout(
        new UIGridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1))

      rootPanel.add(
        emptyPanel,
        new UIGridConstraints(
          2,
          0,
          1,
          1,
          UIGridConstraints.ANCHOR_CENTER,
          UIGridConstraints.FILL_BOTH,
          UIGridConstraints.SIZEPOLICY_CAN_SHRINK | UIGridConstraints.SIZEPOLICY_CAN_GROW,
          UIGridConstraints.SIZEPOLICY_CAN_SHRINK | UIGridConstraints.SIZEPOLICY_CAN_GROW,
          null,
          new Dimension(200, 200),
          null,
          0,
          false
        )
      )
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
