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

    // TODO(pavel): anonymousId will be moved into SnykAnalyticsService after using official SegmentPlugin
    private var anonymousId = ""
    private var itly: Itly? = null

    init {
        val settings = service<SnykApplicationSettingsStateService>()
        anonymousId = settings.userAnonymousId

        val segmentWriteKey = loadSegmentWriteKey()
        if (segmentWriteKey.isBlank()) {
            LOG.debug("Segment analytics write key is empty. No analytics will be collected.")
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
            itly = Itly.getInstance()
        }
    }

    fun flush() {
        itly?.flush()
    }

    fun identify(userId: String) {
        itly?.identify(userId, Identify.builder().build())
        itly?.alias(userId, anonymousId)
    }

    fun logWelcomeIsViewed(userId: String, event: WelcomeIsViewed) {
        itly?.welcomeIsViewed(userId, event)
    }

    fun logProductSelectionIsViewed(userId: String, event: ProductSelectionIsViewed) {
        itly?.productSelectionIsViewed(userId, event)
    }

    fun logAnalysisIsTriggered(userId: String, event: AnalysisIsTriggered) {
        itly?.analysisIsTriggered(userId, event)
    }

    fun logAnalysisIsReady(userId: String, event: AnalysisIsReady) {
        itly?.analysisIsReady(userId, event)
    }

    fun logIssueIsViewed(userId: String, event: IssueIsViewed) {
        itly?.issueIsViewed(userId, event)
    }

    fun logHealthScoreIsClicked(userId: String, event: HealthScoreIsClicked) {
        itly?.healthScoreIsClicked(userId, event)
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
