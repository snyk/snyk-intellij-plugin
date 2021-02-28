package io.snyk.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.RunUtils

class SnykProjectManagerListener : ProjectManagerListener {

    override fun projectClosing(project: Project) {
        RunUtils.instance.runInBackground(
            project,
            "Project closing: " + project.name
        ) {
            // lets all running ProgressIndicators release MUTEX first
            RunUtils.instance.cancelRunningIndicators(project)
            AnalysisData.instance.removeProjectFromCaches(project)
        }
    }
}
