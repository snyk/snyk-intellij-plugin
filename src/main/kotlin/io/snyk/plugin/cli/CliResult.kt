package io.snyk.plugin.cli

import io.snyk.plugin.Severity
import java.time.Instant

class CliResult(var allCliVulnerabilities: Array<CliVulnerabilitiesForFile>?, var error: CliError?) {
    val timeStamp = Instant.now()

    fun isSuccessful(): Boolean = error == null

    val issuesCount = allCliVulnerabilities?.sumBy { it.uniqueCount }

    fun countBySeverity(severity: String): Int? {
        return allCliVulnerabilities?.sumBy { vulnerabilitiesForFile ->
            vulnerabilitiesForFile.vulnerabilities
                .filter { it.severity == severity }
                .distinctBy { it.id }
                .size
        }
    }

    fun criticalSeveritiesCount(): Int = countBySeverity(Severity.CRITICAL) ?: 0

    fun highSeveritiesCount(): Int = countBySeverity(Severity.HIGH) ?: 0

    fun mediumSeveritiesCount(): Int = countBySeverity(Severity.MEDIUM) ?: 0

    fun lowSeveritiesCount(): Int = countBySeverity(Severity.LOW) ?: 0
}
