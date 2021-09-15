package io.snyk.plugin.analytics

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import ly.iterative.itly.Environment
import ly.iterative.itly.IterativelyOptions
import ly.iterative.itly.Options
import ly.iterative.itly.ValidationOptions
import snyk.analytics.AnalysisIsReady
import snyk.analytics.AnalysisIsTriggered
import snyk.analytics.DestinationsOptions
import snyk.analytics.HealthScoreIsClicked
import snyk.analytics.Identify
import snyk.analytics.IssueIsViewed
import snyk.analytics.Itly
import snyk.analytics.ItlyLogger
import snyk.analytics.ProductSelectionIsViewed
import snyk.analytics.WelcomeIsViewed
import java.util.Properties

object Iteratively {
    private val LOG = logger<Iteratively>()

    private var itlyLoaded = false

    // TODO(pavel): anonymousId will be moved into SnykAnalyticsService after using official SegmentPlugin
    private var anonymousId = ""

    init {
        val settings = service<SnykApplicationSettingsStateService>()
        anonymousId = settings.userAnonymousId

        val segmentWriteKey = loadSegmentWriteKey()
        if (segmentWriteKey.isBlank()) {
            LOG.debug("Segment analytics write key is empty. No analytics will be collected.")
            itlyLoaded = false
        } else {
            val environment = loadIterativelyEnvironment()
            LOG.debug("Initializing Iteratively integration for $environment...")

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
                    .logger(ItlyLogger(LOG))
                    .plugins(listOf(SegmentPlugin(segmentWriteKey, anonymousId)))
                    .build()
            )
            itlyLoaded = true
        }
    }

    fun flush() {
        if (!itlyLoaded) return

        Itly.getInstance().flush()
    }

    fun identify(userId: String) {
        if (!itlyLoaded) return

        Itly.getInstance().identify(userId, Identify.builder().build())
        Itly.getInstance().alias(userId, anonymousId)
    }

    fun logWelcomeIsViewed(userId: String, event: WelcomeIsViewed) {
        if (!itlyLoaded) return

        Itly.getInstance().welcomeIsViewed(userId, event)
    }

    fun logProductSelectionIsViewed(userId: String, event: ProductSelectionIsViewed) {
        if (!itlyLoaded) return

        Itly.getInstance().productSelectionIsViewed(userId, event)
    }

    fun logAnalysisIsTriggered(userId: String, event: AnalysisIsTriggered) {
        if (!itlyLoaded) return

        Itly.getInstance().analysisIsTriggered(userId, event)
    }

    fun logAnalysisIsReady(userId: String, event: AnalysisIsReady) {
        if (!itlyLoaded) return

        Itly.getInstance().analysisIsReady(userId, event)
    }

    fun logIssueIsViewed(userId: String, event: IssueIsViewed) {
        if (!itlyLoaded) return

        Itly.getInstance().issueIsViewed(userId, event)
    }

    fun logHealthScoreIsClicked(userId: String, event: HealthScoreIsClicked) {
        if (!itlyLoaded) return

        Itly.getInstance().healthScoreIsClicked(userId, event)
    }

    private fun loadSegmentWriteKey(): String {
        return try {
            val prop = Properties()
            prop.load(javaClass.classLoader.getResourceAsStream("application.properties"))
            prop.getProperty("segment.analytics.write-key") ?: ""
        } catch (t: Throwable) {
            LOG.warn("Could not load Segment write key.", t)
            ""
        }
    }

    private fun loadIterativelyEnvironment(): Environment {
        return try {
            val prop = Properties()
            prop.load(javaClass.classLoader.getResourceAsStream("application.properties"))
            val environment = prop.getProperty("iteratively.analytics.environment") ?: "DEVELOPMENT"

            if (environment.isEmpty()) {
                LOG.warn("Iteratively environment is empty. Use DEVELOPMENT as default")
                Environment.DEVELOPMENT
            } else {
                Environment.valueOf(environment)
            }
        } catch (t: Throwable) {
            LOG.warn("Could not load Iteratively environment: use DEVELOPMENT as default.", t)
            Environment.DEVELOPMENT
        }
    }
}
