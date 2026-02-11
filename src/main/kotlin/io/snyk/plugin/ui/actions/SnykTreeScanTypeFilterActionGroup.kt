package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.publishAsync
import io.snyk.plugin.showSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.snykCodeAvailabilityPostfix
import snyk.common.ProductType

/** Build Snyk tree Scan Types filter actions. */
class SnykTreeScanTypeFilterActionGroup : ActionGroup() {

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private val settings
    get() = pluginSettings()

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled =
      project != null && !project.isDisposed && !settings.token.isNullOrEmpty()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> =
    listOfNotNull(createOssScanAction(), createSecurityIssuesScanAction(), createIacScanAction())
      .toTypedArray()

  private fun createScanFilteringAction(
    productType: ProductType,
    scanTypeAvailable: Boolean,
    resultsTreeFiltering: Boolean,
    setResultsTreeFiltering: (Boolean) -> Unit,
    availabilityPostfix: String = "",
  ): AnAction {
    val text =
      productType.treeName +
        availabilityPostfix.ifEmpty { if (scanTypeAvailable) "" else " (disabled in Settings)" }
    return object : ToggleAction(text) {

      override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
      }

      override fun isSelected(e: AnActionEvent): Boolean = resultsTreeFiltering

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        if (!scanTypeAvailable) {
          showSettings(
            project = e.project!!,
            componentNameToFocus = productType.toString(),
            componentHelpHint = productType.description,
          )
          return
        }
        if (!state && isLastScanTypeDisabling(e)) return

        setResultsTreeFiltering(state)
        publishAsync(e.project!!, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC) {
          filtersChanged()
        }
      }
    }
  }

  private fun createOssScanAction(): AnAction =
    createScanFilteringAction(
      productType = ProductType.OSS,
      scanTypeAvailable = settings.ossScanEnable,
      resultsTreeFiltering = settings.treeFiltering.ossResults,
      setResultsTreeFiltering = { settings.treeFiltering.ossResults = it },
    )

  private fun createSecurityIssuesScanAction(): AnAction =
    createScanFilteringAction(
      productType = ProductType.CODE_SECURITY,
      scanTypeAvailable = settings.snykCodeSecurityIssuesScanEnable,
      resultsTreeFiltering = settings.treeFiltering.codeSecurityResults,
      setResultsTreeFiltering = { settings.treeFiltering.codeSecurityResults = it },
      availabilityPostfix = snykCodeAvailabilityPostfix(),
    )

  private fun createIacScanAction(): AnAction =
    createScanFilteringAction(
      productType = ProductType.IAC,
      scanTypeAvailable = settings.iacScanEnabled,
      resultsTreeFiltering = settings.treeFiltering.iacResults,
      setResultsTreeFiltering = { settings.treeFiltering.iacResults = it },
    )

  private fun isLastScanTypeDisabling(e: AnActionEvent): Boolean {
    val onlyOneEnabled =
      arrayOf(
          settings.ossScanEnable && settings.treeFiltering.ossResults,
          settings.snykCodeSecurityIssuesScanEnable && settings.treeFiltering.codeSecurityResults,
          settings.iacScanEnabled && settings.treeFiltering.iacResults,
        )
        .count { it } == 1
    if (onlyOneEnabled) {
      val message = "At least one Scan type should be enabled and selected"
      SnykBalloonNotificationHelper.showWarnBalloonAtEventPlace(message, e)
    }
    return onlyOneEnabled
  }
}
