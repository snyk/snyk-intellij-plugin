package io.snyk.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.analytics.EventProperties
import io.snyk.plugin.analytics.Segment
import io.snyk.plugin.analytics.internal.SnykApiClient

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

        catchAll(log, "obtainUserPublicId") {
            var endpoint = service<SnykApplicationSettingsStateService>().customEndpointUrl
            if (endpoint.isNullOrEmpty()) {
                endpoint = "https://snyk.io/api/"
            }
            val response = SnykApiClient.create(token, endpoint).userService().userMe().execute()
            if (response.isSuccessful) {
                return response.body()!!.id
            } else {
                log.warn("Failed to obtain user public id: ${response.errorBody()?.string()}")
            }
        }
        return ""
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
            log.error("Failed to execute '$message' analytic event. ${t.message}", t)
        }
    }
}
