package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import io.snyk.plugin.services.CliDownloaderService

class SnykPostStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        ProgressManager.getInstance().run(object: Task.Backgroundable(project, "Check Snyk CLI", true) {
            override fun run(indicator: ProgressIndicator) {
                val cliDownloader = project.service<CliDownloaderService>()

                if (!getCli(project).isCliInstalled()) {
                    cliDownloader.downloadLatestRelease()
                } else {
                    cliDownloader.cliSilentAutoUpdate()
                }
            }
        })
    }
}
