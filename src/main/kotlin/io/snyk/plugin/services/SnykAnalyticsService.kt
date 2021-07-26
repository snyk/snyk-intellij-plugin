package io.snyk.plugin.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.analytics.SegmentPlugin
import ly.iterative.itly.Environment
import ly.iterative.itly.IterativelyOptions
import ly.iterative.itly.Options
import ly.iterative.itly.ValidationOptions
import snyk.analytics.AnalysisIsReady
import snyk.analytics.AnalysisIsTriggered
import snyk.analytics.DestinationsOptions
import snyk.analytics.Identify
import snyk.analytics.IssueIsViewed
import snyk.analytics.Itly
import snyk.analytics.ItlyLogger
import snyk.analytics.ProductSelectionIsViewed
import snyk.analytics.WelcomeIsViewed
import java.util.Properties

@Service
class SnykAnalyticsService : Disposable {
    private val log = logger<SnykAnalyticsService>()

    private var itlyLoaded = false
    private var anonymousId = ""
    private var userId = ""

    init {
        val settings = service<SnykApplicationSettingsStateService>()
        anonymousId = settings.userAnonymousId
        userId = obtainUserId(settings.token)

        val segmentWriteKey = loadSegmentWriteKey()
        if (segmentWriteKey.isBlank()) {
            itlyLoaded = false
            log.debug("Segment analytics write key is empty. No analytics will be collected.")
        } else {
            val environment = loadIterativelyEnvironment()
            log.debug("Initializing Iteratively integration for $environment...")

            Itly.getInstance().load(
                DestinationsOptions.builder()
                    .iteratively(
                        IterativelyOptions.builder().build()
                    )
                    .build(),
                Options.builder()
                    .environment(environment)
                    .validation(
                        ValidationOptions.builder()
                            .errorOnInvalid(true)
                            .trackInvalid(false)
                            .build()
                    )
                    .logger(ItlyLogger(log))
                    .plugins(listOf(SegmentPlugin(segmentWriteKey, anonymousId)))
                    .build()
            )
            itlyLoaded = true
        }
    }

    override fun dispose() {
        if (!itlyLoaded) {
            return
        }
        catchAll(log, "flush-and-shutdown") {
            Itly.getInstance().flush()
            Itly.getInstance().shutdown()
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
        if (!canReportEvents() || userId.isBlank()) {
            return
        }

        catchAll(log, "identify") {
            Itly.getInstance().identify(userId, Identify.builder().build())
            Itly.getInstance().alias(userId, anonymousId)
        }
    }

    fun logWelcomeIsViewed(event: WelcomeIsViewed) {
        if (!canReportEvents()) {
            return
        }

        catchAll(log, "welcomeIsViewed") {
            Itly.getInstance().welcomeIsViewed(userId, event)
        }
    }

    fun logProductSelectionIsViewed(event: ProductSelectionIsViewed) {
        if (!canReportEvents() || userId.isBlank()) {
            return
        }

        catchAll(log, "productSelectionIsViewed") {
            Itly.getInstance().productSelectionIsViewed(userId, event)
        }
    }

    fun logAnalysisIsTriggered(event: AnalysisIsTriggered) {
        if (!canReportEvents() || userId.isBlank()) {
            return
        }

        catchAll(log, "analysisIsTriggered") {
            Itly.getInstance().analysisIsTriggered(userId, event)
        }
    }

    fun logAnalysisIsReady(event: AnalysisIsReady) {
        if (!canReportEvents() || userId.isBlank()) {
            return
        }

        catchAll(log, "analysisIsReady") {
            Itly.getInstance().analysisIsReady(userId, event)
        }
    }

    fun logIssueIsViewed(event: IssueIsViewed) {
        if (!canReportEvents() || userId.isBlank()) {
            return
        }

        catchAll(log, "issueIsViewed") {
            Itly.getInstance().issueIsViewed(userId, event)
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

    private fun loadIterativelyEnvironment(): Environment {
        return try {
            val prop = Properties()
            prop.load(javaClass.classLoader.getResourceAsStream("application.properties"))
            val environment = prop.getProperty("iteratively.analytics.environment") ?: "DEVELOPMENT"

            if (environment.isEmpty()) {
                log.warn("Iteratively environment is empty. Use DEVELOPMENT as default")
                Environment.DEVELOPMENT
            } else {
                Environment.valueOf(environment)
            }
        } catch (t: Throwable) {
            log.warn("Could not load Iteratively environment: use DEVELOPMENT as default.", t)
            Environment.DEVELOPMENT
        }
    }

    private fun loadSegmentWriteKey(): String {
        return try {
            val prop = Properties()
            prop.load(javaClass.classLoader.getResourceAsStream("application.properties"))
            prop.getProperty("segment.analytics.write-key") ?: ""
        } catch (t: Throwable) {
            log.warn("Could not load Segment write key.", t)
            ""
        }
    }

    private fun canReportEvents(): Boolean {
        if (!itlyLoaded) {
            log.debug("Cannot report events because Iteratively not loaded.")
            return false
        }

        val settings = service<SnykApplicationSettingsStateService>()
        return settings.usageAnalyticsEnabled
    }
}
