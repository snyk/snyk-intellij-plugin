package io.snyk.plugin.analytics

import com.intellij.openapi.diagnostic.logger
import com.segment.analytics.Analytics
import com.segment.analytics.messages.AliasMessage
import com.segment.analytics.messages.IdentifyMessage
import com.segment.analytics.messages.TrackMessage
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Segment singleton that provides methods for logging events and setting user properties.
 */
object Segment {
    object Event {
        const val SNYK_CODE_QUALITY_ISSUES_ANALYSIS_READY = "Snyk Code Quality Issues Analysis Ready"
        const val SNYK_CODE_SECURITY_VULNERABILITY_ANALYSIS_READY = "Snyk Code Security Vulnerability Analysis Ready"
        const val SNYK_OPEN_SOURCE_ANALYSIS_READY = "Snyk Open Source Analysis Ready"
        const val USER_LANDED_ON_PRODUCT_SELECTION_PAGE = "User Landed On The Product Selection Page"
        const val USER_LANDED_ON_THE_WELCOME_PAGE = "User Landed On The Welcome Page"
        const val USER_SEES_AN_ISSUE = "User Sees An Issue"
        const val USER_TRIGGERS_AN_ANALYSIS = "User Triggers An Analysis"
        const val USER_TRIGGERS_ITS_FIRST_ANALYSIS = "User Triggers Its First Analysis"
    }

    private val log = logger<Segment>()

    private var analyticsCollectionEnabled = true
    private var analytics: Analytics? = null
    private var anonymousId = UUID.randomUUID().toString()
    private var userId = ""

    init {
        try {
            val prop = Properties()
            prop.load(javaClass.classLoader.getResourceAsStream("/application.properties"))
            val writeKey = prop.getProperty("segment.analytics.write-key") ?: ""

            if (writeKey.isEmpty()) {
                analyticsCollectionEnabled = false
                log.warn("Segment analytics collection is disabled because write key is empty!")
            } else {
                analytics = Analytics.builder(writeKey)
                    .flushQueueSize(5)
                    .flushInterval(10, TimeUnit.SECONDS)
                    .build()
            }
        } catch (t: Throwable) {
            analyticsCollectionEnabled = false
            log.warn("Failed to create Segment analytics. Event collection is disabled.", t)
        }
    }

    fun shutdown() {
        log.info("Flush events in the message queue and stop this segment instance.")

        analytics?.flush()
        analytics?.shutdown()
    }

    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        analyticsCollectionEnabled = enabled
    }

    fun setUserId(userId: String) {
        this.userId = userId
    }

    fun identify() {
        if (!analyticsCollectionEnabled) return

        analytics?.enqueue(IdentifyMessage.builder().userId(anonymousId))
    }

    fun alias(userId: String) {
        if (!analyticsCollectionEnabled) return
        if (userId.isBlank()) {
            log.warn("Alias event cannot be executed because userId is empty")
            return
        }

        setUserId(userId)
        analytics?.enqueue(AliasMessage.builder(anonymousId).userId(userId))
    }

    fun logEvent(event: String, properties: EventProperties) {
        if (!analyticsCollectionEnabled) return

        val message = TrackMessage.builder(event).properties(properties.map.toMap())
        if (userId.isBlank()) {
            message.anonymousId(anonymousId)
        } else {
            message.userId(userId)
        }
        analytics?.enqueue(message)
    }
}
