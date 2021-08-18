package snyk

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import io.snyk.plugin.getApplicationSettingsStateService
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.SnykBalloonNotifications

/**
 * Shows a welcome notification once when Snyk plugin is installed and
 * [SnykApplicationSettingsStateService.pluginFirstRun] property is `true`.
 */
class WelcomeNotifyActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val settings = getApplicationSettingsStateService()

        if (settings.pluginFirstRun) {
            SnykBalloonNotifications.showWelcomeNotification(project)
        }
    }
}
