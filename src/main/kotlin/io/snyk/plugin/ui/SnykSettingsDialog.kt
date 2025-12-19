package io.snyk.plugin.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
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
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.Alarm
import com.intellij.util.FontUtil
import com.intellij.util.containers.toArray
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykFolderConfigListener
import io.snyk.plugin.fromUriToPath
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getPluginPath
import io.snyk.plugin.getSnykCliAuthenticationService
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.isAdditionalParametersValid
import io.snyk.plugin.isProjectSettingsAvailable
import io.snyk.plugin.isUrlValid
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.AuthenticationType
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.settings.SnykProjectSettingsConfigurable
import io.snyk.plugin.ui.settings.IssueViewOptionsPanel
import io.snyk.plugin.ui.settings.ScanTypesPanel
import io.snyk.plugin.ui.settings.SeveritiesEnablementPanel
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import org.jetbrains.concurrency.runAsync
import snyk.SnykBundle
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings
import java.awt.Cursor
import java.awt.Desktop
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ItemEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File.separator
import java.net.URI
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

    private val tokenTextField = JBPasswordField().apply { preferredSize = Dimension(600, preferredSize.height) }
    private val receiveTokenButton = JButton("Connect IDE to Snyk")
    private val authenticationType =
        ComboBox(AuthenticationType.entries.sortedBy { it.dialogIndex }.map { it.dialogName }.toTypedArray()).apply {
            this.isEditable = false
        }

    private val customEndpointTextField = JTextField().apply { preferredSize = Dimension(600, preferredSize.height) }
    private val organizationTextField: JTextField =
        JTextField().apply {
            toolTipText = "Global organization setting. This field is used when auto-select organization is disabled in the Project settings."
            preferredSize = Dimension(600, preferredSize.height)
        }
    private val ignoreUnknownCACheckBox: JCheckBox =
        JCheckBox().apply { toolTipText = "Enabling this causes SSL certificate validation to be disabled" }

    private val scanOnSaveCheckbox =
        JCheckBox().apply { toolTipText = "If enabled, automatically scan on save, start-up and configuration change" }
    private val additionalParametersTextField: JTextField =
        ExpandableTextField().apply { toolTipText = "--all-projects is already defaulted, -d causes problems" }

    private val autoDetectOrgCheckbox: JCheckBox =
        JCheckBox().apply {
            toolTipText = "When checked, the organization is automatically detected from Snyk settings"

            // Update the text field when checkbox state changes
            addActionListener {
                updatePreferredOrgTextField()
            }
        }
    private val preferredOrgTextField: JTextField =
        JTextField().apply {
            preferredSize = Dimension(600, preferredSize.height)
        }

    private val scanTypesPanelOuter = ScanTypesPanel()
    private val scanTypesPanel = scanTypesPanelOuter.scanTypesPanel

    private val issueViewOptionsPanel = IssueViewOptionsPanel(project).panel

    private val severityEnablementPanel = SeveritiesEnablementPanel().panel

    private val manageBinariesAutomatically: JCheckBox = JCheckBox()
    private val cliPathTextBoxWithFileBrowser = TextFieldWithBrowseButton()
    private val channels = listOf("stable", "rc", "preview").toArray(emptyArray())
    private val cliReleaseChannelDropDown = ComboBox(channels).apply { this.isEditable = true }
    private val newIssues = listOf("All issues", "Net new issues").toArray(emptyArray())
    private val netNewIssuesDropDown = ComboBox(newIssues).apply { this.isEditable = false }
    private val cliBaseDownloadUrlTextField = JBTextField()
    private val baseBranchInfoLabel = JBLabel("Base branch: ")

    private val logger = Logger.getInstance(this::class.java)

    init {
        initializeUiComponents()
        initializeValidation()

        receiveTokenButton.isEnabled = !getSnykCliDownloaderService().isCliDownloading()

        val messageBusConnection = ApplicationManager.getApplication().messageBus.connect(rootPanel)

        messageBusConnection.subscribe(
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

        messageBusConnection.subscribe(
            SnykFolderConfigListener.SNYK_FOLDER_CONFIG_TOPIC,
            object : SnykFolderConfigListener {
                override fun folderConfigsChanged(folderConfigsNotEmpty: Boolean) {
                    updateProjectSettingsFields(folderConfigsNotEmpty)
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
                getSnykCliAuthenticationService(project)?.authenticate()
                tokenTextField.text = pluginSettings().token
                customEndpointTextField.text = pluginSettings().customEndpointUrl
            }
        }

        if (nonNull(applicationSettings)) {
            tokenTextField.text = applicationSettings.token
            authenticationType.selectedIndex = applicationSettings.authenticationType.dialogIndex
            customEndpointTextField.text = applicationSettings.customEndpointUrl
            organizationTextField.text = applicationSettings.organization
            ignoreUnknownCACheckBox.isSelected = applicationSettings.ignoreUnknownCA
            manageBinariesAutomatically.isSelected = applicationSettings.manageBinariesAutomatically
            cliPathTextBoxWithFileBrowser.text = applicationSettings.cliPath
            cliBaseDownloadUrlTextField.text = applicationSettings.cliBaseDownloadURL
            val haveFolderConfigs = LanguageServerWrapper.getInstance(project).getFolderConfigsRefreshed().isNotEmpty()
            updateProjectSettingsFields(haveFolderConfigs)
            scanOnSaveCheckbox.isSelected = applicationSettings.scanOnSave
            cliReleaseChannelDropDown.selectedItem = applicationSettings.cliReleaseChannel
            baseBranchInfoLabel.text = service<FolderConfigSettings>().getAll()
                .values.joinToString("\n") {
                    "${it.folderPath}: Reference branch: ${it.baseBranch}, Reference directory: ${it.referenceFolderPath}"
                }
            netNewIssuesDropDown.selectedItem = applicationSettings.issuesToDisplay
        }
    }

    private fun updateProjectSettingsFields(haveFolderConfigs: Boolean) {
        additionalParametersTextField.text = getAdditionalParams(project)
        additionalParametersTextField.isEnabled = haveFolderConfigs


        autoDetectOrgCheckbox.isEnabled = haveFolderConfigs
        autoDetectOrgCheckbox.isSelected = !isOrgSetByUser(project)

        updatePreferredOrgTextField()
    }

    private fun getAdditionalParams(project: Project): String {
        // get workspace folders for project
        val folderConfigSettings = service<FolderConfigSettings>()
        return folderConfigSettings.getAdditionalParameters(project)
    }

    private fun isOrgSetByUser(project: Project): Boolean {
        // get workspace folders for project
        val folderConfigSettings = service<FolderConfigSettings>()
        return !folderConfigSettings.isAutoOrganizationEnabled(project)
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

        val generalSettingsPanel = JPanel(UIGridLayoutManager(8, 3, JBUI.emptyInsets(), -1, -1))
        generalSettingsPanel.border = IdeBorderFactory.createTitledBorder("General Settings")

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

        val customEndpointLabel = JLabel("Custom endpoint:")
        val customEndpointTooltip =
            "The correct endpoint format is https://api.xxx.snyk[gov].io, e.g. https://api.eu.snyk.io"
        customEndpointLabel.toolTipText = customEndpointTooltip
        customEndpointLabel.labelFor = customEndpointTextField
        customEndpointTextField.toolTipText = customEndpointTooltip
        generalSettingsPanel.add(
            customEndpointLabel,
            baseGridConstraintsAnchorWest(
                row = 0,
                indent = 0,
            ),
        )

        generalSettingsPanel.add(
            customEndpointTextField,
            baseGridConstraints(
                row = 0,
                column = 1,
                colSpan = 1,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )

        val endpointDescriptionLabel = JLabel(
            "<html>If you're using SSO with Snyk and OAuth2, the custom endpoint configuration is automatically populated. <br>" +
                "Otherwise, for public regional instances, see the " +
                "<a href='https://docs.snyk.io/working-with-snyk/regional-hosting-and-data-residency#available-snyk-regions'>docs</a>.<br>" +
                "For private instances, contact your team or account manager.</html>"
        ).apply {
            font = FontUtil.minusOne(this.font)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    Desktop.getDesktop()
                        .browse(URI("https://docs.snyk.io/working-with-snyk/regional-hosting-and-data-residency#available-snyk-regions"))
                }
            })
        }

        generalSettingsPanel.add(
            endpointDescriptionLabel,
            baseGridConstraints(
                row = 1,
                column = 1,
                colSpan = 2,
                indent = 0,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
            ),
        )

        ignoreUnknownCACheckBox.text = "Ignore unknown CA"
        generalSettingsPanel.add(
            ignoreUnknownCACheckBox,
            baseGridConstraints(
                row = 2,
                column = 1,
                colSpan = 1,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                indent = 0,
            ),
        )

        val authenticationMethodLabel = JLabel("Authentication method:")
        generalSettingsPanel.add(
            authenticationMethodLabel,
            baseGridConstraints(
                row = 3,
                column = 0,
                indent = 0,
                anchor = UIGridConstraints.ANCHOR_WEST
            ),
        )

        authenticationType.apply {
            addItemListener { event ->
                if (event.stateChange == ItemEvent.SELECTED) {
                    // User has selected a new auth mechanism; update the Language Server config.
                    LanguageServerWrapper.getInstance(project).updateConfiguration(false)
                }
            }
        }

        generalSettingsPanel.add(
            authenticationType,
            baseGridConstraints(
                row = 3,
                column = 1,
                colSpan = 1,
                indent = 0,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
            ),
        )

        val descriptionLabel =
            JLabel(
                "<html>Specifies whether to authenticate with OAuth2 or with a token.<br/>" +
                    "<u>Note:</u> OAuth2 authentication is recommended as it provides enhanced security.</html>",
            ).apply { font = FontUtil.minusOne(this.font) }

        generalSettingsPanel.add(
            descriptionLabel,
            baseGridConstraints(
                row = 4,
                column = 1,
                colSpan = 2,
                indent = 0,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
            ),
        )

        generalSettingsPanel.add(
            receiveTokenButton,
            baseGridConstraintsAnchorWest(
                row = 5,
                column = 1,
                indent = 0,
            ),
        )

        val tokenLabel = JLabel("Token:")
        generalSettingsPanel.add(
            tokenLabel,
            baseGridConstraintsAnchorWest(
                row = 6,
                indent = 0,
            ),
        )

        generalSettingsPanel.add(
            tokenTextField,
            baseGridConstraints(
                row = 6,
                column = 1,
                colSpan = 1,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )

        val organizationLabel = JLabel("Organization:")
        organizationLabel.labelFor = organizationTextField
        generalSettingsPanel.add(
            organizationLabel,
            baseGridConstraintsAnchorWest(
                row = 7,
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

        val organizationPanel = JPanel(GridBagLayout()).apply {
            val gb = GridBag().setDefaultWeightX(0.0).setDefaultFill(GridBagConstraints.NONE).setDefaultInsets(JBUI.emptyInsets())
            add(organizationTextField, gb.nextLine().next())
            add(organizationContextHelpLabel, gb.next().insets(JBUI.insetsLeft(2)))
        }

        generalSettingsPanel.add(
            organizationPanel,
            baseGridConstraints(
                row = 7,
                column = 1,
                colSpan = 2,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK or UIGridConstraints.SIZEPOLICY_CAN_GROW,
                indent = 0,
            ),
        )

        /** Products and Severities selection ------------------ */

        val issueViewPanel = JPanel(UIGridLayoutManager(3, 2, JBUI.emptyInsets(), 30, -1))
        issueViewPanel.isVisible = true
        issueViewPanel.border = IdeBorderFactory.createTitledBorder("Issue View Options")

        val issueViewLabel = JLabel("Show the following issues:")

        val issueViewContextHelpLabel =
            ContextHelpLabel.createWithLink(
                null,
                SnykBundle.message("snyk.settings.issueViewOptions.tooltip.description"),
                SnykBundle.message("snyk.settings.issueViewOptions.tooltip.linkText"),
            ) {
                BrowserUtil.browse(SnykBundle.message("snyk.settings.issueViewOptions.tooltip.link"))
            }

        val issueViewLabelPanel = JPanel(GridBagLayout()).apply {
            val gb = GridBag().setDefaultWeightX(0.0).setDefaultFill(GridBagConstraints.NONE).setDefaultInsets(JBUI.emptyInsets())
            add(issueViewLabel, gb.nextLine().next())
            add(issueViewContextHelpLabel, gb.next().insets(JBUI.insetsLeft(2)))
        }

        issueViewPanel.add(
            issueViewLabelPanel,
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

        runAsync {
            if (!pluginSettings().isGlobalIgnoresFeatureEnabled) {
                LanguageServerWrapper.getInstance(project).refreshFeatureFlags()
            }
            issueViewPanel.isVisible = pluginSettings().isGlobalIgnoresFeatureEnabled
        }

        val productAndSeveritiesPanel = JPanel(UIGridLayoutManager(2, 2, JBUI.emptyInsets(), 30, -1))

        rootPanel.add(
            productAndSeveritiesPanel,
            baseGridConstraints(
                row = 2,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                indent = 0,
            ),
        )

        productAndSeveritiesPanel.add(
            scanTypesPanel,
            baseGridConstraints(
                row = 0,
                column = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                indent = 0,
            ),
        )

        val severitiesPanel = JPanel(UIGridLayoutManager(1, 1, JBUI.emptyInsets(), -1, -1))
        severitiesPanel.border = IdeBorderFactory.createTitledBorder("Severity Selection")

        productAndSeveritiesPanel.add(
            severitiesPanel,
            baseGridConstraints(
                row = 0,
                column = 1,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                indent = 0,
            ),
        )

        severitiesPanel.add(
            severityEnablementPanel,
            baseGridConstraints(
                row = 0,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                vSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                indent = 0,
            ),
        )

        createNetNewPanel(productAndSeveritiesPanel)

        /** Project settings ------------------ */

        if (isProjectSettingsAvailable(project)) {
            val projectSettingsPanel = JPanel(UIGridLayoutManager(4, 3, JBUI.emptyInsets(), -1, -1))
            projectSettingsPanel.border = IdeBorderFactory.createTitledBorder("Project Settings")

            rootPanel.add(
                projectSettingsPanel,
                baseGridConstraints(
                    row = 3,
                    fill = UIGridConstraints.FILL_BOTH,
                    hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
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
                    colSpan = 2,
                    anchor = UIGridConstraints.ANCHOR_WEST,
                    fill = UIGridConstraints.FILL_NONE,
                    hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                    indent = 0,
                ),
            )

            additionalParametersLabel.labelFor = additionalParametersTextField

            val autoDetectOrgLabel = JLabel("Auto-select organization:")
            projectSettingsPanel.add(
                autoDetectOrgLabel,
                baseGridConstraintsAnchorWest(
                    row = 1,
                    indent = 0,
                ),
            )

            val autoDetectOrgContextHelpLabel =
                ContextHelpLabel.createWithLink(
                    null,
                    SnykBundle.message("snyk.settings.autoDetectOrg.tooltip.description"),
                    SnykBundle.message("snyk.settings.autoDetectOrg.tooltip.linkText"),
                ) {
                    BrowserUtil.browse(SnykBundle.message("snyk.settings.autoDetectOrg.tooltip.link"))
                }

            val preferredOrgContextHelpLabel =
                ContextHelpLabel.createWithLink(
                    null,
                    SnykBundle.message("snyk.settings.preferredOrg.tooltip.description"),
                    SnykBundle.message("snyk.settings.preferredOrg.tooltip.linkText"),
                ) {
                    BrowserUtil.browse(SnykBundle.message("snyk.settings.preferredOrg.tooltip.link"))
                }

            val autoDetectOrgPanel = JPanel(GridBagLayout()).apply {
                val gb = GridBag().setDefaultWeightX(0.0).setDefaultFill(GridBagConstraints.NONE).setDefaultInsets(JBUI.emptyInsets())
                add(autoDetectOrgCheckbox, gb.nextLine().next())
                add(autoDetectOrgContextHelpLabel, gb.next().insets(JBUI.insetsLeft(2)))
            }

            val preferredOrgPanel = JPanel(GridBagLayout()).apply {
                val gb = GridBag().setDefaultWeightX(0.0).setDefaultFill(GridBagConstraints.NONE).setDefaultInsets(JBUI.emptyInsets())
                add(preferredOrgTextField, gb.nextLine().next())
                add(preferredOrgContextHelpLabel, gb.next().insets(JBUI.insetsLeft(2)))
            }

            projectSettingsPanel.add(
                autoDetectOrgPanel,
                baseGridConstraints(
                    row = 1,
                    column = 1,
                    colSpan = 2,
                    anchor = UIGridConstraints.ANCHOR_WEST,
                    fill = UIGridConstraints.FILL_NONE,
                    hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                    indent = 0,
                ),
            )

            autoDetectOrgLabel.labelFor = autoDetectOrgCheckbox

            val preferredOrgLabel = JLabel("Preferred organization:")
            projectSettingsPanel.add(
                preferredOrgLabel,
                baseGridConstraintsAnchorWest(
                    row = 2,
                    indent = 0,
                ),
            )

            projectSettingsPanel.add(
                preferredOrgPanel,
                baseGridConstraints(
                    row = 2,
                    column = 1,
                    colSpan = 2,
                    anchor = UIGridConstraints.ANCHOR_WEST,
                    fill = UIGridConstraints.FILL_NONE,
                    hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                    indent = 0,
                ),
            )

            preferredOrgLabel.labelFor = preferredOrgTextField

            val projectSettingsSpacer = Spacer()
            projectSettingsPanel.add(
                projectSettingsSpacer,
                baseGridConstraints(
                    row = 3,
                    fill = UIGridConstraints.FILL_VERTICAL,
                    hSizePolicy = 1,
                    vSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                    colSpan = 2,
                    indent = 0,
                ),
            )
        }

        createExecutableSettingsPanel()

        /** User experience ------------------ */

        val userExperiencePanel = JPanel(UIGridLayoutManager(6, 4, JBUI.emptyInsets(), -1, -1))
        userExperiencePanel.border = IdeBorderFactory.createTitledBorder("User Experience")

        rootPanel.add(
            userExperiencePanel,
            baseGridConstraints(
                row = 6,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
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

        /** Spacer ------------------ */

        val generalSettingsSpacer = Spacer()
        rootPanel.add(
            generalSettingsSpacer,
            panelGridConstraints(
                row = 5,
            ),
        )
    }

    private fun createNetNewPanel(productAndSeveritiesPanel: JPanel) {
        val netNewIssuesPanel = JPanel(UIGridLayoutManager(2, 2, JBUI.insets(Insets(5, 0, 20, 0)), -1, -1))

        productAndSeveritiesPanel.add(
            netNewIssuesPanel,
            baseGridConstraints(
                row = 1,
                column = 0,
                colSpan = 2,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                indent = 0,
            ),
        )

        val newNewIssuesLabel = JLabel("All Issues Vs Net New Issues:")
        newNewIssuesLabel.labelFor = netNewIssuesDropDown
        netNewIssuesPanel.add(
            newNewIssuesLabel,
            baseGridConstraints(
                row = 0,
                column = 0,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                indent = 0,
            ),
        )

        netNewIssuesPanel.add(
            netNewIssuesDropDown,
            baseGridConstraints(
                row = 0,
                column = 1,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_WANT_GROW,
                indent = 0,
            ),
        )

        val netNewIssuesText =
            JLabel(
                "Specifies whether to see only net new issues or all issues."
            ).apply { font = FontUtil.minusOne(this.font) }

        netNewIssuesPanel.add(
            netNewIssuesText,
            baseGridConstraints(
                row = 1,
                column = 0,
                colSpan = 2,
                anchor = UIGridConstraints.ANCHOR_WEST,
                fill = UIGridConstraints.FILL_NONE,
                hSizePolicy = UIGridConstraints.SIZEPOLICY_CAN_SHRINK,
                indent = 0,
            ),
        )
    }

    private fun createExecutableSettingsPanel() {
        val executableSettingsPanel = JPanel(GridBagLayout())
        executableSettingsPanel.border = IdeBorderFactory.createTitledBorder("Executable Settings")
        val gb =
            GridBag()
                .setDefaultWeightX(1.0)
                .setDefaultAnchor(GridBagConstraints.LINE_START)
                .setDefaultFill(GridBagConstraints.HORIZONTAL)

        rootPanel.add(
            executableSettingsPanel,
            baseGridConstraints(
                row = 5,
                anchor = UIGridConstraints.ANCHOR_NORTHWEST,
                fill = UIGridConstraints.FILL_NONE,
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
            gb.nextLine()
        )

        cliBaseDownloadUrlTextField.toolTipText = "The default URL is https://downloads.snyk.io. " +
            "for FIPS-enabled CLIs (only available for Windows and Linux), please use https://downloads.snyk.io/fips"
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
        } catch (_: BadLocationException) {
            ""
        }

    fun getOrganization(): String = organizationTextField.text

    fun getCustomEndpoint(): String = customEndpointTextField.text

    fun isIgnoreUnknownCA(): Boolean = ignoreUnknownCACheckBox.isSelected

    fun isScanOnSaveEnabled(): Boolean = scanOnSaveCheckbox.isSelected

    fun isScanTypeChanged(): Boolean = scanTypesPanel.isModified()

    fun saveScanTypeChanges() = scanTypesPanel.apply()

    fun isSeverityEnablementChanged(): Boolean = severityEnablementPanel.isModified()

    fun saveSeveritiesEnablementChanges() = severityEnablementPanel.apply()

    fun isIssueViewOptionsChanged() = issueViewOptionsPanel.isModified()

    fun saveIssueViewOptionsChanges() = issueViewOptionsPanel.apply()

    fun getAdditionalParameters(): String = additionalParametersTextField.text

    fun getPreferredOrg(): String = preferredOrgTextField.text

    fun isAutoSelectOrgEnabled(): Boolean = autoDetectOrgCheckbox.isSelected

    private fun getFolderConfig(): FolderConfig? {
        val folderConfigSettings = service<FolderConfigSettings>()
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        return languageServerWrapper.getWorkspaceFoldersFromRoots(project, promptForTrust = false)
            .asSequence()
            .filter { languageServerWrapper.configuredWorkspaceFolders.contains(it) }
            .map { folderConfigSettings.getFolderConfig(it.uri.fromUriToPath().toString()) }
            .firstOrNull()
    }

    private fun updatePreferredOrgTextField() {
        val autoDetectOrgSelected = autoDetectOrgCheckbox.isSelected
        val folderConfig = getFolderConfig()

        val organization = if (autoDetectOrgSelected) {
            // Checkbox checked = auto-detect enabled = use autoDeterminedOrg only
            folderConfig?.autoDeterminedOrg ?: ""
        } else {
            // Checkbox unchecked = manual selection = clear textbox for user input
            folderConfig?.preferredOrg ?: ""
        }

        preferredOrgTextField.text = organization
        preferredOrgTextField.isEnabled = !autoDetectOrgSelected
    }


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

        setupValidation(
            cliBaseDownloadUrlTextField,
            "The base URL cannot be empty",
            ::isCliBaseDownloadUrlTextFieldValid,
        )
    }

    private fun isCliBaseDownloadUrlTextFieldValid(params: String?): Boolean {
        return !params.isNullOrEmpty()
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
    fun setDefaultCliPath() {
        cliPathTextBoxWithFileBrowser.text = getPluginPath() + separator + Platform.current().snykWrapperFileName
    }

    fun manageBinariesAutomatically() = manageBinariesAutomatically.isSelected

    fun getCliBaseDownloadURL(): String = cliBaseDownloadUrlTextField.text

    fun getCliReleaseChannel(): String = cliReleaseChannelDropDown.selectedItem as String

    fun getDisplayIssuesSelection(): String = netNewIssuesDropDown.selectedItem as String

    fun getAuthenticationType(): AuthenticationType {
        val typesByIndex = AuthenticationType.entries.associateBy { it.dialogIndex }
        return typesByIndex[authenticationType.selectedIndex] ?: AuthenticationType.OAUTH2
    }
}
