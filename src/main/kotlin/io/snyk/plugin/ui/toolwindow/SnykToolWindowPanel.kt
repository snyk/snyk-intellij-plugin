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
import io.snyk.plugin.*
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.cli.Vulnerability
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.SnykCodeResults
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.PDU
import io.snyk.plugin.snykcode.core.SnykCodeUtils
import io.snyk.plugin.snykcode.severityAsString
import io.snyk.plugin.ui.SnykBalloonNotifications
import java.awt.BorderLayout
import java.time.Instant
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

    var currentCliResults: CliResult? = null
        get() {
            val prevTimeStamp = field?.timeStamp ?: Instant.MIN
            if (prevTimeStamp.plusSeconds(60 * 24) < Instant.now()) {
                field = null
            }
            return field
        }

    var currentCliError: CliError? = null

    var currentSnykCodeError: CliError? = null

    private val rootTreeNode = DefaultMutableTreeNode("")
    private val rootCliTreeNode = RootCliTreeNode(project)
    private val rootSecurityIssuesTreeNode = RootSecurityIssuesTreeNode(project)
    private val rootQualityIssuesTreeNode = RootQualityIssuesTreeNode(project)
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
                updateDescriptionPanelBySelectedTreeNode()
            }
        }

        project.messageBus.connect(this)
            .subscribe(SnykScanListener.SNYK_SCAN_TOPIC, object : SnykScanListener {

                override fun scanningStarted() {
                    currentCliError = null
                    currentSnykCodeError = null
                    ApplicationManager.getApplication().invokeLater { displayScanningMessageAndUpdateTree() }
                }

                override fun scanningCliFinished(cliResult: CliResult) {
                    currentCliResults = cliResult
                    ApplicationManager.getApplication().invokeLater { displayVulnerabilities(cliResult) }
                }

                override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults) =
                    ApplicationManager.getApplication().invokeLater { displaySnykCodeResults(snykCodeResults) }

                override fun scanningCliError(cliError: CliError) {
                    currentCliResults = null
                    ApplicationManager.getApplication().invokeLater {
                        if (cliError.message == "Authentication failed. Please check the API token on https://snyk.io") {
                            displayAuthPanel()
                        } else {
                            currentCliError = cliError
                            updateTreeRootNodesPresentation()
                            displayEmptyDescription()
                            SnykBalloonNotifications.showError(cliError.message, project)
                        }
                    }
                }

                override fun scanningSnykCodeError(cliError: CliError) {
                    AnalysisData.instance.resetCachesAndTasks(project)
                    currentSnykCodeError = cliError
                    ApplicationManager.getApplication().invokeLater {
                        updateTreeRootNodesPresentation()
                        displayEmptyDescription()
                    }
                }
            })

        project.messageBus.connect(this)
            .subscribe(SnykResultsFilteringListener.SNYK_FILTERING_TOPIC, object : SnykResultsFilteringListener {
                override fun filtersChanged() {
                    val snykCodeResults: SnykCodeResults? =
                        if (AnalysisData.instance.isProjectNOTAnalysed(project)) {
                            null
                        } else {
                            val allProjectFiles = AnalysisData.instance.getAllFilesWithSuggestions(project)
                            SnykCodeResults(
                                AnalysisData.instance.getAnalysis(allProjectFiles).mapKeys { PDU.toPsiFile(it.key) }
                            )
                        }
                    ApplicationManager.getApplication().invokeLater {
                        snykCodeResults?.let { displaySnykCodeResults(it) }
                        currentCliResults?.let { displayVulnerabilities(it) }
                    }
                }
            })

        project.messageBus.connect(this)
            .subscribe(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC, object : SnykCliDownloadListener {

                override fun checkCliExistsStarted() =
                    ApplicationManager.getApplication().invokeLater {
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
                override fun stopped(wasCliRunning: Boolean, wasSnykCodeRunning: Boolean) =
                    ApplicationManager.getApplication().invokeLater {
                        updateTreeRootNodesPresentation(
                            cliResultsCount = if (wasCliRunning) -1 else null,
                            securityIssuesCount = if (wasSnykCodeRunning) -1 else null,
                            qualityIssuesCount = if (wasSnykCodeRunning) -1 else null
                        )
                        displayEmptyDescription()
                    }
            })
    }

    private fun updateDescriptionPanelBySelectedTreeNode() {
        descriptionPanel.removeAll()

        val selectionPath = vulnerabilitiesTree.selectionPath

        if (nonNull(selectionPath)) {
            val node: DefaultMutableTreeNode = selectionPath!!.lastPathComponent as DefaultMutableTreeNode
            when (node) {
                is VulnerabilityTreeNode -> {
                    descriptionPanel.add(
                        ScrollPaneFactory.createScrollPane(
                            VulnerabilityDescriptionPanel(node.userObject as Vulnerability),
                            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                        ),
                        BorderLayout.CENTER
                    )
                }
                is SuggestionTreeNode -> {
                    val psiFile = (node.parent as? SnykCodeFileTreeNode)?.userObject as? PsiFile
                        ?: throw IllegalArgumentException(node.toString())
                    val (suggestion, index) = node.userObject as Pair<SuggestionForFile, Int>
                    val scrollPane = ScrollPaneFactory.createScrollPane(
                        SuggestionDescriptionPanel(psiFile, suggestion, index),
                        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                    )
                    descriptionPanel.add(scrollPane, BorderLayout.CENTER)

                    val textRange = suggestion.ranges[index]
                        ?: throw IllegalArgumentException(suggestion.ranges.toString())
                    if (psiFile.virtualFile.isValid) {
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
                }
                is RootCliTreeNode -> {
                    currentCliError?.let { displayCliError(it) } ?: displayEmptyDescription()
                }
                is RootSecurityIssuesTreeNode, is RootQualityIssuesTreeNode -> {
                    currentSnykCodeError?.let { displayCliError(it) } ?: displayEmptyDescription()
                }
                else -> {
                    displayEmptyDescription()
                }
            }
        } else {
            displayEmptyDescription()
        }

        descriptionPanel.revalidate()
        descriptionPanel.repaint()
    }

    override fun dispose() {
    }

    fun cleanUiAndCaches() {
        currentCliResults = null
        currentCliError = null
        currentSnykCodeError = null
        AnalysisData.instance.resetCachesAndTasks(project)

        ApplicationManager.getApplication().invokeLater {
            doCleanUi()
        }
    }

    private fun doCleanUi() {
        removeAllChildren()
        updateTreeRootNodesPresentation(-1, -1, -1)
        reloadTree()

        displayEmptyDescription()

        //revalidate()
    }

    private fun removeAllChildren(rootNodesToUpdate: List<DefaultMutableTreeNode> =
                                      listOf(rootCliTreeNode, rootSecurityIssuesTreeNode, rootQualityIssuesTreeNode)
    ) {
        rootNodesToUpdate.forEach {
            it.removeAllChildren()
            (vulnerabilitiesTree.model as DefaultTreeModel).reload(it)
        }
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
        if (isScanRunning(project)) {
            displayScanningMessage()
        } else if (rootCliTreeNode.childCount == 0
            && rootSecurityIssuesTreeNode.childCount == 0
            && rootQualityIssuesTreeNode.childCount == 0
        ) {
            displayNoVulnerabilitiesMessage()
        } else {
            displaySelectVulnerabilityMessage()
        }

    }

    /** Params value:
     *   `null` - if not qualify for `scanning` or `error` state then do NOT change previous value
     *   `-1` - initial state (clean all postfixes)
     */
    private fun updateTreeRootNodesPresentation(
        cliResultsCount: Int? = null,
        securityIssuesCount: Int? = null,
        qualityIssuesCount: Int? = null
    ) {
        val settings = getApplicationSettingsStateService()

        val newCliTreeNodeText = when {
            currentCliError != null -> "$CLI_ROOT_TEXT (error)"
            isSnykCliRunning(project) && settings.cliScanEnable -> "$CLI_ROOT_TEXT (scanning...)"
            else -> cliResultsCount?.let { count ->
                CLI_ROOT_TEXT + when {
                    count == -1 -> ""
                    count == 0 -> NO_ISSUES_FOUND_TEXT
                    count > 0 -> " - $count vulnerabilit${if (count > 1) "ies" else "y"}"
                    else -> throw IllegalStateException()
                }
            }
        }
        newCliTreeNodeText?.let { rootCliTreeNode.userObject = it }

        val newSecurityIssuesNodeText = when {
            currentSnykCodeError != null -> "$SNYKCODE_SECURITY_ISSUES_ROOT_TEXT (error)"
            isSnykCodeRunning(project) && settings.snykCodeSecurityIssuesScanEnable -> "$SNYKCODE_SECURITY_ISSUES_ROOT_TEXT (scanning...)"
            else -> securityIssuesCount?.let { count ->
                SNYKCODE_SECURITY_ISSUES_ROOT_TEXT + when {
                    count == -1 -> ""
                    count == 0 -> NO_ISSUES_FOUND_TEXT
                    count > 0 -> " - $count vulnerabilit${if (count > 1) "ies" else "y"}"
                    else -> throw IllegalStateException()
                }
            }
        }
        newSecurityIssuesNodeText?.let { rootSecurityIssuesTreeNode.userObject = it }

        val newQualityIssuesNodeText = when {
            currentSnykCodeError != null -> "$SNYKCODE_QUALITY_ISSUES_ROOT_TEXT (error)"
            isSnykCodeRunning(project) && settings.snykCodeQualityIssuesScanEnable -> "$SNYKCODE_QUALITY_ISSUES_ROOT_TEXT (scanning...)"
            else -> qualityIssuesCount?.let { count ->
                SNYKCODE_QUALITY_ISSUES_ROOT_TEXT + when {
                    count == -1 -> ""
                    count == 0 -> NO_ISSUES_FOUND_TEXT
                    count > 0 -> " - $count issue${if (count > 1) "s" else ""}"
                    else -> throw IllegalStateException()
                }
            }
        }
        newQualityIssuesNodeText?.let { rootQualityIssuesTreeNode.userObject = it }

        val nodesToReload = listOfNotNull(
            newCliTreeNodeText?.let { rootCliTreeNode },
            newSecurityIssuesNodeText?.let { rootSecurityIssuesTreeNode },
            newQualityIssuesNodeText?.let { rootQualityIssuesTreeNode }
        )
        //nodesToReload.forEach { reloadTreeNode(it) }
    }

    private fun displayNoVulnerabilitiesMessage() {
        descriptionPanel.removeAll()

        val emptyStatePanel = JPanel()

        emptyStatePanel.add(JLabel("Scan your project for security vulnerabilities and code issues. "))

        val runScanLinkLabel = LinkLabel.create("Run scan") {
            project.service<SnykTaskQueueService>().scan()
        }

        emptyStatePanel.add(runScanLinkLabel)

        descriptionPanel.add(CenterOneComponentPanel(emptyStatePanel), BorderLayout.CENTER)
        revalidate()
    }

    private fun displayScanningMessageAndUpdateTree() {
//        val settings = getApplicationSettingsStateService()
//        removeAllChildren(listOfNotNull(
//            if (isSnykCliRunning(project) && settings.cliScanEnable) rootCliTreeNode else null,
//            if (isSnykCodeRunning(project) && settings.snykCodeSecurityIssuesScanEnable) rootSecurityIssuesTreeNode else null,
//            if (isSnykCodeRunning(project) && settings.snykCodeQualityIssuesScanEnable) rootQualityIssuesTreeNode else null
//        ))

        updateTreeRootNodesPresentation()

        displayScanningMessage()
    }

    private fun displayScanningMessage() {
        descriptionPanel.removeAll()

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

    private fun displayVulnerabilities(cliResult: CliResult) {
        val userObjectsForExpandedChildren = userObjectsForExpandedNodes(rootCliTreeNode)
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)

        rootCliTreeNode.removeAllChildren()

        var issuesCount: Int? = null
        if (getApplicationSettingsStateService().cliScanEnable && cliResult.vulnerabilities != null) {
            issuesCount = 0
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
        }
        updateTreeRootNodesPresentation(cliResultsCount = issuesCount)

        smartReloadRootNode(rootCliTreeNode, userObjectsForExpandedChildren, selectedNodeUserObject)
    }

    private fun displaySnykCodeResults(snykCodeResults: SnykCodeResults) {
        if (currentSnykCodeError != null) return

        // display Security issues
        val userObjectsForExpandedSecurityNodes = userObjectsForExpandedNodes(rootSecurityIssuesTreeNode)
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)

        rootSecurityIssuesTreeNode.removeAllChildren()

        var securityIssuesCount: Int? = null
        if (getApplicationSettingsStateService().snykCodeSecurityIssuesScanEnable) {
            val securityResults = snykCodeResults.cloneFiltered {
                isSeverityFilterPassed(it.severityAsString) && it.categories.contains("Security")
            }
            securityIssuesCount = securityResults.totalCount
            displayResultsForRoot(rootSecurityIssuesTreeNode, securityResults)
        }
        updateTreeRootNodesPresentation(securityIssuesCount = securityIssuesCount)
        smartReloadRootNode(rootSecurityIssuesTreeNode, userObjectsForExpandedSecurityNodes, selectedNodeUserObject)

        // display Quality (non Security) issues
        val userObjectsForExpandedQualityNodes = userObjectsForExpandedNodes(rootQualityIssuesTreeNode)

        rootQualityIssuesTreeNode.removeAllChildren()

        var qualityIssuesCount: Int? = null
        if (getApplicationSettingsStateService().snykCodeQualityIssuesScanEnable) {
            val qualityResults = snykCodeResults.cloneFiltered {
                isSeverityFilterPassed(it.severityAsString) && !it.categories.contains("Security")
            }
            qualityIssuesCount = qualityResults.totalCount
            displayResultsForRoot(rootQualityIssuesTreeNode, qualityResults)
        }
        updateTreeRootNodesPresentation(qualityIssuesCount = qualityIssuesCount)
        smartReloadRootNode(rootQualityIssuesTreeNode, userObjectsForExpandedQualityNodes, selectedNodeUserObject)
    }

    private fun userObjectsForExpandedNodes(rootNode: DefaultMutableTreeNode) =
        if (rootNode.childCount == 0) null
        else TreeUtil.collectExpandedUserObjects(vulnerabilitiesTree, TreePath(rootNode.path))

    private fun isSeverityFilterPassed(severity: String): Boolean {
        val settings = getApplicationSettingsStateService()
        return when (severity) {
            "high" -> settings.highSeverityEnabled
            "medium" -> settings.mediumSeverityEnabled
            "low" -> settings.lowSeverityEnabled
            else -> true
        }
    }

    private fun displayResultsForRoot(rootNode: DefaultMutableTreeNode, snykCodeResults: SnykCodeResults) {
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
                rootNode.add(fileTreeNode)
                snykCodeResults.suggestions(file)
                    .sortedByDescending { it.severity }
                    .forEach { suggestion ->
                        for (index in 0 until suggestion.ranges.size) {
                            fileTreeNode.add(SuggestionTreeNode(suggestion, index))
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

    private fun displayCliError(cliError: CliError) {
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

    /**
     * Re-expand previously expanded children (if `null` then expand All children)
     * Keep selection in the Tree (if any)
     */
    private fun smartReloadRootNode(
        nodeToReload: DefaultMutableTreeNode,
        userObjectsForExpandedChildren: List<Any>?,
        selectedNodeUserObject: Any?
    ) {
        val selectedNode = TreeUtil.findNodeWithObject(rootTreeNode, selectedNodeUserObject)

        displayEmptyDescription()
        reloadTreeNode(nodeToReload)
        userObjectsForExpandedChildren?.let {
            it.forEach { userObject ->
                val pathToNewNode = TreeUtil.findNodeWithObject(nodeToReload, userObject)?.path
                if (pathToNewNode != null) {
                    vulnerabilitiesTree.expandPath(TreePath(pathToNewNode))
                }
            }
        } ?: expandRecursively(nodeToReload)

        selectedNode?.let { TreeUtil.selectNode(vulnerabilitiesTree, it) }
        updateDescriptionPanelBySelectedTreeNode()
    }

    private fun reloadTreeNode(nodeToReload: DefaultMutableTreeNode) {
        (vulnerabilitiesTree.model as DefaultTreeModel).reload(nodeToReload)
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
        const val NO_ISSUES_FOUND_TEXT = " - No issues found"
        private const val TOOL_WINDOW_SPLITTER_PROPORTION_KEY = "SNYK_TOOL_WINDOW_SPLITTER_PROPORTION"
    }
}

class RootCliTreeNode(project: Project) :
    ProjectBasedDefaultMutableTreeNode(SnykToolWindowPanel.CLI_ROOT_TEXT, project)

class RootSecurityIssuesTreeNode(project: Project) :
    ProjectBasedDefaultMutableTreeNode(SnykToolWindowPanel.SNYKCODE_SECURITY_ISSUES_ROOT_TEXT, project)

class RootQualityIssuesTreeNode(project: Project) :
    ProjectBasedDefaultMutableTreeNode(SnykToolWindowPanel.SNYKCODE_QUALITY_ISSUES_ROOT_TEXT, project)

open class ProjectBasedDefaultMutableTreeNode(userObject: Any, val project: Project) :
    DefaultMutableTreeNode(userObject)
