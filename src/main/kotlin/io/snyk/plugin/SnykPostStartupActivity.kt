package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder
import io.snyk.plugin.ui.SnykBalloonNotifications
import java.io.File

class SnykPostStartupActivity : StartupActivity.DumbAware {

    private var listenersActivated = false

    override fun runActivity(project: Project) {
        // clean up left-overs in case project wasn't properly closed before
        AnalysisData.instance.resetCachesAndTasks(project)
        SnykCodeIgnoreInfoHolder.instance.removeProject(project)

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
    }

}
