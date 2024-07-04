package io.snyk.plugin.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.util.maximumWidth
import com.intellij.ui.util.preferredWidth
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.Alarm
import com.intellij.util.FontUtil
import com.intellij.util.containers.toArray
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.isAdditionalParametersValid
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable
import io.snyk.plugin.ui.settings.IssueViewOptionsPanel
import io.snyk.plugin.ui.settings.ScanTypesPanel
import io.snyk.plugin.ui.settings.SeveritiesEnablementPanel
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import snyk.SnykBundle
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.Objects.nonNull
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
    snykProjectSettingsConfigurable: SnykProjectSettingsConfigurable,
) {
    private val rootPanel =
        object : JPanel(), Disposable {
            init {
                Disposer.register(SnykPluginDisposable.getInstance(project), this)
            }

            override fun dispose() = Unit
        }

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, rootPanel)

    private val tokenTextField = JBPasswordField().apply { preferredWidth = 600 }
    private val receiveTokenButton = JButton("Connect IDE to Snyk")
    private val useTokenAuthenticationCheckbox =
        JCheckBox().apply {
            val description =
                "Use token authentication. It is recommended to keep this turned off, as the default OAuth2 authentication is more secure."
            text = description
            toolTipText =
                description
        }
    private val customEndpointTextField = JTextField()
    private val organizationTextField: JTextField =
        JTextField().apply { toolTipText = "The UUID of your organization or the org stub   " }
    private val ignoreUnknownCACheckBox: JCheckBox =
        JCheckBox().apply { toolTipText = "Enabling this causes SSL certificate validation to be disabled" }
    private val usageAnalyticsCheckBox: JCheckBox =
        JCheckBox().apply { toolTipText = "If enabled, send analytics to Amplitude" }
    private val crashReportingCheckBox = JCheckBox().apply { toolTipText = "If enabled, send error reports to Sentry" }
    private val scanOnSaveCheckbox =
        JCheckBox().apply { toolTipText = "If enabled, automatically scan on save, start-up and configuration change" }
    private val additionalParametersTextField: JTextField =
        ExpandableTextField().apply { toolTipText = "--all-projects is already defaulted, -d causes problems" }

    private val scanTypesPanelOuter = ScanTypesPanel(project, rootPanel)
    private val codeAlertPanel = scanTypesPanelOuter.codeAlertPanel
    private val scanTypesPanel = scanTypesPanelOuter.panel

    private val issueViewOptionsPanel = IssueViewOptionsPanel(project).panel

    private val severityEnablementPanel = SeveritiesEnablementPanel().panel

    private val manageBinariesAutomatically: JCheckBox = JCheckBox()
    private val cliPathTextBoxWithFileBrowser = TextFieldWithBrowseButton()
    private val channels = listOf("stable", "rc", "preview").toArray(emptyArray())
    private val cliReleaseChannelDropDown = ComboBox(channels).apply { this.isEditable = true }
    private val cliBaseDownloadUrlTextField = JBTextField()

    private val logger = Logger.getInstance(this::class.java)

    init {
        initializeUiComponents()
        initializeValidation()

        receiveTokenButton.isEnabled = !getSnykCliDownloaderService().isCliDownloading()

        ApplicationManager
            .getApplication()
            .messageBus
            .connect(rootPanel)
            .subscribe(
                SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
                object : SnykCliDownloadListener {
                    override fun cliDownloadStarted() {
                        receiveTokenButton.isEnabled = false
                    }

                    override fun cliDownloadFinished(succeed: Boolean) {
                        receiveTokenButton.isEnabled = true
                    }
                },
            )

        receiveTokenButton.addActionListener {
            ApplicationManager.getApplication().invokeLater {
                try {
                    snykProjectSettingsConfigurable.apply()
                } catch (e: Exception) {
                    logger.error("Failed to apply Snyk settings", e)
                }
                val token = getSnykCliAuthenticationService(project)?.authenticate() ?: ""
                tokenTextField.text = token
                runBackgroundableTask("Checking Snyk Code Enablement In Organisation", project, true) {
                    this.scanTypesPanelOuter.checkSastEnabled()
                }
            }
        }

        if (nonNull(applicationSettings)) {
            tokenTextField.text = applicationSettings.token
            useTokenAuthenticationCheckbox.isSelected = applicationSettings.useTokenAuthentication
            customEndpointTextField.text = applicationSettings.customEndpointUrl
            organizationTextField.text = applicationSettings.organization
            ignoreUnknownCACheckBox.isSelected = applicationSettings.ignoreUnknownCA
            usageAnalyticsCheckBox.isSelected = applicationSettings.usageAnalyticsEnabled
            crashReportingCheckBox.isSelected = applicationSettings.crashReportingEnabled
            manageBinariesAutomatically.isSelected = applicationSettings.manageBinariesAutomatically

            cliPathTextBoxWithFileBrowser.text = applicationSettings.cliPath
            cliBaseDownloadUrlTextField.text = applicationSettings.cliBaseDownloadURL
            additionalParametersTextField.text = applicationSettings.getAdditionalParameters(project)
            scanOnSaveCheckbox.isSelected = applicationSettings.scanOnSave

            cliReleaseChannelDropDown.selectedItem = applicationSettings.cliReleaseChannel
        }
    }

    fun getRootPanel(): JComponent = rootPanel

    // We have to do background task run through Alarm on Alarm.ThreadToUse.POOLED_THREAD due to next (Idea?) bug:
    // Creation of Task.Backgroundable under another Task.Backgroundable does not work for Settings dialog,
    // it postpones inner Background task execution till Setting dialog exit
    fun runBackgroundable(
        runnable: () -> Unit,
        delayMillis: Int = 10,
    ) {
        if (!alarm.isDisposed) {
            alarm.addRequest(runnable, delayMillis)
        }
    }

    private fun initializeUiComponents() {
        rootPanel.layout = UIGridLayoutManager(10, 1, JBUI.emptyInsets(), -1, -1)

        /** General settings ------------------ */

        val generalSettingsPanel = JPanel(UIGridLayoutManager(6, 4, JBUI.emptyInsets(), -1, -1))
        generalSettingsPanel.border = IdeBorderFactory.createTitledBorder("General settings")

        rootPanel.add(
            generalSettingsPanel,
            baseGridConstraints(
                row = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )



        generalSettingsPanel.add(
            receiveTokenButton,
            baseGridConstraintsAnchorWest(
                row = 0,
                column = 1,
                indent = 0,
            ),
        )

        generalSettingsPanel.add(
            useTokenAuthenticationCheckbox,
            baseGridConstraints(
                row = 1,
                column = 1,
                colSpan = 3,
                indent = 0,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
            ),
        )

        val tokenLabel = JLabel("Token:")
        generalSettingsPanel.add(
            tokenLabel,
            baseGridConstraintsAnchorWest(
                row = 2,
                indent = 0,
            ),
        )

        generalSettingsPanel.add(
            tokenTextField,
            baseGridConstraints(
                row = 2,
                column = 1,
                colSpan = 3,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                indent = 0,
            ),
        )

        val customEndpointLabel = JLabel("Custom endpoint:")
        val customEndpointTooltip =
            "The correct endpoint format is https://api.xxx.snyk[gov].io, e.g. https://api.eu.snyk.io"
        customEndpointLabel.toolTipText = customEndpointTooltip
        customEndpointLabel.labelFor = customEndpointTextField
        customEndpointTextField.toolTipText = customEndpointTooltip
        generalSettingsPanel.add(
            customEndpointLabel,
            baseGridConstraintsAnchorWest(
                row = 3,
                indent = 0,
            ),
        )

        generalSettingsPanel.add(
            customEndpointTextField,
            baseGridConstraints(
                row = 3,
                column = 1,
                colSpan = 3,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0,
            ),
        )

        ignoreUnknownCACheckBox.text = "Ignore unknown CA"
        generalSettingsPanel.add(
            ignoreUnknownCACheckBox,
            baseGridConstraints(
                row = 4,
                column = 1,
                colSpan = 3,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0,
            ),
        )

        val organizationLabel = JLabel("Organization:")
        organizationLabel.labelFor = organizationTextField
        generalSettingsPanel.add(
            organizationLabel,
            baseGridConstraintsAnchorWest(
                row = 5,
                indent = 0,
            ),
        )

        generalSettingsPanel.add(
            organizationTextField,
            baseGridConstraints(
                row = 5,
                column = 1,
                colSpan = 2,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0,
            ),
        )

        val organizationContextHelpLabel =
            ContextHelpLabel.createWithLink(
                null,
                SnykBundle.message("snyk.settings.organization.tooltip.description"),
                SnykBundle.message("snyk.settings.organization.tooltip.linkText"),
            ) {
                BrowserUtil.browse(SnykBundle.message("snyk.settings.organization.tooltip.link"))
            }
        generalSettingsPanel.add(
            organizationContextHelpLabel,
            baseGridConstraintsAnchorWest(
                row = 5,
                column = 3,
                indent = 0,
            ),
        )

        /** Products and Severities selection ------------------ */

        if (pluginSettings().isGlobalIgnoresFeatureEnabled) {
            val issueViewPanel = JPanel(UIGridLayoutManager(3, 2, JBUI.emptyInsets(), 30, -1))
            issueViewPanel.border = IdeBorderFactory.createTitledBorder("Issue view options")

            val issueViewLabel = JLabel("Show the following issues:")
            issueViewPanel.add(
                issueViewLabel,
                baseGridConstraintsAnchorWest(
                    row = 0,
                    indent = 0,
                ),
            )

            rootPanel.add(
                issueViewPanel,
                baseGridConstraints(
                    row = 1,
                    anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                    fill = UIGridConstraints.FILL_HORIZONTAL,
                    hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    indent = 0,
                ),
            )

            issueViewPanel.add(
                this.issueViewOptionsPanel,
                baseGridConstraints(
                    row = 1,
                    anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                    fill = UIGridConstraints.FILL_NONE,
                    hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    vSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    indent = 0,
                ),
            )
        }
        val productAndSeveritiesPanel = JPanel(UIGridLayoutManager(1, 2, JBUI.emptyInsets(), 30, -1))

        rootPanel.add(
            productAndSeveritiesPanel,
            baseGridConstraints(
                row = 2,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )

        val productSelectionPanel = JPanel(UIGridLayoutManager(1, 1, JBUI.emptyInsets(), -1, -1))
        productSelectionPanel.border = IdeBorderFactory.createTitledBorder("Product selection")

        productAndSeveritiesPanel.add(
            productSelectionPanel,
            baseGridConstraints(
                row = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )

        productSelectionPanel.add(
            scanTypesPanel,
            baseGridConstraints(
                row = 0,
                column = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                vSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )

        val severitiesPanel = JPanel(UIGridLayoutManager(5, 4, JBUI.emptyInsets(), -1, -1))
        severitiesPanel.border = IdeBorderFactory.createTitledBorder("Severity selection")

        productAndSeveritiesPanel.add(
            severitiesPanel,
            baseGridConstraints(
                row = 0,
                column = 1,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )

        severitiesPanel.add(
            severityEnablementPanel,
            baseGridConstraints(
                row = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                vSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )

        rootPanel.add(
            codeAlertPanel,
            baseGridConstraints(
                row = 2,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                indent = 2,
            ),
        )

        /** Project settings ------------------ */

        if (isProjectSettingsAvailable(project)) {
            val projectSettingsPanel = JPanel(UIGridLayoutManager(3, 3, JBUI.emptyInsets(), -1, -1))
            projectSettingsPanel.border = IdeBorderFactory.createTitledBorder("Project settings")

            rootPanel.add(
                projectSettingsPanel,
                baseGridConstraints(
                    row = 3,
                    fill = UIGridConstraints.FILL_BOTH,
                    hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                    indent = 0,
                ),
            )

            val additionalParametersLabel = JLabel("Additional parameters:")
            projectSettingsPanel.add(
                additionalParametersLabel,
                baseGridConstraintsAnchorWest(
                    row = 0,
                    indent = 0,
                ),
            )

            projectSettingsPanel.add(
                additionalParametersTextField,
                baseGridConstraints(
                    row = 0,
                    column = 1,
                    anchor = UIGridConstraints.ANCHOR_WEST,
                    fill = UIGridConstraints.FILL_HORIZONTAL,
                    hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                    indent = 0,
                ),
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
                    indent = 0,
                ),
            )
        }

        createExecutableSettingsPanel()

        /** User experience ------------------ */

        val userExperiencePanel = JPanel(UIGridLayoutManager(6, 4, JBUI.emptyInsets(), -1, -1))
        userExperiencePanel.border = IdeBorderFactory.createTitledBorder("User experience")

        rootPanel.add(
            userExperiencePanel,
            baseGridConstraints(
                row = 5,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )

        scanOnSaveCheckbox.text = "Scan automatically on start-up and save"
        userExperiencePanel.add(
            scanOnSaveCheckbox,
            baseGridConstraints(
                row = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                indent = 0,
            ),
        )

        usageAnalyticsCheckBox.text = "Send usage statistics to Snyk"
        userExperiencePanel.add(
            usageAnalyticsCheckBox,
            baseGridConstraints(
                row = 1,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                indent = 0,
            ),
        )

        crashReportingCheckBox.text = "Send error reports to Snyk"
        userExperiencePanel.add(
            crashReportingCheckBox,
            baseGridConstraints(
                row = 2,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                indent = 0,
            ),
        )

        /** Spacer ------------------ */

        val generalSettingsSpacer = Spacer()
        rootPanel.add(
            generalSettingsSpacer,
            panelGridConstraints(
                row = 5,
            ),
        )
    }

    private fun createExecutableSettingsPanel() {
        val executableSettingsPanel = JPanel(GridBagLayout())
        executableSettingsPanel.border = IdeBorderFactory.createTitledBorder("Executable settings")
        val gb =
            GridBag()
                .setDefaultWeightX(1.0)
                .setDefaultAnchor(GridBagConstraints.LINE_START)
                .setDefaultFill(GridBagConstraints.HORIZONTAL)

        rootPanel.add(
            executableSettingsPanel,
            baseGridConstraints(
                row = 4,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_HORIZONTAL,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )

        val introLabel =
            JLabel(
                "<html>These options allow you to customize the handling, where and how plugin dependencies are downloaded.<br/><br/></html>",
            )

        introLabel.font = FontUtil.minusOne(introLabel.font)
        executableSettingsPanel.add(
            introLabel,
            gb.nextLine(),
        )

        cliBaseDownloadUrlTextField.toolTipText = "The default URL is https://static.snyk.io. " +
            "for FIPS-enabled CLIs (only available for Windows and Linux), please use https://static.snyk.io/fips"
        val cliBaseDownloadPanel =
            panel {
                row {
                    label("Base URL to download the CLI: ")
                    cell(cliBaseDownloadUrlTextField).align(AlignX.FILL)
                }
            }
        executableSettingsPanel.add(cliBaseDownloadPanel, gb.nextLine())

        cliPathTextBoxWithFileBrowser.toolTipText = "The default path is ${getCliFile().canonicalPath}."
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
        cliPathTextBoxWithFileBrowser.addBrowseFolderListener(
            "",
            "Please choose the Snyk CLI you want to use:",
            null,
            descriptor,
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
        )

        executableSettingsPanel.add(
            panel {
                row {
                    label("Path to Snyk CLI: ")
                    cell(cliPathTextBoxWithFileBrowser).align(AlignX.FILL)
                }
            },
            gb.nextLine(),
        )

        val descriptionLabelManageBinaries =
            JLabel(
                "<html>" +
                    "If <i>Automatically manage needed binaries</i> is checked, " +
                    "the plugin will try to download the CLI every 4 days to the given path,<br/>" +
                    "or to the default path. If unchecked, please make sure to select a valid path to an existing Snyk CLI.<br/><br/></html>",
            )
        descriptionLabelManageBinaries.font = FontUtil.minusOne(descriptionLabelManageBinaries.font)

        executableSettingsPanel.add(
            panel {
                row {
                    label("Automatically manage needed binaries: ")
                    cell(manageBinariesAutomatically).align(AlignX.FILL)
                }
                row { cell(descriptionLabelManageBinaries) }
            },
            gb.nextLine(),
        )

        val descriptionLabelReleaseChannel =
            HyperlinkLabel(
                "Find out about our release channels",
            ).apply { setHyperlinkTarget("https://docs.snyk.io/snyk-cli/releases-and-channels-for-the-snyk-cli") }
        descriptionLabelReleaseChannel.font = FontUtil.minusOne(descriptionLabelReleaseChannel.font)

        executableSettingsPanel.add(
            panel {
                row {
                    label("CLI release channel:")
                    cell(cliReleaseChannelDropDown)
                }
                row { cell(descriptionLabelReleaseChannel) }
            },
            gb.nextLine(),
        )
    }

    fun getToken(): String =
        try {
            tokenTextField.document.getText(0, tokenTextField.document.length)
        } catch (exception: BadLocationException) {
            ""
        }

    fun getOrganization(): String = organizationTextField.text

    fun getCustomEndpoint(): String = customEndpointTextField.text

    fun isIgnoreUnknownCA(): Boolean = ignoreUnknownCACheckBox.isSelected

    fun isUsageAnalyticsEnabled(): Boolean = usageAnalyticsCheckBox.isSelected

    fun isScanOnSaveEnabled(): Boolean = scanOnSaveCheckbox.isSelected

    fun isCrashReportingEnabled(): Boolean = crashReportingCheckBox.isSelected

    fun isScanTypeChanged(): Boolean = scanTypesPanel.isModified()

    fun saveScanTypeChanges() = scanTypesPanel.apply()

    fun isSeverityEnablementChanged(): Boolean = severityEnablementPanel.isModified()

    fun saveSeveritiesEnablementChanges() = severityEnablementPanel.apply()

    fun isIssueOptionChanged() = issueViewOptionsPanel.isModified()

    fun saveIssueOptionChanges() = issueViewOptionsPanel.apply()

    fun getAdditionalParameters(): String = additionalParametersTextField.text

    private fun initializeValidation() {
        setupValidation(
            customEndpointTextField,
            "Invalid custom endpoint URL, please use https://api.xxx.snyk[gov].io",
            ::isUrlValid,
        )
        setupValidation(
            additionalParametersTextField,
            "The -d option is not supported by the Snyk IntelliJ plugin",
            ::isAdditionalParametersValid,
        )
    }

    private fun setupValidation(
        textField: JTextField,
        message: String,
        isValidText: (sourceStr: String?) -> Boolean,
    ) {
        ComponentValidator(rootPanel)
            .withValidator(
                Supplier<ValidationInfo?> {
                    val validationInfo: ValidationInfo =
                        if (!isValidText(textField.text)) {
                            ValidationInfo(message, textField)
                        } else {
                            ValidationInfo("")
                        }
                    validationInfo
                },
            ).installOn(textField)

        textField.document.addDocumentListener(
            object : DocumentAdapter() {
                override fun textChanged(event: DocumentEvent) {
                    ComponentValidator.getInstance(textField).ifPresent {
                        it.revalidate()
                    }
                }
            },
        )
    }

    fun getCliPath(): String = cliPathTextBoxWithFileBrowser.text

    fun manageBinariesAutomatically() = manageBinariesAutomatically.isSelected

    fun getCliBaseDownloadURL(): String = cliBaseDownloadUrlTextField.text

    fun getCliReleaseChannel(): String = cliReleaseChannelDropDown.selectedItem as String

    fun getUseTokenAuthentication(): Boolean = useTokenAuthenticationCheckbox.isSelected
}
