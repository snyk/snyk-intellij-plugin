package io.snyk.plugin.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import io.snyk.plugin.cli.CliGroupedResult
import com.intellij.uiDesigner.core.GridConstraints as IJGridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager as IJGridLayoutManager
import com.intellij.uiDesigner.core.Spacer
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

class VulnerabilityTreeNode(vulnerability: Vulnerability) : DefaultMutableTreeNode(vulnerability)

class TargetFileTreeNode(targetFileName: String) : DefaultMutableTreeNode(targetFileName)

class CenterOneComponentPanel(component: JComponent) : JPanel() {
    init {
        layout = GridBagLayout()

        add(component)
    }
}

class FullDescriptionPanel : JPanel() {
    init {
        layout = BorderLayout()

        displaySelectVulnerabilityMessage()
    }

    fun displayDescription(vulnerability: Vulnerability) {
        removeAll()

        add(ScrollPaneFactory.createScrollPane(VulnerabilityDescriptionPanel(vulnerability)), BorderLayout.CENTER)

        revalidate()
    }

    fun displaySelectVulnerabilityMessage() {
        removeAll()

        add(CenterOneComponentPanel(JLabel("Please, select vulnerability")), BorderLayout.CENTER)

        revalidate()
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
        } else if (value is TargetFileTreeNode) {
            val nodeText = value.userObject.toString()

            icon = when (nodeText.toLowerCase()) {
                "pom.xml" -> Icons.MAVEN
                "build.gradle", "build.gradle.kts"-> Icons.GRADLE
                "package.json", "package-lock.json"-> Icons.NPM
                "requirements.txt"-> Icons.PYTHON
                else -> AllIcons.FileTypes.Text
            }

            font = UIUtil.getTreeFont()

            append(nodeText)
        } else if (value is DefaultMutableTreeNode) {
            icon = Icons.VULNERABILITY_16
            font = UIUtil.getTreeFont()

            append(value.userObject.toString())
        }
    }
}

class VulnerabilityDescriptionPanel(private val vulnerability: Vulnerability) : JPanel() {

