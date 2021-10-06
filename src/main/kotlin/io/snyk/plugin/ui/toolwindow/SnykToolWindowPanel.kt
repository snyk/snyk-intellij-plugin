package io.snyk.plugin.ui.toolwindow

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
import com.intellij.util.Alarm
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.Severity
import io.snyk.plugin.analytics.getIssueSeverityOrNull
import io.snyk.plugin.analytics.getIssueType
import io.snyk.plugin.analytics.getSelectedProducts
import snyk.common.SnykError
import snyk.oss.OssResult
import snyk.oss.Vulnerability
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.head
import io.snyk.plugin.isCliDownloading
import io.snyk.plugin.isScanRunning
import io.snyk.plugin.isOssRunning
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykCliDownloaderService
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.SnykCodeResults
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.PDU
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder
import io.snyk.plugin.snykcode.severityAsString
import io.snyk.plugin.ui.SnykBalloonNotifications
import snyk.analytics.AnalysisIsReady
import snyk.analytics.AnalysisIsReady.Result
import snyk.analytics.AnalysisIsTriggered
import snyk.analytics.IssueIsViewed
import snyk.analytics.ProductSelectionIsViewed
import snyk.analytics.WelcomeIsViewed
import snyk.analytics.WelcomeIsViewed.Ide.JETBRAINS
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

    private val scrollPaneAlarm = Alarm()

    private var descriptionPanel = SimpleToolWindowPanel(true, true)

    var currentOssResults: OssResult? = null
        get() {
            val prevTimeStamp = field?.timeStamp ?: Instant.MIN
            if (prevTimeStamp.plusSeconds(60 * 24) < Instant.now()) {
                field = null
            }
            return field
        }

    var currentOssError: SnykError? = null

    var currentSnykCodeError: SnykError? = null

    private val rootTreeNode = DefaultMutableTreeNode("")
    private val rootOssTreeNode = RootOssTreeNode(project)
    private val rootSecurityIssuesTreeNode = RootSecurityIssuesTreeNode(project)
    private val rootQualityIssuesTreeNode = RootQualityIssuesTreeNode(project)
    private val vulnerabilitiesTree by lazy {
        rootTreeNode.add(rootOssTreeNode)
        rootTreeNode.add(rootSecurityIssuesTreeNode)
        rootTreeNode.add(rootQualityIssuesTreeNode)
        Tree(rootTreeNode).apply {
            this.isRootVisible = false
        }
    }

    init {
        vulnerabilitiesTree.cellRenderer = VulnerabilityTreeCellRenderer()

        initializeUiComponents()

        chooseMainPanelToDisplay()

        vulnerabilitiesTree.selectionModel.addTreeSelectionListener {
            ApplicationManager.getApplication().invokeLater {
                updateDescriptionPanelBySelectedTreeNode()
            }
        }

        project.messageBus.connect(this)
            .subscribe(SnykScanListener.SNYK_SCAN_TOPIC, object : SnykScanListener {

                override fun scanningStarted() {
                    currentOssError = null
                    currentSnykCodeError = null
                    ApplicationManager.getApplication().invokeLater { displayScanningMessageAndUpdateTree() }
                }

                override fun scanningOssFinished(ossResult: OssResult) {
                    currentOssResults = ossResult
                    ApplicationManager.getApplication().invokeLater { displayVulnerabilities(ossResult) }
                    service<SnykAnalyticsService>().logAnalysisIsReady(
                        AnalysisIsReady.builder()
                            .analysisType(AnalysisIsReady.AnalysisType.SNYK_OPEN_SOURCE)
                            .ide(AnalysisIsReady.Ide.JETBRAINS)
                            .result(Result.SUCCESS)
                            .build()
                    )
                }

                override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults?) {
                    ApplicationManager.getApplication().invokeLater { displaySnykCodeResults(snykCodeResults) }
                    if (snykCodeResults == null) {
                        return
                    }
                    logSnykCodeAnalysisIsReady(Result.SUCCESS)
                }

                private fun logSnykCodeAnalysisIsReady(result: Result) {
                    if (pluginSettings().snykCodeSecurityIssuesScanEnable) {
                        doLogSnykCodeAnalysisIsReady(result, AnalysisIsReady.AnalysisType.SNYK_CODE_SECURITY)
                    }
                    if (pluginSettings().snykCodeQualityIssuesScanEnable) {
                        doLogSnykCodeAnalysisIsReady(result, AnalysisIsReady.AnalysisType.SNYK_CODE_QUALITY)
                    }
                }

                private fun doLogSnykCodeAnalysisIsReady(result: Result, analysisType: AnalysisIsReady.AnalysisType) {
                    service<SnykAnalyticsService>().logAnalysisIsReady(
                        AnalysisIsReady.builder()
                            .analysisType(analysisType)
                            .ide(AnalysisIsReady.Ide.JETBRAINS)
                            .result(result)
                            .build()
                    )
                }

                override fun scanningOssError(snykError: SnykError) {
                    currentOssResults = null
                    ApplicationManager.getApplication().invokeLater {
                        SnykBalloonNotifications.showError(snykError.message, project)
                        if (snykError.message.startsWith("Authentication failed. Please check the API token on ")) {
                            pluginSettings().token = null
                            displayAuthPanel()
                        } else {
                            currentOssError = snykError
                            removeAllChildren(listOf(rootOssTreeNode))
                            updateTreeRootNodesPresentation()
                            displayEmptyDescription()
                        }
                    }
                    service<SnykAnalyticsService>().logAnalysisIsReady(
                        AnalysisIsReady.builder()
                            .analysisType(AnalysisIsReady.AnalysisType.SNYK_OPEN_SOURCE)
                            .ide(AnalysisIsReady.Ide.JETBRAINS)
                            .result(Result.ERROR)
                            .build()
                    )
                }

                override fun scanningSnykCodeError(snykError: SnykError) {
                    AnalysisData.instance.resetCachesAndTasks(project)
                    currentSnykCodeError = snykError
                    ApplicationManager.getApplication().invokeLater {
                        removeAllChildren(listOf(rootSecurityIssuesTreeNode, rootQualityIssuesTreeNode))
                        updateTreeRootNodesPresentation()
                        displayEmptyDescription()
                    }
                    logSnykCodeAnalysisIsReady(Result.ERROR)
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
                        displaySnykCodeResults(snykCodeResults)
                        currentOssResults?.let { displayVulnerabilities(it) }
                    }
                }
            })

        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC, object : SnykCliDownloadListener {

                override fun checkCliExistsFinished() =
                    ApplicationManager.getApplication().invokeLater {
                        chooseMainPanelToDisplay()
                    }

                override fun cliDownloadStarted() =
                    ApplicationManager.getApplication().invokeLater { displayDownloadMessage() }
            })

        project.messageBus.connect(this)
            .subscribe(SnykSettingsListener.SNYK_SETTINGS_TOPIC, object : SnykSettingsListener {

                override fun settingsChanged() =
                    ApplicationManager.getApplication().invokeLater {
                        chooseMainPanelToDisplay()
                    }
            })

        project.messageBus.connect(this)
            .subscribe(SnykTaskQueueListener.TASK_QUEUE_TOPIC, object : SnykTaskQueueListener {
                override fun stopped(wasOssRunning: Boolean, wasSnykCodeRunning: Boolean) =
                    ApplicationManager.getApplication().invokeLater {
                        updateTreeRootNodesPresentation(
                            ossResultsCount = if (wasOssRunning) -1 else null,
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
                    val groupedVulns = node.userObject as Collection<Vulnerability>
                    val scrollPane = wrapWithScrollPane(
                        VulnerabilityDescriptionPanel(groupedVulns)
                    )
                    descriptionPanel.add(scrollPane, BorderLayout.CENTER)

                    val issue = groupedVulns.first()
                    service<SnykAnalyticsService>().logIssueIsViewed(
                        IssueIsViewed.builder()
                            .ide(IssueIsViewed.Ide.JETBRAINS)
                            .issueId(issue.id)
                            .issueType(issue.getIssueType())
                            .severity(issue.getIssueSeverityOrNull())
                            .build()
                    )
                    // todo: open package manager file, if any and  was not opened yet
                }
                is SuggestionTreeNode -> {
                    val psiFile = (node.parent as? SnykCodeFileTreeNode)?.userObject as? PsiFile
                        ?: throw IllegalArgumentException(node.toString())
                    val (suggestion, index) = node.userObject as Pair<SuggestionForFile, Int>

                    val scrollPane = wrapWithScrollPane(
                        SuggestionDescriptionPanel(psiFile, suggestion, index)
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

                    service<SnykAnalyticsService>().logIssueIsViewed(
                        IssueIsViewed.builder()
                            .ide(IssueIsViewed.Ide.JETBRAINS)
                            .issueId(suggestion.id)
                            .issueType(suggestion.getIssueType())
                            .severity(suggestion.getIssueSeverityOrNull())
                            .build()
                    )
                }
                is RootOssTreeNode -> {
                    currentOssError?.let { displaySnykError(it) } ?: displayEmptyDescription()
                }
                is RootSecurityIssuesTreeNode, is RootQualityIssuesTreeNode -> {
                    currentSnykCodeError?.let { displaySnykError(it) } ?: displayEmptyDescription()
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

    private fun wrapWithScrollPane(panel: JPanel): JScrollPane {
        val scrollPane = ScrollPaneFactory.createScrollPane(
            panel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        )
        // hack to scroll Panel to beginning after all it content (hopefully) loaded
        scrollPaneAlarm.addRequest(
            {
                ApplicationManager.getApplication().invokeLater {
                    scrollPane.verticalScrollBar.value = 0
                    scrollPane.horizontalScrollBar.value = 0
                }
            }, 50
        )
        return scrollPane
    }

    override fun dispose() {
    }

    fun cleanUiAndCaches() {
        currentOssResults = null
        currentOssError = null
        currentSnykCodeError = null
        AnalysisData.instance.resetCachesAndTasks(project)
        SnykCodeIgnoreInfoHolder.instance.removeProject(project)

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

    private fun removeAllChildren(
        rootNodesToUpdate: List<DefaultMutableTreeNode> =
            listOf(rootOssTreeNode, rootSecurityIssuesTreeNode, rootQualityIssuesTreeNode)
    ) {
        rootNodesToUpdate.forEach {
            it.removeAllChildren()
            (vulnerabilitiesTree.model as DefaultTreeModel).reload(it)
        }
    }

    private fun chooseMainPanelToDisplay() {
        val settings = pluginSettings()
        when {
            settings.token.isNullOrEmpty() -> displayAuthPanel()
            settings.pluginFirstRun -> displayPluginFirstRunPanel()
            else -> displayTreeAndDescriptionPanels()
        }
    }

    fun displayAuthPanel() {
        removeAll()
        add(CenterOneComponentPanel(SnykAuthPanel(project)), BorderLayout.CENTER)
        revalidate()

        service<SnykAnalyticsService>().logWelcomeIsViewed(
            WelcomeIsViewed.builder()
                .ide(JETBRAINS)
                .build()
        )
    }

    private fun displayPluginFirstRunPanel() {
        removeAll()
        add(CenterOneComponentPanel(OnboardPanel(project).panel), BorderLayout.CENTER)
        revalidate()

        service<SnykAnalyticsService>().logProductSelectionIsViewed(
            ProductSelectionIsViewed.builder()
                .ide(ProductSelectionIsViewed.Ide.JETBRAINS)
                .build()
        )
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
        when {
            isScanRunning(project) -> {
                displayScanningMessage()
            }
            isCliDownloading() -> {
                displayDownloadMessage()
            }
            rootOssTreeNode.childCount == 0 &&
                rootSecurityIssuesTreeNode.childCount == 0 &&
                rootQualityIssuesTreeNode.childCount == 0 -> {
                displayNoVulnerabilitiesMessage()
            }
            else -> {
                displaySelectVulnerabilityMessage()
            }
        }
    }

    /** Params value:
     *   `null` - if not qualify for `scanning` or `error` state then do NOT change previous value
     *   `-1` - initial state (clean all postfixes)
     */
    private fun updateTreeRootNodesPresentation(
        ossResultsCount: Int? = null,
        securityIssuesCount: Int? = null,
        qualityIssuesCount: Int? = null,
        addHMLPostfix: String = ""
    ) {
        val settings = pluginSettings()

        val newOssTreeNodeText = when {
            currentOssError != null -> "$OSS_ROOT_TEXT (error)"
            isOssRunning(project) && settings.ossScanEnable -> "$OSS_ROOT_TEXT (scanning...)"
            else -> ossResultsCount?.let { count ->
                OSS_ROOT_TEXT + when {
                    count == -1 -> ""
                    count == 0 -> NO_ISSUES_FOUND_TEXT
                    count > 0 -> " - $count vulnerabilit${if (count > 1) "ies" else "y"}$addHMLPostfix"
                    else -> throw IllegalStateException()
                }
            }
        }
        newOssTreeNodeText?.let { rootOssTreeNode.userObject = it }

        val newSecurityIssuesNodeText = when {
            currentSnykCodeError != null -> "$SNYKCODE_SECURITY_ISSUES_ROOT_TEXT (error)"
            isSnykCodeRunning(project) && settings.snykCodeSecurityIssuesScanEnable -> "$SNYKCODE_SECURITY_ISSUES_ROOT_TEXT (scanning...)"
            else -> securityIssuesCount?.let { count ->
                SNYKCODE_SECURITY_ISSUES_ROOT_TEXT + when {
                    count == -1 -> ""
                    count == 0 -> NO_ISSUES_FOUND_TEXT
                    count > 0 -> " - $count vulnerabilit${if (count > 1) "ies" else "y"}$addHMLPostfix"
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
                    count > 0 -> " - $count issue${if (count > 1) "s" else ""}$addHMLPostfix"
                    else -> throw IllegalStateException()
                }
            }
        }
        newQualityIssuesNodeText?.let { rootQualityIssuesTreeNode.userObject = it }

        val nodesToReload = listOfNotNull(
            newOssTreeNodeText?.let { rootOssTreeNode },
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
            service<SnykAnalyticsService>().logAnalysisIsTriggered(
                AnalysisIsTriggered.builder()
                    .analysisType(getSelectedProducts(pluginSettings()))
                    .ide(AnalysisIsTriggered.Ide.JETBRAINS)
                    .triggeredByUser(true)
                    .build()
            )
        }

        emptyStatePanel.add(runScanLinkLabel)

        descriptionPanel.add(CenterOneComponentPanel(emptyStatePanel), BorderLayout.CENTER)
        revalidate()
    }

    private fun displayScanningMessageAndUpdateTree() {
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
            service<SnykCliDownloaderService>().stopCliDownload()
            displayEmptyDescription()
        })

        descriptionPanel.add(CenterOneComponentPanel(statePanel), BorderLayout.CENTER)

        revalidate()
    }

    private fun displayVulnerabilities(ossResult: OssResult) {
        val userObjectsForExpandedChildren = userObjectsForExpandedNodes(rootOssTreeNode)
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)

        rootOssTreeNode.removeAllChildren()

        if (pluginSettings().ossScanEnable && ossResult.allCliIssues != null) {
            ossResult.allCliIssues!!.forEach { ossVulnerabilitiesForFile ->
                if (ossVulnerabilitiesForFile.vulnerabilities.isNotEmpty()) {
                    val ossGroupedResult = ossVulnerabilitiesForFile.toGroupedResult()

                    val fileTreeNode = FileTreeNode(ossVulnerabilitiesForFile, project)
                    rootOssTreeNode.add(fileTreeNode)

                    ossGroupedResult.id2vulnerabilities.values
                        .filter { isSeverityFilterPassed(it.head.severity) }
                        .sortedByDescending { it.head.getSeverityIndex() }
                        .forEach {
                            fileTreeNode.add(VulnerabilityTreeNode(it, project))
                        }
                }
            }
        }
        updateTreeRootNodesPresentation(
            ossResultsCount = ossResult.issuesCount,
            addHMLPostfix = buildHMLpostfix(ossResult)
        )

        smartReloadRootNode(rootOssTreeNode, userObjectsForExpandedChildren, selectedNodeUserObject)
    }

    private fun displaySnykCodeResults(snykCodeResults: SnykCodeResults?) {
        if (currentSnykCodeError != null) return
        if (snykCodeResults == null) {
            updateTreeRootNodesPresentation(
                securityIssuesCount = -1,
                qualityIssuesCount = -1
            )
            return
        }
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)

        // display Security issues
        val userObjectsForExpandedSecurityNodes = userObjectsForExpandedNodes(rootSecurityIssuesTreeNode)
        rootSecurityIssuesTreeNode.removeAllChildren()

        var securityIssuesCount: Int? = null
        var securityIssuesHMLPostfix = ""
        if (pluginSettings().snykCodeSecurityIssuesScanEnable) {
            val securityResults = snykCodeResults.cloneFiltered {
                it.categories.contains("Security")
            }
            securityIssuesCount = securityResults.totalCount
            securityIssuesHMLPostfix = buildHMLpostfix(securityResults)

            val securityResultsToDisplay = securityResults.cloneFiltered {
                isSeverityFilterPassed(it.severityAsString)
            }
            displayResultsForRoot(rootSecurityIssuesTreeNode, securityResultsToDisplay)
        }
        updateTreeRootNodesPresentation(
            securityIssuesCount = securityIssuesCount,
            addHMLPostfix = securityIssuesHMLPostfix
        )
        smartReloadRootNode(rootSecurityIssuesTreeNode, userObjectsForExpandedSecurityNodes, selectedNodeUserObject)

        // display Quality (non Security) issues
        val userObjectsForExpandedQualityNodes = userObjectsForExpandedNodes(rootQualityIssuesTreeNode)
        rootQualityIssuesTreeNode.removeAllChildren()

        var qualityIssuesCount: Int? = null
        var qualityIssuesHMLPostfix = ""
        if (pluginSettings().snykCodeQualityIssuesScanEnable) {
            val qualityResults = snykCodeResults.cloneFiltered {
                !it.categories.contains("Security")
            }
            qualityIssuesCount = qualityResults.totalCount
            qualityIssuesHMLPostfix = buildHMLpostfix(qualityResults)

            val qualityResultsToDisplay = qualityResults.cloneFiltered {
                isSeverityFilterPassed(it.severityAsString)
            }
            displayResultsForRoot(rootQualityIssuesTreeNode, qualityResultsToDisplay)
        }
        updateTreeRootNodesPresentation(
            qualityIssuesCount = qualityIssuesCount,
            addHMLPostfix = qualityIssuesHMLPostfix
        )
        smartReloadRootNode(rootQualityIssuesTreeNode, userObjectsForExpandedQualityNodes, selectedNodeUserObject)
    }

    private fun buildHMLpostfix(snykCodeResults: SnykCodeResults): String =
        buildHMLpostfix(
            errorsCount = snykCodeResults.totalErrorsCount,
            warnsCount = snykCodeResults.totalWarnsCount,
            infosCount = snykCodeResults.totalInfosCount
        )

    private fun buildHMLpostfix(ossResult: OssResult): String =
        buildHMLpostfix(
            ossResult.criticalSeveritiesCount(),
            ossResult.highSeveritiesCount(),
            ossResult.mediumSeveritiesCount(),
            ossResult.lowSeveritiesCount()
        )

    private fun buildHMLpostfix(criticalCount: Int = 0, errorsCount: Int, warnsCount: Int, infosCount: Int): String {
        var result = ""
        if (criticalCount > 0) result += " | $criticalCount ${Severity.CRITICAL}"
        if (errorsCount > 0) result += " | $errorsCount ${Severity.HIGH}"
        if (warnsCount > 0) result += " | $warnsCount ${Severity.MEDIUM}"
        if (infosCount > 0) result += " | $infosCount ${Severity.LOW}"
        return result.replaceFirst(" |", ":")
    }

    private fun userObjectsForExpandedNodes(rootNode: DefaultMutableTreeNode) =
        if (rootNode.childCount == 0) null
        else TreeUtil.collectExpandedUserObjects(vulnerabilitiesTree, TreePath(rootNode.path))

    private fun isSeverityFilterPassed(severity: String): Boolean {
        val settings = pluginSettings()
        return when (severity) {
            Severity.CRITICAL -> settings.criticalSeverityEnabled
            Severity.HIGH -> settings.highSeverityEnabled
            Severity.MEDIUM -> settings.mediumSeverityEnabled
            Severity.LOW -> settings.lowSeverityEnabled
            else -> true
        }
    }

    private fun displayResultsForRoot(rootNode: DefaultMutableTreeNode, snykCodeResults: SnykCodeResults) {
        snykCodeResults.getSortedFiles()
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

    private fun displaySnykError(snykError: SnykError) {
        descriptionPanel.removeAll()

        descriptionPanel.add(SnykErrorPanel(snykError), BorderLayout.CENTER)

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
        const val OSS_ROOT_TEXT = " Open Source Security"
        const val SNYKCODE_SECURITY_ISSUES_ROOT_TEXT = " Code Security"
        const val SNYKCODE_QUALITY_ISSUES_ROOT_TEXT = " Code Quality"
        const val NO_ISSUES_FOUND_TEXT = " - No issues found"
        private const val TOOL_WINDOW_SPLITTER_PROPORTION_KEY = "SNYK_TOOL_WINDOW_SPLITTER_PROPORTION"
    }
}

class RootOssTreeNode(project: Project) :
    ProjectBasedDefaultMutableTreeNode(SnykToolWindowPanel.OSS_ROOT_TEXT, project)

class RootSecurityIssuesTreeNode(project: Project) :
    ProjectBasedDefaultMutableTreeNode(SnykToolWindowPanel.SNYKCODE_SECURITY_ISSUES_ROOT_TEXT, project)

class RootQualityIssuesTreeNode(project: Project) :
    ProjectBasedDefaultMutableTreeNode(SnykToolWindowPanel.SNYKCODE_QUALITY_ISSUES_ROOT_TEXT, project)

open class ProjectBasedDefaultMutableTreeNode(userObject: Any, val project: Project) :
    DefaultMutableTreeNode(userObject)
