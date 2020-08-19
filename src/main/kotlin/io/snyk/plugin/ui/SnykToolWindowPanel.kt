package io.snyk.plugin.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.cli.CliGroupedResult
import com.intellij.uiDesigner.core.GridConstraints as IntelliJGridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager as IntelliJGridLayoutManager
import io.snyk.plugin.cli.Vulnerability
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

            rootTreeNode.userObject = "Found ${cliGroupedResult.uniqueCount} issues, ${cliGroupedResult.pathsCount} vulnerable paths."

            add(vulnerabilitiesSplitter, BorderLayout.CENTER)

            vulnerabilitiesTree.selectionModel.addTreeSelectionListener {
                ApplicationManager.getApplication().invokeLater {
                    descriptionPanel.removeAll()

                    val selectionPath = vulnerabilitiesTree.selectionPath

                    if (nonNull(selectionPath)) {
                        val node: DefaultMutableTreeNode = selectionPath!!.lastPathComponent as DefaultMutableTreeNode

                        if (node.userObject is Vulnerability) {
                            descriptionPanel.displayDescription(node.userObject as Vulnerability)
                        }
                    }
                }
            }

            vulnerabilitiesSplitter.firstComponent = ScrollPaneFactory.createScrollPane(vulnerabilitiesTree)
            vulnerabilitiesSplitter.secondComponent = descriptionPanel

            val fileTreeNode = DefaultMutableTreeNode(cliGroupedResult.displayTargetFile)
            rootTreeNode.add(fileTreeNode)

            cliGroupedResult.vulnerabilitiesMap.keys.forEach { id ->
                val vulnerabilityIdTreeNode = DefaultMutableTreeNode(id)

                fileTreeNode.add(vulnerabilityIdTreeNode)

                cliGroupedResult.vulnerabilitiesMap[id]?.forEach { vulnerability ->
                    vulnerabilityIdTreeNode.add(VulnerabilityTreeNode(vulnerability))
                }
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
}

class VulnerabilityTreeNode(vulnerability: Vulnerability) : DefaultMutableTreeNode(vulnerability)

class TextPanel(text: String) : CenterOneComponentPanel(JLabel(text))

open class CenterOneComponentPanel(component: JComponent) : JPanel() {
    init {
        layout = GridBagLayout()

        add(component)
    }
}

class FullDescriptionPanel : JPanel() {
    init {
        displayNoAnalysisLabel()
    }

    fun displayDescription(vulnerability: Vulnerability) {
        removeAll()

        layout = BorderLayout()

        add(ShortDescriptionPanel(vulnerability), BorderLayout.NORTH)
    }

    private fun displayNoAnalysisLabel() {
        removeAll()

        layout = GridBagLayout()

        add(JLabel("Please, select vulnerability..."))
    }
}

class ShortDescriptionPanel(private val vulnerability: Vulnerability) : JPanel() {

    init {
        initializeUI()
    }

    private fun initializeUI() {
        layout = BorderLayout()

        val informationPanel = JPanel(BorderLayout())

        val colorPanel = ColorPanel()

        colorPanel.add(iconLabel(Icons.VULNERABILITY_24))

        informationPanel.add(colorPanel, BorderLayout.WEST)

        val titlePanel = JPanel()
        titlePanel.setLayout(IntelliJGridLayoutManager(
            3,
            1,
            Insets(0, 0, 0, 0),
        -1,
        -1))

        val titleLabel = boldLabel(vulnerability.title)
        titlePanel.add(titleLabel,
            IntelliJGridConstraints(
                0,
            0,
            1,
            1,
            IntelliJGridConstraints.ANCHOR_WEST,
            IntelliJGridConstraints.FILL_NONE,
            IntelliJGridConstraints.SIZEPOLICY_FIXED,
            IntelliJGridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false
        ))

        val onDependencyLabel = JLabel()
        onDependencyLabel.horizontalAlignment = 0
        onDependencyLabel.horizontalTextPosition = 0
        onDependencyLabel.text = "on " + vulnerability.moduleName

        titlePanel.add(onDependencyLabel,
            IntelliJGridConstraints(
                1,
            0,
            1,
            1,
            IntelliJGridConstraints.ANCHOR_CENTER,
            IntelliJGridConstraints.FILL_NONE,
            IntelliJGridConstraints.SIZEPOLICY_FIXED,
            IntelliJGridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false
        ))

        /*tailrec fun fillViaDependenciesTree(derivations: Seq[MiniTree[VulnDerivation]], parentTreeNode: DefaultMutableTreeNode) {
            if (derivations.nonEmpty) {
                val dependencyTreeNode = new DefaultMutableTreeNode (derivations.head.content.module.toString)

                parentTreeNode.add(dependencyTreeNode)

                fillViaDependenciesTree(derivations.head.nested, dependencyTreeNode)
            }
        }*/

        val dependencyRootTreeNode = DefaultMutableTreeNode("Via:")

        //fillViaDependenciesTree(miniVulnerability.derivations, dependencyRootTreeNode)

        val viaDependencyTree = Tree(dependencyRootTreeNode)
        titlePanel.add(viaDependencyTree,
            IntelliJGridConstraints(
                2,
            0,
            1,
            1,
            IntelliJGridConstraints.ANCHOR_CENTER,
            IntelliJGridConstraints.FILL_BOTH,
            IntelliJGridConstraints.SIZEPOLICY_WANT_GROW,
            IntelliJGridConstraints.SIZEPOLICY_WANT_GROW,
            null,
            Dimension(150, 50),
        null,
        0,
        false
        ))

        informationPanel.add(titlePanel, BorderLayout.CENTER)

        val descriptionTextArea = JTextArea(vulnerability.description)
        descriptionTextArea.lineWrap = true
        descriptionTextArea.wrapStyleWord = true
        descriptionTextArea.isOpaque = false
        descriptionTextArea.isEditable = false

        val verticalSplitter = OnePixelSplitter(true, 0.2f)

        verticalSplitter.firstComponent = informationPanel
        verticalSplitter.secondComponent = ScrollPaneFactory.createScrollPane(descriptionTextArea, true)

        add(verticalSplitter, BorderLayout.CENTER)
    }
}

private class ColorPanel : JPanel() {
    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)

        graphics.color = Color(-5148144)
        graphics.fillRect(0, 0, this.width - 3, this.height)
        graphics.color = UIUtil.getPanelBackground()
        graphics.fillRect(4, 0, this.width, this.height)
    }
}

private class VulnerabilityTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean) {

        if (value is VulnerabilityTreeNode) {
            if (value.userObject is Vulnerability) {
                val vulnerability = value.userObject as Vulnerability

                val severityIcon = when (vulnerability.severity) {
                    "high" -> Icons.HIGH_SEVERITY
                    "medium" -> Icons.MEDIUM_SEVERITY
                    "low" -> Icons.LOW_SEVERITY
                    else -> Icons.VULNERABILITY_24
                }

                icon = severityIcon
                font = UIUtil.getTreeFont()

                append(vulnerability.title)
            }
        } else if (value is DefaultMutableTreeNode) {
            icon = Icons.VULNERABILITY_16
            font = UIUtil.getTreeFont()

            append(value.userObject.toString())
        }
    }
}
