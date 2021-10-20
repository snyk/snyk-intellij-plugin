package io.snyk.plugin.analytics

import ai.deepcode.javaclient.core.SuggestionForFile
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import snyk.ItlyOverrideHelper
import snyk.advisor.AdvisorPackageManager
import snyk.analytics.AnalysisIsReady.AnalysisType
import snyk.analytics.HealthScoreIsClicked
import snyk.analytics.IssueInTreeIsClicked
import snyk.analytics.IssueInTreeIsClicked.IssueType.CODE_QUALITY_ISSUE
import snyk.analytics.IssueInTreeIsClicked.IssueType.CODE_SECURITY_VULNERABILITY
import snyk.analytics.IssueInTreeIsClicked.IssueType.LICENCE_ISSUE
import snyk.analytics.IssueInTreeIsClicked.IssueType.OPEN_SOURCE_VULNERABILITY
import snyk.oss.Vulnerability

fun getSelectedProducts(settings: SnykApplicationSettingsStateService): Array<String> {
    val selectedProducts = mutableListOf<AnalysisType>()

    if (settings.ossScanEnable) selectedProducts += AnalysisType.SNYK_OPEN_SOURCE
    if (settings.snykCodeSecurityIssuesScanEnable) selectedProducts += AnalysisType.SNYK_CODE_SECURITY
    if (settings.snykCodeQualityIssuesScanEnable) selectedProducts += AnalysisType.SNYK_CODE_QUALITY

    return ItlyOverrideHelper.convertProducts(selectedProducts)
}

fun Vulnerability.getIssueSeverityOrNull(): IssueInTreeIsClicked.Severity? {
    return when (severity) {
        "critical" -> IssueInTreeIsClicked.Severity.CRITICAL
        "high" -> IssueInTreeIsClicked.Severity.HIGH
        "medium" -> IssueInTreeIsClicked.Severity.MEDIUM
        "low" -> IssueInTreeIsClicked.Severity.LOW
        else -> null
    }
}

fun Vulnerability.getIssueType(): IssueInTreeIsClicked.IssueType {
    return if (license.isNullOrEmpty()) OPEN_SOURCE_VULNERABILITY else LICENCE_ISSUE
}

fun SuggestionForFile.getIssueSeverityOrNull(): IssueInTreeIsClicked.Severity? {
    return when (severity) {
        4 -> IssueInTreeIsClicked.Severity.CRITICAL
        3 -> IssueInTreeIsClicked.Severity.HIGH
        2 -> IssueInTreeIsClicked.Severity.MEDIUM
        1 -> IssueInTreeIsClicked.Severity.LOW
        else -> null
    }
}

fun SuggestionForFile.getIssueType(): IssueInTreeIsClicked.IssueType {
    return if (categories.contains("Security")) CODE_SECURITY_VULNERABILITY else CODE_QUALITY_ISSUE
}

fun AdvisorPackageManager.getEcosystem(): HealthScoreIsClicked.Ecosystem {
    return when (this) {
        AdvisorPackageManager.NPM -> HealthScoreIsClicked.Ecosystem.NPM
        AdvisorPackageManager.PYTHON -> HealthScoreIsClicked.Ecosystem.PYTHON
    }
}
