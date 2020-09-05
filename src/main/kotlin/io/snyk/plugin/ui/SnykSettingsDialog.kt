package io.snyk.plugin.ui

import com.intellij.openapi.Disposable
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
import java.awt.Dimension
import java.awt.Insets
import java.util.*
import java.util.Objects.nonNull
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.DocumentEvent
import com.intellij.uiDesigner.core.GridConstraints as UIGridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager as UIGridLayoutManager

class SnykSettingsDialog(
    private val project: Project,
    applicationSettings: SnykApplicationSettingsStateService) {

    private val tokenTextField = ValidationTextField("Invalid token", ::isTokenValid)
    private val customEndpointTextField = ValidationTextField("Invalid custom enpoint URL", ::isUrlValid)
    private val organizationTextField: JTextField = JTextField()
    private val ignoreUnknownCACheckBox: JCheckBox = JCheckBox()
    private val additionalParametersTextField: JTextField = ExpandableTextField()

    private val rootPanel: JPanel = JPanel()

    init {
        initializeUiComponents()

        if (nonNull(applicationSettings)) {
            tokenTextField.text = applicationSettings.token
            customEndpointTextField.text = applicationSettings.customEndpointUrl
            organizationTextField.text = applicationSettings.organization
            ignoreUnknownCACheckBox.isSelected = applicationSettings.ignoreUnknownCA

            additionalParametersTextField.text = applicationSettings.getAdditionalParameters(project)
        }
    }

    fun getRootPanel(): JComponent = rootPanel

    private fun initializeUiComponents() {
        val defaultTextFieldWidth = 500

        rootPanel.layout = UIGridLayoutManager(4, 1, Insets(0, 0, 0, 0), -1, -1)

        val generalSettingsPanel = JPanel(UIGridLayoutManager(5, 3, Insets(0, 0, 0, 0), -1, -1))
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

        val tokenLabel = JLabel("Token:")
        generalSettingsPanel.add(
            tokenLabel,
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
            tokenTextField,
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

        val customEndpointLabel = JLabel("Custom endpoint:")
        generalSettingsPanel.add(
            customEndpointLabel,
            UIGridConstraints(
                1,
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
                1,
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
                2,
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
                false
            ))

        generalSettingsPanel.add(
            organizationTextField,
            UIGridConstraints(
                3,
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
                4,
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

    fun getToken(): String = tokenTextField.text

    fun getOrganization(): String = organizationTextField.text

    fun getCustomEndpoint(): String = customEndpointTextField.text

    fun isIgnoreUnknownCA(): Boolean = ignoreUnknownCACheckBox.isSelected

    fun getAdditionalParameters(): String = additionalParametersTextField.text

    private fun isTokenValid(token: String?): Boolean {
        if (token == null || token.isEmpty()) {
            return true
        }

        return try {
            UUID.fromString(token)

            true
        } catch (exception: IllegalArgumentException) {
            false
        }
    }
}

class ValidationTextField(val message: String, isValidText: (sourceStr: String?) -> Boolean) : JTextField(""), Disposable {

    init {
        ComponentValidator(this).withValidator(Supplier<ValidationInfo?> {
            val validationInfo: ValidationInfo = if (!isValidText(text)) {
                ValidationInfo(message, this@ValidationTextField)
            } else {
                ValidationInfo("")
            }

            validationInfo
        }).installOn(this)

        this.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                ComponentValidator.getInstance(this@ValidationTextField).ifPresent {
                    it.revalidate()
                }
            }
        })
    }

    override fun dispose() {
    }
}
