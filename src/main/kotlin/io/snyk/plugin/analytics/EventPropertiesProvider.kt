package io.snyk.plugin.analytics

import ai.deepcode.javaclient.core.SuggestionForFile
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.cli.Vulnerability
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.snykcode.SnykCodeResults
import io.snyk.plugin.snykcode.severityAsString

object EventPropertiesProvider {
    fun getSelectedProducts(settings: SnykApplicationSettingsStateService): EventProperties {
        val selectedProducts = mutableListOf<String>()

        if (settings.cliScanEnable) selectedProducts += "Snyk Open Source"
        if (settings.snykCodeSecurityIssuesScanEnable) selectedProducts += "Snyk Code Security"
        if (settings.snykCodeQualityIssuesScanEnable) selectedProducts += "Snyk Code Quality"

        return EventProperties(mapOf("selectedProducts" to selectedProducts))
    }

    fun getAnalysisDetailsForOpenSource(cliResult: CliResult): EventProperties {
        val analysisDetails = mutableMapOf<String, Any>()

        analysisDetails["highSeverityIssuesCount"] = cliResult.countBySeverity("high") ?: 0
        analysisDetails["mediumSeverityIssuesCount"] = cliResult.countBySeverity("medium") ?: 0
        analysisDetails["lowSeverityIssuesCount"] = cliResult.countBySeverity("low") ?: 0

        return EventProperties(mapOf("analysisDetails" to analysisDetails))
    }

    fun getAnalysisDetailsForCode(codeResult: SnykCodeResults): EventProperties {
        val analysisDetails = mutableMapOf<String, Any>()

        analysisDetails["highSeverityIssuesCount"] = codeResult.totalErrorsCount
        analysisDetails["mediumSeverityIssuesCount"] = codeResult.totalWarnsCount
        analysisDetails["lowSeverityIssuesCount"] = codeResult.totalInfosCount

        return EventProperties(mapOf("analysisDetails" to analysisDetails))
    }

    fun getIssueDetailsForOpenSource(groupedVulns: Collection<Vulnerability>): EventProperties {
        val issueDetails = mutableMapOf<String, Any>()

        val vulnerability = groupedVulns.first()
        issueDetails["id"] = vulnerability.id
        issueDetails["severity"] = vulnerability.severity

        return EventProperties(mapOf("issueDetails" to issueDetails))
    }

    fun getIssueDetailsForCode(suggestion: SuggestionForFile): EventProperties {
        val issueDetails = mutableMapOf<String, Any>()

        issueDetails["id"] = suggestion.id
        issueDetails["severity"] = suggestion.severityAsString
        issueDetails["type"] = if (suggestion.categories.contains("Security")) "code security" else "code quality"

        return EventProperties(mapOf("issueDetails" to issueDetails))
    }
}
