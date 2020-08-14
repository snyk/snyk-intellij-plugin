package io.snyk.plugin.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.uiDesigner.core.Spacer
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykProjectSettingsStateService
import java.awt.Dimension
import java.awt.Insets
import java.util.Objects.nonNull
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.DocumentEvent
import com.intellij.uiDesigner.core.GridConstraints as UIGridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager as UIGridLayoutManager

class SettingsDialog(private val project: Project, private val applicationSettings: SnykApplicationSettingsStateService) {

    private val customEndpointTextField = UrlValidationTextField()
    private val organizationTextField: JTextField = JTextField()
    private val ignoreUnknownCACheckBox: JCheckBox = JCheckBox()
    private val additionalParametersTextField: JTextField = ExpandableTextField()

    private val rootPanel: JPanel = JPanel()

    init {
        setupUI()

        if (nonNull(applicationSettings)) {
            reset()
        }
    }

    fun getRootPanel(): JComponent = rootPanel

    fun reset() {
        customEndpointTextField.text = applicationSettings.getCustomEndpointUrl()
        organizationTextField.text = applicationSettings.getOrganization()
        ignoreUnknownCACheckBox.isSelected = applicationSettings.isIgnoreUnknownCA()

        if (isProjectSettingsAvailable(project)) {
            additionalParametersTextField.text =
                project.service<SnykProjectSettingsStateService>().getAdditionalParameters()
        }
    }

    private fun setupUI() {
        val defaultTextFieldWidth = 500

        rootPanel.layout = UIGridLayoutManager(3, 1, Insets(0, 0, 0, 0), -1, -1)

        val generalSettingsPanel = JPanel(UIGridLayoutManager(4, 3, Insets(0, 0, 0, 0), -1, -1))
        generalSettingsPanel.border = IdeBorderFactory.createTitledBorder("General settings")

        rootPanel.add(
            generalSettingsPanel,
            UIGridConstraints(
                0,
            0,
            1,
            1,
            UIGridConstraints.ANCHOR_CENTER,
            UIGridConstraints.FILL_BOTH,
            UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
        UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
        null,
        Dimension(200, 200),
        null,
        0,
        false
        ))

        val customEndpointLabel = JLabel("Custom endpoint:")
        generalSettingsPanel.add(
            customEndpointLabel,
            UIGridConstraints(
                0,
            0,
            1,
            1,
            UIGridConstraints.ANCHOR_WEST,
            UIGridConstraints.FILL_NONE,
            UIGridConstraints.SIZEPOLICY_FIXED,
            UIGridConstraints.SIZEPOLICY_FIXED,
            null,
            Dimension(120, 16),
        null,
        0,
        false
        ))

        generalSettingsPanel.add(
            customEndpointTextField,
            UIGridConstraints(
                0,
            1,
            1,
            1,
            UIGridConstraints.ANCHOR_WEST,
            UIGridConstraints.SIZEPOLICY_FIXED,
            UIGridConstraints.SIZEPOLICY_FIXED,
            UIGridConstraints.SIZEPOLICY_FIXED,
            null,
            Dimension(defaultTextFieldWidth, -1),
        null,
        0,
        false
        ))

        ignoreUnknownCACheckBox.text = "Ignore unknown CA"
        generalSettingsPanel.add(
            ignoreUnknownCACheckBox,
            UIGridConstraints(
                1,
            1,
            1,
            1,
            UIGridConstraints.ANCHOR_WEST,
            UIGridConstraints.FILL_NONE,
            UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
            UIGridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false
        ))

        val organizationLabel = JLabel("Organization:")
        generalSettingsPanel.add(
            organizationLabel,
            UIGridConstraints(
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
        ))

        generalSettingsPanel.add(
            organizationTextField,
            UIGridConstraints(
                2,
            1,
            1,
            1,
            UIGridConstraints.ANCHOR_WEST,
            UIGridConstraints.SIZEPOLICY_FIXED,
            UIGridConstraints.SIZEPOLICY_FIXED,
            UIGridConstraints.SIZEPOLICY_FIXED,
            null,
            Dimension(defaultTextFieldWidth, -1),
        null,
        0,
        false
        ))

        val generalSettingsSpacer = Spacer()
        generalSettingsPanel.add(
            generalSettingsSpacer,
            UIGridConstraints(
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
            ))

        customEndpointLabel.labelFor = customEndpointTextField
        organizationLabel.labelFor = organizationTextField

        if (isProjectSettingsAvailable(project)) {
            val projectSettingsPanel = JPanel(UIGridLayoutManager(2, 3, Insets(0, 0, 0, 0), -1, -1))
            projectSettingsPanel.border = IdeBorderFactory.createTitledBorder("Project settings")

            rootPanel.add(
                projectSettingsPanel,
                UIGridConstraints(
                    1,
                    0,
                    1,
                    1,
                    UIGridConstraints.ANCHOR_CENTER,
                    UIGridConstraints.FILL_BOTH,
                    UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    null,
                    Dimension(200, 200),
                    null,
                    0,
                    false
                ))

            val additionalParametersLabel = JLabel("Additional parameters:")
            projectSettingsPanel.add(
                additionalParametersLabel,
                UIGridConstraints(
                    0,
                    0,
                    1,
                    1,
                    UIGridConstraints.ANCHOR_WEST,
                    UIGridConstraints.FILL_NONE,
                    UIGridConstraints.SIZEPOLICY_FIXED,
                    UIGridConstraints.SIZEPOLICY_FIXED,
                    null,
                    Dimension(120, 16),
                    null,
                    0,
                    false
                ))

            projectSettingsPanel.add(
                additionalParametersTextField,
                UIGridConstraints(
                    0,
                1,
                1,
                1,
                UIGridConstraints.ANCHOR_WEST,
                UIGridConstraints.SIZEPOLICY_FIXED,
                UIGridConstraints.SIZEPOLICY_FIXED,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null,
                Dimension(defaultTextFieldWidth, -1),
            null,
            0,
            false
            ))

            additionalParametersLabel.labelFor = additionalParametersTextField

            val projectSettingsSpacer = Spacer()
            projectSettingsPanel.add(
                projectSettingsSpacer,
                UIGridConstraints(
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
                ))

            val emptyPanel = JPanel(UIGridLayoutManager(1, 1, Insets(0, 0, 0, 0), -1, -1))

            rootPanel.add(
                emptyPanel,
                UIGridConstraints(
                    2,
                    0,
                    1,
                    1,
                    UIGridConstraints.ANCHOR_CENTER,
                    UIGridConstraints.FILL_BOTH,
                    UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    null,
                    Dimension(200, 200),
                    null,
                    0,
                    false
                ))
        }
    }

    fun getOrganization(): String = organizationTextField.text

    fun getCustomEndpoint(): String = customEndpointTextField.text

    fun isIgnoreUnknownCA(): Boolean = ignoreUnknownCACheckBox.isSelected

    fun getAdditionalParameters(): String = additionalParametersTextField.text
}

class UrlValidationTextField : JTextField(""), Disposable {

    init {
        ComponentValidator(this).withValidator(Supplier {
            val validationInfo: ValidationInfo = if (!isUrlValid(text)) {
                ValidationInfo("Invalid custom enpoint URL.", this@UrlValidationTextField)
            } else {
                ValidationInfo("")
            }

            validationInfo
        })

        this.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                ComponentValidator.getInstance(this@UrlValidationTextField).ifPresent {
                    it.revalidate()
                }
            }
        })
    }

    override fun dispose() {
    }
}
