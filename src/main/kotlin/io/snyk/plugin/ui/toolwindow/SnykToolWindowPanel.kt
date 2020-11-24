package io.snyk.plugin.ui.toolwindow

import ai.deepcode.javaclient.core.SuggestionForFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
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

    private val descriptionPanel = FullDescriptionPanel()

    private val rootTreeNode = DefaultMutableTreeNode("")
    private val rootCliTreeNode = DefaultMutableTreeNode("OPEN SOURCE VULNERABILITIES")
    private val rootSnykCodeTreeNode = DefaultMutableTreeNode("CODE ANALYSIS")
    private val vulnerabilitiesTree by lazy {
        rootTreeNode.add(rootCliTreeNode)
        rootTreeNode.add(rootSnykCodeTreeNode)
        Tree(rootTreeNode).apply {
            this.isRootVisible = false
        }
    }

    private val vulnerabilitiesSplitter = OnePixelSplitter(false, 0.4f, 0.1f, 0.9f)

    init {
        vulnerabilitiesTree.cellRenderer = VulnerabilityTreeCellRenderer()

        initializeUiComponents()

        displayNoVulnerabilitiesMessage()

        project.messageBus.connect(this)
            .subscribe(SnykScanListener.SNYK_SCAN_TOPIC, object: SnykScanListener {

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
            .subscribe(SnykCliDownloadListener.CLI_DOWNLOAD_TOPIC, object: SnykCliDownloadListener {

            override fun checkCliExistsStarted() =
                ApplicationManager.getApplication().invokeLater { displayCliCheckMessage() }

            override fun checkCliExistsFinished() =
                ApplicationManager.getApplication().invokeLater { displayNoVulnerabilitiesMessage() }

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
            removeAll()

            rootCliTreeNode.userObject = ""
            rootCliTreeNode.removeAllChildren()

            rootSnykCodeTreeNode.userObject = ""
            rootSnykCodeTreeNode.removeAllChildren()

            reloadTree()

            displayNoVulnerabilitiesMessage()

            revalidate()
        }
    }

    fun displayNoVulnerabilitiesMessage() {
        removeAll()

        val emptyStatePanel = JPanel()

        emptyStatePanel.add(JLabel("No vulnerabilities added. "))

        val runScanLinkLabel = LinkLabel.create("Run scan") {
            project.service<SnykTaskQueueService>().scan()
        }

        emptyStatePanel.add(runScanLinkLabel)

        add(CenterOneComponentPanel(emptyStatePanel), BorderLayout.CENTER)
    }

    fun displayScanningMessage() {
        removeAll()

        val statePanel = StatePanel("Scanning project for vulnerabilities...", "Stop Scanning", Runnable {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()

            displayNoVulnerabilitiesMessage()
        })

        add(CenterOneComponentPanel(statePanel), BorderLayout.CENTER)

        revalidate()
    }

    fun displayDownloadMessage() {
        removeAll()

        val statePanel = StatePanel("Downloading Snyk CLI...", "Stop Downloading", Runnable {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()

            displayNoVulnerabilitiesMessage()
        })

        add(CenterOneComponentPanel(statePanel), BorderLayout.CENTER)

        revalidate()
    }

    fun isEmpty(): Boolean = rootCliTreeNode.childCount == 0 && rootSnykCodeTreeNode.childCount == 0

    fun displayVulnerabilities(cliResult: CliResult) {
        ApplicationManager.getApplication().invokeLater {
            removeAll()

            rootCliTreeNode.removeAllChildren()

            rootCliTreeNode.userObject = rootCliTreeNode.userObject as String + " - ${cliResult.issuesCount()}"

            add(vulnerabilitiesSplitter, BorderLayout.CENTER)

            vulnerabilitiesTree.selectionModel.addTreeSelectionListener {
                ApplicationManager.getApplication().invokeLater {
                    descriptionPanel.removeAll()

                    val selectionPath = vulnerabilitiesTree.selectionPath

                    if (nonNull(selectionPath)) {
                        val node: DefaultMutableTreeNode = selectionPath!!.lastPathComponent as DefaultMutableTreeNode

                        when (node.userObject) {
                            is Vulnerability -> {
                                descriptionPanel.displayDescription(node.userObject as Vulnerability)
                            }
                            is SuggestionForFile -> {
                                val psiFile = (node.parent as? SnykCodeFileTreeNode)?.userObject as? PsiFile
                                    ?: throw IllegalArgumentException(node.toString())
                                descriptionPanel.displayDescription(psiFile, node.userObject as SuggestionForFile)
                            }
                            else -> {
                                descriptionPanel.displaySelectVulnerabilityMessage()
                            }
                        }
                    }
                }
            }

            vulnerabilitiesSplitter.firstComponent = ScrollPaneFactory.createScrollPane(vulnerabilitiesTree)
            vulnerabilitiesSplitter.secondComponent = descriptionPanel

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
    }

    fun displaySnykCodeResults(snykCodeResults: SnykCodeResults) {
        rootSnykCodeTreeNode.userObject =
            rootSnykCodeTreeNode.userObject as String + " - ${snykCodeResults.totalCount}"

        snykCodeResults.files.forEach {file ->
            val fileTreeNode = SnykCodeFileTreeNode(file)
            rootSnykCodeTreeNode.add(fileTreeNode)
            snykCodeResults.suggestions(file).forEach {suggestion ->
                suggestion.ranges.forEach {rangeInFile ->
                    fileTreeNode.add(SuggestionTreeNode(
                        SuggestionForFile(
                            suggestion.id,
                            suggestion.rule,
                            suggestion.message,
                            suggestion.severity,
                            suggestion.repoDatasetSize,
                            suggestion.exampleCommitFixes,
                            listOf(rangeInFile)
                        )
                    ))
                }
            }
        }

        reloadTree()
        TreeUtil.expandAll(vulnerabilitiesTree)
    }

    fun displayError(cliError: CliError) {
        removeAll()

        add(CliErrorPanel(cliError), BorderLayout.CENTER)

        revalidate()
    }

    fun displayCliCheckMessage() {
        removeAll()

        val checkingPanel = JPanel()

        checkingPanel.layout = GridLayoutManager(3, 1, Insets(0, 0, 0, 0), -1, -1)

        checkingPanel.add(JLabel("Checking Snyk CLI existence..."),
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
                false))

        checkingPanel.add(JLabel(""),
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
                false))

        val stopCheckingLinkLabel = LinkLabel.create("Stop Checking") {
            project.service<SnykTaskQueueService>().getCurrentProgressIndicator()?.cancel()

            displayNoVulnerabilitiesMessage()
        }

        checkingPanel.add(stopCheckingLinkLabel,
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
                false))

        add(CenterOneComponentPanel(checkingPanel), BorderLayout.CENTER)

        revalidate()
    }

    private fun initializeUiComponents() {
        layout = BorderLayout()

        TreeSpeedSearch(vulnerabilitiesTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true)
    }

    private fun reloadTree() {
        (vulnerabilitiesTree.model as DefaultTreeModel).reload()
    }
}
