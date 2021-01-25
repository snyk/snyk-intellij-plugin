package io.snyk.plugin.ui.toolwindow

import ai.deepcode.javaclient.core.DeepCodeUtilsBase
import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.psi.PsiFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.cli.Vulnerability
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.head
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.SnykCodeResults
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.PDU
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.snykcode.core.SnykCodeUtils
import io.snyk.plugin.snykcode.severityAsString
import java.awt.BorderLayout
import java.util.Objects.nonNull
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath


/**
 * Main panel for Snyk tool window.
 */
@Service
class SnykToolWindowPanel(val project: Project) : JPanel(), Disposable {

    private var descriptionPanel = SimpleToolWindowPanel(true, true)

    private var currentCliResults: CliResult? = null

    private val rootTreeNode = DefaultMutableTreeNode("")
    private val rootCliTreeNode = RootCliTreeNode()
    private val rootSecurityIssuesTreeNode = RootSecurityIssuesTreeNode()
    private val rootQualityIssuesTreeNode = RootQualityIssuesTreeNode()
    private val vulnerabilitiesTree by lazy {
        rootTreeNode.add(rootCliTreeNode)
        rootTreeNode.add(rootSecurityIssuesTreeNode)
        rootTreeNode.add(rootQualityIssuesTreeNode)
        Tree(rootTreeNode).apply {
            this.isRootVisible = false
        }
    }

    init {
        vulnerabilitiesTree.cellRenderer = VulnerabilityTreeCellRenderer()

        initializeUiComponents()

        displayTreeAndDescriptionPanels()

        vulnerabilitiesTree.selectionModel.addTreeSelectionListener {
            ApplicationManager.getApplication().invokeLater {
                descriptionPanel.removeAll()

                val selectionPath = vulnerabilitiesTree.selectionPath

                if (nonNull(selectionPath)) {
                    val node: DefaultMutableTreeNode = selectionPath!!.lastPathComponent as DefaultMutableTreeNode

                    when (node.userObject) {
                        is Vulnerability -> {
                            descriptionPanel.add(
                                ScrollPaneFactory.createScrollPane(
                                    VulnerabilityDescriptionPanel(node.userObject as Vulnerability),
                                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                                ),
                                BorderLayout.CENTER
                            )
                        }
                        is SuggestionForFile -> {
                            val psiFile = (node.parent as? SnykCodeFileTreeNode)?.userObject as? PsiFile
                                ?: throw IllegalArgumentException(node.toString())
                            val suggestion = node.userObject as SuggestionForFile
                            val scrollPane = ScrollPaneFactory.createScrollPane(
                                SuggestionDescriptionPanel(psiFile, suggestion),
                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                            )
                            descriptionPanel.add(scrollPane, BorderLayout.CENTER)
                            //descriptionPanel.add(DeepCodeConfigForm().root, BorderLayout.CENTER)

                            val textRange = suggestion.ranges.firstOrNull()
                                ?: throw IllegalArgumentException(suggestion.ranges.toString())
                            // jump to Source
                            PsiNavigationSupport.getInstance().createNavigatable(
                                project,
                                psiFile.virtualFile,
                                textRange.start
                            ).navigate(false)

                            // highlight(by selection) suggestion range in source file
                            val editor = FileEditorManager.getInstance(project).selectedTextEditor
                            editor?.selectionModel?.setSelection(textRange.start, textRange.end)
                        }
                        else -> {
                            displayEmptyDescription()
                        }
                    }
                }
                descriptionPanel.revalidate()
                descriptionPanel.repaint()
            }
        }

        project.messageBus.connect(this)
            .subscribe(SnykScanListener.SNYK_SCAN_TOPIC, object : SnykScanListener {

                override fun scanningStarted() =
                    ApplicationManager.getApplication().invokeLater { displayScanningMessage() }

                override fun scanningCliFinished(cliResult: CliResult) {
                    currentCliResults = cliResult
                    ApplicationManager.getApplication().invokeLater { displayVulnerabilities(cliResult) }
                }

                override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults) =
                    ApplicationManager.getApplication().invokeLater { displaySnykCodeResults(snykCodeResults) }

                override fun scanError(cliError: CliError) =
                    ApplicationManager.getApplication().invokeLater {
                        if (cliError.message == "Authentication failed. Please check the API token on https://snyk.io") {
                            displayAuthPanel()
                        } else {
                            displayError(cliError)
                        }
                    }
            })

