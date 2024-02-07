package io.snyk.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.serviceContainer.AlreadyDisposedException
import io.snyk.plugin.services.SnykTaskQueueService.Companion.ls
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder

class SnykProjectManagerListener : ProjectManagerListener {

    override fun projectClosing(project: Project) {
        RunUtils.instance.runInBackground(
            project,
            "Project closing: " + project.name
        ) {
            // lets all running ProgressIndicators release MUTEX first
            RunUtils.instance.cancelRunningIndicators(project)
            AnalysisData.instance.removeProjectFromCaches(project)
            SnykCodeIgnoreInfoHolder.instance.removeProject(project)
            if (ls.isInitialized) {
                try {
                    ls.updateWorkspaceFolders(emptySet(), ls.getWorkspaceFolders(project))
                } catch (ignore: AlreadyDisposedException) {
                    // ignore
                }
            }
        }
    }
}
