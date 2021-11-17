package io.snyk.plugin

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.ide.plugins.PluginStateListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager
import io.snyk.plugin.services.SnykAnalyticsService
import io.snyk.plugin.services.SnykApiService
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder
import io.snyk.plugin.ui.SnykBalloonNotifications
import snyk.PLUGIN_ID
import snyk.amplitude.AmplitudeExperimentService
import snyk.amplitude.api.ExperimentUser
import snyk.analytics.PluginIsInstalled
import snyk.analytics.PluginIsUninstalled
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

private val LOG = logger<SnykPostStartupActivity>()

class SnykPostStartupActivity : StartupActivity.DumbAware {

    private var listenersActivated = false
    val settings = pluginSettings()

    override fun runActivity(project: Project) {
        PluginInstaller.addStateListener(UninstallListener())

        // clean up left-overs in case project wasn't properly closed before
        AnalysisData.instance.resetCachesAndTasks(project)
        SnykCodeIgnoreInfoHolder.instance.removeProject(project)

        if (!settings.pluginInstalled) {
            settings.pluginInstalled = true
            service<SnykAnalyticsService>().logPluginIsInstalled(
                PluginIsInstalled.builder()
                    .ide(PluginIsInstalled.Ide.JETBRAINS)
                    .build()
            )
        }

        if (!listenersActivated) {
            val messageBusConnection = ApplicationManager.getApplication().messageBus.connect()
            messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, SnykBulkFileListener())
            messageBusConnection.subscribe(ProjectManager.TOPIC, SnykProjectManagerListener())
            listenersActivated = true
        }

        SnykCodeIgnoreInfoHolder.instance.createDcIgnoreIfNeeded(project)

        if (!ApplicationManager.getApplication().isUnitTestMode) {
            project.service<SnykTaskQueueService>().downloadLatestRelease()
        }

        val feedbackRequestShownMoreThenTwoWeeksAgo =
            settings.lastTimeFeedbackRequestShown.toInstant()
                .plus(14, ChronoUnit.DAYS) // we'll give 2 weeks to evaluate initially
                .isBefore(Instant.now())
        if (settings.showFeedbackRequest && feedbackRequestShownMoreThenTwoWeeksAgo) {
            SnykBalloonNotifications.showFeedbackRequest(project)
            settings.lastTimeFeedbackRequestShown = Date.from(Instant.now())
        }

        val userToken = settings.token ?: ""
        val publicUserId = if (userToken.isNotBlank()) {
            if (!settings.scanningReminderWasShown) {
                SnykBalloonNotifications.showScanningReminder(project)
                settings.scanningReminderWasShown = true
            }
            service<SnykApiService>().userId ?: ""
        } else ""

        LOG.info("Loading variants for all amplitude experiments")
        val experimentUser = ExperimentUser(publicUserId)
        service<AmplitudeExperimentService>().fetch(experimentUser)
    }
}

private class UninstallListener : PluginStateListener {
    @Suppress("EmptyFunctionBlock")
    override fun install(descriptor: IdeaPluginDescriptor) {
    }

    override fun uninstall(descriptor: IdeaPluginDescriptor) {
        if (descriptor.pluginId.idString != PLUGIN_ID) return

        // reset pluginInstalled flag to track PluginIsInstalled event next time
        pluginSettings().pluginInstalled = false

        service<SnykAnalyticsService>().logPluginIsUninstalled(
            PluginIsUninstalled.builder()
                .ide(PluginIsUninstalled.Ide.JETBRAINS)
                .build()
        )
    }
}
