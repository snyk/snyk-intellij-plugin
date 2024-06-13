package io.snyk.plugin

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import snyk.common.lsp.LanguageServerWrapper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TIMEOUT = 1L

class SnykProjectManagerListener : ProjectManagerListener {
    val threadPool: ExecutorService = Executors.newWorkStealingPool()

    override fun projectClosing(project: Project) {
        val closingTask = object : Backgroundable(project, "Project closing ${project.name}") {
            override fun run(indicator: ProgressIndicator) {
                // limit clean up to TIMEOUT
                try {
                    threadPool.submit {
                        // lets all running ProgressIndicators release MUTEX first
                        val ls = LanguageServerWrapper.getInstance()
                        if (ls.isInitialized) {
                            ls.updateWorkspaceFolders(emptySet(), ls.getWorkspaceFolders(project))
                        }
                    }.get(TIMEOUT, TimeUnit.SECONDS)
                } catch (ignored: RuntimeException) {
                    logger<SnykProjectManagerListener>().info("Project closing clean up took too long", ignored)
                }
            }
        }

        ProgressManager.getInstance().run(closingTask)
    }
}
