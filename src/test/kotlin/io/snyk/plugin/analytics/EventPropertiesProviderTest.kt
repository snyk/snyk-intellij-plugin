package io.snyk.plugin.analytics

import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import org.hamcrest.collection.IsCollectionWithSize.hasSize
import org.hamcrest.collection.IsMapContaining.hasKey
import org.hamcrest.core.AllOf.allOf
import org.hamcrest.core.IsCollectionContaining.hasItem
import org.junit.Assert.assertThat
import org.junit.Test

class EventPropertiesProviderTest {

    @Test
    fun `empty selected products if nothing configured`() {
        val settings = SnykApplicationSettingsStateService()
        settings.cliScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false

        val actualProducts = EventPropertiesProvider.getSelectedProducts(settings)

        assertThat(actualProducts.map, hasKey("selectedProducts"))
        assertThat(actualProducts.map["selectedProducts"] as List<*>, hasSize(0))
    }

    @Test
    fun `one selected product if only one configured`() {
        val settings = SnykApplicationSettingsStateService()
        settings.cliScanEnable = true
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false

        val actualProducts = EventPropertiesProvider.getSelectedProducts(settings)

        assertThat(actualProducts.map, hasKey("selectedProducts"))
        assertThat(actualProducts.map["selectedProducts"] as List<*>, hasSize(1))
    }

    @Test
    fun `empty analysis details for open source if cliResult empty`() {
        val cliResult = CliResult(null, null)

        val actualAnalysis = EventPropertiesProvider.getAnalysisDetailsForOpenSource(cliResult)
        @Suppress("UNCHECKED_CAST") val actualAnalysisDetails = actualAnalysis.map["analysisDetails"] as java.util.HashMap<*, *>

        assertThat(actualAnalysis.map, hasKey("analysisDetails"))
        assertThat(
            actualAnalysisDetails.keys, allOf(
                hasItem("highSeverityIssuesCount"),
                hasItem("mediumSeverityIssuesCount"),
                hasItem("lowSeverityIssuesCount")
            )
        )
        assertThat(actualAnalysisDetails.values, hasItem(0))
    }
}
