package io.snyk.plugin.cli

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
}