    init {
        this.layout = IJGridLayoutManager(11, 1, Insets(20, 0, 0, 0), -1, 10)

        this.add(Spacer(),
            IJGridConstraints(
                10,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_VERTICAL,
                1,
                IJGridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0, false))

        val titleLabel = JLabel()
        val titleLabelFont: Font? = getFont(-1, 18, titleLabel.font)

        if (titleLabelFont != null) {
            titleLabel.font = titleLabelFont
        }

        titleLabel.text = vulnerability.title
        this.add(titleLabel,
            IJGridConstraints(
                1,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                1,
                false))

        this.add(buildTwoLabelsPanel("Vulnerable module:", vulnerability.moduleName),
            IJGridConstraints(
                2,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                1,
                false))

        this.add(buildTwoLabelsPanel("Introduced through:", "Unknown"),
            IJGridConstraints(
                3,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                1,
                false))

        this.add(buildTwoLabelsPanel("Exploit maturity:", vulnerability.exploit),
            IJGridConstraints(
                4,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                1,
                false))

        this.add(buildTwoLabelsPanel("Fixed in:", vulnerability.fixedIn.joinToString()),
            IJGridConstraints(
                5,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                1,
                false))

        val fixPanel = JPanel()
        fixPanel.isVisible = false
        fixPanel.layout = IJGridLayoutManager(1, 2, Insets(0, 0, 0, 0), -1, -1)

        this.add(fixPanel,
            IJGridConstraints(
                6,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                1,
                false))

        val fixIssueButton = JButton()
        fixIssueButton.text = "Fix this vulnerability"

        fixPanel.add(fixIssueButton,
            IJGridConstraints(
                0,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_HORIZONTAL,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        fixPanel.add(Spacer(),
            IJGridConstraints(
                0,
                1,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_HORIZONTAL,
                IJGridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false))

        val detailsPanel = JPanel()
        detailsPanel.layout = IJGridLayoutManager(4, 2, Insets(0, 0, 0, 0), -1, -1)
        this.add(detailsPanel,
            IJGridConstraints(
                7,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                1,
                false))


        detailsPanel.add(buildBoldTitleLabel("Detailed paths and remediation"),
            IJGridConstraints(
                0,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        detailsPanel.add(Spacer(),
            IJGridConstraints(
                0,
                1,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_HORIZONTAL,
                IJGridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false))

        detailsPanel.add(Spacer(),
            IJGridConstraints(
                3,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_VERTICAL,
                1,
                IJGridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false))

        detailsPanel.add(buildTwoLabelsPanel("Introduced through:", vulnerability.from.joinToString()),
            IJGridConstraints(
                1,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        detailsPanel.add(buildTwoLabelsPanel("Remediation:", "Upgrade to " + vulnerability.fixedIn.joinToString()),
            IJGridConstraints(
                2,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        val overviewPanel = JPanel()
        overviewPanel.layout = IJGridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, -1)

        this.add(overviewPanel,
            IJGridConstraints(
                8,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                1,
                false))

        overviewPanel.add(buildBoldTitleLabel("Overview"),
            IJGridConstraints(
                0,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        val descriptionTextArea = JTextArea(vulnerability.getOverview())
        descriptionTextArea.lineWrap = true
        descriptionTextArea.wrapStyleWord = true
        descriptionTextArea.isOpaque = false
        descriptionTextArea.isEditable = false
        descriptionTextArea.background = UIUtil.getPanelBackground()

        overviewPanel.add(ScrollPaneFactory.createScrollPane(descriptionTextArea, true),
            IJGridConstraints(
                1,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_GROW or IJGridConstraints.SIZEPOLICY_CAN_SHRINK,
                IJGridConstraints.SIZEPOLICY_CAN_GROW or IJGridConstraints.SIZEPOLICY_CAN_SHRINK,
                null,
                null,
                null,
                1,
                false))

        val moreInfoPanel = JPanel()
        moreInfoPanel.layout = IJGridLayoutManager(1, 2, Insets(0, 0, 0, 0), -1, -1)
        this.add(moreInfoPanel,
            IJGridConstraints(
                9,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false))

        val moreInfoLabel = LinkLabel.create("More about this issue") {
            BrowserUtil.open("https://snyk.io/vuln/" + vulnerability.id)
        }

        moreInfoPanel.add(moreInfoLabel,
            IJGridConstraints(
                0,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_HORIZONTAL,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                1,
                false))

        moreInfoPanel.add(Spacer(),
            IJGridConstraints(
                0,
                1,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_HORIZONTAL,
                IJGridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false))

        val severityPanel = SeverityColorPanel(vulnerability.severity)
        severityPanel.layout = IJGridLayoutManager(2, 2, Insets(10, 10, 10, 10), -1, -1)

        this.add(severityPanel,
            IJGridConstraints(
                0,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false))

        val severityLabel = JLabel()

        val severityLabelFont: Font? = getFont(-1, 14, severityLabel.font)

        if (severityLabelFont != null) {
            severityLabel.font = severityLabelFont
        }

        severityLabel.text = when (vulnerability.severity) {
            "high" -> "HIGH SEVERITY"
            "medium" -> "MEDIUM SEVERITY"
            "low" -> "LOW SEVERITY"
            else -> "UNKNOWN SEVERITY"
        }

        severityLabel.foreground = Color(-1)

        severityPanel.add(severityLabel,
            IJGridConstraints(
                0,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        severityPanel.add(Spacer(),
            IJGridConstraints(
                0,
                1,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_HORIZONTAL,
                IJGridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false))

        severityPanel.add(Spacer(),
            IJGridConstraints(
                1,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_VERTICAL,
                1,
                IJGridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false))
    }
}

class SeverityColorPanel(private val severity: String) : JPanel() {
    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)

        graphics.color = when (severity) {
            "high" -> Color.decode("#B31B6B")
            "medium" -> Color.decode("#DF8620")
            "low" -> Color.decode("#595775")
            else -> UIUtil.getPanelBackground()
        }

        graphics.fillRoundRect(0, 0, 150, this.height, 5, 5)
        graphics.fillRect(0, 0, 20, this.height)
    }
}

class CliErrorPanel(private val cliError: CliError) : JPanel() {

    init {
        this.layout = IJGridLayoutManager(11, 1, Insets(20, 0, 0, 0), -1, 10)

        this.add(Spacer(),
            IJGridConstraints(
                10,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_VERTICAL,
                1,
                IJGridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0, false))

        val pathPanel = JPanel()
        pathPanel.layout = IJGridLayoutManager(4, 2, Insets(0, 0, 0, 0), -1, -1)
        this.add(pathPanel,
            IJGridConstraints(
                7,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                1,
                false))


        pathPanel.add(buildBoldTitleLabel("Path"),
            IJGridConstraints(
                0,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        val pathTextArea = JTextArea(cliError.path)
        pathTextArea.lineWrap = true
        pathTextArea.wrapStyleWord = true
        pathTextArea.isOpaque = false
        pathTextArea.isEditable = false
        pathTextArea.background = UIUtil.getPanelBackground()

        pathPanel.add(ScrollPaneFactory.createScrollPane(pathTextArea, true),
            IJGridConstraints(
                2,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_GROW or IJGridConstraints.SIZEPOLICY_CAN_SHRINK,
                IJGridConstraints.SIZEPOLICY_CAN_GROW or IJGridConstraints.SIZEPOLICY_CAN_SHRINK,
                null,
                null,
                null,
                1,
                false))

        val messagePanel = JPanel()
        messagePanel.layout = IJGridLayoutManager(2, 1, Insets(0, 0, 0, 0), -1, -1)

        this.add(messagePanel,
            IJGridConstraints(
                8,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                1,
                false))

        messagePanel.add(buildBoldTitleLabel("Message"),
            IJGridConstraints(
                0,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        val errorMessageTextArea = JTextArea(cliError.message)
        errorMessageTextArea.lineWrap = true
        errorMessageTextArea.wrapStyleWord = true
        errorMessageTextArea.isOpaque = false
        errorMessageTextArea.isEditable = false
        errorMessageTextArea.background = UIUtil.getPanelBackground()

        messagePanel.add(ScrollPaneFactory.createScrollPane(errorMessageTextArea, true),
            IJGridConstraints(
                1,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_GROW or IJGridConstraints.SIZEPOLICY_CAN_SHRINK,
                IJGridConstraints.SIZEPOLICY_CAN_GROW or IJGridConstraints.SIZEPOLICY_CAN_SHRINK,
                null,
                null,
                null,
                1,
                false))

        val errorLabelPanel = SeverityColorPanel("high")
        errorLabelPanel.layout = IJGridLayoutManager(2, 2, Insets(10, 10, 10, 10), -1, -1)

        this.add(errorLabelPanel,
            IJGridConstraints(
                0,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_BOTH,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                IJGridConstraints.SIZEPOLICY_CAN_SHRINK or IJGridConstraints.SIZEPOLICY_CAN_GROW,
                null,
                null,
                null,
                0,
                false))

        val errorLabel = JLabel()

        val severityLabelFont: Font? = getFont(-1, 14, errorLabel.font)

        if (severityLabelFont != null) {
            errorLabel.font = severityLabelFont
        }

        errorLabel.text = "Error"

        errorLabel.foreground = Color(-1)

        errorLabelPanel.add(errorLabel,
            IJGridConstraints(
                0,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_WEST,
                IJGridConstraints.FILL_NONE,
                IJGridConstraints.SIZEPOLICY_FIXED,
                IJGridConstraints.SIZEPOLICY_FIXED,
                null,
                null,
                null,
                0,
                false))

        errorLabelPanel.add(Spacer(),
            IJGridConstraints(
                0,
                1,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_HORIZONTAL,
                IJGridConstraints.SIZEPOLICY_WANT_GROW,
                1,
                null,
                null,
                null,
                0,
                false))

        errorLabelPanel.add(Spacer(),
            IJGridConstraints(
                1,
                0,
                1,
                1,
                IJGridConstraints.ANCHOR_CENTER,
                IJGridConstraints.FILL_VERTICAL,
                1,
                IJGridConstraints.SIZEPOLICY_WANT_GROW,
                null,
                null,
                null,
                0,
                false))
    }
}
