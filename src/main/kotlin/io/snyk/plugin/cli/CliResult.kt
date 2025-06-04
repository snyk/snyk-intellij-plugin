package io.snyk.plugin.cli

import io.snyk.plugin.Severity
import snyk.common.SnykError

abstract class CliResult<CliIssues>(
    var allCliIssues: List<CliIssues>?,
    var errors: List<SnykError>
) {

    abstract val issuesCount: Int?

    protected abstract fun countBySeverity(severity: Severity): Int?

    open fun getFirstError(): SnykError? = errors.firstOrNull()
}
