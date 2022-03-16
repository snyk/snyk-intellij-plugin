package snyk.iac

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil.pathsEqual
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import io.snyk.plugin.SnykBulkFileListener
import io.snyk.plugin.getSnykToolWindowPanel
import okhttp3.internal.toImmutableList

class IacBulkFileListener : SnykBulkFileListener() {

    private val log = logger<IacBulkFileListener>()

    override fun before(project: Project, events: List<VFileEvent>) {
        val virtualFilesDeletedOrMovedOrRenamed = getAffectedVirtualFiles(
            events,
            classesOfEventsToFilter = listOf(
                VFileDeleteEvent::class.java,
                VFileMoveEvent::class.java,
                VFilePropertyChangeEvent::class.java
            ),
            eventsFilter = { (it as? VFilePropertyChangeEvent)?.isRename != false }
        )
        // clean IaC cached results for deleted/moved/renamed files
        updateIacCache(
            virtualFilesDeletedOrMovedOrRenamed,
            project
        )
    }

    override fun after(project: Project, events: List<VFileEvent>) {
        val virtualFilesAffected = getAffectedVirtualFiles(
            events,
            eventToVirtualFileTransformer = { transformEventToNewVirtualFile(it) },
            classesOfEventsToFilter = listOf(
                VFileCreateEvent::class.java,
                VFileContentChangeEvent::class.java,
                VFileMoveEvent::class.java,
                VFileCopyEvent::class.java,
                VFilePropertyChangeEvent::class.java
            ),
            eventsFilter = { (it as? VFilePropertyChangeEvent)?.isRename != false }
        )
        // update IaC cached results if needed
        updateIacCache(virtualFilesAffected, project)
    }

    private fun updateIacCache(
        virtualFilesAffected: Set<VirtualFile>,
        project: Project
    ) {
        if (virtualFilesAffected.isEmpty()) return
        val toolWindowPanel = getSnykToolWindowPanel(project)
        val iacFiles = toolWindowPanel?.currentIacResult?.allCliIssues ?: return

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
            toolWindowPanel.currentIacResult = newIacCache
            ApplicationManager.getApplication().invokeLater {
                toolWindowPanel.displayIacResults(newIacCache)
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
