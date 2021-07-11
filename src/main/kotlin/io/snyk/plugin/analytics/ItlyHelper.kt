package io.snyk.plugin.analytics

import ai.deepcode.javaclient.core.SuggestionForFile
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import snyk.analytics.AnalysisIsReady.AnalysisType
import snyk.analytics.IssueIsViewed
import snyk.analytics.ItlyOverrideHelper

object ItlyHelper {
    fun getSelectedProducts(settings: SnykApplicationSettingsStateService): Array<String> {
        val selectedProducts = mutableListOf<AnalysisType>()

        if (settings.cliScanEnable) selectedProducts += AnalysisType.SNYK_OPEN_SOURCE
        if (settings.snykCodeSecurityIssuesScanEnable) selectedProducts += AnalysisType.SNYK_CODE_SECURITY
        if (settings.snykCodeQualityIssuesScanEnable) selectedProducts += AnalysisType.SNYK_CODE_QUALITY

        return ItlyOverrideHelper.convertProducts(selectedProducts)
    }

    fun getIssueSeverityOrNull(severity: String): IssueIsViewed.Severity? {
        return when (severity) {
            "critical" -> IssueIsViewed.Severity.CRITICAL
            "high" -> IssueIsViewed.Severity.HIGH
            "medium" -> IssueIsViewed.Severity.MEDIUM
            "low" -> IssueIsViewed.Severity.LOW
            else -> null
        }
    }

    fun getIssueSeverityOrNull(suggestion: SuggestionForFile): IssueIsViewed.Severity? {
        return when (suggestion.severity) {
            4 -> IssueIsViewed.Severity.CRITICAL
            3 -> IssueIsViewed.Severity.HIGH
            2 -> IssueIsViewed.Severity.MEDIUM
            1 -> IssueIsViewed.Severity.LOW
            else -> null
        }
    }

    fun getIssueType(suggestion: SuggestionForFile): IssueIsViewed.IssueType {
        return if (suggestion.categories.contains("Security"))
            IssueIsViewed.IssueType.CODE_SECURITY_VULNERABILITY
        else
            IssueIsViewed.IssueType.CODE_QUALITY_ISSUE
    }
}
