package io.snyk.plugin.ui.toolwindow.panels

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.JBUI
import io.snyk.plugin.toVirtualFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.jcef.ApplyAiFixEditHandler
import io.snyk.plugin.ui.jcef.GenerateAIFixHandler
import io.snyk.plugin.ui.jcef.IgnoreInFileHandler
import io.snyk.plugin.ui.jcef.JCEFUtils
import io.snyk.plugin.ui.jcef.LoadHandlerGenerator
import io.snyk.plugin.ui.jcef.OpenFileLoadHandlerGenerator
import io.snyk.plugin.ui.jcef.SubmitIgnoreRequestHandler
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.SnykToolWindowPanel
import io.snyk.plugin.ui.wrapWithScrollPane
import java.awt.BorderLayout
import javax.swing.JPanel
import org.jetbrains.annotations.TestOnly
import snyk.common.lsp.ScanIssue

class SuggestionDescriptionPanel(val project: Project, private val issue: ScanIssue) :
  JPanel(BorderLayout()), IssueDescriptionPanel, Disposable {
  private val logger = logger<SuggestionDescriptionPanel>()
  private var jbCefBrowser: com.intellij.ui.jcef.JBCefBrowser? = null
  private val unexpectedErrorMessage =
    "Snyk encountered an issue while rendering the vulnerability description. Please try again, or contact support if the problem persists. We apologize for any inconvenience caused."

  @Volatile private var isDisposed = false

  // Used by tests to check if async initialization is complete
  @Volatile private var initialized = false

  @TestOnly fun isInitialized() = initialized

  /**
   * Returns the formatted HTML with CSS and scripts injected. This method is primarily for testing
   * purposes. Note: This makes a blocking call to get issue details from the Language Server.
   */
  fun getCustomCssAndScript(): String {
    val issueDetails = issue.details(project)
    return PanelHTMLUtils.getFormattedHtml(issueDetails)
  }

  init {
    logger.debug("SuggestionDescriptionPanel init starting for issue ${issue.id}")
    // Show loading state immediately to avoid blocking EDT
    val loadingPanel = StatePanel("Loading issue details...")
    this.add(wrapWithScrollPane(loadingPanel), BorderLayout.CENTER)

    // Load content asynchronously to avoid EDT blocking
    // issue.details() may call generateIssueDescription() which makes a blocking LS call
    logger.debug("SuggestionDescriptionPanel: scheduling background task for issue details")
    ApplicationManager.getApplication().executeOnPooledThread {
      logger.debug("SuggestionDescriptionPanel: background task starting")
      if (isDisposed || project.isDisposed) {
        logger.debug("SuggestionDescriptionPanel: disposed or project disposed, aborting")
        return@executeOnPooledThread
      }

      // Fetch issue details in background (may block on LS call)
      logger.debug("SuggestionDescriptionPanel: fetching issue details from LS")
      val issueDetails = issue.details(project)
      logger.debug(
        "SuggestionDescriptionPanel: issue details received, length=${issueDetails.length}"
      )

      // Switch back to EDT for UI updates and theme-dependent operations
      logger.debug("SuggestionDescriptionPanel: scheduling invokeLater for browser initialization")
      invokeLater {
        logger.debug("SuggestionDescriptionPanel: invokeLater executing")
        if (isDisposed || project.isDisposed) {
          logger.debug("SuggestionDescriptionPanel: disposed in invokeLater, aborting")
          return@invokeLater
        }
        initializeBrowser(issueDetails)
      }
    }
    logger.debug("SuggestionDescriptionPanel init completed (async loading scheduled)")
  }

  private fun initializeBrowser(issueDetails: String) {
    logger.debug("SuggestionDescriptionPanel: initializeBrowser starting")
    val loadHandlerGenerators: MutableList<LoadHandlerGenerator> =
      emptyList<LoadHandlerGenerator>().toMutableList()

    when (issue.filterableIssueType) {
      ScanIssue.CODE_SECURITY -> {
        val virtualFiles = LinkedHashMap<String, VirtualFile?>()
        for (dataFlow in issue.additionalData.dataFlow) {
          virtualFiles[dataFlow.filePath] = dataFlow.filePath.toVirtualFile()
        }

        val openFileLoadHandlerGenerator = OpenFileLoadHandlerGenerator(project, virtualFiles)
        loadHandlerGenerators += { openFileLoadHandlerGenerator.generate(it) }

        val generateAIFixHandler = GenerateAIFixHandler(project)
        loadHandlerGenerators += { generateAIFixHandler.generateAIFixCommand(it) }

        val applyAiFixEditHandler = ApplyAiFixEditHandler(project)
        loadHandlerGenerators += { applyAiFixEditHandler.generateApplyAiFixEditCommand(it) }

        val submitIgnoreRequestHandler = SubmitIgnoreRequestHandler(project)
        loadHandlerGenerators += { submitIgnoreRequestHandler.submitIgnoreRequestCommand(it) }
      }
      ScanIssue.INFRASTRUCTURE_AS_CODE -> {
        val applyIgnoreInFileHandler = IgnoreInFileHandler(project)
        loadHandlerGenerators += { applyIgnoreInFileHandler.generateIgnoreInFileCommand(it) }
      }
    }

    // getFormattedHtml accesses Swing components, must be called on EDT
    logger.debug("SuggestionDescriptionPanel: getting formatted HTML")
    val html = PanelHTMLUtils.getFormattedHtml(issueDetails)
    logger.debug("SuggestionDescriptionPanel: creating JCEF browser")
    jbCefBrowser = JCEFUtils.getJBCefBrowserIfSupported(html, loadHandlerGenerators)
    val jbCefBrowser = jbCefBrowser
    logger.debug("SuggestionDescriptionPanel: JCEF browser created, isNull=${jbCefBrowser == null}")

    // Remove loading panel and show actual content
    this.removeAll()

    if (jbCefBrowser == null) {
      logger.warn("SuggestionDescriptionPanel: JCEF browser is null, showing error")
      val statePanel = StatePanel(SnykToolWindowPanel.SELECT_ISSUE_TEXT)
      this.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)
      SnykBalloonNotificationHelper.showError(unexpectedErrorMessage, null)
    } else {
      logger.debug("SuggestionDescriptionPanel: adding JCEF browser to panel")
      val lastRowToAddSpacer = 5
      val panel =
        JPanel(GridLayoutManager(lastRowToAddSpacer + 1, 1, JBUI.insets(0, 10, 20, 10), -1, 20))
          .apply { this.add(jbCefBrowser.component, panelGridConstraints(1)) }
      this.add(wrapWithScrollPane(panel), BorderLayout.CENTER)
      this.add(panel)
    }

    logger.debug("SuggestionDescriptionPanel: revalidating and repainting")
    this.revalidate()
    this.repaint()
    logger.debug("SuggestionDescriptionPanel: initializeBrowser completed")
    initialized = true
  }

  override fun dispose() {
    isDisposed = true
    jbCefBrowser?.dispose()
    jbCefBrowser = null
  }
}
