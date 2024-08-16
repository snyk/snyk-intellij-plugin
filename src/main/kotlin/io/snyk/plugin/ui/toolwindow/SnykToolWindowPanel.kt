package io.snyk.plugin.ui.toolwindow

import com.intellij.notification.NotificationAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.containers.Convertor
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykScanListenerLS
import io.snyk.plugin.events.SnykSettingsListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.getOssTextRangeFinderService
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykCachedResultsForProduct
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.getSnykTaskQueueService
import io.snyk.plugin.head
import io.snyk.plugin.isCliDownloading
import io.snyk.plugin.isContainerEnabled
import io.snyk.plugin.isContainerRunning
import io.snyk.plugin.isIacEnabled
import io.snyk.plugin.isIacRunning
import io.snyk.plugin.isOssRunning
import io.snyk.plugin.isScanRunning
import io.snyk.plugin.isSnykCodeRunning
import io.snyk.plugin.isSnykOSSLSEnabled
import io.snyk.plugin.navigateToSource
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles
import io.snyk.plugin.snykToolWindow
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.expandTreeNodeRecursively
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.ErrorHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.leaf.SuggestionTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.leaf.VulnerabilityTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootContainerIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootIacIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootOssTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootQualityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootSecurityIssuesTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.root.RootTreeNodeBase
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.ErrorTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.FileTreeNode
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanel
import io.snyk.plugin.ui.toolwindow.panels.SnykAuthPanel
import io.snyk.plugin.ui.toolwindow.panels.SnykErrorPanel
import io.snyk.plugin.ui.toolwindow.panels.StatePanel
import io.snyk.plugin.ui.toolwindow.panels.TreePanel
import io.snyk.plugin.ui.wrapWithScrollPane
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.runAsync
import snyk.common.ProductType
import snyk.common.SnykError
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.ScanIssue
import snyk.container.ContainerIssuesForImage
import snyk.container.ContainerResult
import snyk.container.ContainerService
import snyk.container.ui.ContainerImageTreeNode
import snyk.container.ui.ContainerIssueTreeNode
import snyk.iac.IacIssue
import snyk.iac.IacResult
import snyk.iac.ignorableErrorCodes
import snyk.iac.ui.toolwindow.IacFileTreeNode
import snyk.iac.ui.toolwindow.IacIssueTreeNode
import snyk.oss.OssResult
import snyk.oss.OssVulnerabilitiesForFile
import snyk.oss.Vulnerability
import java.awt.BorderLayout
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import java.util.Objects.nonNull
import javax.swing.JPanel
import javax.swing.JScrollPane
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
    internal val descriptionPanel = SimpleToolWindowPanel(true, true).apply { name = "descriptionPanel" }
    private val logger = Logger.getInstance(this::class.java)
    private val rootTreeNode = DefaultMutableTreeNode("")
    private val rootOssTreeNode = RootOssTreeNode(project)
    private val rootSecurityIssuesTreeNode = RootSecurityIssuesTreeNode(project)
    private val rootQualityIssuesTreeNode = RootQualityIssuesTreeNode(project)
    private val rootIacIssuesTreeNode = RootIacIssuesTreeNode(project)
    private val rootContainerIssuesTreeNode = RootContainerIssuesTreeNode(project)

    internal val vulnerabilitiesTree by lazy {
        rootTreeNode.add(rootOssTreeNode)
        rootTreeNode.add(rootSecurityIssuesTreeNode)
        rootTreeNode.add(rootQualityIssuesTreeNode)
        if (isIacEnabled()) rootTreeNode.add(rootIacIssuesTreeNode)
        if (isContainerEnabled()) rootTreeNode.add(rootContainerIssuesTreeNode)
        Tree(rootTreeNode).apply {
            this.isRootVisible = false
        }
    }

    /** Flag used to recognize not-user-initiated Description panel reload cases for purposes like:
     *  - disable Itly logging
     *  - don't navigate to source (in the Editor)
     *  */
    private var smartReloadMode = false

    var navigateToSourceEnabled = true

    private val treeNodeStub =
        object : RootTreeNodeBase("", project) {
            override fun getSnykError(): SnykError? = null
        }

    init {
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

        vulnerabilitiesTree.selectionModel.addTreeSelectionListener {
            updateDescriptionPanelBySelectedTreeNode()
        }

        val scanListenerLS =
            run {
                val scanListener =
                    SnykToolWindowSnykScanListenerLS(
                        project,
                        this,
                        vulnerabilitiesTree,
                        rootSecurityIssuesTreeNode,
                        rootQualityIssuesTreeNode,
                        rootOssTreeNode,
                    )
                project.messageBus.connect(this).subscribe(
                    SnykScanListenerLS.SNYK_SCAN_TOPIC,
                    scanListener,
                )
                scanListener
            }

        project.messageBus
            .connect(this)
            .subscribe(
                SnykScanListener.SNYK_SCAN_TOPIC,
                object : SnykScanListener {
                    override fun scanningStarted() {
                        rootOssTreeNode.originalCliErrorMessage = null
                        ApplicationManager.getApplication().invokeLater {
                            updateTreeRootNodesPresentation()
                            displayScanningMessage()
                        }
                    }

                    override fun scanningOssFinished(ossResult: OssResult) {
                        ApplicationManager.getApplication().invokeLater {
                            displayOssResults(ossResult)
                            notifyAboutErrorsIfNeeded(ProductType.OSS, ossResult)
                            refreshAnnotationsForOpenFiles(project)
                        }
                    }

                    override fun scanningIacFinished(iacResult: IacResult) {
                        ApplicationManager.getApplication().invokeLater {
                            displayIacResults(iacResult)
                            if (iacResult.getVisibleErrors().isNotEmpty()) {
                                notifyAboutErrorsIfNeeded(ProductType.IAC, iacResult)
                            }
                            refreshAnnotationsForOpenFiles(project)
                        }
                    }

                    override fun scanningContainerFinished(containerResult: ContainerResult) {
                        ApplicationManager.getApplication().invokeLater {
                            displayContainerResults(containerResult)
                            notifyAboutErrorsIfNeeded(ProductType.CONTAINER, containerResult)
                            refreshAnnotationsForOpenFiles(project)
                        }
                    }

                    private fun notifyAboutErrorsIfNeeded(
                        prodType: ProductType,
                        cliResult: CliResult<*>,
                    ) {
                        if (cliResult.isSuccessful() && cliResult.errors.isNotEmpty()) {
                            val message =
                                "${prodType.productSelectionName} analysis finished with errors for some artifacts:\n" +
                                    cliResult.errors.joinToString(", ") { it.path }
                            SnykBalloonNotificationHelper.showError(
                                message,
                                project,
                                NotificationAction.createSimpleExpiring("Open Snyk Tool Window") {
                                    snykToolWindow(project)?.show()
                                },
                            )
                        }
                    }

                    override fun scanningOssError(snykError: SnykError) {
                        var ossResultsCount: Int? = null
                        ApplicationManager.getApplication().invokeLater {
                            if (snykError.message.contains(NO_OSS_FILES)) {
                                rootOssTreeNode.originalCliErrorMessage = snykError.message
                                ossResultsCount = NODE_NOT_SUPPORTED_STATE
                            } else {
                                rootOssTreeNode.originalCliErrorMessage = null
                                SnykBalloonNotificationHelper.showError(snykError.message, project)
                                if (snykError.message.startsWith(AUTH_FAILED_TEXT)) {
                                    pluginSettings().token = null
                                }
                            }
                            removeAllChildren(listOf(rootOssTreeNode))
                            updateTreeRootNodesPresentation(ossResultsCount = ossResultsCount)
                            chooseMainPanelToDisplay()
                            refreshAnnotationsForOpenFiles(project)
                        }
                    }

                    override fun scanningIacError(snykError: SnykError) {
                        var iacResultsCount: Int? = null
                        ApplicationManager.getApplication().invokeLater {
                            if (snykError.code != null && ignorableErrorCodes.contains(snykError.code)) {
                                iacResultsCount = NODE_NOT_SUPPORTED_STATE
                            } else {
                                SnykBalloonNotificationHelper.showError(snykError.message, project)
                                if (snykError.message.startsWith(AUTH_FAILED_TEXT)) {
                                    pluginSettings().token = null
                                }
                            }
                            removeAllChildren(listOf(rootIacIssuesTreeNode))
                            updateTreeRootNodesPresentation(iacResultsCount = iacResultsCount)
                            chooseMainPanelToDisplay()
                            refreshAnnotationsForOpenFiles(project)
                        }
                    }

                    override fun scanningContainerError(snykError: SnykError) {
                        var containerResultsCount: Int? = null
                        ApplicationManager.getApplication().invokeLater {
                            if (snykError == ContainerService.NO_IMAGES_TO_SCAN_ERROR) {
                                containerResultsCount = NODE_NOT_SUPPORTED_STATE
                            } else {
                                SnykBalloonNotificationHelper.showError(snykError.message, project)
                                if (snykError.message.startsWith(AUTH_FAILED_TEXT)) {
                                    pluginSettings().token = null
                                }
                            }
                            removeAllChildren(listOf(rootContainerIssuesTreeNode))
                            updateTreeRootNodesPresentation(containerResultsCount = containerResultsCount)
                            chooseMainPanelToDisplay()
                            refreshAnnotationsForOpenFiles(project)
                        }
                    }
                },
            )

        project.messageBus
            .connect(this)
            .subscribe(
                SnykResultsFilteringListener.SNYK_FILTERING_TOPIC,
                object : SnykResultsFilteringListener {
                    override fun filtersChanged() {
                        ApplicationManager.getApplication().invokeLater {
                            val codeResultsLS =
                                getSnykCachedResultsForProduct(project, ProductType.CODE_SECURITY) ?: return@invokeLater
                            scanListenerLS.displaySnykCodeResults(codeResultsLS)
                        }
                        if (!isSnykOSSLSEnabled()) {
                            ApplicationManager.getApplication().invokeLater {
                                val snykCachedResults = getSnykCachedResults(project) ?: return@invokeLater
                                snykCachedResults.currentOssResults?.let { displayOssResults(it) }
                            }
                        } else {
                            ApplicationManager.getApplication().invokeLater {
                                val ossResultsLS =
                                    getSnykCachedResultsForProduct(project, ProductType.OSS) ?: return@invokeLater
                                scanListenerLS.displayOssResults(ossResultsLS)
                            }
                        }
                        ApplicationManager.getApplication().invokeLater {
                            val snykCachedResults = getSnykCachedResults(project) ?: return@invokeLater
                            snykCachedResults.currentIacResult?.let { displayIacResults(it) }
                            snykCachedResults.currentContainerResult?.let { displayContainerResults(it) }
                        }
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
                    override fun stopped(
                        wasOssRunning: Boolean,
                        wasSnykCodeRunning: Boolean,
                        wasIacRunning: Boolean,
                        wasContainerRunning: Boolean,
                    ) = ApplicationManager.getApplication().invokeLater {
                        updateTreeRootNodesPresentation(
                            ossResultsCount = if (wasOssRunning) NODE_INITIAL_STATE else null,
                            securityIssuesCount = if (wasSnykCodeRunning) NODE_INITIAL_STATE else null,
                            qualityIssuesCount = if (wasSnykCodeRunning) NODE_INITIAL_STATE else null,
                            iacResultsCount = if (wasIacRunning) NODE_INITIAL_STATE else null,
                            containerResultsCount = if (wasContainerRunning) NODE_INITIAL_STATE else null,
                        )
                        displayEmptyDescription()
                    }
                },
            )
    }

    private fun updateDescriptionPanelBySelectedTreeNode() {
        val capturedSmartReloadMode = smartReloadMode
        val capturedNavigateToSourceEnabled = navigateToSourceEnabled

        ApplicationManager.getApplication().invokeLater {
            descriptionPanel.removeAll()
            val selectionPath = vulnerabilitiesTree.selectionPath
            if (nonNull(selectionPath)) {
                val lastPathComponent = selectionPath!!.lastPathComponent
                if (!capturedSmartReloadMode &&
                    capturedNavigateToSourceEnabled &&
                    lastPathComponent is NavigatableToSourceTreeNode
                ) {
                    lastPathComponent.navigateToSource()
                }
                when (val selectedNode: DefaultMutableTreeNode = lastPathComponent as DefaultMutableTreeNode) {
                    is DescriptionHolderTreeNode -> {
                        descriptionPanel.add(
                            selectedNode.getDescriptionPanel(),
                            BorderLayout.CENTER,
                        )
                    }

                    is ErrorHolderTreeNode -> {
                        selectedNode.getSnykError()?.let {
                            displaySnykError(it)
                        } ?: displayEmptyDescription()
                    }

                    else -> displayEmptyDescription()
                }
            } else {
                displayEmptyDescription()
            }
            descriptionPanel.revalidate()
            descriptionPanel.repaint()
        }
    }

    var isDisposed: Boolean = false

    override fun dispose() {
        isDisposed = true
    }

    fun cleanUiAndCaches() {
        getSnykCachedResults(project)?.cleanCaches()
        rootOssTreeNode.originalCliErrorMessage = null

        if (isContainerEnabled()) {
            getKubernetesImageCache(project)?.let {
                it.clear()
                it.cacheKubernetesFileFromProject()
            }
        }

        ApplicationManager.getApplication().invokeLater {
            doCleanUi(true)
            refreshAnnotationsForOpenFiles(project)
        }
    }

    private fun doCleanUi(reDisplayDescription: Boolean) {
        removeAllChildren()
        updateTreeRootNodesPresentation(
            ossResultsCount = NODE_INITIAL_STATE,
            securityIssuesCount = NODE_INITIAL_STATE,
            qualityIssuesCount = NODE_INITIAL_STATE,
            iacResultsCount = NODE_INITIAL_STATE,
            containerResultsCount = NODE_INITIAL_STATE,
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
                rootQualityIssuesTreeNode,
                rootIacIssuesTreeNode,
                rootContainerIssuesTreeNode,
            ),
    ) {
        rootNodesToUpdate.forEach {
            it.removeAllChildren()
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
                    // don't trigger scan for Default project i.e. no project opened state
                    if (project.basePath != null) triggerScan()
                } catch (e: Exception) {
                    displaySnykError(SnykError(e.message ?: "Exception while initializing plugin {${e.message}", ""))
                    logger.error("Failed to apply Snyk settings", e)
                }
            }

            else -> displayEmptyDescription()
        }
    }

    private fun triggerScan() {
        getSnykTaskQueueService(project)?.scan(false)
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
                val sastSettings = LanguageServerWrapper.getInstance().getSastSettings()
                sastOnServerEnabled = sastSettings?.sastEnabled ?: false
                val codeScanAllowed = sastOnServerEnabled == true
                snykCodeSecurityIssuesScanEnable = snykCodeSecurityIssuesScanEnable && codeScanAllowed
                snykCodeQualityIssuesScanEnable = snykCodeQualityIssuesScanEnable && codeScanAllowed
            } catch (clientException: RuntimeException) {
                logger.error(clientException)
            }
        }
    }

    private fun createTreeAndDescriptionPanel() {
        removeAll()
        val vulnerabilitiesSplitter = OnePixelSplitter(TOOL_WINDOW_SPLITTER_PROPORTION_KEY, 0.4f)
        add(vulnerabilitiesSplitter, BorderLayout.CENTER)
        vulnerabilitiesSplitter.firstComponent = TreePanel(vulnerabilitiesTree)
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

    private fun noIssuesInAnyProductFound() =
        rootOssTreeNode.childCount == 0 &&
            rootSecurityIssuesTreeNode.childCount == 0 &&
            rootQualityIssuesTreeNode.childCount == 0 &&
            rootIacIssuesTreeNode.childCount == 0 &&
            rootContainerIssuesTreeNode.childCount == 0

    /**
     * public only for Tests
     * Params value:
     *   `null` - if not qualify for `scanning` or `error` state then do NOT change previous value
     *   `NODE_INITIAL_STATE` - initial state (clean all postfixes)
     */
    fun updateTreeRootNodesPresentation(
        ossResultsCount: Int? = null,
        securityIssuesCount: Int? = null,
        qualityIssuesCount: Int? = null,
        iacResultsCount: Int? = null,
        containerResultsCount: Int? = null,
        addHMLPostfix: String = "",
    ) {
        val settings = pluginSettings()

        val realError =
            getSnykCachedResults(project)?.currentOssError != null &&
                ossResultsCount != NODE_NOT_SUPPORTED_STATE

        val newOssTreeNodeText =
            when {
                isOssRunning(project) && settings.ossScanEnable -> "$OSS_ROOT_TEXT (scanning...)"
                realError -> "$OSS_ROOT_TEXT (error)"

                else ->
                    ossResultsCount?.let { count ->
                        OSS_ROOT_TEXT +
                            when {
                                count == NODE_INITIAL_STATE -> ""
                                count == 0 -> NO_ISSUES_FOUND_TEXT
                                count > 0 -> ProductType.OSS.getCountText(count, isUniqueCount = true) + addHMLPostfix
                                count == NODE_NOT_SUPPORTED_STATE -> NO_SUPPORTED_PACKAGE_MANAGER_FOUND
                                else -> throw IllegalStateException("ResultsCount is not meaningful")
                            }
                    }
            }
        newOssTreeNodeText?.let { rootOssTreeNode.userObject = it }

        val newSecurityIssuesNodeText =
            when {
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
        newSecurityIssuesNodeText?.let { rootSecurityIssuesTreeNode.userObject = it }

        val newQualityIssuesNodeText =
            when {
                getSnykCachedResults(project)?.currentSnykCodeError != null -> "$CODE_QUALITY_ROOT_TEXT (error)"
                isSnykCodeRunning(
                    project,
                ) &&
                    settings.snykCodeQualityIssuesScanEnable -> "$CODE_QUALITY_ROOT_TEXT (scanning...)"

                else ->
                    qualityIssuesCount?.let { count ->
                        CODE_QUALITY_ROOT_TEXT +
                            when {
                                count == NODE_INITIAL_STATE -> ""
                                count == 0 -> NO_ISSUES_FOUND_TEXT
                                count > 0 -> ProductType.CODE_QUALITY.getCountText(count) + addHMLPostfix
                                else -> throw IllegalStateException("ResultsCount is meaningful")
                            }
                    }
            }
        newQualityIssuesNodeText?.let { rootQualityIssuesTreeNode.userObject = it }

        val newIacTreeNodeText =
            when {
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
        newIacTreeNodeText?.let { rootIacIssuesTreeNode.userObject = it }

        val newContainerTreeNodeText =
            when {
                getSnykCachedResults(project)?.currentContainerError != null -> "$CONTAINER_ROOT_TEXT (error)"
                isContainerRunning(project) && settings.containerScanEnabled -> "$CONTAINER_ROOT_TEXT (scanning...)"
                else ->
                    containerResultsCount?.let { count ->
                        CONTAINER_ROOT_TEXT +
                            when {
                                count == NODE_INITIAL_STATE -> ""
                                count == 0 -> NO_ISSUES_FOUND_TEXT
                                count > 0 ->
                                    ProductType.CONTAINER.getCountText(
                                        count,
                                        isUniqueCount = true,
                                    ) + addHMLPostfix

                                count == NODE_NOT_SUPPORTED_STATE -> NO_CONTAINER_IMAGES_FOUND
                                else -> throw IllegalStateException("ResultsCount is meaningful")
                            }
                    }
            }
        newContainerTreeNodeText?.let { rootContainerIssuesTreeNode.userObject = it }
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

    private fun displayScanningMessage() {
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

    private fun displayOssResults(ossResult: OssResult) {
        val userObjectsForExpandedChildren = userObjectsForExpandedNodes(rootOssTreeNode)
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)

        rootOssTreeNode.removeAllChildren()

        fun navigateToOssVulnerability(
            filePath: String,
            vulnerability: Vulnerability?,
        ): () -> Unit =
            {
                runAsync {
                    var virtualFile: VirtualFile? = null
                    ReadAction.run<RuntimeException> {
                        virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Paths.get(filePath))
                    }
                    val vf = virtualFile
                    if (vf == null || !vf.isValid) {
                        return@runAsync
                    }

                    if (vulnerability == null) {
                        navigateToSource(project, vf, 0)
                    } else {
                        ReadAction.run<RuntimeException> {
                            val psiFile = PsiManager.getInstance(project).findFile(vf)
                            val textRange =
                                psiFile?.let { getOssTextRangeFinderService().findTextRange(it, vulnerability) }
                            navigateToSource(
                                project = project,
                                virtualFile = vf,
                                selectionStartOffset = textRange?.startOffset ?: 0,
                                selectionEndOffset = textRange?.endOffset,
                            )
                        }
                    }
                }
            }

        val settings = pluginSettings()
        if (settings.ossScanEnable && settings.treeFiltering.ossResults) {
            ossResult.allCliIssues?.forEach { vulnsForFile ->
                if (vulnsForFile.vulnerabilities.isNotEmpty()) {
                    val ossGroupedResult = vulnsForFile.toGroupedResult()
                    val fileTreeNode = FileTreeNode(vulnsForFile, project)
                    rootOssTreeNode.add(fileTreeNode)

                    ossGroupedResult.id2vulnerabilities.values
                        .filter { settings.hasSeverityEnabledAndFiltered(it.head.getSeverity()) }
                        .sortedByDescending { it.head.getSeverity() }
                        .forEach {
                            val navigateToSource =
                                try {
                                    val filePath = sanitizeNavigationalFilePath(vulnsForFile)
                                    navigateToOssVulnerability(filePath, it.head)
                                } catch (ignore: InvalidPathException) {
                                    // empty navigation function for invalid path
                                    {}
                                }
                            fileTreeNode.add(VulnerabilityTreeNode(it, project, navigateToSource))
                        }
                }
            }
            ossResult.errors.forEach { snykError ->
                rootOssTreeNode.add(
                    ErrorTreeNode(snykError, project, navigateToOssVulnerability(snykError.path, null)),
                )
            }
        }
        updateTreeRootNodesPresentation(
            ossResultsCount = ossResult.issuesCount,
            addHMLPostfix = buildHMLpostfix(ossResult),
        )

        smartReloadRootNode(rootOssTreeNode, userObjectsForExpandedChildren, selectedNodeUserObject)
    }

    fun sanitizeNavigationalFilePath(vulnsForFile: OssVulnerabilitiesForFile): String {
        val dirPath = vulnsForFile.path
        val targetFilePath = vulnsForFile.sanitizedTargetFile
        val filePath =
            if (Paths.get(targetFilePath).isAbsolute) {
                targetFilePath
            } else {
                Paths.get(dirPath, targetFilePath).toString()
            }
        return filePath
    }

    fun displayIacResults(iacResult: IacResult) {
        val userObjectsForExpandedChildren = userObjectsForExpandedNodes(rootIacIssuesTreeNode)
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)

        rootIacIssuesTreeNode.removeAllChildren()

        fun navigateToIaCIssue(
            virtualFile: VirtualFile?,
            lineStartOffset: Int,
        ): () -> Unit =
            {
                if (virtualFile?.isValid == true) {
                    navigateToSource(project, virtualFile, lineStartOffset)
                }
            }

        val settings = pluginSettings()
        if (settings.iacScanEnabled && settings.treeFiltering.iacResults) {
            iacResult.allCliIssues?.forEach { iacVulnerabilitiesForFile ->
                if (iacVulnerabilitiesForFile.infrastructureAsCodeIssues.isNotEmpty()) {
                    val fileTreeNode = IacFileTreeNode(iacVulnerabilitiesForFile, project)
                    rootIacIssuesTreeNode.add(fileTreeNode)

                    iacVulnerabilitiesForFile.infrastructureAsCodeIssues
                        .filter { settings.hasSeverityEnabledAndFiltered(it.getSeverity()) }
                        .sortedByDescending { it.getSeverity() }
                        .forEach {
                            val navigateToSource =
                                navigateToIaCIssue(
                                    iacVulnerabilitiesForFile.virtualFile,
                                    it.lineStartOffset,
                                )
                            fileTreeNode.add(IacIssueTreeNode(it, project, navigateToSource))
                        }
                }
            }
            iacResult.getVisibleErrors().forEach { snykError ->
                rootIacIssuesTreeNode.add(
                    ErrorTreeNode(snykError, project, navigateToIaCIssue(snykError.virtualFile, 0)),
                )
            }
        }

        updateTreeRootNodesPresentation(
            iacResultsCount = iacResult.issuesCount,
            addHMLPostfix = buildHMLpostfix(iacResult),
        )

        smartReloadRootNode(rootIacIssuesTreeNode, userObjectsForExpandedChildren, selectedNodeUserObject)
    }

    fun displayContainerResults(containerResult: ContainerResult) {
        val userObjectsForExpandedChildren = userObjectsForExpandedNodes(rootContainerIssuesTreeNode)
        val selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)

        rootContainerIssuesTreeNode.removeAllChildren()

        fun navigateToImage(
            issuesForImage: ContainerIssuesForImage?,
            virtualFile: VirtualFile?,
        ): () -> Unit =
            {
                val image = issuesForImage?.workloadImages?.getOrNull(0)

                var file = virtualFile
                if (image != null) {
                    file = image.virtualFile
                }

                if (file?.isValid == true) {
                    navigateToSource(project, file, image?.lineStartOffset ?: 0)
                }
            }

        val settings = pluginSettings()
        if (settings.containerScanEnabled && settings.treeFiltering.containerResults) {
            containerResult.allCliIssues?.forEach { issuesForImage ->
                val image = issuesForImage.workloadImages.getOrNull(0)
                val virtualFile = image?.virtualFile
                if (issuesForImage.vulnerabilities.isNotEmpty()) {
                    val imageTreeNode =
                        ContainerImageTreeNode(
                            issuesForImage,
                            project,
                            navigateToImage(
                                issuesForImage,
                                virtualFile,
                            ),
                        )
                    rootContainerIssuesTreeNode.add(imageTreeNode)

                    issuesForImage.groupedVulnsById.values
                        .filter { settings.hasSeverityEnabledAndFiltered(it.head.getSeverity()) }
                        .sortedByDescending { it.head.getSeverity() }
                        .forEach {
                            imageTreeNode.add(
                                ContainerIssueTreeNode(
                                    it,
                                    project,
                                    navigateToImage(
                                        issuesForImage,
                                        virtualFile,
                                    ),
                                ),
                            )
                        }
                }
            }
            containerResult.errors.forEach { snykError ->
                rootContainerIssuesTreeNode.add(
                    ErrorTreeNode(snykError, project, navigateToImage(null, snykError.virtualFile)),
                )
            }
        }

        updateTreeRootNodesPresentation(
            containerResultsCount = containerResult.issuesCount,
            addHMLPostfix = buildHMLpostfix(containerResult),
        )

        smartReloadRootNode(rootContainerIssuesTreeNode, userObjectsForExpandedChildren, selectedNodeUserObject)
    }

    private fun buildHMLpostfix(cliResult: CliResult<*>): String =
        buildHMLpostfix(
            cliResult.criticalSeveritiesCount(),
            cliResult.highSeveritiesCount(),
            cliResult.mediumSeveritiesCount(),
            cliResult.lowSeveritiesCount(),
        )

    private fun buildHMLpostfix(
        criticalCount: Int = 0,
        errorsCount: Int,
        warnsCount: Int,
        infosCount: Int,
    ): String {
        var result = ""
        if (criticalCount > 0) result += ", $criticalCount ${Severity.CRITICAL}"
        if (errorsCount > 0) result += ", $errorsCount ${Severity.HIGH}"
        if (warnsCount > 0) result += ", $warnsCount ${Severity.MEDIUM}"
        if (infosCount > 0) result += ", $infosCount ${Severity.LOW}"
        return result.replaceFirst(",", ":")
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

        displayEmptyDescription()
        (vulnerabilitiesTree.model as DefaultTreeModel).reload(nodeToReload)

        userObjectsForExpandedChildren?.let {
            it.forEach { userObject ->
                val pathToNewNode = TreeUtil.findNodeWithObject(nodeToReload, userObject)?.path
                if (pathToNewNode != null) {
                    vulnerabilitiesTree.expandPath(TreePath(pathToNewNode))
                }
            }
        } ?: expandTreeNodeRecursively(vulnerabilitiesTree, nodeToReload)

        smartReloadMode = true
        try {
            selectedNode?.let { TreeUtil.selectNode(vulnerabilitiesTree, it) }
            // for some reason TreeSelectionListener is not initiated here on node selection
            // also we need to update Description panel in case if no selection was made before
            updateDescriptionPanelBySelectedTreeNode()
        } finally {
            smartReloadMode = false
        }
    }

    private fun selectAndDisplayNodeWithIssueDescription(selectCondition: (DefaultMutableTreeNode) -> Boolean) {
        val node = TreeUtil.findNode(rootTreeNode) { selectCondition(it) }
        if (node != null) {
            navigateToSourceEnabled = false
            try {
                TreeUtil.selectNode(vulnerabilitiesTree, node)
                // here TreeSelectionListener is invoked, so no needs for explicit updateDescriptionPanelBySelectedTreeNode()
            } finally {
                navigateToSourceEnabled = true
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun selectNodeAndDisplayDescription(vulnerability: Vulnerability) =
        selectAndDisplayNodeWithIssueDescription { treeNode ->
            treeNode is VulnerabilityTreeNode &&
                (treeNode.userObject as Collection<Vulnerability>).any {
                    it == vulnerability
                }
        }

    fun selectNodeAndDisplayDescription(iacIssue: IacIssue) =
        selectAndDisplayNodeWithIssueDescription { treeNode ->
            treeNode is IacIssueTreeNode &&
                (treeNode.userObject as IacIssue) == iacIssue
        }

    fun selectNodeAndDisplayDescription(issuesForImage: ContainerIssuesForImage) =
        selectAndDisplayNodeWithIssueDescription { treeNode ->
            treeNode is ContainerImageTreeNode &&
                (treeNode.userObject as ContainerIssuesForImage) == issuesForImage
        }

    fun selectNodeAndDisplayDescription(scanIssue: ScanIssue) =
        selectAndDisplayNodeWithIssueDescription { treeNode ->
            treeNode is SuggestionTreeNode &&
                (treeNode.userObject as ScanIssue).id == scanIssue.id
        }

    @TestOnly
    fun getRootIacIssuesTreeNode() = rootIacIssuesTreeNode

    @TestOnly
    fun getRootContainerIssuesTreeNode() = rootContainerIssuesTreeNode

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
        val CODE_QUALITY_ROOT_TEXT = " " + ProductType.CODE_QUALITY.treeName
        val IAC_ROOT_TEXT = " " + ProductType.IAC.treeName
        val CONTAINER_ROOT_TEXT = " " + ProductType.CONTAINER.treeName

        const val SELECT_ISSUE_TEXT = "Select an issue and start improving your project."
        const val SCAN_PROJECT_TEXT = "Scan your project for security vulnerabilities and code issues."
        const val SCANNING_TEXT = "Scanning project for vulnerabilities..."
        const val AUTH_FAILED_TEXT = "Authentication failed. Please check the API token on "
        const val NO_ISSUES_FOUND_TEXT = " - No issues found"
        const val NO_OSS_FILES = "Could not detect supported target files in"
        const val NO_IAC_FILES = "Could not find any valid IaC files"
        const val NO_SUPPORTED_IAC_FILES_FOUND = " - No supported IaC files found"
        const val NO_CONTAINER_IMAGES_FOUND = " - No container images found"
        const val NO_SUPPORTED_PACKAGE_MANAGER_FOUND = " - No supported package manager found"
        private const val TOOL_WINDOW_SPLITTER_PROPORTION_KEY = "SNYK_TOOL_WINDOW_SPLITTER_PROPORTION"
        internal const val NODE_INITIAL_STATE = -1
        const val NODE_NOT_SUPPORTED_STATE = -2

        private val CONTAINER_DOCS_TEXT_WITH_LINK =
            """
            If you are curious to know more about how the Snyk Container integration works, have a look at our
            <a href="https://docs.snyk.io/features/integrations/ide-tools/jetbrains-plugins#analysis-results-snyk-container">docs</a>.
            """.trimIndent()

        private val CONTAINER_SCAN_COMMON_POSTFIX =
            """
            The plugin searches for Kubernetes workload files (*.yaml, *.yml) and extracts the used images.<br>
            During testing the image, the CLI will download the image
            if it is not already available locally in your Docker daemon.<br><br>
            $CONTAINER_DOCS_TEXT_WITH_LINK
            """.trimIndent()

        val CONTAINER_SCAN_START_TEXT =
            "Snyk Container scan for vulnerabilities.<br><br>$CONTAINER_SCAN_COMMON_POSTFIX"
        val CONTAINER_SCAN_RUNNING_TEXT =
            "Snyk Container scan for vulnerabilities is now running.<br><br>$CONTAINER_SCAN_COMMON_POSTFIX"

        private val CONTAINER_NO_FOUND_COMMON_POSTFIX =
            """
            The plugin searches for Kubernetes workload files (*.yaml, *.yml) and extracts the used images.<br>
            Consider checking if your container application definition has an image specified.<br>
            Make sure that the container image has been successfully built locally
            and/or pushed to a container registry.<br><br>
            $CONTAINER_DOCS_TEXT_WITH_LINK
            """.trimIndent()

        val CONTAINER_NO_ISSUES_FOUND_TEXT =
            "Snyk Container scan didn't find any issues in the scanned container images.<br><br>$CONTAINER_NO_FOUND_COMMON_POSTFIX"
        val CONTAINER_NO_IMAGES_FOUND_TEXT =
            "Snyk Container scan didn't find any container images.<br><br>$CONTAINER_NO_FOUND_COMMON_POSTFIX"
    }
}
