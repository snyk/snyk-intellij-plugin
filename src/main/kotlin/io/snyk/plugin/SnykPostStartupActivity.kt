package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import io.snyk.plugin.services.SnykTaskQueueService

class SnykPostStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return
        }

        project.service<SnykTaskQueueService>().downloadLatestRelease()
    }
}
