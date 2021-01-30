package io.snyk.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
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
        if (LoginUtils.instance.isLogged(project, true)) {
            RunUtils.instance.rescanInBackgroundCancellableDelayed(project, 0, false, false)
        }
    }
}
