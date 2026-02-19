package snyk

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.SnykBalloonNotifications

/**
 * Shows a welcome notification once when Snyk plugin is installed and
 * [SnykApplicationSettingsStateService.pluginFirstRun] property is `true`.
 */
class WelcomeNotifyActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val settings = pluginSettings()

    if (settings.pluginFirstRun) {
      SnykBalloonNotifications.showWelcomeNotification(project)
    }
  }
}
