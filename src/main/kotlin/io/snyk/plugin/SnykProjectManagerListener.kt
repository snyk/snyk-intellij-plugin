package io.snyk.plugin

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder
import snyk.common.lsp.LanguageServerWrapper
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TIMEOUT = 5L

class SnykProjectManagerListener : ProjectManagerListener {
    override fun projectClosing(project: Project) {
        RunUtils.instance.runInBackground(
            project,
            "Project closing: " + project.name
        ) {
            // limit clean up to 5s
            try {
                Executors.newSingleThreadExecutor().submit {
                    // lets all running ProgressIndicators release MUTEX first
                    RunUtils.instance.cancelRunningIndicators(project)
                    AnalysisData.instance.removeProjectFromCaches(project)
                    SnykCodeIgnoreInfoHolder.instance.removeProject(project)
                    val ls = LanguageServerWrapper.getInstance()
                    if (ls.ensureLanguageServerInitialized()) {
                        ls.updateWorkspaceFolders(emptySet(), ls.getWorkspaceFolders(project))
                    }
                }.get(TIMEOUT, TimeUnit.SECONDS)
            } catch (ignored: RuntimeException) {
                logger<SnykProjectManagerListener>().info("Project closing clean up took too long", ignored)
            }
        }
    }
}
