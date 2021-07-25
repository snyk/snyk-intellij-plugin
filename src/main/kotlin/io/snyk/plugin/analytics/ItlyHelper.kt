package io.snyk.plugin.analytics

import ai.deepcode.javaclient.core.SuggestionForFile
import io.snyk.plugin.cli.Vulnerability
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import snyk.analytics.AnalysisIsReady.AnalysisType
import snyk.analytics.IssueIsViewed
import snyk.analytics.IssueIsViewed.IssueType.CODE_QUALITY_ISSUE
import snyk.analytics.IssueIsViewed.IssueType.CODE_SECURITY_VULNERABILITY
import snyk.analytics.IssueIsViewed.IssueType.LICENCE_ISSUE
import snyk.analytics.IssueIsViewed.IssueType.OPEN_SOURCE_VULNERABILITY
import snyk.analytics.ItlyOverrideHelper

fun getSelectedProducts(settings: SnykApplicationSettingsStateService): Array<String> {
    val selectedProducts = mutableListOf<AnalysisType>()

    if (settings.cliScanEnable) selectedProducts += AnalysisType.SNYK_OPEN_SOURCE
    if (settings.snykCodeSecurityIssuesScanEnable) selectedProducts += AnalysisType.SNYK_CODE_SECURITY
    if (settings.snykCodeQualityIssuesScanEnable) selectedProducts += AnalysisType.SNYK_CODE_QUALITY

    return ItlyOverrideHelper.convertProducts(selectedProducts)
}

fun Vulnerability.getIssueSeverityOrNull(): IssueIsViewed.Severity? {
    return when (severity) {
        "critical" -> IssueIsViewed.Severity.CRITICAL
        "high" -> IssueIsViewed.Severity.HIGH
        "medium" -> IssueIsViewed.Severity.MEDIUM
        "low" -> IssueIsViewed.Severity.LOW
        else -> null
    }
}

fun Vulnerability.getIssueType(): IssueIsViewed.IssueType {
    return if (license.isNullOrEmpty()) OPEN_SOURCE_VULNERABILITY else LICENCE_ISSUE
}

fun SuggestionForFile.getIssueSeverityOrNull(): IssueIsViewed.Severity? {
    return when (severity) {
        4 -> IssueIsViewed.Severity.CRITICAL
        3 -> IssueIsViewed.Severity.HIGH
        2 -> IssueIsViewed.Severity.MEDIUM
        1 -> IssueIsViewed.Severity.LOW
        else -> null
    }
}

fun SuggestionForFile.getIssueType(): IssueIsViewed.IssueType {
    return if (categories.contains("Security")) CODE_SECURITY_VULNERABILITY else CODE_QUALITY_ISSUE
}
