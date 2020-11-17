package io.snyk.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.snykcode.core.AnalysisData
import io.snyk.plugin.snykcode.core.LoginUtils
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.snykcode.core.SCLogger

/**
 * Wrap work with SnykCode.
 */
@Service
class SnykCodeService(val project: Project) {

    fun scan() {
        SCLogger.instance.logInfo("Re-Analyse Project requested for: $project")
        // fixme: ?? background task here to avoid potential freeze due to MUTEX lock
        AnalysisData.instance.resetCachesAndTasks(project)
        if (LoginUtils.instance.isLogged(project, true)) {
            RunUtils.instance.asyncAnalyseProjectAndUpdatePanel(project)
        }
    }
}
