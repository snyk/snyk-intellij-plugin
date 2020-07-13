package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import io.snyk.plugin.client.CliDownloader
import io.snyk.plugin.ui.state.SnykPluginState

class SnykPostStartupActivity extends StartupActivity {

  override def runActivity(project: Project): Unit = {
    ApplicationManager.getApplication.invokeLater(() => {
      val pluginState: SnykPluginState = SnykPluginState.newInstance(project)

      val cliDownloader = CliDownloader(pluginState)

      if (!pluginState.isCliInstalled) {
        cliDownloader.downloadLatestRelease()
      } else {
        cliDownloader.cliSilentAutoUpdate()
      }
    })
  }
}
