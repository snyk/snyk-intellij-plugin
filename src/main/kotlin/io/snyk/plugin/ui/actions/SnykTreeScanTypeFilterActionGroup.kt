package io.snyk.plugin.ui.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.publishAsync
import io.snyk.plugin.runInBackground
import io.snyk.plugin.showSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.snykCodeAvailabilityPostfix
import snyk.common.ProductType
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.settings.FolderConfigSettings

/** Build Snyk tree Scan Types filter actions. */
class SnykTreeScanTypeFilterActionGroup : ActionGroup() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private val settings
    get() = pluginSettings()

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled =
      project != null && !project.isDisposed && !settings.token.isNullOrEmpty()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project
    return listOfNotNull(
        createOssScanAction(project),
        createSecurityIssuesScanAction(project),
        createIacScanAction(project),
      )
      .toTypedArray()
  }

  private fun createScanFilteringAction(
    productType: ProductType,
    globalScanTypeAvailable: Boolean,
    project: Project?,
    resultsTreeFiltering: Boolean,
    setResultsTreeFiltering: (Boolean) -> Unit,
    availabilityPostfix: String = "",
  ): AnAction {
    val scanTypeAvailable =
      if (project != null && !project.isDisposed) {
        service<FolderConfigSettings>()
          .isProductEnabledForProjectToolWindow(productType, project, globalScanTypeAvailable)
      } else {
        globalScanTypeAvailable
      }
    val text =
      productType.treeName +
        availabilityPostfix.ifEmpty { if (scanTypeAvailable) "" else " (disabled in Settings)" }
    return object : ToggleAction(text) {

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

      override fun isSelected(e: AnActionEvent): Boolean = resultsTreeFiltering

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        val eventProject = e.project ?: return
        if (!scanTypeAvailable) {
          // Server-side gating (e.g. SAST disabled in Snyk.io / unreachable / misconfigured) can't
          // be fixed from the toolbar — fall back to the Settings dialog so the user can act.
          if (availabilityPostfix.isNotEmpty()) {
            showSettings(
              project = eventProject,
              componentNameToFocus = productType.toString(),
              componentHelpHint = productType.description,
            )
            return
          }
          // Folder-only by design: re-enable the product per-folder so the next config push tells
          // snyk-ls to scan it. If the project has no folder configs yet (LS still bootstrapping),
          // silently no-op rather than mutating app-level product flags.
          if (!state) return
          val applied =
            service<FolderConfigSettings>()
              .setProductEnabledForProject(eventProject, productType, true)
          if (!applied) return
          setResultsTreeFiltering(true)
          runInBackground("Snyk: updating configuration", eventProject) {
            LanguageServerWrapper.getInstance(eventProject).updateConfiguration()
          }
          return
        }
        if (!state && isLastScanTypeDisabling(e)) return

        // Tree-filter-only toggle (product remains enabled in plugin state); no LS push needed.
        setResultsTreeFiltering(state)
        publishAsync(eventProject, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC) {
          filtersChanged()
        }
      }
    }
  }

  private fun createOssScanAction(project: Project?): AnAction =
    createScanFilteringAction(
      productType = ProductType.OSS,
      globalScanTypeAvailable = settings.ossScanEnable,
      project = project,
      resultsTreeFiltering = settings.treeFiltering.ossResults,
      setResultsTreeFiltering = { settings.treeFiltering.ossResults = it },
    )

  private fun createSecurityIssuesScanAction(project: Project?): AnAction =
    createScanFilteringAction(
      productType = ProductType.CODE_SECURITY,
      globalScanTypeAvailable = settings.snykCodeSecurityIssuesScanEnable,
      project = project,
      resultsTreeFiltering = settings.treeFiltering.codeSecurityResults,
      setResultsTreeFiltering = { settings.treeFiltering.codeSecurityResults = it },
      availabilityPostfix = snykCodeAvailabilityPostfix(),
    )

  private fun createIacScanAction(project: Project?): AnAction =
    createScanFilteringAction(
      productType = ProductType.IAC,
      globalScanTypeAvailable = settings.iacScanEnabled,
      project = project,
      resultsTreeFiltering = settings.treeFiltering.iacResults,
      setResultsTreeFiltering = { settings.treeFiltering.iacResults = it },
    )

  private fun isLastScanTypeDisabling(e: AnActionEvent): Boolean {
    val project = e.project
    val fcs = if (project != null && !project.isDisposed) service<FolderConfigSettings>() else null
    val ossAvailable =
      fcs?.isProductEnabledForProjectToolWindow(ProductType.OSS, project!!, settings.ossScanEnable)
        ?: settings.ossScanEnable
    val codeAvailable =
      fcs?.isProductEnabledForProjectToolWindow(
        ProductType.CODE_SECURITY,
        project!!,
        settings.snykCodeSecurityIssuesScanEnable,
      ) ?: settings.snykCodeSecurityIssuesScanEnable
    val iacAvailable =
      fcs?.isProductEnabledForProjectToolWindow(ProductType.IAC, project!!, settings.iacScanEnabled)
        ?: settings.iacScanEnabled
    val onlyOneEnabled =
      arrayOf(
          ossAvailable && settings.treeFiltering.ossResults,
          codeAvailable && settings.treeFiltering.codeSecurityResults,
          iacAvailable && settings.treeFiltering.iacResults,
        )
        .count { it } == 1
    if (onlyOneEnabled) {
      val message = "At least one Scan type should be enabled and selected"
      SnykBalloonNotificationHelper.showWarnBalloonAtEventPlace(message, e)
    }
    return onlyOneEnabled
  }
}
