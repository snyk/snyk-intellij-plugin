package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.snykcode.core.AnalysisData

class SnykPostStartupActivity : StartupActivity {

    override fun runActivity(project: Project) {
        AnalysisData.instance.resetCachesAndTasks(project)

        if (!ApplicationManager.getApplication().isUnitTestMode) {
            project.service<SnykTaskQueueService>().downloadLatestRelease()
        }
    }
}
