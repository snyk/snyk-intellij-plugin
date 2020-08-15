package io.snyk.plugin.ui

import com.intellij.ide.plugins.newui.TwoLineProgressIndicator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.uiDesigner.core.GridConstraints as IntelliJGridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager as IntelliJGridLayoutManager
import io.snyk.plugin.cli.Vulnerability
import java.awt.*
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Main panel for Snyk tool window.
 */
@Service
class SnykToolWindowPanel : JPanel(), VulnerabilitiesView {

    private val descriptionPanel = FullDescriptionPanel()

    private val rootTreeNode = DefaultMutableTreeNode("package.json")
    private val vulnerabilitiesTree = Tree(rootTreeNode)

    private val vulnerabilitiesSplitter = OnePixelSplitter(false, 0.4f, 0.1f, 0.9f)

    init {
        vulnerabilitiesTree.cellRenderer = VulnerabilityTreeCellRenderer()

        initializeUI()
    }

    override fun error(error: String) {
        vulnerabilitiesSplitter.firstComponent = TextPanel(error)
    }

    override fun scanning() {
        ApplicationManager.getApplication().invokeLater {
            removeAll()

            val indicator = TwoLineProgressIndicator()
            indicator.text = "Scanning..."

            add(CenterOneComponentPanel(indicator.createBaselineWrapper()), BorderLayout.CENTER)
        }
    }

    override fun vulnerabilities(vulnerabilitis: List<Vulnerability>) {
        ApplicationManager.getApplication().invokeLater {
            removeAll()

            add(vulnerabilitiesSplitter, BorderLayout.CENTER)

            vulnerabilitiesTree.selectionModel.addTreeSelectionListener {
                ApplicationManager.getApplication().invokeLater {
                    descriptionPanel.removeAll()

                    val selectionPath = vulnerabilitiesTree.selectionPath

                    if (selectionPath != null) {
                        val node: DefaultMutableTreeNode = selectionPath.lastPathComponent as DefaultMutableTreeNode

                        if (node.userObject is Vulnerability) {
                            descriptionPanel.displayDescription(node.userObject as Vulnerability)
                        }
                    }
                }
            }

            vulnerabilitiesSplitter.firstComponent = ScrollPaneFactory.createScrollPane(vulnerabilitiesTree)
            vulnerabilitiesSplitter.secondComponent = descriptionPanel

            rootTreeNode.removeAllChildren()

            vulnerabilitis.forEach {
                rootTreeNode.add(VulnerabilityTreeNode(it))
            }

            (vulnerabilitiesTree.model as DefaultTreeModel).reload()
            TreeUtil.expandAll(vulnerabilitiesTree)
        }
    }

    private fun initializeUI() {
        layout = BorderLayout()

        TreeSpeedSearch(vulnerabilitiesTree, TreeSpeedSearch.NODE_DESCRIPTOR_TOSTRING, true)
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

        //colorPanel.add(LabelFactory.icon(Icons.vulnerability24))

        informationPanel.add(colorPanel, BorderLayout.WEST)

        val titlePanel = JPanel()
        titlePanel.setLayout(IntelliJGridLayoutManager(
            3,
            1,
            Insets(0, 0, 0, 0),
        -1,
        -1))

        val titleLabel = LabelFactory.bold(vulnerability.title)
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

//        graphics.setColor(Color(-5148144))
//        graphics.fillRect(0, 0, this.getWidth() - 3, this.getHeight())
//        graphics.setColor(ColorProvider.intellij.bgColor)
//        graphics.fillRect(4, 0, this.getWidth, this.getHeight)
    }
}

private class VulnerabilityTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: JTree, value: Any, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
        if (value is VulnerabilityTreeNode) {
            val vulnerabilityTreeNode = value as VulnerabilityTreeNode

            if (vulnerabilityTreeNode.userObject is Vulnerability) {
                val vulnerability = vulnerabilityTreeNode.userObject as Vulnerability

                val severityIcon = when (vulnerability.severity) {
                    "high" -> Icons.HIGH_SEVERITY
                    "medium" -> Icons.MEDIUM_SEVERITY
                    "low" -> Icons.LOW_SEVERITY
                    else -> Icons.VULNERABILITY_24
                }

                icon = severityIcon
                font = UIUtil.getTreeFont()

                append(vulnerability.title)
            } else if (vulnerabilityTreeNode.userObject is DefaultMutableTreeNode) {
                val rootNode = vulnerabilityTreeNode.userObject as DefaultMutableTreeNode

                icon = Icons.VULNERABILITY_16
                font = UIUtil.getTreeFont()

                append(rootNode.userObject.toString())
            }
        }
    }
}

object LabelFactory {
    fun bold(title: String): JLabel {
        val label = JLabel(title)
        val labelFont = label.font
        label.font = labelFont.deriveFont(labelFont.style or Font.BOLD)

        return label
    }

    fun icon(imageIcon: ImageIcon): JLabel {
        val label = JLabel()
        label.horizontalAlignment = 0
        label.icon = imageIcon
        label.text = ""

        return label
    }
}

interface VulnerabilitiesView {
    companion object {
        val EMPTY: VulnerabilitiesView = object: VulnerabilitiesView {
            override fun error(error: String) {
            }

            override fun scanning() {
            }

            override fun vulnerabilities(vulnerabilitis: List<Vulnerability>) {
            }
        }
    }

    fun error(error: String)
    fun scanning()
    fun vulnerabilities(vulnerabilitis: List<Vulnerability>)
}
