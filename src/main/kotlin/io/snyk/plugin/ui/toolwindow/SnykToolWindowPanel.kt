package io.snyk.plugin.ui.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.cli.CliGroupedResult
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.Vulnerability
import io.snyk.plugin.head
import java.awt.*
import java.util.Objects.nonNull
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Main panel for Snyk tool window.
 */
@Service
class SnykToolWindowPanel : JPanel() {

    private val descriptionPanel = FullDescriptionPanel()

    private val rootTreeNode = DefaultMutableTreeNode("")
    private val vulnerabilitiesTree = Tree(rootTreeNode)

    private val vulnerabilitiesSplitter = OnePixelSplitter(false, 0.4f, 0.1f, 0.9f)

    init {
        vulnerabilitiesTree.cellRenderer = VulnerabilityTreeCellRenderer()

        initializeUI()

        displayInitialMessage()
    }

    fun clean() {
        ApplicationManager.getApplication().invokeLater {
            removeAll()

            rootTreeNode.userObject = ""
            rootTreeNode.removeAllChildren()

            reloadTree()

            displayInitialMessage()

            revalidate()
        }
    }

    fun isEmpty(): Boolean = rootTreeNode.childCount == 0

    fun displayVulnerabilities(cliGroupedResult: CliGroupedResult) {
        ApplicationManager.getApplication().invokeLater {
            removeAll()

            rootTreeNode.userObject = "Found ${cliGroupedResult.uniqueCount} issues."

            add(vulnerabilitiesSplitter, BorderLayout.CENTER)

            vulnerabilitiesTree.selectionModel.addTreeSelectionListener {
                ApplicationManager.getApplication().invokeLater {
                    descriptionPanel.removeAll()

                    val selectionPath = vulnerabilitiesTree.selectionPath

                    if (nonNull(selectionPath)) {
                        val node: DefaultMutableTreeNode = selectionPath!!.lastPathComponent as DefaultMutableTreeNode

                        if (node.userObject is Vulnerability) {
                            descriptionPanel.displayDescription(node.userObject as Vulnerability)
                        } else {
                            descriptionPanel.displaySelectVulnerabilityMessage()
                        }
                    }
                }
            }

            vulnerabilitiesSplitter.firstComponent = ScrollPaneFactory.createScrollPane(vulnerabilitiesTree)
            vulnerabilitiesSplitter.secondComponent = descriptionPanel

            val fileTreeNode = TargetFileTreeNode(cliGroupedResult.displayTargetFile)
            rootTreeNode.add(fileTreeNode)

            cliGroupedResult.vulnerabilitiesMap.keys.forEach { id ->
                fileTreeNode.add(VulnerabilityTreeNode(cliGroupedResult.vulnerabilitiesMap.getValue(id).head))
            }

            reloadTree()

            TreeUtil.expandAll(vulnerabilitiesTree)
        }
    }

    private fun initializeUI() {
        layout = BorderLayout()

        TreeSpeedSearch(vulnerabilitiesTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true)
    }

    private fun displayInitialMessage() {
        add(CenterOneComponentPanel(JLabel("Please, run scan")), BorderLayout.CENTER)
    }

    private fun reloadTree() {
        (vulnerabilitiesTree.model as DefaultTreeModel).reload()
    }

    fun displayError(cliError: CliError) {
        removeAll()

        add(CliErrorPanel(cliError), BorderLayout.CENTER)

        revalidate()
    }
}
