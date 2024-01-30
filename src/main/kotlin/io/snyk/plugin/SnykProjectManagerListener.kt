package io.snyk.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.serviceContainer.AlreadyDisposedException
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder
import org.eclipse.lsp4j.WorkspaceFolder

class SnykProjectManagerListener : ProjectManagerListener {

    override fun projectClosing(project: Project) {
        RunUtils.instance.runInBackground(
            project, "Project closing: " + project.name
        ) {
            // lets all running ProgressIndicators release MUTEX first
            RunUtils.instance.cancelRunningIndicators(project)
            AnalysisData.instance.removeProjectFromCaches(project)
            SnykCodeIgnoreInfoHolder.instance.removeProject(project)
        }

        // doesn't need to run in the background, as the called update is already running in a background thread
        if (SnykTaskQueueService.ls.isInitialized) {
            val removedWorkspaceFolders = ProjectRootManager.getInstance(project).contentRoots
                .mapNotNull { WorkspaceFolder(it.url, it.name) }
                .toCollection(mutableListOf())
            try {
                SnykTaskQueueService.ls.updateWorkspaceFolders(project, emptyList(), removedWorkspaceFolders)
            } catch (ignore: AlreadyDisposedException) {
                // ignore
            }
        }
    }
}
