package io.snyk.plugin.ui.toolwindow

import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.psi.PsiFile
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.cli.Vulnerability
import io.snyk.plugin.events.SnykCliDownloadListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.events.SnykTaskQueueListener
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.head
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.SnykCodeResults
import java.awt.BorderLayout
import java.awt.Insets
import java.util.Objects.nonNull
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel


/**
 * Main panel for Snyk tool window.
 */
@Service
class SnykToolWindowPanel(val project: Project) : JPanel(), Disposable {

    private var descriptionPanel = SimpleToolWindowPanel(true, true)

    private val rootTreeNode = DefaultMutableTreeNode("")
    private val rootCliTreeNode = DefaultMutableTreeNode(CLI_ROOT_TEXT)
    private val rootSnykCodeTreeNode = DefaultMutableTreeNode(SNYKCODE_ROOT_TEXT)
    private val vulnerabilitiesTree by lazy {
        rootTreeNode.add(rootCliTreeNode)
        rootTreeNode.add(rootSnykCodeTreeNode)
        Tree(rootTreeNode).apply {
            this.isRootVisible = false
        }
    }


    init {
        vulnerabilitiesTree.cellRenderer = VulnerabilityTreeCellRenderer()

        initializeUiComponents()

        vulnerabilitiesTree.selectionModel.addTreeSelectionListener {
            ApplicationManager.getApplication().invokeLater {
                descriptionPanel.removeAll()

                val selectionPath = vulnerabilitiesTree.selectionPath

                if (nonNull(selectionPath)) {
                    val node: DefaultMutableTreeNode = selectionPath!!.lastPathComponent as DefaultMutableTreeNode

                    when (node.userObject) {
                        is Vulnerability -> {
                            descriptionPanel.add(
                                ScrollPaneFactory.createScrollPane(VulnerabilityDescriptionPanel(node.userObject as Vulnerability)),
                                BorderLayout.CENTER
                            )
                        }
                        is SuggestionForFile -> {
                            val psiFile = (node.parent as? SnykCodeFileTreeNode)?.userObject as? PsiFile
                                ?: throw IllegalArgumentException(node.toString())
                            val suggestion = node.userObject as SuggestionForFile
                            val scrollPane = ScrollPaneFactory.createScrollPane(
                                SuggestionDescriptionPanel(psiFile, suggestion)
                            )
                            descriptionPanel.add(scrollPane, BorderLayout.CENTER)
                            //descriptionPanel.add(DeepCodeConfigForm().root, BorderLayout.CENTER)

                            // jump to Source
                            PsiNavigationSupport.getInstance().createNavigatable(
                                project,
                                psiFile.virtualFile,
                                suggestion.ranges.first().start
                            ).navigate(true)
                        }
                        else -> {
                            displaySelectVulnerabilityMessage()
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

                override fun scanningFinished(cliResult: CliResult) =
                    ApplicationManager.getApplication().invokeLater { displayVulnerabilities(cliResult) }

                override fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults) =
                    ApplicationManager.getApplication().invokeLater { displaySnykCodeResults(snykCodeResults) }

                override fun scanError(cliError: CliError) =
                    ApplicationManager.getApplication().invokeLater { displayError(cliError) }
            })

        project.messageBus.connect(this)
            .subscribe(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC, object : SnykCliDownloadListener {

                override fun checkCliExistsStarted() =
                    ApplicationManager.getApplication().invokeLater { displayCliCheckMessage() }

                override fun checkCliExistsFinished() =
                    ApplicationManager.getApplication().invokeLater {
                        if (getApplicationSettingsStateService().token.isNullOrEmpty()) {
                            displayAuthPanel()
                        } else {
                            displayTreeAndDescriptionPanels()
                        }
                    }

                override fun cliDownloadStarted() =
                    ApplicationManager.getApplication().invokeLater { displayDownloadMessage() }
            })

        project.messageBus.connect(this)
            .subscribe(SnykTaskQueueListener.TASK_QUEUE_TOPIC, object : SnykTaskQueueListener {
                override fun stopped() =
                    ApplicationManager.getApplication().invokeLater { displayNoVulnerabilitiesMessage() }
            })
    }

    override fun dispose() {
    }

    fun cleanAll() {
        ApplicationManager.getApplication().invokeLater {
            descriptionPanel.removeAll()

            rootCliTreeNode.userObject = CLI_ROOT_TEXT
            rootCliTreeNode.removeAllChildren()

            rootSnykCodeTreeNode.userObject = SNYKCODE_ROOT_TEXT
            rootSnykCodeTreeNode.removeAllChildren()

            reloadTree()

            displayNoVulnerabilitiesMessage()

            //revalidate()
        }
    }

    fun displayAuthPanel() {
        removeAll()
        add(CenterOneComponentPanel(SnykAuthPanel(project).root), BorderLayout.CENTER)
        revalidate()
    }

    fun displayTreeAndDescriptionPanels() {
        removeAll()

        val vulnerabilitiesSplitter = OnePixelSplitter(TOOL_WINDOW_SPLITTER_PROPORTION_KEY, 0.4f)
        add(vulnerabilitiesSplitter, BorderLayout.CENTER)
        vulnerabilitiesSplitter.firstComponent = ScrollPaneFactory.createScrollPane(vulnerabilitiesTree)
        vulnerabilitiesSplitter.secondComponent = descriptionPanel

        displayNoVulnerabilitiesMessage()
    }

    fun displayNoVulnerabilitiesMessage() {
        descriptionPanel.removeAll()

        val emptyStatePanel = JPanel()

        emptyStatePanel.add(JLabel("No vulnerabilities added. "))

        val runScanLinkLabel = LinkLabel.create("Run scan") {
            project.service<SnykTaskQueueService>().scan()
        }

        emptyStatePanel.add(runScanLinkLabel)

        descriptionPanel.add(CenterOneComponentPanel(emptyStatePanel), BorderLayout.CENTER)
    }

    fun displayScanningMessage() {
        descriptionPanel.removeAll()

        val statePanel = StatePanel("Scanning project for vulnerabilities...", "Stop Scanning", Runnable {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()

            displayNoVulnerabilitiesMessage()
        })

        descriptionPanel.add(CenterOneComponentPanel(statePanel), BorderLayout.CENTER)

        //revalidate()
    }

    fun displayDownloadMessage() {
        descriptionPanel.removeAll()

        val statePanel = StatePanel("Downloading Snyk CLI...", "Stop Downloading", Runnable {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()

            displayNoVulnerabilitiesMessage()
        })

        descriptionPanel.add(CenterOneComponentPanel(statePanel), BorderLayout.CENTER)

        revalidate()
    }

    fun isEmpty(): Boolean = rootCliTreeNode.childCount == 0 && rootSnykCodeTreeNode.childCount == 0

    fun displayVulnerabilities(cliResult: CliResult) {
        displaySelectVulnerabilityMessage()

        rootCliTreeNode.removeAllChildren()

        rootCliTreeNode.userObject = CLI_ROOT_TEXT + " - ${cliResult.issuesCount()}"

        if (cliResult.vulnerabilities != null) {
            cliResult.vulnerabilities!!.forEach { vulnerability ->
                if (vulnerability.vulnerabilities.isNotEmpty()) {
                    val cliGroupedResult = vulnerability.toCliGroupedResult()

                    val fileTreeNode = FileTreeNode(cliGroupedResult.displayTargetFile, vulnerability.packageManager)
                    rootCliTreeNode.add(fileTreeNode)

                    cliGroupedResult.vulnerabilitiesMap.keys.forEach { id ->
                        fileTreeNode.add(VulnerabilityTreeNode(cliGroupedResult.vulnerabilitiesMap.getValue(id).head))
                    }
                }
            }
        }
        reloadTree()
        TreeUtil.expandAll(vulnerabilitiesTree)
    }

    fun displaySnykCodeResults(snykCodeResults: SnykCodeResults) {
        displaySelectVulnerabilityMessage()

        rootSnykCodeTreeNode.removeAllChildren()

        rootSnykCodeTreeNode.userObject =
            SNYKCODE_ROOT_TEXT + " - ${snykCodeResults.totalCount}"

        snykCodeResults.files.forEach { file ->
            val fileTreeNode = SnykCodeFileTreeNode(file)
            rootSnykCodeTreeNode.add(fileTreeNode)
            snykCodeResults.suggestions(file).forEach { suggestion ->
                suggestion.ranges.forEach { rangeInFile ->
                    fileTreeNode.add(
                        SuggestionTreeNode(
                            SuggestionForFile(
                                suggestion.id,
                                suggestion.rule,
                                suggestion.message,
                                suggestion.severity,
                                suggestion.repoDatasetSize,
                                suggestion.exampleCommitFixes,
                                listOf(rangeInFile)
                            )
                        )
                    )
                }
            }
        }

        reloadTree()
        TreeUtil.expandAll(vulnerabilitiesTree)
    }

    private fun displaySelectVulnerabilityMessage() {
        descriptionPanel.removeAll()
        descriptionPanel.add(CenterOneComponentPanel(JLabel("Please, select vulnerability")), BorderLayout.CENTER)
        //revalidate()
    }

    fun displayError(cliError: CliError) {
        descriptionPanel.removeAll()

        descriptionPanel.add(CliErrorPanel(cliError), BorderLayout.CENTER)

        revalidate()
    }

    fun displayCliCheckMessage() {
        descriptionPanel.removeAll()

        val checkingPanel = JPanel()

        checkingPanel.layout = GridLayoutManager(3, 1, Insets(0, 0, 0, 0), -1, -1)

        checkingPanel.add(
            JLabel("Checking Snyk CLI existence..."),
            GridConstraints(
                0,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_HORIZONTAL,
                GridConstraints.SIZEPOLICY_FIXED,
                GridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false
            )
        )

        checkingPanel.add(
            JLabel(""),
            GridConstraints(
                1,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_VERTICAL,
                GridConstraints.SIZEPOLICY_CAN_SHRINK,
                GridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false
            )
        )

        val stopCheckingLinkLabel = LinkLabel.create("Stop Checking") {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()

            displayNoVulnerabilitiesMessage()
        }

        checkingPanel.add(
            stopCheckingLinkLabel,
            GridConstraints(
                2,
                0,
                1,
                1,
                GridConstraints.ANCHOR_CENTER,
                GridConstraints.FILL_BOTH,
                GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                GridConstraints.SIZEPOLICY_CAN_GROW or GridConstraints.SIZEPOLICY_CAN_SHRINK,
                null,
                null,
                null,
                0,
                false
            )
        )

        descriptionPanel.add(CenterOneComponentPanel(checkingPanel), BorderLayout.CENTER)

        revalidate()
    }

    private fun initializeUiComponents() {
        layout = BorderLayout()

        TreeSpeedSearch(vulnerabilitiesTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true)
    }

    private fun reloadTree() {
        (vulnerabilitiesTree.model as DefaultTreeModel).reload()
    }

    companion object {
        const val CLI_ROOT_TEXT = "OPEN SOURCE VULNERABILITIES"
        const val SNYKCODE_ROOT_TEXT = "CODE ANALYSIS"
        private const val TOOL_WINDOW_SPLITTER_PROPORTION_KEY =
            "SNYK_TOOL_WINDOW_SPLITTER_PROPORTION"
    }
}
