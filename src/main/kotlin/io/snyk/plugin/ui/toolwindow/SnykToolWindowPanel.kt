package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.events.SnykShowIssueDetailListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCachedResultsForProduct
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.isCliDownloading
import io.snyk.plugin.isScanRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.ui.toolwindow.panels.HtmlTreePanel
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanel
import io.snyk.plugin.ui.toolwindow.panels.SnykAuthPanel
import io.snyk.plugin.ui.toolwindow.panels.SnykErrorPanel
import io.snyk.plugin.ui.toolwindow.panels.StatePanel
import io.snyk.plugin.ui.toolwindow.panels.SuggestionDescriptionPanel
import io.snyk.plugin.ui.toolwindow.panels.SummaryPanel
import io.snyk.plugin.ui.wrapWithScrollPane
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.runAsync
import snyk.common.SnykError
import snyk.common.lsp.AiFixParams
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanIssue

/** Main panel for Snyk tool window. */
@Service(Service.Level.PROJECT)
class SnykToolWindowPanel(val project: Project) : JPanel(), Disposable {
  private val descriptionPanel =
    SimpleToolWindowPanel(true, true).apply { name = "descriptionPanel" }
  private val summaryPanel = SimpleToolWindowPanel(true, true).apply { name = "summaryPanel" }
  private var summaryPanelContent: SummaryPanel? = null
  private var htmlTreePanel: HtmlTreePanel? = null
  private val logger = Logger.getInstance(this::class.java)

  companion object {
    const val SELECT_ISSUE_TEXT = "Select an issue and start improving your project."
    const val SCAN_PROJECT_TEXT = "Scan your project for security vulnerabilities and code issues."
    const val SCANNING_TEXT = "Scanning project for vulnerabilities..."
    const val AUTH_FAILED_TEXT = "Authentication failed. Please check the API token on "
    private const val TOOL_WINDOW_SPLITTER_PROPORTION_KEY = "SNYK_TOOL_WINDOW_SPLITTER_PROPORTION"
    private const val TOOL_TREE_SPLITTER_PROPORTION_KEY = "SNYK_TOOL_TREE_SPLITTER_PROPORTION"
  }

  init {
    Disposer.register(SnykPluginDisposable.getInstance(project), this)

    layout = BorderLayout()
    createTreeAndDescriptionPanel()
    chooseMainPanelToDisplay()
    updateSummaryPanel()

    ApplicationManager.getApplication()
      .messageBus
      .connect(this)
      .subscribe(
        SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
        object : SnykCliDownloadListener {
          override fun checkCliExistsFinished() =
            ApplicationManager.getApplication().invokeLater { chooseMainPanelToDisplay() }

          override fun cliDownloadStarted() =
            ApplicationManager.getApplication().invokeLater { displayDownloadMessage() }
        },
      )

    project.messageBus
      .connect(this)
      .subscribe(
        SnykSettingsListener.SNYK_SETTINGS_TOPIC,
        object : SnykSettingsListener {
          override fun settingsChanged() =
            ApplicationManager.getApplication().invokeLater { chooseMainPanelToDisplay() }
        },
      )

    project.messageBus
      .connect(this)
      .subscribe(
        SnykTaskQueueListener.TASK_QUEUE_TOPIC,
        object : SnykTaskQueueListener {
          override fun stopped() =
            ApplicationManager.getApplication().invokeLater { displayEmptyDescription() }
        },
      )

    project.messageBus
      .connect(this)
      .subscribe(
        SnykShowIssueDetailListener.SHOW_ISSUE_DETAIL_TOPIC,
        object : SnykShowIssueDetailListener {
          override fun onShowIssueDetail(aiFixParams: AiFixParams) {
            val issueId = aiFixParams.issueId
            val product = aiFixParams.product
            if (logger.isDebugEnabled) {
              logger.debug("onShowIssueDetail: issueId=$issueId, product=$product")
            }
            getSnykCachedResultsForProduct(project, product)?.let { results ->
              if (logger.isDebugEnabled) {
                val issueCount = results.values.sumOf { it.size }
                logger.debug(
                  "onShowIssueDetail: cache has ${results.size} files, $issueCount issues"
                )
                val sampleIds = results.values.asSequence().flatten().take(5).map { it.id }.toList()
                logger.debug("onShowIssueDetail: first cached IDs: $sampleIds")
              }
              results.values
                .firstNotNullOfOrNull { issues -> issues.firstOrNull { it.id == issueId } }
                ?.let { scanIssue ->
                  if (logger.isDebugEnabled) logger.debug("onShowIssueDetail: found issue $issueId")
                  selectNodeAndDisplayDescription(scanIssue, forceRefresh = true)
                }
                ?: run {
                  if (logger.isDebugEnabled) {
                    logger.debug("Failed to find issue $issueId in $product cache")
                  }
                }
            }
          }
        },
      )
  }

