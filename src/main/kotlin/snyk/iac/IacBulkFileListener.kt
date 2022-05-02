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
        val iacFiles = snykCachedResults?.currentIacResult?.allCliIssues ?: return

        val newIacFileList = iacFiles.toMutableList()
        val iacRelatedvirtualFilesAffected = virtualFilesAffected
            .filter { iacFileExtensions.contains(it.extension) }
            .filter { ProjectRootManager.getInstance(project).fileIndex.isInContent(it) }
        val changed = iacRelatedvirtualFilesAffected.isNotEmpty() // for new files we need to "dirty" the cache, too
        iacRelatedvirtualFilesAffected.forEach {
            newIacFileList.forEachIndexed { i, iacIssuesForFile ->
                if (pathsEqual(it.path, iacIssuesForFile.targetFilePath)) {
                    val obsoleteIacFile = makeObsolete(iacIssuesForFile)
                    newIacFileList[i] = obsoleteIacFile
                }
            }
        }

        if (changed) {
            log.debug("update IaC cache for $iacRelatedvirtualFilesAffected")
            val newIacCache = IacResult(newIacFileList.toImmutableList(), null)
            newIacCache.iacScanNeeded = true
            snykCachedResults.currentIacResult = newIacCache
            ApplicationManager.getApplication().invokeLater {
                getSnykToolWindowPanel(project)?.displayIacResults(newIacCache)
            }
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }

    private fun makeObsolete(iacIssuesForFile: IacIssuesForFile): IacIssuesForFile =
        iacIssuesForFile.copy(
            infrastructureAsCodeIssues = iacIssuesForFile.infrastructureAsCodeIssues
                .map { elem -> elem.copy(obsolete = true) }
        )

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
