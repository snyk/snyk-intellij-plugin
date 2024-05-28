package io.snyk.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.snyk.plugin.analytics.Iteratively
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.getSnykApiService
import io.snyk.plugin.pluginSettings
import snyk.analytics.AnalysisIsReady
import snyk.analytics.AnalysisIsTriggered
import snyk.analytics.AuthenticateButtonIsClicked
import snyk.analytics.HealthScoreIsClicked
import snyk.analytics.IssueInTreeIsClicked
import snyk.analytics.PluginIsInstalled
import snyk.analytics.PluginIsUninstalled
import snyk.analytics.QuickFixIsDisplayed
import snyk.analytics.QuickFixIsTriggered
import snyk.analytics.WelcomeIsViewed
import snyk.common.SnykError
import snyk.common.isAnalyticsPermitted
import snyk.container.ContainerResult
import snyk.iac.IacResult
import snyk.oss.OssResult

@Service
class SnykAnalyticsService : Disposable {
    private val log = logger<SnykAnalyticsService>()
    private val itly = Iteratively
    private val settings
        get() = pluginSettings()

    private var userId = ""

    init {
        if (settings.usageAnalyticsEnabled) userId = obtainUserId(settings.token)
    }

    fun initAnalyticsReporter(project: Project) = project.messageBus.connect().subscribe(
        SnykScanListener.SNYK_SCAN_TOPIC,
        object : SnykScanListener {
            override fun scanningStarted() {
                // todo? move logAnalysisIsTriggered() calls here,
                //  require review of scan-not-started-and-cache-used logic around that event
            }

            override fun scanningOssFinished(ossResult: OssResult) {
                logAnalysisIsReady(
                    AnalysisIsReady.builder()
                        .analysisType(AnalysisIsReady.AnalysisType.SNYK_OPEN_SOURCE)
                        .ide(AnalysisIsReady.Ide.JETBRAINS)
                        .result(AnalysisIsReady.Result.SUCCESS)
                        .build()
                )
            }

            override fun scanningIacFinished(iacResult: IacResult) {
                logAnalysisIsReady(
                    AnalysisIsReady.builder()
                        .analysisType(AnalysisIsReady.AnalysisType.SNYK_INFRASTRUCTURE_AS_CODE)
                        .ide(AnalysisIsReady.Ide.JETBRAINS)
                        .result(AnalysisIsReady.Result.SUCCESS)
                        .build()
                )
            }

            override fun scanningContainerFinished(containerResult: ContainerResult) {
                logAnalysisIsReady(
                    AnalysisIsReady.builder()
                        .analysisType(AnalysisIsReady.AnalysisType.SNYK_CONTAINER)
                        .ide(AnalysisIsReady.Ide.JETBRAINS)
                        .result(AnalysisIsReady.Result.SUCCESS)
                        .build()
                )
            }

            override fun scanningOssError(snykError: SnykError) {
                logAnalysisIsReady(
                    AnalysisIsReady.builder()
                        .analysisType(AnalysisIsReady.AnalysisType.SNYK_OPEN_SOURCE)
                        .ide(AnalysisIsReady.Ide.JETBRAINS)
                        .result(AnalysisIsReady.Result.ERROR)
                        .build()
                )
            }

            override fun scanningIacError(snykError: SnykError) {
                logAnalysisIsReady(
                    AnalysisIsReady.builder()
                        .analysisType(AnalysisIsReady.AnalysisType.SNYK_INFRASTRUCTURE_AS_CODE)
                        .ide(AnalysisIsReady.Ide.JETBRAINS)
                        .result(AnalysisIsReady.Result.ERROR)
                        .build()
                )
            }

            override fun scanningContainerError(snykError: SnykError) {
                logAnalysisIsReady(
                    AnalysisIsReady.builder()
                        .analysisType(AnalysisIsReady.AnalysisType.SNYK_CONTAINER)
                        .ide(AnalysisIsReady.Ide.JETBRAINS)
                        .result(AnalysisIsReady.Result.ERROR)
                        .build()
                )
            }
        }
    )

    override fun dispose() {
        catchAll(log, "flush") {
            itly.flush()
        }
    }

    fun setUserId(userId: String) {
        this.userId = userId
    }

    fun obtainUserId(token: String?): String {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted()) {
            log.warn("Token is null or empty, or analytics disabled. User public id will not be obtained.")
            return ""
        }

        if (token.isNullOrBlank()) {
            log.warn("Token is null or empty, user public id will not be obtained.")
            return ""
        }
        val userId = getSnykApiService().userId
        if (userId == null) {
            log.warn("Not able to obtain user public id.")
            return ""
        }
        return userId
    }

    fun identify() {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted() || userId.isBlank()) {
            return
        }

        catchAll(log, "identify") {
            itly.identify(userId)
        }
    }

    fun logWelcomeIsViewed(event: WelcomeIsViewed) {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted()) return

        catchAll(log, "welcomeIsViewed") {
            itly.logWelcomeIsViewed(userId, event)
        }
    }

    fun logAnalysisIsTriggered(event: AnalysisIsTriggered) {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted() || userId.isBlank()) {
            return
        }

        catchAll(log, "analysisIsTriggered") {
            itly.logAnalysisIsTriggered(userId, event)
        }
    }

    private fun logAnalysisIsReady(event: AnalysisIsReady) {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted() || userId.isBlank()) {
            return
        }

        catchAll(log, "analysisIsReady") {
            itly.logAnalysisIsReady(userId, event)
        }
    }

    fun logIssueInTreeIsClicked(event: IssueInTreeIsClicked) {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted() || userId.isBlank()) {
            return
        }

        catchAll(log, "issueInTreeIsClicked") {
            itly.logIssueInTreeIsClicked(userId, event)
        }
    }

    fun logHealthScoreIsClicked(event: HealthScoreIsClicked) {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted() || userId.isBlank()) {
            return
        }

        catchAll(log, "healthScoreIsClicked") {
            itly.logHealthScoreIsClicked(userId, event)
        }
    }

    fun logPluginIsInstalled(event: PluginIsInstalled) {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted()) return

        catchAll(log, "pluginIsInstalled") {
            itly.logPluginIsInstalled(userId, event)
        }
    }

    fun logPluginIsUninstalled(event: PluginIsUninstalled) {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted()) return

        catchAll(log, "pluginIsUninstalled") {
            itly.logPluginIsUninstalled(userId, event)
        }
    }

    fun logAuthenticateButtonIsClicked(event: AuthenticateButtonIsClicked) {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted()) return

        catchAll(log, "authenticateButtonIsClicked") {
            itly.logAuthenticateButtonIsClicked(userId, event)
        }
    }

    fun logQuickFixIsDisplayed(event: QuickFixIsDisplayed) {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted()) return

        catchAll(log, "quickFixIsDisplayed") {
            itly.logQuickFixIsDisplayed(userId, event)
        }
    }

    fun logQuickFixIsTriggered(event: QuickFixIsTriggered) {
        if (!settings.usageAnalyticsEnabled || !isAnalyticsPermitted()) return

        catchAll(log, "quickFixIsTriggered") {
            itly.logQuickFixIsTriggered(userId, event)
        }
    }

    private inline fun catchAll(log: Logger, message: String, action: () -> Unit) {
        try {
            action()
        } catch (e: IllegalArgumentException) {
            log.debug("Iteratively validation error", e)
        } catch (t: Throwable) {
            log.warn("Failed to execute '$message' analytic event. ${t.message}", t)
        }
    }
}
