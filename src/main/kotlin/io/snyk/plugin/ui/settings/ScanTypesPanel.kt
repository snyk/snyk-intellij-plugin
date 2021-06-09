package io.snyk.plugin.ui.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import com.intellij.util.Alarm
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.getSnykCodeSettingsUrl
import io.snyk.plugin.services.SnykApiService
import io.snyk.plugin.snykcode.core.SnykCodeUtils
import javax.swing.JLabel

class ScanTypesPanel(
    private val project: Project,
    parentDisposable: Disposable,
    cliScanComments: String? = null,
    snykCodeQualityIssueCheckboxVisible: Boolean = true
) {
    private val settings = getApplicationSettingsStateService()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable)

    private var snykCodeCheckbox: JBCheckBox? = null
    private var snykCodeQualityCheckbox: JBCheckBox? = null
    private var snykCodeComment: JLabel? = null
    private var snykCodeAlertLinkLabel = HyperlinkLabel().apply {
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

            setSnykCodeAvailability(false)
            setSnykCodeComment(progressMessage = "Checking if Snyk Code enabled for organisation...") {
                settings.sastOnServerEnabled =
                    service<SnykApiService>().sastOnServerEnabled ?: false

                if (settings.sastOnServerEnabled) {
                    doShowFilesToUpload()
                } else {
                    settings.snykCodeSecurityIssuesScanEnable = false
                    settings.snykCodeQualityIssuesScanEnable = false
                    showSnykCodeAlert(
                        message = "Snyk Code is disabled by your organisation's configuration. You can enable it by navigating to ",
                        linkText = "Snyk > Settings > Snyk Code",
                        url = getSnykCodeSettingsUrl(),
                        forceShow = true)

                    // check sastEnablement every 1 sec.
                    lateinit var checkIfSastEnabled: () -> Unit
                    checkIfSastEnabled = {
                        settings.sastOnServerEnabled = service<SnykApiService>().sastOnServerEnabled ?: false
                        if (settings.sastOnServerEnabled) {
                            doShowFilesToUpload()
                        } else if (!alarm.isDisposed) {
                            alarm.addRequest(checkIfSastEnabled, 1000)
                        }
                    }
                    checkIfSastEnabled.invoke()
                }
                null
            }
        }
        row {
            snykCodeAlertLinkLabel()
                .withLargeLeftGap()
        }
    }

    private fun doShowFilesToUpload() {
        setSnykCodeAvailability(true)
        showSnykCodeAlert("", forceShow = true)
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

    fun showSnykCodeAlert(message: String, linkText: String = "", url: String = "", forceShow: Boolean = false) {
        if (settings.sastOnServerEnabled || forceShow) {
            snykCodeAlertLinkLabel.isVisible = message.isNotEmpty()
            // todo: change to setTextWithHyperlink() after move to sinceId >= 211
            snykCodeAlertLinkLabel.setHyperlinkText(message, linkText, "")
            snykCodeAlertLinkLabel.setHyperlinkTarget(url)
        }
    }

    fun setSnykCodeAvailability(available: Boolean) {
        val enabled = settings.sastOnServerEnabled && available
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