        project.messageBus.connect(this)
            .subscribe(SnykResultsFilteringListener.SNYK_FILTERING_TOPIC, object : SnykResultsFilteringListener {
                override fun filtersChanged() {
                    val allProjectFiles = AnalysisData.instance.getAllFilesWithSuggestions(project)
                    val snykCodeResults: SnykCodeResults = SnykCodeResults(
                        AnalysisData.instance.getAnalysis(allProjectFiles).mapKeys { PDU.toPsiFile(it.key) }
                    )
                    ApplicationManager.getApplication().invokeLater {
                        displaySnykCodeResults(snykCodeResults)
                        currentCliResults?.let { displayVulnerabilities(it) }
                    }
                }
            })

        project.messageBus.connect(this)
            .subscribe(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC, object : SnykCliDownloadListener {

                override fun checkCliExistsStarted() =
                    ApplicationManager.getApplication().invokeLater {

                        // fixme !!!!!!!!!!!! uncomment below for debug only
                        //getApplicationSettingsStateService().pluginFirstRun = true

                        displayCliCheckMessage()
                    }

                override fun checkCliExistsFinished() =
                    ApplicationManager.getApplication().invokeLater {
                        when {
                            getApplicationSettingsStateService().token.isNullOrEmpty() -> {
                                displayAuthPanel()
                            }
                            getApplicationSettingsStateService().pluginFirstRun -> {
                                displayPluginFirstRunPanel()
                            }
                            else -> {
                                displayTreeAndDescriptionPanels()
                            }
                        }
                    }

                override fun cliDownloadStarted() =
                    ApplicationManager.getApplication().invokeLater { displayDownloadMessage() }
            })

