package io.snyk.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.analytics.Iteratively
import io.snyk.plugin.pluginSettings
import snyk.analytics.AnalysisIsReady
import snyk.analytics.AnalysisIsTriggered
import snyk.analytics.AuthenticateButtonIsClicked
import snyk.analytics.HealthScoreIsClicked
import snyk.analytics.IssueInTreeIsClicked
import snyk.analytics.PluginIsInstalled
import snyk.analytics.PluginIsUninstalled
import snyk.analytics.ProductSelectionIsViewed
import snyk.analytics.QuickFixIsDisplayed
import snyk.analytics.QuickFixIsTriggered
import snyk.analytics.WelcomeIsViewed

@Service
class SnykAnalyticsService : Disposable {
    private val log = logger<SnykAnalyticsService>()
    private val itly = Iteratively
    private val settings
        get() = pluginSettings()

    private var userId = ""

    init {
        userId = obtainUserId(settings.token)
    }

    override fun dispose() {
        catchAll(log, "flush") {
            itly.flush()
        }
    }

    fun setUserId(userId: String) {
        this.userId = userId
    }

    fun obtainUserId(token: String?): String {
        if (token.isNullOrBlank()) {
            log.warn("Token is null or empty, user public id will not be obtained.")
            return ""
        }
        val userId = service<SnykApiService>().userId
        if (userId == null) {
            log.warn("Not able to obtain user public id.")
            return ""
        }
        return userId
    }

    fun identify() {
        if (!settings.usageAnalyticsEnabled || userId.isBlank()) {
            return
        }

        catchAll(log, "identify") {
            itly.identify(userId)
        }
    }

    fun logWelcomeIsViewed(event: WelcomeIsViewed) {
        if (!settings.usageAnalyticsEnabled) return

        catchAll(log, "welcomeIsViewed") {
            itly.logWelcomeIsViewed(userId, event)
        }
    }

    fun logProductSelectionIsViewed(event: ProductSelectionIsViewed) {
        if (!settings.usageAnalyticsEnabled || userId.isBlank()) {
            return
        }

        catchAll(log, "productSelectionIsViewed") {
            itly.logProductSelectionIsViewed(userId, event)
        }
    }

    fun logAnalysisIsTriggered(event: AnalysisIsTriggered) {
        if (!settings.usageAnalyticsEnabled || userId.isBlank()) {
            return
        }

        catchAll(log, "analysisIsTriggered") {
            itly.logAnalysisIsTriggered(userId, event)
        }
    }

    fun logAnalysisIsReady(event: AnalysisIsReady) {
        if (!settings.usageAnalyticsEnabled || userId.isBlank()) {
            return
        }

        catchAll(log, "analysisIsReady") {
            itly.logAnalysisIsReady(userId, event)
        }
    }

    fun logIssueInTreeIsClicked(event: IssueInTreeIsClicked) {
        if (!settings.usageAnalyticsEnabled || userId.isBlank()) {
            return
        }

        catchAll(log, "issueInTreeIsClicked") {
            itly.logIssueInTreeIsClicked(userId, event)
        }
    }

    fun logHealthScoreIsClicked(event: HealthScoreIsClicked) {
        if (!settings.usageAnalyticsEnabled || userId.isBlank()) {
            return
        }

        catchAll(log, "healthScoreIsClicked") {
            itly.logHealthScoreIsClicked(userId, event)
        }
    }

    fun logPluginIsInstalled(event: PluginIsInstalled) {
        if (!settings.usageAnalyticsEnabled) return

        catchAll(log, "pluginIsInstalled") {
            itly.logPluginIsInstalled(userId, event)
        }
    }

    fun logPluginIsUninstalled(event: PluginIsUninstalled) {
        if (!settings.usageAnalyticsEnabled) return

        catchAll(log, "pluginIsUninstalled") {
            itly.logPluginIsUninstalled(userId, event)
        }
    }

    fun logAuthenticateButtonIsClicked(event: AuthenticateButtonIsClicked) {
        if (!settings.usageAnalyticsEnabled) return

        catchAll(log, "authenticateButtonIsClicked") {
            itly.logAuthenticateButtonIsClicked(userId, event)
        }
    }

    fun logQuickFixIsDisplayed(event: QuickFixIsDisplayed) {
        if (!settings.usageAnalyticsEnabled) return

        catchAll(log, "quickFixIsDisplayed") {
            itly.logQuickFixIsDisplayed(userId, event)
        }
    }

    fun logQuickFixIsTriggered(event: QuickFixIsTriggered) {
        if (!settings.usageAnalyticsEnabled) return

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
