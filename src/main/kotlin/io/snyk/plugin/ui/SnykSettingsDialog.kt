package io.snyk.plugin.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.layout.GrowPolicy
import com.intellij.ui.layout.panel
import com.intellij.uiDesigner.core.Spacer
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykCliAuthenticationService
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable
import io.snyk.plugin.snykcode.core.SnykCodeParams
import io.snyk.plugin.ui.settings.ScanTypesPanel
import java.awt.Dimension
import java.awt.Insets
import java.util.*
import java.util.Objects.nonNull
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.text.BadLocationException
import com.intellij.uiDesigner.core.GridConstraints as UIGridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager as UIGridLayoutManager

class SnykSettingsDialog(
    private val project: Project,
    applicationSettings: SnykApplicationSettingsStateService,
    snykProjectSettingsConfigurable: SnykProjectSettingsConfigurable
) {

    private val tokenTextField = JBPasswordField()
    private val tokenAuthenticateButton = JButton("Authenticate")
    private val customEndpointTextField = JTextField()
    private val organizationTextField: JTextField = JTextField()
    private val ignoreUnknownCACheckBox: JCheckBox = JCheckBox()
    private val additionalParametersTextField: JTextField = ExpandableTextField()
    private val scanTypesPanel = ScanTypesPanel().panel

    private val filteringPanel = panel {
        row {
            label("Filter by minimal Severity level:")
            comboBox(
                DefaultComboBoxModel(arrayOf("low", "medium", "high")),
                { getApplicationSettingsStateService().filterMinimalSeverity },
                { getApplicationSettingsStateService().filterMinimalSeverity = it!! },
                renderer = SimpleListCellRenderer.create("low") { it }
            )
        }
    }

    private val deepcodeTokenPanel = panel {
        row {
            label("Deepcode.ai token:")
            textField(
                { getApplicationSettingsStateService().deepcodeToken },
                {
                    getApplicationSettingsStateService().deepcodeToken = it
                    SnykCodeParams.instance.sessionToken = it
                }
            ).growPolicy(GrowPolicy.MEDIUM_TEXT)
        }
    }

    private val rootPanel = object : JPanel(), Disposable {
        override fun dispose() = Unit
    }

    init {
        initializeUiComponents()
        initializeValidation()

        tokenAuthenticateButton.addActionListener {
            ApplicationManager.getApplication().invokeLater {
                snykProjectSettingsConfigurable.apply()
                val token = service<SnykCliAuthenticationService>().authenticate()
                tokenTextField.text = token
            }
        }

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
        rootPanel.layout = UIGridLayoutManager(5, 1, Insets(0, 0, 0, 0), -1, -1)

        val generalSettingsPanel = JPanel(UIGridLayoutManager(5, 4, Insets(0, 0, 0, 0), -1, -1))
        generalSettingsPanel.border = IdeBorderFactory.createTitledBorder("General settings")

        rootPanel.add(
            generalSettingsPanel,
            UIGridConstraints(
                0,
                0,
                1,
                1,
                UIGridConstraints.ANCHOR_NORTHWEST,
                UIGridConstraints.FILL_BOTH,
                UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                Dimension(150, 200),
                null,
                0,
                false
            )
        )

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
                null,
                null,
                0,
                false
            )
        )

        generalSettingsPanel.add(
            tokenTextField,
            UIGridConstraints(
                0,
                1,
                1,
                1,
                UIGridConstraints.ANCHOR_WEST,
                UIGridConstraints.FILL_HORIZONTAL,
                UIGridConstraints.SIZEPOLICY_WANT_GROW,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            ))

        generalSettingsPanel.add(
            tokenAuthenticateButton,
            UIGridConstraints(
                0,
                2,
                1,
                1,
                UIGridConstraints.ANCHOR_EAST,
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
                null,
                null,
                0,
                false
            )
        )

        generalSettingsPanel.add(
            customEndpointTextField,
            UIGridConstraints(
                1,
                1,
                1,
                2,
                UIGridConstraints.ANCHOR_WEST,
                UIGridConstraints.FILL_HORIZONTAL,
                UIGridConstraints.SIZEPOLICY_WANT_GROW,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        )

        ignoreUnknownCACheckBox.text = "Ignore unknown CA"
        generalSettingsPanel.add(
            ignoreUnknownCACheckBox,
            UIGridConstraints(
                2,
                1,
                1,
                2,
                UIGridConstraints.ANCHOR_WEST,
                UIGridConstraints.FILL_NONE,
                UIGridConstraints.SIZEPOLICY_WANT_GROW,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        )

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
            )
        )

        generalSettingsPanel.add(
            organizationTextField,
            UIGridConstraints(
                3,
                1,
                1,
                2,
                UIGridConstraints.ANCHOR_WEST,
                UIGridConstraints.FILL_HORIZONTAL,
                UIGridConstraints.SIZEPOLICY_WANT_GROW,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        )

        rootPanel.add(
            deepcodeTokenPanel,
            UIGridConstraints(
                1,
                0,
                1,
                1,
                UIGridConstraints.ANCHOR_WEST,
                UIGridConstraints.FILL_NONE,
                UIGridConstraints.SIZEPOLICY_WANT_GROW,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null,
                Dimension(120, 16),
                null,
                0,
                false
            )
        )

/*
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
            )
        )
*/

        customEndpointLabel.labelFor = customEndpointTextField
        organizationLabel.labelFor = organizationTextField

        rootPanel.add(
            scanTypesPanel,
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
            )
        )

        rootPanel.add(
            filteringPanel,
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
            )
        )

        if (isProjectSettingsAvailable(project)) {
            val projectSettingsPanel = JPanel(UIGridLayoutManager(2, 3, Insets(0, 0, 0, 0), -1, -1))
            projectSettingsPanel.border = IdeBorderFactory.createTitledBorder("Project settings")

            rootPanel.add(
                projectSettingsPanel,
                UIGridConstraints(
                    4,
                    0,
                    1,
                    1,
                    UIGridConstraints.ANCHOR_CENTER,
                    UIGridConstraints.FILL_BOTH,
                    UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    null,
                    Dimension(150, 200),
                    null,
                    0,
                    false
                )
            )

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
                    null,
                    null,
                    0,
                    false
                )
            )

            projectSettingsPanel.add(
                additionalParametersTextField,
                UIGridConstraints(
                    0,
                    1,
                    1,
                    1,
                    UIGridConstraints.ANCHOR_WEST,
                    UIGridConstraints.FILL_HORIZONTAL,
                    UIGridConstraints.SIZEPOLICY_WANT_GROW,
                    UIGridConstraints.SIZEPOLICY_FIXED,
                    null,
                    null,
                    null,
                    0,
                    false
                )
            )

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
                )
            )

