package io.snyk.plugin.analytics

import io.snyk.plugin.Severity
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import snyk.ItlyOverrideHelper
import snyk.advisor.AdvisorPackageManager
import snyk.analytics.AnalysisIsReady.AnalysisType
import snyk.analytics.HealthScoreIsClicked
import snyk.analytics.IssueInTreeIsClicked
import snyk.analytics.IssueInTreeIsClicked.IssueType.LICENCE_ISSUE
import snyk.analytics.IssueInTreeIsClicked.IssueType.OPEN_SOURCE_VULNERABILITY
import snyk.container.ContainerIssue
import snyk.iac.IacIssue
import snyk.oss.Vulnerability

fun getSelectedProducts(settings: SnykApplicationSettingsStateService): Array<String> {
    val selectedProducts = mutableListOf<AnalysisType>()

    if (settings.ossScanEnable) selectedProducts += AnalysisType.SNYK_OPEN_SOURCE
    if (settings.snykCodeSecurityIssuesScanEnable) selectedProducts += AnalysisType.SNYK_CODE_SECURITY
    if (settings.snykCodeQualityIssuesScanEnable) selectedProducts += AnalysisType.SNYK_CODE_QUALITY
    if (settings.iacScanEnabled) selectedProducts += AnalysisType.SNYK_INFRASTRUCTURE_AS_CODE
    if (settings.containerScanEnabled) selectedProducts += AnalysisType.SNYK_CONTAINER

    return ItlyOverrideHelper.convertProducts(selectedProducts)
}

fun Vulnerability.getIssueSeverityOrNull(): IssueInTreeIsClicked.Severity? {
    return mapIssueSeverity(getSeverity())
}

fun IacIssue.getIssueSeverityOrNull(): IssueInTreeIsClicked.Severity? {
    return mapIssueSeverity(getSeverity())
}

fun ContainerIssue.getIssueSeverityOrNull(): IssueInTreeIsClicked.Severity? {
    return mapIssueSeverity(getSeverity())
}

private fun mapIssueSeverity(severity: Severity): IssueInTreeIsClicked.Severity? {
    return when (severity) {
        Severity.CRITICAL -> IssueInTreeIsClicked.Severity.CRITICAL
        Severity.HIGH -> IssueInTreeIsClicked.Severity.HIGH
        Severity.MEDIUM -> IssueInTreeIsClicked.Severity.MEDIUM
        Severity.LOW -> IssueInTreeIsClicked.Severity.LOW
        else -> null
    }
}

fun Vulnerability.getIssueType(): IssueInTreeIsClicked.IssueType {
    return if (license.isNullOrEmpty()) OPEN_SOURCE_VULNERABILITY else LICENCE_ISSUE
}

fun AdvisorPackageManager.getEcosystem(): HealthScoreIsClicked.Ecosystem {
    return when (this) {
        AdvisorPackageManager.NPM -> HealthScoreIsClicked.Ecosystem.NPM
        AdvisorPackageManager.PYTHON -> HealthScoreIsClicked.Ecosystem.PYTHON
    }
}
