package io.snyk.plugin.ui.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.layout.panel
import com.intellij.util.Alarm
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getSnykCodeSettingsUrl
import io.snyk.plugin.isSnykCodeAvailable
import io.snyk.plugin.services.SnykApiService
import io.snyk.plugin.snykcode.core.SnykCodeUtils
import io.snyk.plugin.startSastEnablementCheckLoop
import javax.swing.JLabel
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

class ScanTypesPanel(
    private val project: Project,
    private val parentDisposable: Disposable,
    cliScanComments: String? = null,
    snykCodeQualityIssueCheckboxVisible: Boolean = true
) {
    private val settings = getApplicationSettingsStateService()
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
            checkBox(
                "Snyk Open Source vulnerabilities",
                { settings.cliScanEnable },
                { settings.cliScanEnable = it },
                cliScanComments
            )
        }
        row {
            cell {
                snykCodeCheckbox = checkBox(
                    "Snyk Code Security issues",
                    { settings.snykCodeSecurityIssuesScanEnable },
                    { settings.snykCodeSecurityIssuesScanEnable = it }
                )
                    .component

                if (snykCodeQualityIssueCheckboxVisible) {
                    snykCodeQualityCheckbox = checkBox(
                        "Snyk Code Quality issues",
                        { settings.snykCodeQualityIssuesScanEnable },
                        { settings.snykCodeQualityIssuesScanEnable = it }
                    )
                        .withLargeLeftGap()
                        .component
                }
            }
        }
        row {
            snykCodeComment = label("")
                .withLargeLeftGap()
                .component

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
            getUploadingFilesMessage(false)
        }
    }

    private fun shouldSnykCodeCommentBeVisible() =
        snykCodeCheckbox?.isSelected == true || snykCodeQualityCheckbox?.isSelected == true

    private fun getUploadingFilesMessage(scanAllMissedIgnoreFile: Boolean = false): String {
        val allSupportedFilesInProject =
            SnykCodeUtils.instance.getAllSupportedFilesInProject(project, scanAllMissedIgnoreFile, null)
        val allSupportedFilesCount = allSupportedFilesInProject.size
        val allFilesCount = SnykCodeUtils.instance.allProjectFilesCount(project)
        return "We will upload and analyze up to $allSupportedFilesCount files " +
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
        snykCodeAlertHyperLinkLabel.isVisible = message.isNotEmpty()
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
        snykCodeReCheckLinkLabel.isVisible = message.isNotEmpty()
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
}