/*
            val emptyPanel = JPanel(UIGridLayoutManager(1, 1, Insets(0, 0, 0, 0), -1, -1))

            rootPanel.add(
                emptyPanel,
                UIGridConstraints(
                    3,
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
*/
        }
    }

    fun getToken(): String = try {
        tokenTextField.document.getText(0, tokenTextField.document.length)
    } catch (exception: BadLocationException) {
        ""
    }

    fun getOrganization(): String = organizationTextField.text

    fun getCustomEndpoint(): String = customEndpointTextField.text

    fun isIgnoreUnknownCA(): Boolean = ignoreUnknownCACheckBox.isSelected

    fun isScanTypeChanged(): Boolean = scanTypesPanel.isModified()

    fun saveScanTypeChanges() = scanTypesPanel.apply()

    fun isFilteringChanged(): Boolean = filteringPanel.isModified()

    fun saveFilteringChanges() = filteringPanel.apply()

    fun isDeepcodeTokenChanged(): Boolean = deepcodeTokenPanel.isModified()

    fun saveDeepcodeTokenChanges() = deepcodeTokenPanel.apply()

    fun getAdditionalParameters(): String = additionalParametersTextField.text

    private fun initializeValidation() {
        setupValidation(tokenTextField, "Invalid token", ::isTokenValid)
        setupValidation(customEndpointTextField, "Invalid custom enpoint URL", ::isUrlValid)
    }

    private fun setupValidation(textField: JTextField, message: String, isValidText: (sourceStr: String?) -> Boolean) {
        ComponentValidator(rootPanel).withValidator(Supplier<ValidationInfo?> {
            val validationInfo: ValidationInfo = if (!isValidText(textField.text)) {
                ValidationInfo(message, textField)
            } else {
                ValidationInfo("")
            }


            validationInfo
        }).installOn(textField)


        textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                ComponentValidator.getInstance(textField).ifPresent {
                    it.revalidate()
                }
            }
        })
    }

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
