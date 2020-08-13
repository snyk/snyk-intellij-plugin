package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import io.snyk.plugin.cli.CliDownloaderService

class SnykPostStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            val cliDownloader = project.service<CliDownloaderService>()

            if (!getCli(project).isCliInstalled()) {
                cliDownloader.downloadLatestRelease()
            } else {
                cliDownloader.cliSilentAutoUpdate()
            }
        }
    }
}
