package snyk.common

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.refreshAnnotationsForOpenFiles

object AnnotatorCommon {
    val logger = logger<AnnotatorCommon>()

    fun prepareAnnotate(psiFile: PsiFile?) {
        logger.debug("Preparing annotation for $psiFile")
        // todo: review later if any way to provide up-to-date context for CLI scans is available
        // force saving here will break some user's workflow: https://github.com/snyk/snyk-intellij-plugin/issues/324
    }

    fun isSeverityToShow(severity: Severity): Boolean =
        pluginSettings().hasSeverityEnabled(severity) || severity == Severity.UNKNOWN

    fun initRefreshing(project: Project) {
        logger.debug("Initializing annotations refresh listener")
        // todo: do we need to refresh annotations when Tree filters changing?
        project.messageBus.connect()
            .subscribe(SnykResultsFilteringListener.SNYK_FILTERING_TOPIC, object : SnykResultsFilteringListener {
                override fun filtersChanged() {
                    refreshAnnotationsForOpenFiles(project)
                }
            })
    }
}
