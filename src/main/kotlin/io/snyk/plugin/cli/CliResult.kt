package io.snyk.plugin.cli

import io.snyk.plugin.Severity
import java.time.Instant
import snyk.common.SnykError

abstract class CliResult<CliIssues>(
    var allCliIssues: List<CliIssues>?,
    var error: SnykError?
) {

    val timeStamp: Instant = Instant.now()

    fun isSuccessful(): Boolean = error == null

    abstract val issuesCount: Int?

    protected abstract fun countBySeverity(severity: String): Int?

    fun criticalSeveritiesCount(): Int = countBySeverity(Severity.CRITICAL) ?: 0

    fun highSeveritiesCount(): Int = countBySeverity(Severity.HIGH) ?: 0

    fun mediumSeveritiesCount(): Int = countBySeverity(Severity.MEDIUM) ?: 0

    fun lowSeveritiesCount(): Int = countBySeverity(Severity.LOW) ?: 0
}
