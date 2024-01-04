package snyk.iac.ui.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import io.snyk.plugin.analytics.getIssueSeverityOrNull
import io.snyk.plugin.findPsiFileIgnoringExceptions
import io.snyk.plugin.getSnykAnalyticsService
import io.snyk.plugin.ui.toolwindow.nodes.DescriptionHolderTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.NavigatableToSourceTreeNode
import io.snyk.plugin.ui.toolwindow.panels.IssueDescriptionPanelBase
import snyk.analytics.IssueInTreeIsClicked
import snyk.iac.IacIssue
import snyk.iac.IacIssuesForFile
import snyk.iac.IacSuggestionDescriptionPanel
import java.nio.file.Paths
import javax.swing.tree.DefaultMutableTreeNode

class IacIssueTreeNode(
    private val issue: IacIssue,
    val project: Project,
    override val navigateToSource: () -> Unit,
) : DefaultMutableTreeNode(issue), NavigatableToSourceTreeNode, DescriptionHolderTreeNode {

    override fun getDescriptionPanel(logEventNeeded: Boolean): IssueDescriptionPanelBase {
        if (logEventNeeded) getSnykAnalyticsService().logIssueInTreeIsClicked(
            IssueInTreeIsClicked.builder()
                .ide(IssueInTreeIsClicked.Ide.JETBRAINS)
                .issueType(IssueInTreeIsClicked.IssueType.INFRASTRUCTURE_AS_CODE_ISSUE)
                .issueId(issue.id)
                .severity(issue.getIssueSeverityOrNull())
                .build()
        )
        val iacIssuesForFile = (this.parent as? IacFileTreeNode)?.userObject as? IacIssuesForFile
            ?: throw IllegalArgumentException(this.toString())
        val fileName = iacIssuesForFile.targetFilePath
        val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(Paths.get(fileName))
        val psiFile = virtualFile?.let { findPsiFileIgnoringExceptions(it, project) }

        return IacSuggestionDescriptionPanel(issue, psiFile, project)
    }
}