  var isDisposed: Boolean = false

  override fun dispose() {
    isDisposed = true
    clearDescriptionPanel()
  }

  fun cleanUiAndCaches(resetSummaryPanel: Boolean = true) {
    getSnykCachedResults(project)?.clearCaches()
    doCleanUi(true)
    if (resetSummaryPanel) {
      invokeLater {
        if (isDisposed || project.isDisposed) return@invokeLater
        updateSummaryPanel()
      }
    }
    refreshAnnotationsForOpenFiles(project)
  }

  private fun doCleanUi(reDisplayDescription: Boolean) {
    htmlTreePanel?.reset()
    if (reDisplayDescription) {
      displayEmptyDescription()
    }
  }

  internal fun chooseMainPanelToDisplay() {
    val settings = pluginSettings()
    when {
      settings.token.isNullOrEmpty() -> displayAuthPanel()
      settings.pluginFirstRun -> {
        pluginSettings().pluginFirstRun = false
        displayEmptyDescription()
        runAsync {
          try {
            enableCodeScanAccordingToServerSetting()
          } catch (e: Exception) {
            invokeLater {
              displaySnykError(
                SnykError(e.message ?: "Exception while initializing plugin {${e.message}", "")
              )
            }
            logger.error("Failed to apply Snyk settings", e)
          }
        }
      }
      else -> displayEmptyDescription()
    }
  }

  private fun enableCodeScanAccordingToServerSetting() {
    pluginSettings().apply {
      try {
        val sastSettings = LanguageServerWrapper.getInstance(project).getSastSettings()
        sastOnServerEnabled = sastSettings?.sastEnabled ?: false
        val codeScanAllowed = sastOnServerEnabled == true
        snykCodeSecurityIssuesScanEnable = snykCodeSecurityIssuesScanEnable && codeScanAllowed
      } catch (clientException: RuntimeException) {
        logger.error(clientException)
      }
    }
  }

  private fun createTreeAndDescriptionPanel() {
    removeAll()
    val vulnerabilitiesSplitter = OnePixelSplitter(TOOL_WINDOW_SPLITTER_PROPORTION_KEY, 0.4f)
    add(vulnerabilitiesSplitter, BorderLayout.CENTER)

    val treeSplitter = OnePixelSplitter(true, TOOL_TREE_SPLITTER_PROPORTION_KEY, 0.25f)
    treeSplitter.firstComponent = summaryPanel

    htmlTreePanel = HtmlTreePanel(project)
    Disposer.register(this, htmlTreePanel!!)
    treeSplitter.secondComponent = htmlTreePanel

    vulnerabilitiesSplitter.firstComponent = treeSplitter
    vulnerabilitiesSplitter.secondComponent = descriptionPanel
  }

  private fun displayEmptyDescription() {
    when {
      isCliDownloading() -> displayDownloadMessage()
      pluginSettings().token.isNullOrEmpty() -> displayAuthPanel()
      isScanRunning(project) -> displayScanningMessage()
      noIssuesInAnyProductFound() -> displayNoVulnerabilitiesMessage()
      else -> displaySelectVulnerabilityMessage()
    }
  }

