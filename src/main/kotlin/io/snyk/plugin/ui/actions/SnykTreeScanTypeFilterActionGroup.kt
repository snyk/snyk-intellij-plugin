package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isContainerEnabled
import io.snyk.plugin.isIacEnabled
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.showSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.snykCodeAvailabilityPostfix
import snyk.common.ProductType

/**
 * Build Snyk tree Scan Types filter actions.
 */
class SnykTreeScanTypeFilterActionGroup : ActionGroup() {

    private val settings
        get() = pluginSettings()

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null && !project.isDisposed && !settings.token.isNullOrEmpty()
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> = listOfNotNull(
            createOssScanAction(),
            createSecurityIssuesScanAction(),
            createQualityIssuesScanAction(),
            if (isIacEnabled()) createIacScanAction() else null,
            if (isContainerEnabled()) createContainerScanAction() else null
        ).toTypedArray()

    private fun createScanFilteringAction(
        productType: ProductType,
        scanTypeAvailable: Boolean,
        resultsTreeFiltering: Boolean,
        setResultsTreeFiltering: (Boolean) -> Unit,
        availabilityPostfix: String = ""
    ): AnAction {
        val text =
            productType.treeName + availabilityPostfix.ifEmpty { if (scanTypeAvailable) "" else " (disabled in Settings)" }
        return object : ToggleAction(text) {

            override fun isSelected(e: AnActionEvent): Boolean = resultsTreeFiltering

            override fun setSelected(e: AnActionEvent, state: Boolean) {
                if (!scanTypeAvailable) {
                    showSettings(
                        project = e.project!!,
                        componentNameToFocus = productType.toString(),
                        componentHelpHint = productType.description
                    )
                    return
                }
                if (!state && isLastScanTypeDisabling(e)) return

                setResultsTreeFiltering(state)
                getSyncPublisher(e.project!!, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC)?.filtersChanged()
            }
        }
    }

    private fun createOssScanAction(): AnAction = createScanFilteringAction(
        productType = ProductType.OSS,
        scanTypeAvailable = settings.ossScanEnable,
        resultsTreeFiltering = settings.treeFiltering.ossResults,
        setResultsTreeFiltering = { settings.treeFiltering.ossResults = it }
    )

    private fun isSnykCodeAvailable(): Boolean = snykCodeAvailabilityPostfix().isEmpty()

    private fun createSecurityIssuesScanAction(): AnAction = createScanFilteringAction(
        productType = ProductType.CODE_SECURITY,
        scanTypeAvailable = settings.snykCodeSecurityIssuesScanEnable && isSnykCodeAvailable(),
        resultsTreeFiltering = settings.treeFiltering.codeSecurityResults,
        setResultsTreeFiltering = { settings.treeFiltering.codeSecurityResults = it },
        availabilityPostfix = snykCodeAvailabilityPostfix()
    )

    private fun createQualityIssuesScanAction(): AnAction = createScanFilteringAction(
        productType = ProductType.CODE_QUALITY,
        scanTypeAvailable = settings.snykCodeQualityIssuesScanEnable && isSnykCodeAvailable(),
        resultsTreeFiltering = settings.treeFiltering.codeQualityResults,
        setResultsTreeFiltering = { settings.treeFiltering.codeQualityResults = it },
        availabilityPostfix = snykCodeAvailabilityPostfix()
    )

    private fun createIacScanAction(): AnAction = createScanFilteringAction(
        productType = ProductType.IAC,
        scanTypeAvailable = settings.iacScanEnabled,
        resultsTreeFiltering = settings.treeFiltering.iacResults,
        setResultsTreeFiltering = { settings.treeFiltering.iacResults = it }
    )

    private fun createContainerScanAction(): AnAction = createScanFilteringAction(
        productType = ProductType.CONTAINER,
        scanTypeAvailable = settings.containerScanEnabled,
        resultsTreeFiltering = settings.treeFiltering.containerResults,
        setResultsTreeFiltering = { settings.treeFiltering.containerResults = it }
    )

    private fun isLastScanTypeDisabling(e: AnActionEvent): Boolean {
        val onlyOneEnabled = arrayOf(
            settings.ossScanEnable && settings.treeFiltering.ossResults,
            settings.snykCodeSecurityIssuesScanEnable && settings.treeFiltering.codeSecurityResults,
            settings.snykCodeQualityIssuesScanEnable && settings.treeFiltering.codeQualityResults,
            settings.iacScanEnabled && settings.treeFiltering.iacResults,
            settings.containerScanEnabled && settings.treeFiltering.containerResults
        ).count { it } == 1
        if (onlyOneEnabled) {
            val message = "At least one Scan type should be enabled and selected"
            SnykBalloonNotificationHelper.showWarnBalloonAtEventPlace(message, e)
        }
        return onlyOneEnabled
    }
}
