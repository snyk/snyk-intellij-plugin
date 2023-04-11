package snyk.whoami

import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class WhoamiResult(
    allWhoamiIssues: List<WhoamiIssues>?,
    errors: List<SnykError> = emptyList()
) : CliResult<WhoamiIssues>(allWhoamiIssues, errors) {

    override val issuesCount = allWhoamiIssues?.sumBy { it.uniqueCount }

    override fun countBySeverity(severity: Severity): Int {
        return 0
    }
}