  private fun noIssuesInAnyProductFound(): Boolean {
    val cache = getSnykCachedResults(project) ?: return true

    fun Map<*, Set<*>>.hasNoIssues() = isEmpty() || values.all { it.isEmpty() }
    return cache.currentOSSResultsLS.hasNoIssues() &&
      cache.currentSnykCodeResultsLS.hasNoIssues() &&
      cache.currentIacResultsLS.hasNoIssues() &&
      cache.currentSecretsResultsLS.hasNoIssues()
  }

  private fun updateSummaryPanel() {
    this.summaryPanelContent?.let { Disposer.dispose(it) }
    val summaryPanelContent = SummaryPanel(project)
    this.summaryPanelContent = summaryPanelContent
    summaryPanel.removeAll()
    Disposer.register(this, summaryPanelContent)
    summaryPanel.add(summaryPanelContent)
    revalidate()
  }

  fun displayAuthPanel() {
    if (isDisposed) return
    doCleanUi(false)
    clearDescriptionPanel()
    val authPanel = SnykAuthPanel(project)
    Disposer.register(this, authPanel)
    descriptionPanel.add(authPanel, BorderLayout.CENTER)
    revalidate()
  }

  private fun displayNoVulnerabilitiesMessage() {
    clearDescriptionPanel()
    val emptyStatePanel =
      StatePanel(SCAN_PROJECT_TEXT, "Run Scan") { getSnykTaskQueueService(project)?.scan() }
    descriptionPanel.add(wrapWithScrollPane(emptyStatePanel), BorderLayout.CENTER)
    revalidate()
  }

  fun displayScanningMessage() {
    clearDescriptionPanel()
    val statePanel =
      StatePanel(SCANNING_TEXT, "Stop Scanning") { getSnykTaskQueueService(project)?.stopScan() }
    descriptionPanel.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)
    revalidate()
  }

  private fun displayDownloadMessage() {
    clearDescriptionPanel()
    val statePanel =
      StatePanel("Downloading Snyk CLI...", "Stop Downloading") {
        getSnykCliDownloaderService().stopCliDownload()
        displayEmptyDescription()
      }
    descriptionPanel.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)
    revalidate()
  }

  private fun displaySnykError(snykError: SnykError) {
    clearDescriptionPanel()
    descriptionPanel.add(SnykErrorPanel(snykError), BorderLayout.CENTER)
    revalidate()
  }

  private fun displaySelectVulnerabilityMessage() {
    val scrollPanelCandidate = descriptionPanel.components.firstOrNull()
    if (
      scrollPanelCandidate is JScrollPane &&
        scrollPanelCandidate.components.firstOrNull() is IssueDescriptionPanel
    ) {
      return
    }
    clearDescriptionPanel()
    val statePanel = StatePanel(SELECT_ISSUE_TEXT)
    descriptionPanel.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)
    revalidate()
  }

  @Suppress("UNUSED_PARAMETER")
  fun selectNodeAndDisplayDescription(scanIssue: ScanIssue, forceRefresh: Boolean) {
    selectNodeInHtmlTreeAndShowDescription(scanIssue)
  }

  private fun selectNodeInHtmlTreeAndShowDescription(scanIssue: ScanIssue) {
    htmlTreePanel?.selectNode(scanIssue.id)
    invokeLater {
      if (isDisposed || project.isDisposed) return@invokeLater
      clearDescriptionPanel()
      descriptionPanel.add(SuggestionDescriptionPanel(project, scanIssue), BorderLayout.CENTER)
      descriptionPanel.revalidate()
      descriptionPanel.repaint()
    }
  }

  private fun clearDescriptionPanel() {
    for (child in descriptionPanel.components) {
      if (child is Disposable) {
        Disposer.dispose(child)
      }
    }
    descriptionPanel.removeAll()
  }

  @TestOnly fun getDescriptionPanel() = descriptionPanel

  @TestOnly
  fun setHtmlTreePanelForTest(panel: HtmlTreePanel?) {
    htmlTreePanel = panel
  }
}
