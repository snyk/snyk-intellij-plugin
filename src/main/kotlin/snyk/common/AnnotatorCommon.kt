package snyk.common

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import io.snyk.plugin.Severity
import io.snyk.plugin.events.SnykProductsOrSeverityListener
import io.snyk.plugin.events.SnykSettingsListener
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
        project.messageBus.connect()
            .subscribe(
                SnykProductsOrSeverityListener.SNYK_ENABLEMENT_TOPIC, object : SnykProductsOrSeverityListener {
                override fun enablementChanged() {
                    refreshAnnotationsForOpenFiles(project)
                }
            })
        project.messageBus.connect()
            .subscribe(SnykSettingsListener.SNYK_SETTINGS_TOPIC, object : SnykSettingsListener {
                override fun settingsChanged() {
                    refreshAnnotationsForOpenFiles(project)
                }
            })
    }
}