        project.messageBus.connect(this)
            .subscribe(SnykTaskQueueListener.TASK_QUEUE_TOPIC, object : SnykTaskQueueListener {
                override fun stopped() =
                    ApplicationManager.getApplication().invokeLater {
                        updateTreePresentationAfterStop()
                        displayEmptyDescription()
                    }
            })
    }

    override fun dispose() {
    }

    fun cleanAll() {
        ApplicationManager.getApplication().invokeLater {
            doCleanAll()
        }
    }

    private fun doCleanAll() {
        descriptionPanel.removeAll()

        rootCliTreeNode.userObject = CLI_ROOT_TEXT
        rootCliTreeNode.removeAllChildren()

        rootSecurityIssuesTreeNode.userObject = SNYKCODE_SECURITY_ISSUES_ROOT_TEXT
        rootSecurityIssuesTreeNode.removeAllChildren()

        rootQualityIssuesTreeNode.userObject = SNYKCODE_QUALITY_ISSUES_ROOT_TEXT
        rootQualityIssuesTreeNode.removeAllChildren()

        reloadTree()

        displayEmptyDescription()

        //revalidate()
    }

    fun displayAuthPanel() {
        removeAll()
        add(CenterOneComponentPanel(SnykAuthPanel(project).root), BorderLayout.CENTER)
        revalidate()
    }

    private fun displayPluginFirstRunPanel() {
        removeAll()
        add(CenterOneComponentPanel(OnboardPanel(project).panel), BorderLayout.CENTER)
        revalidate()
    }

    private fun displayTreeAndDescriptionPanels() {
        removeAll()

        val vulnerabilitiesSplitter = OnePixelSplitter(TOOL_WINDOW_SPLITTER_PROPORTION_KEY, 0.4f)
        add(vulnerabilitiesSplitter, BorderLayout.CENTER)
        vulnerabilitiesSplitter.firstComponent = TreePanel(vulnerabilitiesTree)
        vulnerabilitiesSplitter.secondComponent = descriptionPanel

        displayEmptyDescription()
    }

    private fun displayEmptyDescription() {
        if (rootCliTreeNode.childCount == 0
            && rootSecurityIssuesTreeNode.childCount == 0
            && rootQualityIssuesTreeNode.childCount == 0
        ) {
            displayNoVulnerabilitiesMessage()
        } else {
            displaySelectVulnerabilityMessage()
        }
    }

    private fun updateTreePresentationAfterStop() {
        if (rootCliTreeNode.childCount == 0) {
            rootCliTreeNode.userObject = CLI_ROOT_TEXT
        }
        if (rootSecurityIssuesTreeNode.childCount == 0) {
            rootSecurityIssuesTreeNode.userObject = SNYKCODE_SECURITY_ISSUES_ROOT_TEXT
        }
        if (rootQualityIssuesTreeNode.childCount == 0) {
            rootQualityIssuesTreeNode.userObject = SNYKCODE_QUALITY_ISSUES_ROOT_TEXT
        }
    }

    private fun displayNoVulnerabilitiesMessage() {
        descriptionPanel.removeAll()

        val emptyStatePanel = JPanel()

        emptyStatePanel.add(JLabel("No vulnerability to display. Adjust Filters or"))

        val runScanLinkLabel = LinkLabel.create("Run scan") {
            project.service<SnykTaskQueueService>().scan()
        }

        emptyStatePanel.add(runScanLinkLabel)

        descriptionPanel.add(CenterOneComponentPanel(emptyStatePanel), BorderLayout.CENTER)
        revalidate()
    }

    private fun displayScanningMessage() {
        doCleanAll()
        descriptionPanel.removeAll()

        val settings = getApplicationSettingsStateService()
        if (settings.cliScanEnable) {
            rootCliTreeNode.userObject = "$CLI_ROOT_TEXT (scanning...)"
        }
        if (settings.snykCodeScanEnable) {
            rootSecurityIssuesTreeNode.userObject = "$SNYKCODE_SECURITY_ISSUES_ROOT_TEXT (scanning...)"
        }
        if (settings.snykCodeQualityIssuesScanEnable) {
            rootQualityIssuesTreeNode.userObject = "$SNYKCODE_QUALITY_ISSUES_ROOT_TEXT (scanning...)"
        }

        val statePanel = StatePanel(
            "Scanning project for vulnerabilities...",
            "Stop Scanning",
            Runnable { project.service<SnykTaskQueueService>().stopScan() }
        )

        descriptionPanel.add(CenterOneComponentPanel(statePanel), BorderLayout.CENTER)

        revalidate()
    }

    private fun displayDownloadMessage() {
        descriptionPanel.removeAll()

        val statePanel = StatePanel("Downloading Snyk CLI...", "Stop Downloading", Runnable {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()

            displayEmptyDescription()
        })

        descriptionPanel.add(CenterOneComponentPanel(statePanel), BorderLayout.CENTER)

        revalidate()
    }

    fun isEmpty(): Boolean =
        rootCliTreeNode.childCount == 0
            && rootSecurityIssuesTreeNode.childCount == 0
            && rootQualityIssuesTreeNode.childCount == 0

    private fun displayVulnerabilities(cliResult: CliResult) {
        rootCliTreeNode.removeAllChildren()

        if (getApplicationSettingsStateService().cliScanEnable && cliResult.vulnerabilities != null) {
            var issuesCount = 0
            cliResult.vulnerabilities!!.forEach { vulnerability ->
                if (vulnerability.vulnerabilities.isNotEmpty()) {
                    val cliGroupedResult = vulnerability.toCliGroupedResult()

                    val fileTreeNode =
                        FileTreeNode(cliGroupedResult.displayTargetFile, vulnerability.packageManager)
                    rootCliTreeNode.add(fileTreeNode)

                    cliGroupedResult.vulnerabilitiesMap.values
                        .filter { isSeverityFilterPassed(it.head.severity) }
                        .sortedByDescending { it.head.getSeverityIndex() }
                        .forEach {
                            issuesCount++
                            fileTreeNode.add(VulnerabilityTreeNode(it.head))
                        }
                }
            }
            rootCliTreeNode.userObject = CLI_ROOT_TEXT + " - ${
                if (issuesCount == 0)
                    NO_ISSUES_FOUND_TEXT
                else "$issuesCount vulnerabilit${if (issuesCount > 1) "ies" else "y"}"
            }"
        } else {
            rootCliTreeNode.userObject = CLI_ROOT_TEXT
        }

        displayEmptyDescription()
        reloadTreeKeepingSelection(listOf(rootCliTreeNode))
    }

    fun displaySnykCodeResults(snykCodeResults: SnykCodeResults) {
        // display Security issues
        rootSecurityIssuesTreeNode.removeAllChildren()
        if (getApplicationSettingsStateService().snykCodeScanEnable) {
            val securityResults = snykCodeResults.cloneFiltered {
                isSeverityFilterPassed(it.severityAsString) && it.categories.contains("Security")
            }
            val issuesCount = securityResults.totalCount
            rootSecurityIssuesTreeNode.userObject = SNYKCODE_SECURITY_ISSUES_ROOT_TEXT + " - ${
                if (issuesCount == 0)
                    NO_ISSUES_FOUND_TEXT
                else "$issuesCount vulnerabilit${if (issuesCount > 1) "ies" else "y"}"
            }"
            displayResultsForRoot(rootSecurityIssuesTreeNode, securityResults)
        } else {
            rootSecurityIssuesTreeNode.userObject = SNYKCODE_SECURITY_ISSUES_ROOT_TEXT
        }

        // display Quality (non Security) issues
        rootQualityIssuesTreeNode.removeAllChildren()
        if (getApplicationSettingsStateService().snykCodeQualityIssuesScanEnable) {
            val qualityResults = snykCodeResults.cloneFiltered {
                isSeverityFilterPassed(it.severityAsString) && !it.categories.contains("Security")
            }
            val issuesCount = qualityResults.totalCount
            rootQualityIssuesTreeNode.userObject = SNYKCODE_QUALITY_ISSUES_ROOT_TEXT + " - ${
                if (issuesCount == 0)
                    NO_ISSUES_FOUND_TEXT
                else "$issuesCount issue${if (issuesCount > 1) "s" else ""}"
            }"
            displayResultsForRoot(rootQualityIssuesTreeNode, qualityResults)
        } else {
            rootQualityIssuesTreeNode.userObject = SNYKCODE_QUALITY_ISSUES_ROOT_TEXT
        }

        displayEmptyDescription()
        reloadTreeKeepingSelection(listOf(rootSecurityIssuesTreeNode, rootQualityIssuesTreeNode))
    }

    private fun isSeverityFilterPassed(severity: String): Boolean {
        val settings = getApplicationSettingsStateService()
        return when (severity) {
            "high" -> settings.highSeverityEnabled
            "medium" -> settings.mediumSeverityEnabled
            "low" -> settings.lowSeverityEnabled
            else -> true
        }
    }

    private fun displayResultsForRoot(rootTreeNode: DefaultMutableTreeNode, snykCodeResults: SnykCodeResults) {
        snykCodeResults.files
            // sort by Errors-Warnings-Infos
            .sortedWith(Comparator { file1, file2 ->
                val ewi1: DeepCodeUtilsBase.ErrorsWarningsInfos = SnykCodeUtils.instance.getEWI(setOf(file1))
                val ewi2: DeepCodeUtilsBase.ErrorsWarningsInfos = SnykCodeUtils.instance.getEWI(setOf(file2))
                return@Comparator when {
                    ewi1.errors != ewi2.errors -> ewi2.errors - ewi1.errors
                    ewi1.warnings != ewi2.warnings -> ewi2.warnings - ewi1.warnings
                    else -> ewi2.infos - ewi1.infos
                }
            })
            .forEach { file ->
                val fileTreeNode = SnykCodeFileTreeNode(file)
                rootTreeNode.add(fileTreeNode)
                snykCodeResults.suggestions(file)
                    .sortedWith(Comparator { o1, o2 -> o2.severity - o1.severity })
                    .forEach { suggestion ->
                        suggestion.ranges.forEach { rangeInFile ->
                            fileTreeNode.add(
                                SuggestionTreeNode(
                                    SuggestionForFile(
                                        suggestion.id,
                                        suggestion.rule,
                                        suggestion.message,
                                        suggestion.title,
                                        suggestion.text,
                                        suggestion.severity,
                                        suggestion.repoDatasetSize,
                                        suggestion.exampleCommitDescriptions,
                                        suggestion.exampleCommitFixes,
                                        listOf(rangeInFile),
                                        suggestion.categories,
                                        suggestion.tags,
                                        suggestion.cwe
                                    )
                                )
                            )
                        }
                    }
            }
    }

    private fun displaySelectVulnerabilityMessage() {
        if (descriptionPanel.components.firstOrNull() is JScrollPane) return // vulnerability/suggestion already selected
        descriptionPanel.removeAll()
        descriptionPanel.add(
            CenterOneComponentPanel(JLabel("Select an issue and start improving your project.")),
            BorderLayout.CENTER
        )
        revalidate()
    }

    private fun displayError(cliError: CliError) {
        descriptionPanel.removeAll()

        descriptionPanel.add(CliErrorPanel(cliError), BorderLayout.CENTER)

        revalidate()
    }

    private fun displayCliCheckMessage() {
        descriptionPanel.removeAll()

        val checkingPanel = CliCheckingPanel(Runnable {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()
            displayEmptyDescription()
        })

        descriptionPanel.add(CenterOneComponentPanel(checkingPanel), BorderLayout.CENTER)

        revalidate()
    }

    private fun initializeUiComponents() {
        layout = BorderLayout()

        TreeSpeedSearch(vulnerabilitiesTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true)
    }

    private fun reloadTreeKeepingSelection(nodesToReload: List<DefaultMutableTreeNode> = emptyList()) {
        val selectionPath = vulnerabilitiesTree.selectionPath
        if (nodesToReload.isEmpty()) {
            reloadTree()
            TreeUtil.expandAll(vulnerabilitiesTree)
        } else {
            nodesToReload.forEach {
                (vulnerabilitiesTree.model as DefaultTreeModel).reload(it)
                expandRecursively(it)
            }
        }
        vulnerabilitiesTree.selectionPath = selectionPath
        ApplicationManager.getApplication().invokeLater {
            vulnerabilitiesTree.scrollPathToVisible(selectionPath)
        }
    }

    private fun expandRecursively(rootNode: DefaultMutableTreeNode) {
        vulnerabilitiesTree.expandPath(TreePath(rootNode.path))
        rootNode.children().asSequence().forEach {
            expandRecursively(it as DefaultMutableTreeNode)
        }
    }

    private fun reloadTree() {
        (vulnerabilitiesTree.model as DefaultTreeModel).reload()
    }

    companion object {
        const val CLI_ROOT_TEXT = " Open Source Security"
        const val SNYKCODE_SECURITY_ISSUES_ROOT_TEXT = " Code Security"
        const val SNYKCODE_QUALITY_ISSUES_ROOT_TEXT = " Code Quality"
        const val NO_ISSUES_FOUND_TEXT = "No issues found"
        private const val TOOL_WINDOW_SPLITTER_PROPORTION_KEY =
            "SNYK_TOOL_WINDOW_SPLITTER_PROPORTION"
    }
}

class RootCliTreeNode : DefaultMutableTreeNode(SnykToolWindowPanel.CLI_ROOT_TEXT)

class RootSecurityIssuesTreeNode : DefaultMutableTreeNode(SnykToolWindowPanel.SNYKCODE_SECURITY_ISSUES_ROOT_TEXT)

class RootQualityIssuesTreeNode : DefaultMutableTreeNode(SnykToolWindowPanel.SNYKCODE_QUALITY_ISSUES_ROOT_TEXT)
