package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.SnykFile
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.events.SnykShowIssueDetailListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCachedResultsForProduct
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.isCliDownloading
import io.snyk.plugin.isIacRunning
import io.snyk.plugin.isOssRunning
import io.snyk.plugin.isScanRunning
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.ReferenceChooserDialog
import io.snyk.plugin.ui.expandTreeNodeRecursively
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.ErrorHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootTreeNodeBase
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.ChooseBranchNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.InfoTreeNode
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanel
import io.snyk.plugin.ui.toolwindow.panels.SnykAuthPanel
import io.snyk.plugin.ui.toolwindow.panels.SnykErrorPanel
import io.snyk.plugin.ui.toolwindow.panels.StatePanel
import io.snyk.plugin.ui.toolwindow.panels.SummaryPanel
import io.snyk.plugin.ui.toolwindow.panels.TreePanel
import io.snyk.plugin.ui.wrapWithScrollPane
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.runAsync
import snyk.common.ProductType
import snyk.common.SnykError
import snyk.common.lsp.AiFixParams
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.LsProduct
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams
import snyk.common.lsp.settings.FolderConfigSettings
import java.awt.BorderLayout
import java.util.Objects.nonNull
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Main panel for Snyk tool window.
 */
@Service(Service.Level.PROJECT)
class SnykToolWindowPanel(
    val project: Project,
) : JPanel(),
    Disposable {
    private val descriptionPanel = SimpleToolWindowPanel(true, true).apply { name = "descriptionPanel" }
    private val summaryPanel = SimpleToolWindowPanel(true, true).apply { name = "summaryPanel" }
    private val logger = Logger.getInstance(this::class.java)
    private val rootTreeNode = ChooseBranchNode(project = project)
    private val rootOssTreeNode = RootOssTreeNode(project)
    private val rootSecurityIssuesTreeNode = RootSecurityIssuesTreeNode(project)
    private val rootIacIssuesTreeNode = RootIacIssuesTreeNode(project)

    internal val vulnerabilitiesTree by lazy {
        rootTreeNode.add(rootOssTreeNode)
        rootTreeNode.add(rootSecurityIssuesTreeNode)
        rootTreeNode.add(rootIacIssuesTreeNode)
        Tree(rootTreeNode).apply {
            this.isRootVisible = pluginSettings().isDeltaFindingsEnabled()
        }
    }

    private fun getRootNodeText(folderConfig: FolderConfig): String {
        val detail = if (folderConfig.referenceFolderPath.isNullOrBlank()) {
            folderConfig.baseBranch
        } else {
            folderConfig.referenceFolderPath
        }
        val path = folderConfig.folderPath.toNioPathOrNull()
        return "Click to choose base branch or reference folder for ${path?.fileName ?: path.toString()}: [ current: $detail ]"
    }

    /** Flag used to recognize not-user-initiated Description panel reload cases for purposes like:
     *  - don't navigate to source (in the Editor)
     *  */
    private var smartReloadMode = false

    var triggerSelectionListeners = true

    private val treeNodeStub =
        object : RootTreeNodeBase("", project) {
            override fun getSnykError(): SnykError? = null
        }


    init {
        Disposer.register(SnykPluginDisposable.getInstance(project), this)
        val contentRoots = project.getContentRootPaths()
        var rootNodeText = ""
        if (contentRoots.isNotEmpty()) {
            val folderConfig = service<FolderConfigSettings>().getFolderConfig(contentRoots.first().toString())
            rootNodeText = getRootNodeText(folderConfig)
        }

        rootTreeNode.info = rootNodeText

        vulnerabilitiesTree.cellRenderer = SnykTreeCellRenderer()
        layout = BorderLayout()

        // convertor interface seems to be still used in TreeSpeedSearch, although it's marked obsolete
        val convertor =
            Convertor<TreePath, String> {
                TreeSpeedSearch.NODE_PRESENTATION_FUNCTION.apply(it)
            }
        TreeUIHelper.getInstance().installTreeSpeedSearch(vulnerabilitiesTree, convertor, true)

        createTreeAndDescriptionPanel()
        chooseMainPanelToDisplay()
        updateSummaryPanel()

        vulnerabilitiesTree.selectionModel.addTreeSelectionListener { treeSelectionEvent ->
            runAsync {
                updateDescriptionPanelBySelectedTreeNode(treeSelectionEvent)
            }
        }

        val scanListenerLS =
            run {
                val scanListener =
                    SnykToolWindowSnykScanListener(
                        project,
                        this,
                        vulnerabilitiesTree,
                        rootSecurityIssuesTreeNode,
                        rootOssTreeNode,
                        rootIacIssuesTreeNode
                    )
                project.messageBus.connect(this).subscribe(
                    SnykScanListener.SNYK_SCAN_TOPIC,
                    scanListener,
                )
                scanListener
            }

        project.messageBus
            .connect(this)
            .subscribe(
                SnykScanListener.SNYK_SCAN_TOPIC,
                object : SnykScanListener {
                    override fun onPublishDiagnostics(
                        product: LsProduct,
                        snykFile: SnykFile,
                        issues: Set<ScanIssue>
                    ) {
                        getSnykCachedResults(project)?.let {
                            when (product) {
                                LsProduct.Code -> it.currentSnykCodeResultsLS[snykFile] = issues
                                LsProduct.OpenSource -> it.currentOSSResultsLS[snykFile] = issues
                                LsProduct.InfrastructureAsCode -> it.currentIacResultsLS[snykFile] = issues
                                LsProduct.Unknown -> Unit
                            }
                        }
                        // Refresh the tree view on receiving new diags from the Language Server. This must be done on
                        // the Event Dispatch Thread (EDT).
                        invokeLater {
                            vulnerabilitiesTree.isRootVisible = pluginSettings().isDeltaFindingsEnabled()
                        }
                    }

                    override fun scanningSnykCodeFinished() = Unit
                    override fun scanningOssFinished() = Unit
                    override fun scanningIacFinished() = Unit
                    override fun scanningError(snykScan: SnykScanParams) = Unit
                },
            )

        project.messageBus
            .connect(this)
            .subscribe(
                SnykResultsFilteringListener.SNYK_FILTERING_TOPIC,
                object : SnykResultsFilteringListener {
                    override fun filtersChanged() {
                        val codeSecurityResultsLS =
                            getSnykCachedResultsForProduct(project, ProductType.CODE_SECURITY) ?: return
                        scanListenerLS.displaySnykCodeResults(codeSecurityResultsLS)

                        val ossResultsLS =
                            getSnykCachedResultsForProduct(project, ProductType.OSS) ?: return
                        scanListenerLS.displayOssResults(ossResultsLS)

                        val iacResultsLS =
                            getSnykCachedResultsForProduct(project, ProductType.IAC) ?: return
                        scanListenerLS.displayIacResults(iacResultsLS)
                    }
                },
            )

        ApplicationManager
            .getApplication()
            .messageBus
            .connect(this)
            .subscribe(
                SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC,
                object : SnykCliDownloadListener {
                    override fun checkCliExistsFinished() =
                        ApplicationManager.getApplication().invokeLater {
                            chooseMainPanelToDisplay()
                        }

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
                        ApplicationManager.getApplication().invokeLater {
                            chooseMainPanelToDisplay()
                        }
                },
            )

        project.messageBus
            .connect(this)
            .subscribe(
                SnykTaskQueueListener.TASK_QUEUE_TOPIC,
                object : SnykTaskQueueListener {
                    override fun stopped() = ApplicationManager.getApplication().invokeLater {
                        updateTreeRootNodesPresentation(
                            ossResultsCount = NODE_INITIAL_STATE,
                            securityIssuesCount = NODE_INITIAL_STATE,
                            iacResultsCount = NODE_INITIAL_STATE,
                        )
                        displayEmptyDescription()
                    }
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
                        getSnykCachedResultsForProduct(project, product)?.let { results ->
                            results.values.flatten().firstOrNull { scanIssue ->
                                scanIssue.id == issueId
                            }?.let { scanIssue ->
                                logger.debug("Select node and display description for issue $issueId")
                                selectNodeAndDisplayDescription(scanIssue, forceRefresh = true)
                            } ?: run { logger.debug("Failed to find issue $issueId in $product cache") }
                        }
                    }
                }
            )
    }

    private fun updateDescriptionPanelBySelectedTreeNode(treeSelectionEvent: TreeSelectionEvent) {
        val capturedSmartReloadMode = smartReloadMode
        val capturedNavigateToSourceEnabled = triggerSelectionListeners

        val selectionPath = treeSelectionEvent.path
        if (nonNull(selectionPath) && treeSelectionEvent.isAddedPath) {
            val lastPathComponent = selectionPath.lastPathComponent

            if (lastPathComponent is ChooseBranchNode && capturedNavigateToSourceEnabled && !capturedSmartReloadMode) {
                invokeLater {
                    ReferenceChooserDialog(project).show()
                }
            }

            if (!capturedSmartReloadMode &&
                capturedNavigateToSourceEnabled &&
                lastPathComponent is NavigatableToSourceTreeNode
            ) {
                lastPathComponent.navigateToSource()
            }
            when (val selectedNode: DefaultMutableTreeNode = lastPathComponent as DefaultMutableTreeNode) {
                is DescriptionHolderTreeNode -> {
                    if (selectedNode is SuggestionTreeNode) {
                        val cache = getSnykCachedResults(project) ?: return
                        val issue = selectedNode.issue
                        val productIssues = when (issue.filterableIssueType) {
                            ScanIssue.CODE_SECURITY -> cache.currentSnykCodeResultsLS
                            ScanIssue.OPEN_SOURCE -> cache.currentOSSResultsLS
                            ScanIssue.INFRASTRUCTURE_AS_CODE -> cache.currentIacResultsLS
                            else -> {
                                emptyMap()
                            }
                        }
                        productIssues.values.flatten().filter { issue.id == it.id }.forEach { _ ->
                            val newDescriptionPanel = selectedNode.getDescriptionPanel()
                            descriptionPanel.removeAll()
                            descriptionPanel.add(
                                newDescriptionPanel,
                                BorderLayout.CENTER,
                            )
                        }
                    } else {
                        descriptionPanel.removeAll()
                        descriptionPanel.add(
                            selectedNode.getDescriptionPanel(),
                            BorderLayout.CENTER,
                        )
                    }

                }

                is ErrorHolderTreeNode -> {
                    descriptionPanel.removeAll()
                    selectedNode.getSnykError()?.let {
                        displaySnykError(it)
                    } ?: displayEmptyDescription()
                }

                else -> {
                    descriptionPanel.removeAll()
                    displayEmptyDescription()
                }
            }
        } else {
            displayEmptyDescription()
        }
        invokeLater {
            descriptionPanel.revalidate()
            descriptionPanel.repaint()
        }
    }

    var isDisposed: Boolean = false

    override fun dispose() {
        isDisposed = true
    }

    fun cleanUiAndCaches() {
        getSnykCachedResults(project)?.clearCaches()
        rootOssTreeNode.originalCliErrorMessage = null
        doCleanUi(true)
        refreshAnnotationsForOpenFiles(project)
    }

    private fun doCleanUi(reDisplayDescription: Boolean) {
        removeAllChildren()
        updateTreeRootNodesPresentation(
            ossResultsCount = NODE_INITIAL_STATE,
            securityIssuesCount = NODE_INITIAL_STATE,
            iacResultsCount = NODE_INITIAL_STATE,
        )
        (vulnerabilitiesTree.model as DefaultTreeModel).reload()

        if (reDisplayDescription) {
            displayEmptyDescription()
        }
    }

    private fun removeAllChildren(
        rootNodesToUpdate: List<DefaultMutableTreeNode> =
            listOf(
                rootOssTreeNode,
                rootSecurityIssuesTreeNode,
                rootIacIssuesTreeNode,
            ),
    ) {
        rootNodesToUpdate.forEach {
            if (it.childCount > 0) it.removeAllChildren()
            (vulnerabilitiesTree.model as DefaultTreeModel).reload(it)
        }
    }

    internal fun chooseMainPanelToDisplay() {
        val settings = pluginSettings()
        when {
            settings.token.isNullOrEmpty() -> displayAuthPanel()
            settings.pluginFirstRun -> {
                pluginSettings().pluginFirstRun = false
                try {
                    enableCodeScanAccordingToServerSetting()
                    displayEmptyDescription()
                } catch (e: Exception) {
                    displaySnykError(
                        SnykError(
                            e.message ?: "Exception while initializing plugin {${e.message}",
                            ""
                        )
                    )
                    logger.error("Failed to apply Snyk settings", e)
                }
            }

            else -> displayEmptyDescription()
        }
    }

    private fun triggerScan() {
        getSnykTaskQueueService(project)?.scan()
    }

    fun displayAuthPanel() {
        if (isDisposed) return
        doCleanUi(false)
        descriptionPanel.removeAll()
        val authPanel = SnykAuthPanel(project)
        Disposer.register(this, authPanel)
        descriptionPanel.add(authPanel, BorderLayout.CENTER)
        revalidate()
    }

    private fun enableCodeScanAccordingToServerSetting() {
        pluginSettings().apply {
            try {
                // update settings if we get a valid/correct response, else log the error and do nothing
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
        treeSplitter.secondComponent = TreePanel(vulnerabilitiesTree)

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

    private fun updateSummaryPanel() {
        val summaryPanelContent = SummaryPanel(project)
        summaryPanel.removeAll()
        Disposer.register(this, summaryPanelContent)
        summaryPanel.add(summaryPanelContent)
        revalidate()
    }

    private fun noIssuesInAnyProductFound() =
        rootOssTreeNode.childCount == 0 &&
            rootSecurityIssuesTreeNode.childCount == 0 &&
            rootIacIssuesTreeNode.childCount == 0

    /**
     * public only for Tests
     * Params value:
     *   `null` - if not qualify for `scanning` or `error` state then do NOT change previous value
     *   `NODE_INITIAL_STATE` - initial state (clean all postfixes)
     */
    fun updateTreeRootNodesPresentation(
        ossResultsCount: Int? = null,
        securityIssuesCount: Int? = null,
        iacResultsCount: Int? = null,
        addHMLPostfix: String = "",
    ) {
        val settings = pluginSettings()

        val realError =
            getSnykCachedResults(project)?.currentOssError != null && ossResultsCount != NODE_NOT_SUPPORTED_STATE

        val newOssTreeNodeText = getNewOssTreeNodeText(settings, realError, ossResultsCount, addHMLPostfix)
        newOssTreeNodeText?.let { rootOssTreeNode.userObject = it }

        val newSecurityIssuesNodeText =
            getNewSecurityIssuesNodeText(settings, securityIssuesCount, addHMLPostfix)
        newSecurityIssuesNodeText?.let { rootSecurityIssuesTreeNode.userObject = it }

        val newIacTreeNodeText = getNewIacTreeNodeText(settings, iacResultsCount, addHMLPostfix)
        newIacTreeNodeText?.let { rootIacIssuesTreeNode.userObject = it }

        val newRootTreeNodeText = getNewRootTreeNodeText()
        newRootTreeNodeText.let { rootTreeNode.info = it }
    }

    private fun getNewRootTreeNodeText(): String {
        val contentRoots = project.getContentRootPaths()
        if (contentRoots.isEmpty()) {
            return "No content roots found"
        }
        val folderConfig = service<FolderConfigSettings>().getFolderConfig(contentRoots.first().toString())
        return getRootNodeText(folderConfig)
    }

    private fun getNewIacTreeNodeText(
        settings: SnykApplicationSettingsStateService,
        iacResultsCount: Int?,
        addHMLPostfix: String
    ) = when {
        getSnykCachedResults(project)?.currentIacError != null -> "$IAC_ROOT_TEXT (error)"
        isIacRunning(project) && settings.iacScanEnabled -> "$IAC_ROOT_TEXT (scanning...)"
        else ->
            iacResultsCount?.let { count ->
                IAC_ROOT_TEXT +
                    when {
                        count == NODE_INITIAL_STATE -> ""
                        count == 0 -> NO_ISSUES_FOUND_TEXT
                        count > 0 -> ProductType.IAC.getCountText(count, isUniqueCount = true) + addHMLPostfix
                        count == NODE_NOT_SUPPORTED_STATE -> NO_SUPPORTED_IAC_FILES_FOUND
                        else -> throw IllegalStateException("ResultsCount is meaningful")
                    }
            }
    }

    private fun getNewSecurityIssuesNodeText(
        settings: SnykApplicationSettingsStateService,
        securityIssuesCount: Int?,
        addHMLPostfix: String
    ) = when {
        getSnykCachedResults(project)?.currentSnykCodeError != null -> "$CODE_SECURITY_ROOT_TEXT (error)"
        isSnykCodeRunning(project) &&
            settings.snykCodeSecurityIssuesScanEnable -> "$CODE_SECURITY_ROOT_TEXT (scanning...)"

        else ->
            securityIssuesCount?.let { count ->
                CODE_SECURITY_ROOT_TEXT +
                    when {
                        count == NODE_INITIAL_STATE -> ""
                        count == 0 -> NO_ISSUES_FOUND_TEXT
                        count > 0 -> ProductType.CODE_SECURITY.getCountText(count) + addHMLPostfix
                        else -> throw IllegalStateException("ResultsCount is meaningful")
                    }
            }
    }

    private fun getNewOssTreeNodeText(
        settings: SnykApplicationSettingsStateService,
        realError: Boolean,
        ossResultsCount: Int?,
        addHMLPostfix: String
    ) = when {
        isOssRunning(project) && settings.ossScanEnable -> "$OSS_ROOT_TEXT (scanning...)"
        realError -> "$OSS_ROOT_TEXT (error)"

        else ->
            ossResultsCount?.let { count ->
                OSS_ROOT_TEXT +
                    when {
                        count == NODE_INITIAL_STATE -> ""
                        count == 0 -> {
                            NO_ISSUES_FOUND_TEXT
                        }

                        count > 0 -> ProductType.OSS.getCountText(count, isUniqueCount = true) + addHMLPostfix
                        count == NODE_NOT_SUPPORTED_STATE -> NO_SUPPORTED_PACKAGE_MANAGER_FOUND
                        else -> throw IllegalStateException("ResultsCount is not meaningful")
                    }
            }
    }

    private fun displayNoVulnerabilitiesMessage() {
        descriptionPanel.removeAll()

        val selectedTreeNode =
            vulnerabilitiesTree.selectionPath?.lastPathComponent as? RootTreeNodeBase ?: treeNodeStub
        val messageHtmlText = selectedTreeNode.getNoVulnerabilitiesMessage()

        val emptyStatePanel =
            StatePanel(
                messageHtmlText,
                "Run Scan",
            ) { triggerScan() }

        descriptionPanel.add(wrapWithScrollPane(emptyStatePanel), BorderLayout.CENTER)
        revalidate()
    }

    fun displayScanningMessage() {
        descriptionPanel.removeAll()

        val selectedTreeNode =
            vulnerabilitiesTree.selectionPath?.lastPathComponent as? RootTreeNodeBase ?: treeNodeStub
        val messageHtmlText = selectedTreeNode.getScanningMessage()

        val statePanel =
            StatePanel(
                messageHtmlText,
                "Stop Scanning",
            ) { getSnykTaskQueueService(project)?.stopScan() }

        descriptionPanel.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)

        revalidate()
    }

    private fun displayDownloadMessage() {
        descriptionPanel.removeAll()

        val statePanel =
            StatePanel(
                "Downloading Snyk CLI...",
                "Stop Downloading",
            ) {
                getSnykCliDownloaderService().stopCliDownload()
                displayEmptyDescription()
            }

        descriptionPanel.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)

        revalidate()
    }

    internal fun userObjectsForExpandedNodes(rootNode: DefaultMutableTreeNode) =
        if (rootNode.childCount == 0) {
            null
        } else {
            TreeUtil.collectExpandedUserObjects(vulnerabilitiesTree, TreePath(rootNode.path))
        }

    private fun displaySelectVulnerabilityMessage() {
        val scrollPanelCandidate = descriptionPanel.components.firstOrNull()
        if (scrollPanelCandidate is JScrollPane &&
            scrollPanelCandidate.components.firstOrNull() is IssueDescriptionPanel
        ) {
            // vulnerability/suggestion already selected
            return
        }
        descriptionPanel.removeAll()

        val selectedTreeNode =
            vulnerabilitiesTree.selectionPath?.lastPathComponent as? RootTreeNodeBase ?: treeNodeStub
        val messageHtmlText = selectedTreeNode.getSelectVulnerabilityMessage()
        val statePanel = StatePanel(messageHtmlText)

        descriptionPanel.add(wrapWithScrollPane(statePanel), BorderLayout.CENTER)
        revalidate()
    }

    private fun displaySnykError(snykError: SnykError) {
        descriptionPanel.removeAll()

        descriptionPanel.add(SnykErrorPanel(snykError), BorderLayout.CENTER)

        revalidate()
    }

    /**
     * Re-expand previously expanded children (if `null` then expand All children)
     * Keep selection in the Tree (if any)
     */
    internal fun smartReloadRootNode(
        nodeToReload: DefaultMutableTreeNode,
        userObjectsForExpandedChildren: List<Any>?,
        selectedNodeUserObject: Any?,
    ) {
        val selectedNode = TreeUtil.findNodeWithObject(rootTreeNode, selectedNodeUserObject)
        if (selectedNode is InfoTreeNode) return

        displayEmptyDescription()

        ApplicationManager.getApplication().invokeAndWait {
            (vulnerabilitiesTree.model as DefaultTreeModel).reload(nodeToReload)
        }

        userObjectsForExpandedChildren?.let {
            it.forEach { userObject ->
                val pathToNewNode = TreeUtil.findNodeWithObject(nodeToReload, userObject)?.path
                if (pathToNewNode != null) {
                    invokeLater {
                        vulnerabilitiesTree.expandPath(TreePath(pathToNewNode))
                    }
                }
            }
        } ?: expandTreeNodeRecursively(vulnerabilitiesTree, nodeToReload)

        smartReloadMode = true
        try {
            selectedNode?.let {
                invokeLater {
                    TreeUtil.selectNode(vulnerabilitiesTree, it)
                }
            }
        } finally {
            smartReloadMode = false
        }
    }

    private fun selectAndDisplayNodeWithIssueDescription(
        selectCondition: (DefaultMutableTreeNode) -> Boolean,
        forceRefresh: Boolean = false
    ) {
        val node = TreeUtil.findNode(rootTreeNode) { selectCondition(it) }
        if (node != null) {
            invokeLater {
                try {
                    if (forceRefresh) {
                        vulnerabilitiesTree.clearSelection()
                    } else {
                        triggerSelectionListeners = false
                    }
                    TreeUtil.selectNode(vulnerabilitiesTree, node)
                } finally {
                    triggerSelectionListeners = true
                }
            }
        }
    }

    fun selectNodeAndDisplayDescription(scanIssue: ScanIssue, forceRefresh: Boolean) =
        selectAndDisplayNodeWithIssueDescription({ treeNode ->
            treeNode is SuggestionTreeNode &&
                (treeNode.userObject as ScanIssue).id == scanIssue.id
        }, forceRefresh)

    @TestOnly
    fun getRootIacIssuesTreeNode() = rootIacIssuesTreeNode

    @TestOnly
    fun getRootOssIssuesTreeNode() = rootOssTreeNode

    fun getTree() = vulnerabilitiesTree

    @TestOnly
    fun getRootNode() = rootTreeNode

    @TestOnly
    fun getDescriptionPanel() = descriptionPanel

    companion object {
        val OSS_ROOT_TEXT = " " + ProductType.OSS.treeName
        val CODE_SECURITY_ROOT_TEXT = " " + ProductType.CODE_SECURITY.treeName
        val IAC_ROOT_TEXT = " " + ProductType.IAC.treeName

        const val SELECT_ISSUE_TEXT = "Select an issue and start improving your project."
        const val SCAN_PROJECT_TEXT = "Scan your project for security vulnerabilities and code issues."
        const val SCANNING_TEXT = "Scanning project for vulnerabilities..."
        const val AUTH_FAILED_TEXT = "Authentication failed. Please check the API token on "
        const val NO_ISSUES_FOUND_TEXT = " - No issues found"
        const val NO_OSS_FILES = "Could not detect supported target files in"
        const val NO_IAC_FILES = "Could not find any valid IaC files"
        const val NO_SUPPORTED_IAC_FILES_FOUND = " - No supported IaC files found"
        const val NO_SUPPORTED_PACKAGE_MANAGER_FOUND = " - No supported package manager found"
        private const val TOOL_WINDOW_SPLITTER_PROPORTION_KEY = "SNYK_TOOL_WINDOW_SPLITTER_PROPORTION"
        private const val TOOL_TREE_SPLITTER_PROPORTION_KEY = "SNYK_TOOL_TREE_SPLITTER_PROPORTION"
        internal const val NODE_INITIAL_STATE = -1
        const val NODE_NOT_SUPPORTED_STATE = -2
    }
}
