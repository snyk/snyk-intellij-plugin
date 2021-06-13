package io.snyk.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.analytics.EventProperties
import io.snyk.plugin.analytics.Segment
import io.snyk.plugin.net.SnykApiClient

@Service
class SnykAnalyticsService : Disposable {
    private val log = logger<SnykAnalyticsService>()
    private val segment = Segment

    init {
        val settings = service<SnykApplicationSettingsStateService>()
        segment.setAnalyticsCollectionEnabled(settings.usageAnalyticsEnabled)
        val userId = obtainUserId(settings.token)
        segment.setUserId(userId)
    }

    override fun dispose() {
        segment.shutdown()
    }

    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        log.info("Analytics collection is activated: '$enabled'")

        segment.setAnalyticsCollectionEnabled(enabled)
    }

    fun setUserId(userId: String) {
        segment.setUserId(userId)
    }

    fun obtainUserId(token: String?): String {
        if (token.isNullOrBlank()) {
            log.warn("Token is null or empty, user public id will not be obtained.")
            return ""
        }
        val userId = service<SnykApiService>().userId
        if (userId == null) {
            log.warn("Not able to obtain User public id.")
            return ""
        }
        return userId
    }

    fun identify() {
        catchAll(log, "identify") { segment.identify() }
    }

    fun alias(userId: String) {
        catchAll(log, "alias") { segment.alias(userId) }
    }

    fun logEvent(event: String, properties: EventProperties = EventProperties(emptyMap())) {
        catchAll(log, "track") { segment.logEvent(event, properties) }
    }

    private inline fun catchAll(log: Logger, message: String, action: () -> Unit) {
        try {
            action()
        } catch (t: Throwable) {
            log.warn("Failed to execute '$message' analytic event. ${t.message}", t)
        }
    }
}
