package io.snyk.plugin

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import snyk.common.lsp.LanguageServerWrapper

private const val TIMEOUT = 1L

class SnykProjectManagerListener : ProjectManagerListener {
  val threadPool: ExecutorService = Executors.newWorkStealingPool()

  override fun projectClosing(project: Project) {
    val closingTask =
      object : Backgroundable(project, "Snyk: Project closing ${project.name}") {
        override fun run(indicator: ProgressIndicator) {
          // limit clean up to TIMEOUT
          try {
            threadPool
              .submit {
                val ls = LanguageServerWrapper.getInstance(project)
                if (ls.isInitialized) {
                  ls.updateWorkspaceFolders(emptySet(), ls.getWorkspaceFoldersFromRoots(project))
                }
              }
              .get(TIMEOUT, TimeUnit.SECONDS)
          } catch (ignored: Exception) {
            val logger = logger<SnykProjectManagerListener>()
            logger.warn("Project closing clean up took longer than $TIMEOUT seconds")
            logger.debug(ignored)
          }
        }
      }

    ProgressManager.getInstance().run(closingTask)
  }
}
