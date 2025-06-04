package io.snyk.plugin.ui.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.FontUtil
import com.intellij.util.progress.sleepCancellable
import com.intellij.util.ui.JBUI
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.runInBackground
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import snyk.common.ProductType
import snyk.common.isSnykCodeAvailable
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.toSnykCodeSettingsUrl
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

class ScanTypesPanel(
    private val project: Project,
    cliScanComments: String? = null,
) {
    private val settings
        get() = pluginSettings()

    private var currentOSSScanEnabled = settings.ossScanEnable
    private var currentSnykCodeSecurityScanEnabled = settings.snykCodeSecurityIssuesScanEnable
    private var currentIaCScanEnabled = settings.iacScanEnabled
    private var codeSecurityCheckbox: JBCheckBox? = null
    private var snykCodeComment: JLabel? = null
    private var snykCodeAlertHyperLinkLabel = HyperlinkLabel()
    private var snykCodeReCheckLinkLabel = ActionLink("Check again") {
        runBackgroundableTask("Checking Snyk Code enablement in organisation", project, true) {
            checkSastEnabled()
        }
    }

    val scanTypesPanel = panel {
        getOSSCheckbox(cliScanComments)
        getIaCCheckbox()
        getCodeCheckbox()
        row {
            cell(snykCodeAlertHyperLinkLabel).applyToComponent { font = FontUtil.minusOne(font) }
            cell(snykCodeReCheckLinkLabel).applyToComponent { font = FontUtil.minusOne(font) }
        }
        runBackgroundableTask("Checking Snyk Code enablement in organisation", project, true) {
            checkSastEnabled()
        }
    }.apply {
        name = "scanTypesPanel"
        border = JBUI.Borders.empty(2)
    }

    private fun Panel.getCodeCheckbox() {
        row {
            checkBox(ProductType.CODE_SECURITY.productSelectionName).applyToComponent {
                name = text
                codeSecurityCheckbox = this
                label("").component.convertIntoHelpHintLabel(ProductType.CODE_SECURITY.description)
                isSelected = settings.snykCodeSecurityIssuesScanEnable
            }
                .actionListener { _, it ->
                    val isSelected = it.isSelected
                    if (canBeChanged(it)) {
                        currentSnykCodeSecurityScanEnabled = isSelected
                        snykCodeComment?.isVisible = shouldSnykCodeCommentBeVisible()
                    }
                }
                .bindSelected(settings::snykCodeSecurityIssuesScanEnable)
        }
    }

    private fun Panel.getIaCCheckbox() {
        row {
            checkBox(ProductType.IAC.productSelectionName).applyToComponent {
                name = text
                label("").component.convertIntoHelpHintLabel(ProductType.IAC.description)
            }
                .actionListener { _, it ->
                    val isSelected = it.isSelected
                    if (canBeChanged(it)) {
                        currentIaCScanEnabled = isSelected
                    }
                }
                .bindSelected(settings::iacScanEnabled)
        }
    }

    private fun Panel.getOSSCheckbox(cliScanComments: String?) {
        row {
            checkBox(ProductType.OSS.productSelectionName).applyToComponent {
                name = text
                cliScanComments?.let { comment(it) }
                label("").component.convertIntoHelpHintLabel(ProductType.OSS.description)
            }
                .actionListener { _, it ->
                    val isSelected = it.isSelected
                    if (canBeChanged(it)) {
                        // we need to change the settings in here in order for the validation to work pre-apply
                        currentOSSScanEnabled = isSelected
                    }
                }
                // bindSelected is needed to trigger isModified() and then apply() on the settings dialog that this panel is rendered in
                // that way we trigger the re-rendering of the Tree Nodes
                .bindSelected(settings::ossScanEnable)
        }
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

    // TODO move to LS
    fun checkSastEnabled() {
        if (pluginSettings().token.isNullOrBlank()) {
            showSnykCodeAlert("A Snyk Token is necessary to check for Snyk Code enablement.")
            return
        }
        val snykCodeAvailable = isSnykCodeAvailable(settings.customEndpointUrl)

        if (snykCodeAvailable) {
            showSnykCodeAlert("Checking if Snyk Code enabled for organisation...")
            var sastSettings = try {
                val sastSettings = LanguageServerWrapper.getInstance(project).getSastSettings()
                settings.sastSettingsError = false
                sastSettings
            } catch (t: RuntimeException) {
                settings.sastSettingsError = true
                val defaultErrorMsg = "Your org's SAST settings are misconfigured."
                val userMessage = if (t.message.isNullOrEmpty()) defaultErrorMsg else t.message!!
                showSnykCodeAlert(userMessage)
                null
            }

            updateSettingsWithSastSettings(sastSettings)

            if (sastSettings != null) {
                when (sastSettings.sastEnabled) {
                    true -> {
                        setSnykCodeAvailability(true)
                        val message =
                            "Snyk Code is enabled for your organisation ${settings.organization}. \nAutofix enabled: ${sastSettings.autofixEnabled}"
                        showSnykCodeAlert(message)
                    }

                    false -> {
                        disableSnykCode()
                        val baseMessage = "Periodically checking if Snyk Code is enabled on organization level..."
                        showSnykCodeAlert(
                            message = "Snyk Code is disabled by your organisation's configuration: ",
                            linkText = "Snyk > Settings > Snyk Code",
                            url = toSnykCodeSettingsUrl(settings.customEndpointUrl),
                            runOnClick = {
                                runInBackground(baseMessage, project, true) {
                                    val maxTries = 60
                                    for (i in 0..maxTries) {
                                        it.checkCanceled()
                                        it.text = "$baseMessage (tries: $i/$maxTries)"

                                        val enabled = try {
                                            it.text = "$baseMessage (tries: $i/$maxTries - calling API)"
                                            sastSettings = LanguageServerWrapper.getInstance(project).getSastSettings()
                                            updateSettingsWithSastSettings(sastSettings)
                                            sastSettings?.sastEnabled ?: false
                                        } catch (_: RuntimeException) {
                                            continue
                                        }
                                        setSnykCodeAvailability(enabled)
                                        if (enabled) break

                                        it.checkCanceled()
                                        val sleepTime = 2L
                                        it.text = "$baseMessage (tries: $i/$maxTries - sleeping for $sleepTime secs)"
                                        sleepCancellable(sleepTime * 1000)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun updateSettingsWithSastSettings(sastSettings: LanguageServerWrapper.SastSettings?) {
        settings.sastOnServerEnabled = sastSettings?.sastEnabled
        settings.localCodeEngineEnabled = sastSettings?.localCodeEngine?.enabled
        settings.localCodeEngineUrl = sastSettings?.localCodeEngine?.url
        settings.autofixEnabled = sastSettings?.autofixEnabled
    }

    private fun disableSnykCode() {
        codeSecurityCheckbox?.isSelected = false
        codeSecurityCheckbox?.isEnabled = false
        settings.snykCodeSecurityIssuesScanEnable = false
    }

    private fun shouldSnykCodeCommentBeVisible() =
        codeSecurityCheckbox?.isSelected == true

    private var currentHyperlinkListener: HyperlinkListener? = null

    private fun showSnykCodeAlert(
        message: String,
        linkText: String = "",
        url: String = "",
        runOnClick: (() -> Unit)? = null
    ) {
        val showAlert = message.isNotEmpty()
        snykCodeAlertHyperLinkLabel.isVisible = showAlert
        snykCodeReCheckLinkLabel.isVisible = showAlert
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

    private fun setSnykCodeAvailability(available: Boolean) {
        val enabled = (settings.sastOnServerEnabled == true) && available
        codeSecurityCheckbox?.let {
            it.isEnabled = enabled
        }
    }

    private fun canBeChanged(component: JBCheckBox): Boolean {
        val onlyOneEnabled = arrayOf(
            currentOSSScanEnabled,
            currentSnykCodeSecurityScanEnabled,
            currentIaCScanEnabled,
        ).count { it } == 1

        if (onlyOneEnabled && !component.isSelected) {
            SnykBalloonNotificationHelper.showWarnBalloonForComponent(
                "At least one Scan type should be enabled",
                component
            )
            component.isSelected = true
            return false
        }
        return true
    }
}
