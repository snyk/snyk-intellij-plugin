package io.snyk.plugin.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.snykcode.core.RunUtils
import io.snyk.plugin.snykcode.core.SCLogger
import io.snyk.plugin.snykcode.core.SnykCodeIgnoreInfoHolder

/**
 * Wrap work with SnykCode.
 */
@Service
class SnykCodeService(val project: Project) {

    fun scan() {
        SCLogger.instance.logInfo("Re-Analyse Project requested for: $project")
        SnykCodeIgnoreInfoHolder.instance.createDcIgnoreIfNeeded(project)
        RunUtils.instance.rescanInBackgroundCancellableDelayed(project, 0, false, false)
        getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)?.scanningStarted()
    }
}
