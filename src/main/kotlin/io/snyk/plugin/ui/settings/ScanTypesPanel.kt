package io.snyk.plugin.ui.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.getSnykApiService
import io.snyk.plugin.isSnykCodeLSEnabled
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
import java.awt.event.ItemEvent
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

    val codeAlertPanel = panel {
        row {
            cell(snykCodeAlertHyperLinkLabel)
            cell(snykCodeReCheckLinkLabel)
        }
    }.apply {
        border = JBUI.Borders.empty()
        this.isVisible = false
    }

    val panel = panel {
        row {
            checkBox(ProductType.OSS.productSelectionName).applyToComponent {
                name = text
                cliScanComments?.let { comment(it) }
                label("").component.convertIntoHelpHintLabel(ProductType.OSS.description)
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                // we need to change the settings in here in order for the validation to work pre-apply
                settings.ossScanEnable = hasBeenSelected
                isLastProductDisabling(it, !hasBeenSelected)
            }
            // bindSelected is needed to trigger apply() on the settings dialog that this panel is rendered in
            // that way we trigger the re-rendering of the Tree Nodes
            .bindSelected(settings::ossScanEnable)
        }
        row {
            checkBox(ProductType.ADVISOR.productSelectionName).applyToComponent {
                name = text
                label("").component.convertIntoHelpHintLabel(ProductType.ADVISOR.description)
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                settings.advisorEnable = hasBeenSelected
                isLastProductDisabling(it, !hasBeenSelected)
            }.bindSelected(settings::advisorEnable)
        }
        row {
            checkBox(ProductType.IAC.productSelectionName).applyToComponent {
                name = text
                label("").component.convertIntoHelpHintLabel(ProductType.IAC.description)
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                settings.iacScanEnabled = hasBeenSelected
                isLastProductDisabling(it, !hasBeenSelected)
            }
            .bindSelected(settings::iacScanEnabled)
        }
        row {
            checkBox(ProductType.CONTAINER.productSelectionName).applyToComponent {
                name = text
                label("").component.convertIntoHelpHintLabel(ProductType.CONTAINER.description)
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                settings.containerScanEnabled = hasBeenSelected
                isLastProductDisabling(it, !hasBeenSelected)
                val imagesCache = getKubernetesImageCache(project)
                if (hasBeenSelected) imagesCache?.cacheKubernetesFileFromProject() else imagesCache?.clear()
            }
            .bindSelected(settings::containerScanEnabled)
        }
        row {
            checkBox(ProductType.CODE_SECURITY.productSelectionName).applyToComponent {
                name = text
                codeSecurityCheckbox = this
                label("").component.convertIntoHelpHintLabel(ProductType.CODE_SECURITY.description)
                isSelected = settings.snykCodeSecurityIssuesScanEnable
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                settings.snykCodeSecurityIssuesScanEnable = hasBeenSelected
                isLastProductDisabling(it, !hasBeenSelected)
                snykCodeComment?.isVisible = shouldSnykCodeCommentBeVisible()
            }
            .bindSelected(settings::snykCodeSecurityIssuesScanEnable)
            checkBox(ProductType.CODE_QUALITY.productSelectionName).applyToComponent {
                name = text
                codeQualityCheckbox = this
                label("").component.convertIntoHelpHintLabel(ProductType.CODE_QUALITY.description)
            }
            .actionListener{ event, it ->
                val hasBeenSelected = it.isSelected
                settings.snykCodeQualityIssuesScanEnable = hasBeenSelected
                isLastProductDisabling(it, !hasBeenSelected)
                snykCodeComment?.isVisible = shouldSnykCodeCommentBeVisible()
            }
            .bindSelected(settings::snykCodeQualityIssuesScanEnable)
        }
        row {
            snykCodeComment = label("")
                .component.apply {
                    foreground = UIUtil.getContextHelpForeground()
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

        var sastSettingsError = ""
        val sastCliConfigSettings: CliConfigSettings? = try {
            val sastSettings = getSnykApiService().getSastSettings()
            settings.sastSettingsError = false
            sastSettings
        } catch (t: ClientException) {
            settings.sastSettingsError = true
            val defaultErrorMsg = "Your org's SAST settings are misconfigured."
            val userMessage = if (t.message.isNullOrEmpty()) defaultErrorMsg else t.message!!
            SnykBalloonNotificationHelper.showError(userMessage, null)
            sastSettingsError = userMessage
            null
        }

        settings.sastOnServerEnabled = sastCliConfigSettings?.sastEnabled
        settings.localCodeEngineEnabled = sastCliConfigSettings?.localCodeEngine?.enabled
        settings.localCodeEngineUrl = sastCliConfigSettings?.localCodeEngine?.url
        val snykCodeAvailable = isSnykCodeAvailable(settings.customEndpointUrl)
        showSnykCodeAlert(
            sastSettingsError.ifEmpty { "" }
        )
        if (snykCodeAvailable) {
            setSnykCodeComment(progressMessage = "Checking if Snyk Code enabled for organisation...") {
                when (settings.sastOnServerEnabled) {
                    true -> {
                        doShowFilesToUpload()
                    }

                    false -> {
                        disableSnykCode()
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
                        disableSnykCode()
                        showSnykCodeAlert(
                            message = "Not able to connect to Snyk server. Check your connection and network settings."
                        )
                    }
                }
                null
            }
        }
    }

    private fun disableSnykCode() {
        codeSecurityCheckbox?.isSelected = false
        codeQualityCheckbox?.isSelected = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
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
        // FIXME: implement count of files
        if (isSnykCodeLSEnabled()) return "Snyk Code Language Server is enabled. Count of files is not implemented yet."
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
            snykCodeAlertHyperLinkLabel.setTextWithHyperlink("$message<hyperlink>$linkText</hyperlink>")
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
        }
        codeQualityCheckbox?.let {
            it.isEnabled = enabled
        }
    }

    private fun isLastProductDisabling(component: JBCheckBox, deselected: Boolean): Boolean {
        val zeroEnabled = arrayOf(
            settings.ossScanEnable,
            settings.snykCodeSecurityIssuesScanEnable,
            settings.snykCodeQualityIssuesScanEnable,
            settings.iacScanEnabled,
            settings.containerScanEnabled,
            settings.advisorEnable,
        ).count { it } == 0

        if (zeroEnabled && deselected) {
            component.isSelected = true
            SnykBalloonNotificationHelper.showWarnBalloonForComponent(
                "At least one Scan type should be enabled",
                component
            )
        }
        return zeroEnabled
    }
}
