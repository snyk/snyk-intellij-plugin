package io.snyk.plugin.ui.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.getSnykApiService
import io.snyk.plugin.isContainerEnabled
import io.snyk.plugin.isIacEnabled
import io.snyk.plugin.net.CliConfigSettings
import io.snyk.plugin.net.ClientException
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.snykcode.core.SnykCodeUtils
import io.snyk.plugin.startSastEnablementCheckLoop
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import snyk.common.ProductType
import snyk.common.isSnykCodeAvailable
import snyk.common.toSnykCodeSettingsUrl
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

class ScanTypesPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
    cliScanComments: String? = null,
    private val simplifyForOnboardPanel: Boolean = false
) {
    private val settings
        get() = pluginSettings()

    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)

    private var codeSecurityCheckbox: JBCheckBox? = null
    private var codeQualityCheckbox: JBCheckBox? = null
    private var snykCodeComment: JLabel? = null
    private var snykCodeAlertHyperLinkLabel = HyperlinkLabel()
    private var snykCodeReCheckLinkLabel = ActionLink("Check again") {
        runBackgroundableTask("Checking Snyk Code enablement in organisation", project, true) {
            checkSastEnabled()
        }
    }

    private var currentOssScanEnabled = settings.ossScanEnable
    private var currentCodeSecurityScanEnabled = settings.snykCodeSecurityIssuesScanEnable
    private var currentCodeQualityScanEnabled = settings.snykCodeQualityIssuesScanEnable
    private var currentIacScanPanelEnabled = settings.iacScanEnabled
    private var currentContainerScanEnabled = settings.containerScanEnabled

    val codeAlertPanel = panel {
        row {
            cell {
                snykCodeAlertHyperLinkLabel()
                    .withLargeLeftGap()
                snykCodeReCheckLinkLabel()
                    .withLargeLeftGap()
            }
        }
    }.apply {
        border = JBUI.Borders.empty()
        this.isVisible = false
    }

    val panel = panel {
        row {
            cell {
                checkBox(
                    text = ProductType.OSS.productSelectionName,
                    getter = { settings.ossScanEnable },
                    setter = { settings.ossScanEnable = it },
                    comment = cliScanComments
                ).component.apply {
                    this.addItemListener {
                        isLastProductDisabling(this, currentOssScanEnabled)
                        currentOssScanEnabled = this.isSelected
                    }
                    name = ProductType.OSS.toString()
                }
                label("").component.convertIntoHelpHintLabel(ProductType.OSS.description)
            }
        }
        row {
            cell {
                checkBox(
                    text = ProductType.ADVISOR.productSelectionName,
                    getter = { settings.advisorEnable },
                    setter = { settings.advisorEnable = it }
                )
                label("").component.convertIntoHelpHintLabel(
                    "Discover the health (maintenance, community, popularity & security)\n" +
                        "status of your open source packages"
                )
            }
        }
        if (isIacEnabled()) {
            row {
                cell {
                    checkBox(
                        text = ProductType.IAC.productSelectionName,
                        getter = { settings.iacScanEnabled },
                        setter = { settings.iacScanEnabled = it }
                    ).component.apply {
                        this.addItemListener {
                            isLastProductDisabling(this, currentIacScanPanelEnabled)
                            currentIacScanPanelEnabled = this.isSelected
                        }
                        name = ProductType.IAC.toString()
                    }
                    label("").component.convertIntoHelpHintLabel(ProductType.IAC.description)
                }
            }
        }
        if (isContainerEnabled()) {
            row {
                cell {
                    checkBox(
                        text = ProductType.CONTAINER.productSelectionName,
                        getter = { settings.containerScanEnabled },
                        setter = { enabled ->
                            settings.containerScanEnabled = enabled
                            val imagesCache = getKubernetesImageCache(project)
                            if (enabled) imagesCache?.scanProjectForKubernetesFiles() else imagesCache?.clear()
                        }
                    ).component.apply {
                        this.addItemListener {
                            isLastProductDisabling(this, currentContainerScanEnabled)
                            currentContainerScanEnabled = this.isSelected
                        }
                        name = ProductType.CONTAINER.toString()
                    }
                    label("").component.convertIntoHelpHintLabel(ProductType.CONTAINER.description)
                }
            }
        }
        row {
            cell {
                codeSecurityCheckbox = checkBox(
                    text = ProductType.CODE_SECURITY.productSelectionName,
                    getter = { settings.snykCodeSecurityIssuesScanEnable },
                    setter = { settings.snykCodeSecurityIssuesScanEnable = it }
                )
                    .component.apply {
                        this.addItemListener {
                            isLastProductDisabling(this, currentCodeSecurityScanEnabled)
                            currentCodeSecurityScanEnabled = this.isSelected
                        }
                        name = ProductType.CODE_SECURITY.toString()
                    }
                label("").component.convertIntoHelpHintLabel(ProductType.CODE_SECURITY.description)

                if (!simplifyForOnboardPanel) {
                    codeQualityCheckbox = checkBox(
                        text = ProductType.CODE_QUALITY.productSelectionName,
                        getter = { settings.snykCodeQualityIssuesScanEnable },
                        setter = { settings.snykCodeQualityIssuesScanEnable = it }
                    )
                        .withLargeLeftGap()
                        .component.apply {
                            this.addItemListener {
                                isLastProductDisabling(this, currentCodeQualityScanEnabled)
                                currentCodeQualityScanEnabled = this.isSelected
                            }
                            name = ProductType.CODE_QUALITY.toString()
                        }
                    label("").component.convertIntoHelpHintLabel(ProductType.CODE_QUALITY.description)
                }
            }
        }
        row {
            snykCodeComment = label("")
                .withLargeLeftGap()
                .component.apply {
                    foreground = UIUtil.getContextHelpForeground()
                }

            codeSecurityCheckbox?.addItemListener {
                snykCodeComment?.isVisible = shouldSnykCodeCommentBeVisible()
            }
            codeQualityCheckbox?.addItemListener {
                snykCodeComment?.isVisible = shouldSnykCodeCommentBeVisible()
            }
            runBackgroundableTask("Checking Snyk Code enablement in organisation", project, true) {
                checkSastEnabled()
            }
        }
    }.apply {
        name = "scanTypesPanel"
        border = JBUI.Borders.empty(2)
    }

    private fun JLabel.convertIntoHelpHintLabel(text: String) {
        icon = AllIcons.General.ContextHelp
        addMouseListener(ShowHintMouseAdapter(this, text))
    }

    private var currentHint: Balloon? = null

    inner class ShowHintMouseAdapter(val component: Component, val text: String) : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
            currentHint?.hide()
            currentHint = SnykBalloonNotificationHelper.showInfoBalloonForComponent(text, component, true)
        }
    }

    fun checkSastEnabled() {
        setSnykCodeAvailability(false)
        if (pluginSettings().token.isNullOrBlank()) {
            showSnykCodeAlert("A Snyk Token is necessary to check for Snyk Code enablement.")
            return
        }

        var sastSettingsError: String = ""
        val sastCliConfigSettings: CliConfigSettings? =
        try {
            getSnykApiService().getSastSettings()
        } catch (t: ClientException) {
            val defaultErrorMsg = "Your org's SAST settings are misconfigured."
            val userMessage = if (t.message.isNullOrEmpty()) defaultErrorMsg else t.message!!
            SnykBalloonNotificationHelper.showError(userMessage, null)
            sastSettingsError = userMessage;
            null
        }

        settings.sastOnServerEnabled = sastCliConfigSettings?.sastEnabled
        settings.localCodeEngineEnabled = sastCliConfigSettings?.localCodeEngine?.enabled
        settings.localCodeEngineUrl = sastCliConfigSettings?.localCodeEngine?.url
        val snykCodeAvailable = isSnykCodeAvailable(settings.customEndpointUrl)
        if (sastSettingsError.isNotEmpty()) {
            showSnykCodeAlert(sastSettingsError)
        }
        showSnykCodeAlert(
            if (sastSettingsError.isNotEmpty()) sastSettingsError else ""
        )
        if (snykCodeAvailable) {
            setSnykCodeComment(progressMessage = "Checking if Snyk Code enabled for organisation...") {

                when (settings.sastOnServerEnabled) {
                    true -> {
                        doShowFilesToUpload()
                    }

                    false -> {
                        settings.snykCodeSecurityIssuesScanEnable = false
                        settings.snykCodeQualityIssuesScanEnable = false
                        showSnykCodeAlert(
                            message = "Snyk Code is disabled by your organisation's configuration: ",
                            linkText = "Snyk > Settings > Snyk Code",
                            url = toSnykCodeSettingsUrl(settings.customEndpointUrl),
                            runOnClick = {
                                startSastEnablementCheckLoop(
                                    parentDisposable,
                                    onSuccess = { doShowFilesToUpload() }
                                )
                            }
                        )
                    }

                    null -> {
                        settings.snykCodeSecurityIssuesScanEnable = false
                        settings.snykCodeQualityIssuesScanEnable = false
                        showSnykCodeAlert(
                            message = "Not able to connect to Snyk server. Check your connection and network settings."
                        )
                    }
                }
                null
            }
        }
    }

    private fun doShowFilesToUpload() {
        setSnykCodeAvailability(true)
        showSnykCodeAlert("")
        setSnykCodeComment("Checking number of files to be uploaded...") {
            getUploadingFilesMessage()
        }
    }

    private fun shouldSnykCodeCommentBeVisible() =
        codeSecurityCheckbox?.isSelected == true || codeQualityCheckbox?.isSelected == true

    private fun getUploadingFilesMessage(): String {
        val allSupportedFilesInProject =
            SnykCodeUtils.instance.getAllSupportedFilesInProject(project, true, null)
        val allSupportedFilesCount = allSupportedFilesInProject.size
        val allFilesCount = SnykCodeUtils.instance.allProjectFilesCount(project)
        return "We will upload and analyze $allSupportedFilesCount files " +
            "(${(100.0 * allSupportedFilesCount / allFilesCount).toInt()}%)" +
            " out of $allFilesCount files"
    }

    private fun setSnykCodeComment(progressMessage: String, messageProducer: () -> String?) {
        snykCodeComment?.isVisible = true
        setLabelMessage(snykCodeComment, progressMessage) {
            val result = messageProducer.invoke()
            snykCodeComment?.isVisible = shouldSnykCodeCommentBeVisible()
            result
        }
    }

    private fun setLabelMessage(label: JLabel?, progressMessage: String, messageProducer: () -> String?) {
        label?.text = progressMessage
        runBackgroundable({ messageProducer.invoke()?.let { label?.text = it } })
    }

    // We have to do background task run through Alarm on Alarm.ThreadToUse.POOLED_THREAD due to next (Idea?) bug:
    // Creation of Task.Backgroundable under another Task.Backgroundable does not work for Settings dialog,
    // it postpones inner Background task execution till Setting dialog exit
    private fun runBackgroundable(runnable: () -> Unit, delayMillis: Int = 10) {
        if (!alarm.isDisposed) {
            alarm.addRequest(runnable, delayMillis)
        }
    }

    private var currentHyperlinkListener: HyperlinkListener? = null

    private fun showSnykCodeAlert(
        message: String,
        linkText: String = "",
        url: String = "",
        runOnClick: (() -> Unit)? = null
    ) {
        val showAlert = message.isNotEmpty()
        if (simplifyForOnboardPanel) {
            codeSecurityCheckbox?.text =
                ProductType.CODE_SECURITY.productSelectionName +
                    if (showAlert) " (you can enable it later in the Settings)" else ""
        } else {
            codeAlertPanel.isVisible = showAlert
            // todo: change to setTextWithHyperlink() after move to sinceId >= 211
            snykCodeAlertHyperLinkLabel.setHyperlinkText(message, linkText, "")
            snykCodeAlertHyperLinkLabel.setHyperlinkTarget(url)
            if (runOnClick == null) {
                currentHyperlinkListener?.let { snykCodeAlertHyperLinkLabel.removeHyperlinkListener(it) }
            } else {
                currentHyperlinkListener = HyperlinkListener {
                    if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                        runOnClick.invoke()
                    }
                }
                snykCodeAlertHyperLinkLabel.addHyperlinkListener(currentHyperlinkListener)
            }
        }
    }

    private fun setSnykCodeAvailability(available: Boolean) {
        val enabled = (settings.sastOnServerEnabled == true) && available
        codeSecurityCheckbox?.let {
            it.isEnabled = enabled
            it.isSelected = enabled && settings.snykCodeSecurityIssuesScanEnable
        }
        codeQualityCheckbox?.let {
            it.isEnabled = enabled
            it.isSelected = enabled && settings.snykCodeQualityIssuesScanEnable
        }
    }

    private fun isLastProductDisabling(component: JBCheckBox, wasEnabled: Boolean): Boolean {
        val onlyOneEnabled = arrayOf(
            currentOssScanEnabled,
            currentCodeSecurityScanEnabled,
            currentCodeQualityScanEnabled,
            currentIacScanPanelEnabled,
            currentContainerScanEnabled
        ).count { it } == 1

        if (onlyOneEnabled && wasEnabled) {
            component.isSelected = true
            SnykBalloonNotificationHelper.showWarnBalloonForComponent(
                "At least one Scan type should be enabled",
                component
            )
        }
        return onlyOneEnabled
    }
}
