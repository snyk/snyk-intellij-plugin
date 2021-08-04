package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager
import io.snyk.plugin.services.SnykApiService
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder
import io.snyk.plugin.ui.SnykBalloonNotifications
import snyk.amplitude.AmplitudeExperimentService
import snyk.amplitude.api.ExperimentUser
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

private val LOG = logger<SnykPostStartupActivity>()

class SnykPostStartupActivity : StartupActivity.DumbAware {

    private var listenersActivated = false

    override fun runActivity(project: Project) {
        // clean up left-overs in case project wasn't properly closed before
        AnalysisData.instance.resetCachesAndTasks(project)
        SnykCodeIgnoreInfoHolder.instance.removeProject(project)
        val settings = getApplicationSettingsStateService()

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
            settings.lastTimeFeedbackRequestShown.toInstant().plus(14, ChronoUnit.DAYS).isBefore(Instant.now())
        if (settings.showFeedbackRequest && feedbackRequestShownMoreThenTwoWeeksAgo) {
            SnykBalloonNotifications.showFeedbackRequest(project)
            settings.lastTimeFeedbackRequestShown = Date.from(Instant.now())
        }

        val userToken = settings.token ?: ""
        if (userToken.isNotBlank()) {
            LOG.info("Loading variants for all amplitude experiments")
            val publicUserId = service<SnykApiService>().userId ?: ""
            val experimentUser = ExperimentUser(publicUserId)
            service<AmplitudeExperimentService>().fetch(experimentUser)

            if (service<AmplitudeExperimentService>().isShowScanningReminderEnabled()) {
                SnykBalloonNotifications.showScanningReminder(project)
            }
        }
    }
}
