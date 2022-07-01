package io.snyk.plugin.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.Alarm
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.UI
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.getAmplitudeExperimentService
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getSnykAnalyticsService
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable
import io.snyk.plugin.ui.settings.ScanTypesPanel
import io.snyk.plugin.ui.settings.SeveritiesEnablementPanel
import snyk.SnykBundle
import snyk.amplitude.api.ExperimentUser
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
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

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, rootPanel)

    private val tokenTextField = JBPasswordField()
    private val receiveTokenButton = JButton("Connect IDE to Snyk")
    private val customEndpointTextField = JTextField()
    private val organizationTextField: JTextField = JTextField()
    private val ignoreUnknownCACheckBox: JCheckBox = JCheckBox()
    private val usageAnalyticsCheckBox: JCheckBox = JCheckBox()
    private val crashReportingCheckBox = JCheckBox()
    private val additionalParametersTextField: JTextField = ExpandableTextField()

    private val scanTypesPanelOuter = ScanTypesPanel(project, rootPanel)
    private val codeAlertPanel = scanTypesPanelOuter.codeAlertPanel
    private val scanTypesPanel = scanTypesPanelOuter.panel

    private val severityEnablementPanel = SeveritiesEnablementPanel().panel

    private val enableAutomaticCLIUpdateCheckbox: JCheckBox = JCheckBox()
    private val cliPathTextBoxWithFileBrowser = TextFieldWithBrowseButton()

    init {
        initializeUiComponents()
        initializeValidation()

        receiveTokenButton.isEnabled = !getSnykCliDownloaderService().isCliDownloading()

        ApplicationManager.getApplication().messageBus.connect(rootPanel)
            .subscribe(
                SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
                object : SnykCliDownloadListener {
                    override fun cliDownloadStarted() {
                        receiveTokenButton.isEnabled = false
                    }

                    override fun cliDownloadFinished(succeed: Boolean) {
                        receiveTokenButton.isEnabled = true
                    }
                }
            )

        receiveTokenButton.addActionListener {
            ApplicationManager.getApplication().invokeLater {
                snykProjectSettingsConfigurable.apply()
                val token = getSnykCliAuthenticationService(project)?.authenticate() ?: ""
                tokenTextField.text = token

                val analytics = getSnykAnalyticsService()
                val userId = analytics.obtainUserId(token)
                analytics.setUserId(userId)

                getAmplitudeExperimentService().fetch(ExperimentUser(userId))
            }
        }

        if (nonNull(applicationSettings)) {
            tokenTextField.text = applicationSettings.token
            customEndpointTextField.text = applicationSettings.customEndpointUrl
            organizationTextField.text = applicationSettings.organization
            ignoreUnknownCACheckBox.isSelected = applicationSettings.ignoreUnknownCA
            usageAnalyticsCheckBox.isSelected = applicationSettings.usageAnalyticsEnabled
            crashReportingCheckBox.isSelected = applicationSettings.crashReportingEnabled
            enableAutomaticCLIUpdateCheckbox.isSelected = applicationSettings.automaticCLIUpdatesEnabled

            cliPathTextBoxWithFileBrowser.text = applicationSettings.cliPath
            additionalParametersTextField.text = applicationSettings.getAdditionalParameters(project)
        }
    }

    fun getRootPanel(): JComponent = rootPanel

    // We have to do background task run through Alarm on Alarm.ThreadToUse.POOLED_THREAD due to next (Idea?) bug:
    // Creation of Task.Backgroundable under another Task.Backgroundable does not work for Settings dialog,
    // it postpones inner Background task execution till Setting dialog exit
    fun runBackgroundable(runnable: () -> Unit, delayMillis: Int = 10) {
        if (!alarm.isDisposed) {
            alarm.addRequest(runnable, delayMillis)
        }
    }

    private fun initializeUiComponents() {
        rootPanel.layout = UIGridLayoutManager(10, 1, Insets(0, 0, 0, 0), -1, -1)

        /** General settings ------------------ */

        val generalSettingsPanel = JPanel(UIGridLayoutManager(5, 4, Insets(0, 0, 0, 0), -1, -1))
        generalSettingsPanel.border = IdeBorderFactory.createTitledBorder("General settings")

        rootPanel.add(
            generalSettingsPanel,
            baseGridConstraints(
                row = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0
            )
        )

        generalSettingsPanel.add(
            receiveTokenButton,
            baseGridConstraintsAnchorWest(
                row = 0,
                column = 1,
                indent = 0
            )
        )

        val tokenLabel = JLabel("Token:")
        generalSettingsPanel.add(
            tokenLabel,
            baseGridConstraintsAnchorWest(
                row = 1,
                indent = 0
            )
        )

        generalSettingsPanel.add(
            tokenTextField,
            baseGridConstraints(
                row = 1,
                column = 1,
                colSpan = 3,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0
            )
        )

        val customEndpointLabel = JLabel("Custom endpoint:")
        customEndpointLabel.labelFor = customEndpointTextField
        generalSettingsPanel.add(
            customEndpointLabel,
            baseGridConstraintsAnchorWest(
                row = 2,
                indent = 0
            )
        )

        generalSettingsPanel.add(
            customEndpointTextField,
            baseGridConstraints(
                row = 2,
                column = 1,
                colSpan = 3,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0
            )
        )

        ignoreUnknownCACheckBox.text = "Ignore unknown CA"
        generalSettingsPanel.add(
            ignoreUnknownCACheckBox,
            baseGridConstraints(
                row = 3,
                column = 1,
                colSpan = 3,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0
            )
        )

        val organizationLabel = JLabel("Organization:")
        organizationLabel.labelFor = organizationTextField
        generalSettingsPanel.add(
            organizationLabel,
            baseGridConstraintsAnchorWest(
                row = 4,
                indent = 0
            )
        )

        generalSettingsPanel.add(
            organizationTextField,
            baseGridConstraints(
                row = 4,
                column = 1,
                colSpan = 2,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0
            )
        )

        val organizationContextHelpLabel = ContextHelpLabel.createWithLink(
            null,
            SnykBundle.message("snyk.settings.organization.tooltip.description"),
            SnykBundle.message("snyk.settings.organization.tooltip.linkText")
        ) {
            BrowserUtil.browse(SnykBundle.message("snyk.settings.organization.tooltip.link"))
        }
        generalSettingsPanel.add(
            organizationContextHelpLabel,
            baseGridConstraintsAnchorWest(
                row = 4,
                column = 3,
                indent = 0
            )
        )

        /** Products and Severities selection ------------------ */

        val productAndSeveritiesPanel = JPanel(UIGridLayoutManager(5, 4, Insets(0, 0, 0, 0), 30, -1))

        rootPanel.add(
            productAndSeveritiesPanel,
            baseGridConstraints(
                row = 1,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0
            )
        )

        val productSelectionPanel = JPanel(UIGridLayoutManager(5, 4, Insets(0, 0, 0, 0), -1, -1))
        productSelectionPanel.border = IdeBorderFactory.createTitledBorder("Product selection")

        productAndSeveritiesPanel.add(
            productSelectionPanel,
            baseGridConstraints(
                row = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0
            )
        )

        productSelectionPanel.add(
            scanTypesPanel,
            baseGridConstraints(
                row = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                vSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0
            )
        )

        val severitiesPanel = JPanel(UIGridLayoutManager(5, 4, Insets(0, 0, 0, 0), -1, -1))
        severitiesPanel.border = IdeBorderFactory.createTitledBorder("Severity selection")

        productAndSeveritiesPanel.add(
            severitiesPanel,
            baseGridConstraints(
                row = 0,
                column = 1,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0
            )
        )

        severitiesPanel.add(
            severityEnablementPanel,
            baseGridConstraints(
                row = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                vSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0
            )
        )

        rootPanel.add(
            codeAlertPanel,
            baseGridConstraints(
                row = 2,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                indent = 2
            )
        )

        /** Project settings ------------------ */

        if (isProjectSettingsAvailable(project)) {
            val projectSettingsPanel = JPanel(UIGridLayoutManager(2, 3, Insets(0, 0, 0, 0), -1, -1))
            projectSettingsPanel.border = IdeBorderFactory.createTitledBorder("Project settings")

            rootPanel.add(
                projectSettingsPanel,
                baseGridConstraints(
                    row = 3,
                    fill = UIGridConstraints.FILL_BOTH,
                    hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    indent = 0
                )
            )

            val additionalParametersLabel = JLabel("Additional parameters:")
            projectSettingsPanel.add(
                additionalParametersLabel,
                baseGridConstraintsAnchorWest(
                    row = 0,
                    indent = 0
                )
            )

            projectSettingsPanel.add(
                additionalParametersTextField,
                baseGridConstraints(
                    row = 0,
                    column = 1,
                    anchor = UIGridConstraints.ANCHOR_WEST,
                    fill = UIGridConstraints.FILL_HORIZONTAL,
                    hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                    indent = 0
                )
            )

            additionalParametersLabel.labelFor = additionalParametersTextField

            val projectSettingsSpacer = Spacer()
            projectSettingsPanel.add(
                projectSettingsSpacer,
                baseGridConstraints(
                    row = 1,
                    fill = UIGridConstraints.FILL_VERTICAL,
                    hSizePolicy = 1,
                    vSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                    indent = 0
                )
            )
        }

        /** User experience ------------------ */

        val userExperiencePanel = JPanel(UIGridLayoutManager(5, 4, Insets(0, 0, 0, 0), -1, -1))
        userExperiencePanel.border = IdeBorderFactory.createTitledBorder("User experience")

        rootPanel.add(
            userExperiencePanel,
            baseGridConstraints(
                row = 4,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0
            )
        )

        usageAnalyticsCheckBox.text = "Help us with anonymous usage analytics"
        userExperiencePanel.add(
            usageAnalyticsCheckBox,
            baseGridConstraints(
                row = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                indent = 0
            )
        )

        crashReportingCheckBox.text = "Allow automatically sending crash reports"
        userExperiencePanel.add(
            crashReportingCheckBox,
            baseGridConstraints(
                row = 1,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                indent = 0
            )
        )

        /** Spacer ------------------ */

        val generalSettingsSpacer = Spacer()
        rootPanel.add(
            generalSettingsSpacer,
            panelGridConstraints(
                row = 5
            )
        )

        createExecutableSettingsPanel()
    }

    private fun createExecutableSettingsPanel() {
        val executableSettingsPanel = JPanel(GridBagLayout())
        executableSettingsPanel.border = IdeBorderFactory.createTitledBorder("Executable settings")
        val gb = GridBag().setDefaultWeightX(1.0)
            .setDefaultAnchor(GridBagConstraints.LINE_START)
            .setDefaultFill(GridBagConstraints.HORIZONTAL)

        rootPanel.add(
            executableSettingsPanel,
            baseGridConstraints(
                row = 5,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0
            )
        )

        cliPathTextBoxWithFileBrowser.toolTipText = "The default path is ${getCliFile().canonicalPath}."
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
        cliPathTextBoxWithFileBrowser.addBrowseFolderListener(
            "", "Please choose the Snyk CLI you want to use:", null,
            descriptor,
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        )

        executableSettingsPanel.add(
            UI.PanelFactory
                .panel(cliPathTextBoxWithFileBrowser)
                .withLabel("Path to Snyk CLI: ").createPanel(),
            gb.nextLine()
        )

        enableAutomaticCLIUpdateCheckbox.text = "Automatically download updates for the Snyk CLI"
        executableSettingsPanel.add(
            enableAutomaticCLIUpdateCheckbox,
            gb.nextLine()
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

    fun isSeverityEnablementChanged(): Boolean = severityEnablementPanel.isModified()

    fun saveSeveritiesEnablementChanges() = severityEnablementPanel.apply()

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

    fun getCliPath(): String = cliPathTextBoxWithFileBrowser.text
    fun isAutomaticCLIUpdatesEnabled() = enableAutomaticCLIUpdateCheckbox.isSelected
}
