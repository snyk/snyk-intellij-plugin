package io.snyk.plugin.analytics

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import ly.iterative.itly.Environment
import ly.iterative.itly.Options
import ly.iterative.itly.ValidationOptions
import snyk.ItlyLogger
import snyk.PropertyLoader
import snyk.analytics.AnalysisIsReady
import snyk.analytics.AnalysisIsTriggered
import snyk.analytics.AuthenticateButtonIsClicked
import snyk.analytics.DestinationsOptions
import snyk.analytics.HealthScoreIsClicked
import snyk.analytics.Identify
import snyk.analytics.IssueInTreeIsClicked
import snyk.analytics.Itly
import snyk.analytics.PluginIsInstalled
import snyk.analytics.PluginIsUninstalled
import snyk.analytics.ProductSelectionIsViewed
import snyk.analytics.WelcomeIsViewed

object Iteratively {
    private val LOG = logger<Iteratively>()

    // TODO(pavel): anonymousId will be moved into SnykAnalyticsService after using official SegmentPlugin
    private var anonymousId = ""
    private var itly: Itly? = null

    init {
        val settings = service<SnykApplicationSettingsStateService>()
        anonymousId = settings.userAnonymousId

        val segmentWriteKey = PropertyLoader.segmentWriteKey
        if (segmentWriteKey.isBlank()) {
            LOG.debug("Segment analytics write key is empty. No analytics will be collected.")
        } else {
            val environment = loadIterativelyEnvironment()
            LOG.debug("Initializing Iteratively integration for $environment...")

            Itly.getInstance().load(
                DestinationsOptions.builder().build(),
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

    fun logIssueInTreeIsClicked(userId: String, event: IssueInTreeIsClicked) {
        itly?.issueInTreeIsClicked(userId, event)
    }

    fun logHealthScoreIsClicked(userId: String, event: HealthScoreIsClicked) {
        itly?.healthScoreIsClicked(userId, event)
    }

    fun logPluginIsInstalled(userId: String, event: PluginIsInstalled) {
        itly?.pluginIsInstalled(userId, event)
    }

    fun logPluginIsUninstalled(userId: String, event: PluginIsUninstalled) {
        itly?.pluginIsUninstalled(userId, event)
    }

    fun logAuthenticateButtonIsClicked(userId: String, event: AuthenticateButtonIsClicked) {
        itly?.authenticateButtonIsClicked(userId, event)
    }

    private fun loadIterativelyEnvironment(): Environment {
        val environment = PropertyLoader.environment
        return if (environment.isEmpty()) {
            LOG.warn("Iteratively environment is empty. Use DEVELOPMENT as default")
            Environment.DEVELOPMENT
        } else {
            Environment.valueOf(environment)
        }
    }
}
