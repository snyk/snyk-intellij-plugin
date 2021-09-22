package io.snyk.plugin.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.uiDesigner.core.Spacer
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykCliAuthenticationService
import io.snyk.plugin.services.SnykCliDownloaderService
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable
import io.snyk.plugin.ui.settings.ScanTypesPanel
import snyk.amplitude.AmplitudeExperimentService
import snyk.amplitude.api.ExperimentUser
import java.awt.Insets
import java.util.Objects.nonNull
import java.util.UUID
import java.util.function.Supplier
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.text.BadLocationException
import com.intellij.uiDesigner.core.GridConstraints as UIGridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager as UIGridLayoutManager

class SnykSettingsDialog(
    private val project: Project,
    applicationSettings: SnykApplicationSettingsStateService,
    snykProjectSettingsConfigurable: SnykProjectSettingsConfigurable
) {

    private val rootPanel = object : JPanel(), Disposable {
        override fun dispose() = Unit
    }

    private val tokenTextField = JBPasswordField()
    private val receiveTokenButton = JButton("Connect IDE to Snyk")
    private val customEndpointTextField = JTextField()
    private val organizationTextField: JTextField = JTextField()
    private val ignoreUnknownCACheckBox: JCheckBox = JCheckBox()
    private val usageAnalyticsCheckBox: JCheckBox = JCheckBox()
    private val crashReportingCheckBox = JCheckBox()
    private val additionalParametersTextField: JTextField = ExpandableTextField()
    private val scanTypesPanelOuter = ScanTypesPanel(project, rootPanel)
    private val scanTypesPanel = scanTypesPanelOuter.panel

    init {
        initializeUiComponents()
        initializeValidation()

        receiveTokenButton.isEnabled = !service<SnykCliDownloaderService>().isCliDownloading()

        ApplicationManager.getApplication().messageBus.connect(rootPanel)
            .subscribe(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC, object : SnykCliDownloadListener {
                override fun cliDownloadStarted() {
                    receiveTokenButton.isEnabled = false
                }

                override fun cliDownloadFinished(succeed: Boolean) {
                    receiveTokenButton.isEnabled = true
                }
            })

        receiveTokenButton.addActionListener {
            ApplicationManager.getApplication().invokeLater {
                snykProjectSettingsConfigurable.apply()
                val token = project.service<SnykCliAuthenticationService>().authenticate()
                tokenTextField.text = token

                val analytics = service<SnykAnalyticsService>()
                val userId = analytics.obtainUserId(token)
                analytics.setUserId(userId)

                service<AmplitudeExperimentService>().fetch(ExperimentUser(userId))
            }
        }

        if (nonNull(applicationSettings)) {
            tokenTextField.text = applicationSettings.token
            customEndpointTextField.text = applicationSettings.customEndpointUrl
            organizationTextField.text = applicationSettings.organization
            ignoreUnknownCACheckBox.isSelected = applicationSettings.ignoreUnknownCA
            usageAnalyticsCheckBox.isSelected = applicationSettings.usageAnalyticsEnabled
            crashReportingCheckBox.isSelected = applicationSettings.crashReportingEnabled

            additionalParametersTextField.text = applicationSettings.getAdditionalParameters(project)
        }
    }

    fun getRootPanel(): JComponent = rootPanel

    private fun initializeUiComponents() {
        rootPanel.layout = UIGridLayoutManager(5, 1, Insets(0, 0, 0, 0), -1, -1)

        /** General settings ------------------ */

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
                UIGridConstraints.FILL_HORIZONTAL,
                UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null,
                null, //Dimension(150, 200),
                null,
                0,
                false
            )
        )

        generalSettingsPanel.add(
            receiveTokenButton,
            UIGridConstraints(
                0,
                1,
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

        val tokenLabel = JLabel("Token:")
        generalSettingsPanel.add(
            tokenLabel,
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
            tokenTextField,
            UIGridConstraints(
                1,
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

        val customEndpointLabel = JLabel("Custom endpoint:")
        customEndpointLabel.labelFor = customEndpointTextField
        generalSettingsPanel.add(
            customEndpointLabel,
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

        generalSettingsPanel.add(
            customEndpointTextField,
            UIGridConstraints(
                2,
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
                3,
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
        organizationLabel.labelFor = organizationTextField
        generalSettingsPanel.add(
            organizationLabel,
            UIGridConstraints(
                4,
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
                4,
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

        /** Product selection ------------------ */

        val productSelectionPanel = JPanel(UIGridLayoutManager(5, 4, Insets(0, 0, 0, 0), -1, -1))
        productSelectionPanel.border = IdeBorderFactory.createTitledBorder("Product selection")

        rootPanel.add(
            productSelectionPanel,
            UIGridConstraints(
                1,
                0,
                1,
                1,
                UIGridConstraints.ANCHOR_NORTHWEST,
                UIGridConstraints.FILL_HORIZONTAL,
                UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        )

        productSelectionPanel.add(
            scanTypesPanel,
            UIGridConstraints(
                0,
                0,
                1,
                1,
                UIGridConstraints.ANCHOR_NORTHWEST,
                UIGridConstraints.FILL_NONE,
                UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false
            )
        )

        /** Project settings ------------------ */

        if (isProjectSettingsAvailable(project)) {
            val projectSettingsPanel = JPanel(UIGridLayoutManager(2, 3, Insets(0, 0, 0, 0), -1, -1))
            projectSettingsPanel.border = IdeBorderFactory.createTitledBorder("Project settings")

            rootPanel.add(
                projectSettingsPanel,
                UIGridConstraints(
                    2,
                    0,
                    1,
                    1,
                    UIGridConstraints.ANCHOR_CENTER,
                    UIGridConstraints.FILL_BOTH,
                    UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    UIGridConstraints.SIZEPOLICY_FIXED,
                    null,
                    null,
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
        }

        /** User experience ------------------ */

        val userExperiencePanel = JPanel(UIGridLayoutManager(5, 4, Insets(0, 0, 0, 0), -1, -1))
        userExperiencePanel.border = IdeBorderFactory.createTitledBorder("User experience")

        rootPanel.add(
            userExperiencePanel,
            UIGridConstraints(
                3,
                0,
                1,
                1,
                UIGridConstraints.ANCHOR_NORTHWEST,
                UIGridConstraints.FILL_HORIZONTAL,
                UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        )

        usageAnalyticsCheckBox.text = "Help us with anonymous usage analytics"
        userExperiencePanel.add(
            usageAnalyticsCheckBox,
            UIGridConstraints(
                0, 0, 1, 1,
                UIGridConstraints.ANCHOR_NORTHWEST,
                UIGridConstraints.FILL_NONE,
                UIGridConstraints.SIZEPOLICY_FIXED,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null, null, null, 0, false
            )
        )

        crashReportingCheckBox.text = "Allow automatically sending crash reports"
        userExperiencePanel.add(
            crashReportingCheckBox,
            UIGridConstraints(
                1, 0, 1, 1,
                UIGridConstraints.ANCHOR_NORTHWEST,
                UIGridConstraints.FILL_NONE,
                UIGridConstraints.SIZEPOLICY_FIXED,
                UIGridConstraints.SIZEPOLICY_FIXED,
                null, null, null, 0, false
            )
        )

        /** Spacer ------------------ */

        val generalSettingsSpacer = Spacer()
        rootPanel.add(
            generalSettingsSpacer,
            UIGridConstraints(
                4, 0, 1, 1,
                UIGridConstraints.ANCHOR_CENTER,
                UIGridConstraints.FILL_BOTH,
                UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_WANT_GROW,
                UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_WANT_GROW,
                null, null, null, 0, false
            )
        )
    }

    fun getToken(): String = try {
        tokenTextField.document.getText(0, tokenTextField.document.length)
    } catch (exception: BadLocationException) {
        ""
    }

    fun getOrganization(): String = organizationTextField.text

    fun getCustomEndpoint(): String = customEndpointTextField.text

    fun isIgnoreUnknownCA(): Boolean = ignoreUnknownCACheckBox.isSelected

    fun isUsageAnalyticsEnabled(): Boolean = usageAnalyticsCheckBox.isSelected

    fun isCrashReportingEnabled(): Boolean = crashReportingCheckBox.isSelected

    fun isScanTypeChanged(): Boolean = scanTypesPanel.isModified()

    fun saveScanTypeChanges() = scanTypesPanel.apply()

    fun getAdditionalParameters(): String = additionalParametersTextField.text

    private fun initializeValidation() {
        setupValidation(tokenTextField, "Invalid token", ::isTokenValid)
        setupValidation(customEndpointTextField, "Invalid custom endpoint URL", ::isUrlValid)
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
