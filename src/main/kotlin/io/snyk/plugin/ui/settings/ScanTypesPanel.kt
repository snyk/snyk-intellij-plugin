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
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import org.jetbrains.concurrency.runAsync
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
    private var currentSnykCodeQualityScanEnabled = settings.snykCodeQualityIssuesScanEnable
    private var currentIaCScanEnabled = settings.iacScanEnabled
    private var currentContainerScanEnabled = settings.containerScanEnabled

    private var codeSecurityCheckbox: JBCheckBox? = null
    private var codeQualityCheckbox: JBCheckBox? = null
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
        getContainerCheckbox()
        getCodeCheckboxes()
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

    private fun Panel.getCodeCheckboxes() {
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

            checkBox(ProductType.CODE_QUALITY.productSelectionName).applyToComponent {
                name = text
                codeQualityCheckbox = this
                label("").component.convertIntoHelpHintLabel(ProductType.CODE_QUALITY.description)
            }
                .actionListener { _, it ->
                    val isSelected = it.isSelected
                    if (canBeChanged(it)) {
                        currentSnykCodeQualityScanEnabled = isSelected
                    }
                }
                .bindSelected(settings::snykCodeQualityIssuesScanEnable)
        }
    }

    private fun Panel.getContainerCheckbox() {
        row {
            checkBox(ProductType.CONTAINER.productSelectionName).applyToComponent {
                name = text
                label("").component.convertIntoHelpHintLabel(ProductType.CONTAINER.description)
            }
                .actionListener { _, it ->
                    val isSelected = it.isSelected
                    if (canBeChanged(it)) {
                        currentContainerScanEnabled = isSelected
                        val imagesCache = getKubernetesImageCache(project)
                        if (isSelected) imagesCache?.cacheKubernetesFileFromProject() else imagesCache?.clear()
                    }
                }
                .bindSelected(settings::containerScanEnabled)
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
                val sastSettings = LanguageServerWrapper.getInstance().getSastSettings()
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
                            showSnykCodeAlert(
                                message = "Snyk Code is disabled by your organisation's configuration: ",
                                linkText = "Snyk > Settings > Snyk Code",
                                url = toSnykCodeSettingsUrl(settings.customEndpointUrl),
                                runOnClick = {
                                    runAsync {
                                        for (i in 0..20) {
                                            val enabled = try {
                                                sastSettings = LanguageServerWrapper.getInstance().getSastSettings()
                                                updateSettingsWithSastSettings(sastSettings)
                                                sastSettings?.sastEnabled ?: false
                                            } catch (ignored: RuntimeException) {
                                                continue
                                            }
                                            setSnykCodeAvailability(enabled)
                                            if (enabled) break
                                            sleepCancellable(1000)
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
        codeQualityCheckbox?.isSelected = false
        codeQualityCheckbox?.isEnabled = false
        codeSecurityCheckbox?.isEnabled = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
    }

    private fun shouldSnykCodeCommentBeVisible() =
        codeSecurityCheckbox?.isSelected == true || codeQualityCheckbox?.isSelected == true

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
        codeQualityCheckbox?.let {
            it.isEnabled = enabled
        }
    }

    private fun canBeChanged(component: JBCheckBox): Boolean {
        val onlyOneEnabled = arrayOf(
            currentOSSScanEnabled,
            currentSnykCodeSecurityScanEnabled,
            currentSnykCodeQualityScanEnabled,
            currentIaCScanEnabled,
            currentContainerScanEnabled,
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
