package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VirtualFileManager
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.core.AnalysisData

class SnykPostStartupActivity : StartupActivity.DumbAware {

    private var listenersActivated = false

    override fun runActivity(project: Project) {
        AnalysisData.instance.resetCachesAndTasks(project)

        if (!listenersActivated) {
            val messageBusConnection = ApplicationManager.getApplication().messageBus.connect()
            messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, SnykBulkFileListener())
            messageBusConnection.subscribe(ProjectManager.TOPIC, SnykProjectManagerListener())
            listenersActivated = true
        }

        if (!ApplicationManager.getApplication().isUnitTestMode) {
            project.service<SnykTaskQueueService>().downloadLatestRelease()
        }
    }

}
