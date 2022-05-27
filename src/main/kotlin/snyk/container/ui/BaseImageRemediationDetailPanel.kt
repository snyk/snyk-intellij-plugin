package snyk.container.ui

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.uiDesigner.core.GridLayoutManager
import icons.SnykIcons
import io.snyk.plugin.Severity
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.ui.DescriptionHeaderPanel
import io.snyk.plugin.ui.baseGridConstraintsAnchorWest
import io.snyk.plugin.ui.descriptionHeaderPanel
import io.snyk.plugin.ui.panelGridConstraints
import io.snyk.plugin.ui.toolwindow.IssueDescriptionPanelBase
import snyk.container.BaseImageInfo
import snyk.container.BaseImageVulnerabilities
import snyk.container.ContainerIssuesForImage
import snyk.container.KubernetesWorkloadImage
import java.awt.Font
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

class BaseImageRemediationDetailPanel(
    private val project: Project,
    private val imageIssues: ContainerIssuesForImage
) : IssueDescriptionPanelBase(
    title = imageIssues.imageName,
    severity = imageIssues.getSeverities().max() ?: Severity.UNKNOWN
) {

    private val targetImages: List<KubernetesWorkloadImage> = getKubernetesImageCache(project)
        ?.getKubernetesWorkloadImages()
        ?.filter { it.image == imageIssues.imageName }
        ?: emptyList()

    init {
        createUI()
    }

    override fun createMainBodyPanel(): Pair<JPanel, Int> {
        val lastRowToAddSpacer = 1
        val panel = JPanel(
            GridLayoutManager(lastRowToAddSpacer + 1, 1, Insets(10, 10, 20, 20), -1, 0)
        )

        if (imageIssues.baseImageRemediationInfo != null) {
            panel.add(
                baseRemediationInfoPanel(),
                panelGridConstraints(0)
            )
        }

        return Pair(panel, lastRowToAddSpacer)
    }

    private fun baseRemediationInfoPanel(): JPanel {
        val panel = JPanel(GridLayoutManager(4, 4, Insets(0, 0, 0, 0), 0, 10))

        panel.add(
            JLabel("Recommendations for upgrading the base image"),
            baseGridConstraintsAnchorWest(0)
        )

        panel.add(
            innerRemediationInfoPanel(),
            baseGridConstraintsAnchorWest(1)
        )

        return panel
    }

    private fun innerRemediationInfoPanel(): JPanel {
        val panel = JPanel(GridLayoutManager(4, 4, Insets(0, 0, 0, 0), 0, 10))

        imageIssues.baseImageRemediationInfo?.currentImage?.let {
            addBaseImageInfo(panel, 0, CURRENT_IMAGE, it)
        }

        imageIssues.baseImageRemediationInfo?.minorUpgrades?.let {
            addBaseImageInfo(panel, 1, MINOR_UPGRADES, it)
        }

        imageIssues.baseImageRemediationInfo?.majorUpgrades?.let {
            addBaseImageInfo(panel, 2, MAJOR_UPGRADES, it)
        }

        imageIssues.baseImageRemediationInfo?.alternativeUpgrades?.let {
            addBaseImageInfo(panel, 3, ALTERNATIVE_UPGRADES, it)
        }

        return panel
    }

    private fun addBaseImageInfo(panel: JPanel, row: Int, title: String, info: BaseImageInfo) {
        panel.add(
            JLabel(title).apply {
                font = io.snyk.plugin.ui.getFont(Font.BOLD, -1, JLabel().font)
            },
            baseGridConstraintsAnchorWest(row, 0, indent = 0)
        )
        panel.add(
            JLabel(info.name).apply {
                name = title
            },
            baseGridConstraintsAnchorWest(row, 1, indent = 5)
        )
        panel.add(
            getVulnerabilitiesCHMLpanel(info.vulnerabilities),
            baseGridConstraintsAnchorWest(row, 2, indent = 5)
        )
    }

    private fun getVulnerabilitiesCHMLpanel(vulnerabilities: BaseImageVulnerabilities?): JPanel {
        val panel = JPanel()
        panel.layout = GridLayoutManager(1, 4, Insets(0, 0, 0, 0), 5, 0)

        if (vulnerabilities == null) return panel

        panel.add(
            getSeverityCountItem(vulnerabilities.critical, Severity.CRITICAL),
            baseGridConstraintsAnchorWest(0, column = 0, indent = 0)
        )
        panel.add(
            getSeverityCountItem(vulnerabilities.high, Severity.HIGH),
            baseGridConstraintsAnchorWest(0, column = 1, indent = 0)
        )
        panel.add(
            getSeverityCountItem(vulnerabilities.medium, Severity.MEDIUM),
            baseGridConstraintsAnchorWest(0, column = 2, indent = 0)
        )
        panel.add(
            getSeverityCountItem(vulnerabilities.low, Severity.LOW),
            baseGridConstraintsAnchorWest(0, column = 3, indent = 0)
        )

        return panel
    }

    private fun getSeverityCountItem(count: Int, severity: Severity): JPanel {
        val panel = JPanel()
        panel.layout = GridLayoutManager(1, 2, Insets(0, 0, 0, 0), 0, 0)

        val baseColor = severity.getColor()
        panel.add(
            JLabel("%3d ".format(count)).apply {
                font = io.snyk.plugin.ui.getFont(Font.BOLD, 14, JTextArea().font)
                foreground = baseColor
            },
            baseGridConstraintsAnchorWest(0, column = 0, indent = 0)
        )
        panel.add(
            JLabel(" ").apply {
                icon = SnykIcons.getSeverityIcon(severity)
            },
            baseGridConstraintsAnchorWest(0, column = 1, indent = 0)
        )

        panel.isOpaque = true
        panel.background = severity.getBgColor()// UIUtil.mix(Color.WHITE, baseColor, 0.25)

        return panel
    }

    override fun getTitleIcon(): Icon = SnykIcons.CONTAINER_IMAGE_24

    override fun secondRowTitlePanel(): DescriptionHeaderPanel = descriptionHeaderPanel(
        issueNaming = "Image",
        customLabels = secondRowTitleLabels()
    )

    private fun secondRowTitleLabels(): List<JLabel> {
        val affectedFile2Line = targetImages.map { Pair(it.virtualFile, it.lineNumber) }

        return listOf(JLabel("${imageIssues.uniqueCount} vulnerabilities")) +
            // todo: fix UI(?)
            affectedFile2Line.map { (file, line) ->
                val targetFileName = file.name + ":" + line
                LinkLabel.create(targetFileName) {
                    navigateToTargetFile(file, line - 1) // to 1-based count used in the editor
                }
            }
    }

    private fun navigateToTargetFile(file: VirtualFile, line: Int) {
        if (file.isValid) {
            val document = FileDocumentManager.getInstance().getDocument(file)
            if (document != null) {
                val lineNumber = if (0 <= line && line < document.lineCount) line else 0
                val lineStartOffset = document.getLineStartOffset(lineNumber)
                PsiNavigationSupport.getInstance().createNavigatable(
                    project,
                    file,
                    lineStartOffset
                ).navigate(false)
            }
        }
    }

    companion object {
        const val CURRENT_IMAGE = "Current image"
        const val MINOR_UPGRADES = "Minor upgrades"
        const val MAJOR_UPGRADES = "Major upgrades"
        const val ALTERNATIVE_UPGRADES = "Alternative upgrades"
    }
}
