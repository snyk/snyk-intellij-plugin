package io.snyk.plugin.ui.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.layout.panel
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import io.snyk.plugin.getSnykCodeSettingsUrl
import io.snyk.plugin.isIacEnabled
import io.snyk.plugin.isSnykCodeAvailable
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApiService
import io.snyk.plugin.snykcode.core.SnykCodeUtils
import io.snyk.plugin.startSastEnablementCheckLoop
import io.snyk.plugin.ui.SnykBalloonNotifications
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

    private var snykCodeCheckbox: JBCheckBox? = null
    private var snykCodeQualityCheckbox: JBCheckBox? = null
    private var snykCodeComment: JLabel? = null
    private var snykCodeAlertHyperLinkLabel = HyperlinkLabel().apply {
        this.isVisible = false
    }
    private var snykCodeReCheckLinkLabel = LinkLabel.create("Check again") {
        checkSastEnable()
    }.apply {
        this.isVisible = false
    }

    val panel = panel {
        row {
            cell {
                checkBox(
                    "Snyk Open Source vulnerabilities",
                    { settings.ossScanEnable },
                    { settings.ossScanEnable = it },
                    cliScanComments
                )
                label("").component.convertIntoHelpHintLabel(
                    "Find and automatically fix open source vulnerabilities"
                )
            }
        }
        row {
            cell {
                checkBox(
                    "Snyk Advisor (early preview)",
                    { settings.advisorEnable },
                    { settings.advisorEnable = it },
                    null
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
                        "Snyk Infrastructure as Code issues",
                        { settings.iacScanEnabled },
                        { settings.iacScanEnabled = it },
                        null
                    )
                    label("").component.convertIntoHelpHintLabel(
                        "Find and fix insecure configurations in Terraform and Kubernetes code"
                    )
                }
            }
        }
        row {
            cell {
                snykCodeCheckbox = checkBox(
                    SNYK_CODE_SECURITY_ISSUES,
                    { settings.snykCodeSecurityIssuesScanEnable },
                    { settings.snykCodeSecurityIssuesScanEnable = it }
                )
                    .component
                label("").component.convertIntoHelpHintLabel(
                    "Find and fix vulnerabilities in your application code in real time"
                )
                if (!simplifyForOnboardPanel) {
                    snykCodeQualityCheckbox = checkBox(
                        SNYK_CODE_QUALITY_ISSUES,
                        { settings.snykCodeQualityIssuesScanEnable },
                        { settings.snykCodeQualityIssuesScanEnable = it }
                    )
                        .withLargeLeftGap()
                        .component
                    label("").component.convertIntoHelpHintLabel(
                        "Find and fix code quality issues in your application code in real time"
                    )
                }
            }
        }
        row {
            snykCodeComment = label("")
                .withLargeLeftGap()
                .component.apply {
                    foreground = UIUtil.getContextHelpForeground()
                }

            snykCodeCheckbox?.addItemListener {
                snykCodeComment?.isVisible = shouldSnykCodeCommentBeVisible()
            }
            snykCodeQualityCheckbox?.addItemListener {
                snykCodeComment?.isVisible = shouldSnykCodeCommentBeVisible()
            }
            checkSastEnable()
        }
        row {
            cell {
                snykCodeAlertHyperLinkLabel()
                    .withLargeLeftGap()
                snykCodeReCheckLinkLabel()
                    .withLargeLeftGap()
            }
        }
    }.apply {
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
            currentHint = SnykBalloonNotifications.showInfoBalloonForComponent(text, component, true)
        }
    }

    private fun checkSastEnable() {
        setSnykCodeAvailability(false)
        val snykCodeAvailable = isSnykCodeAvailable(settings.customEndpointUrl)
        showSnykCodeAlert(
            if (snykCodeAvailable) "" else "Snyk Code only works in SAAS mode (i.e. no Custom Endpoint usage)"
        )
        if (snykCodeAvailable) {
            setSnykCodeComment(progressMessage = "Checking if Snyk Code enabled for organisation...") {
                settings.sastOnServerEnabled = service<SnykApiService>().sastOnServerEnabled
                when (settings.sastOnServerEnabled) {
                    true -> doShowFilesToUpload()
                    false -> {
                        settings.snykCodeSecurityIssuesScanEnable = false
                        settings.snykCodeQualityIssuesScanEnable = false
                        showSnykCodeAlert(
                            message = "Snyk Code is disabled by your organisation's configuration: ",
                            linkText = "Snyk > Settings > Snyk Code",
                            url = getSnykCodeSettingsUrl(),
                            runOnClick = { startSastEnablementCheckLoop(parentDisposable, onSuccess = { doShowFilesToUpload() }) }
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
        snykCodeCheckbox?.isSelected == true || snykCodeQualityCheckbox?.isSelected == true

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
        // We have to do background task run through Alarm on Alarm.ThreadToUse.POOLED_THREAD due to next (Idea?) bug:
        // Creation of Task.Backgroundable under another Task.Backgroundable does not work for Settings dialog,
        // it postpone inner Background task execution till Setting dialog exit
        alarm.addRequest({
            messageProducer.invoke()?.let { label?.text = it }
        }, 0)
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
            snykCodeCheckbox?.text = SNYK_CODE_SECURITY_ISSUES + if (showAlert) " (you can enable it later in the Settings)" else ""
        } else {
            snykCodeAlertHyperLinkLabel.isVisible = showAlert
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
            snykCodeReCheckLinkLabel.isVisible = showAlert
        }
    }

    private fun setSnykCodeAvailability(available: Boolean) {
        val enabled = (settings.sastOnServerEnabled == true) && available
        snykCodeCheckbox?.let {
            it.isEnabled = enabled
            it.isSelected = enabled && settings.snykCodeSecurityIssuesScanEnable
        }
        snykCodeQualityCheckbox?.let {
            it.isEnabled = enabled
            it.isSelected = enabled && settings.snykCodeQualityIssuesScanEnable
        }
    }

    companion object {
        private const val SNYK_CODE_SECURITY_ISSUES = "Snyk Code Security issues"
        private const val SNYK_CODE_QUALITY_ISSUES = "Snyk Code Quality issues"
    }
}
