package snyk.iac

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil.pathsEqual
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSnykToolWindowPanel
import okhttp3.internal.toImmutableList

class IacBulkFileListener : SnykBulkFileListener() {

    private val log = logger<IacBulkFileListener>()

    override fun before(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        // clean IaC cached results for deleted/moved/renamed files
        updateIacCache(
            virtualFilesAffected,
            project
        )
    }

    override fun after(project: Project, virtualFilesAffected: Set<VirtualFile>) {
        // update IaC cached results if needed
        updateIacCache(virtualFilesAffected, project)
    }

    private fun updateIacCache(
        virtualFilesAffected: Set<VirtualFile>,
        project: Project
    ) {
        if (virtualFilesAffected.isEmpty()) return
        val snykCachedResults = getSnykCachedResults(project)
        val currentIacResult = snykCachedResults?.currentIacResult ?: return
        val allIacIssuesForFiles = currentIacResult.allCliIssues ?: return

        val iacRelatedVFsAffected = virtualFilesAffected
            .filter { iacFileExtensions.contains(it.extension) }
            .filter { ProjectRootManager.getInstance(project).fileIndex.isInContent(it) }

        allIacIssuesForFiles
            .filter { iacIssuesForFile ->
                iacRelatedVFsAffected.any {
                    pathsEqual(it.path, iacIssuesForFile.targetFilePath)
                }
            }
            .forEach(::markObsolete)

        val changed = iacRelatedVFsAffected.isNotEmpty() // for new/deleted/renamed files we also need to "dirty" the cache, too
        if (changed) {
            log.debug("update IaC cache for $iacRelatedVFsAffected")
            currentIacResult.iacScanNeeded = true
            ApplicationManager.getApplication().invokeLater {
                getSnykToolWindowPanel(project)?.displayIacResults(currentIacResult)
            }
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }

    private fun markObsolete(iacIssuesForFile: IacIssuesForFile) {
        iacIssuesForFile.infrastructureAsCodeIssues.forEach { it.obsolete = true }
    }

    companion object {
        // see https://github.com/snyk/snyk/blob/master/src/lib/iac/constants.ts#L7
        private val iacFileExtensions = listOf(
            "yaml",
            "yml",
            "json",
            "tf"
        )
    }
}
