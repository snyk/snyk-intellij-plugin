package io.snyk.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.BackgroundTaskQueue
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.getCli
import io.snyk.plugin.ui.SnykToolWindowPanel

@Service
class SnykTaskQueueService(val project: Project) {
    private val taskQueue = BackgroundTaskQueue(project, "Snyk")

    private var currentProgressIndicator: ProgressIndicator? = null

    fun getCurrentProgressIndicator(): ProgressIndicator? = currentProgressIndicator

    fun getTaskQueue() = taskQueue

    fun scan() {
        taskQueue.run(object : Task.Backgroundable(project, "Snyk scanning", true) {
            override fun run(indicator: ProgressIndicator) {
                currentProgressIndicator = indicator

                indicator.checkCanceled()

                project.service<SnykToolWindowPanel>().clean()

                indicator.checkCanceled()

                val cliResult: CliResult = getCli(project).scan()

                indicator.checkCanceled()

                project.service<SnykToolWindowPanel>().displayVulnerabilities(cliResult.toCliGroupedResult())

                currentProgressIndicator = null
            }
        })
    }

    fun downloadLatestRelease() {
        taskQueue.run(object : Task.Backgroundable(project, "Check Snyk CLI", true) {
            override fun run(indicator: ProgressIndicator) {
                currentProgressIndicator = indicator

                val cliDownloader = project.service<SnykCliDownloaderService>()

                indicator.checkCanceled()

                if (!getCli(project).isCliInstalled()) {
                    cliDownloader.downloadLatestRelease(indicator)
                } else {
                    cliDownloader.cliSilentAutoUpdate(indicator)
                }

                currentProgressIndicator = null
            }
        })
    }
}
