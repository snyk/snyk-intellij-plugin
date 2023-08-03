package snyk.whoami

import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class WhoamiResult(
    allWhoamiOutputs: List<WhoamiOutput>?,
    errors: List<SnykError> = emptyList()
) : CliResult<WhoamiOutput>(allWhoamiOutputs, errors) {

    override val issuesCount = allWhoamiOutputs?.sumOf { it.uniqueCount }

    override fun countBySeverity(severity: Severity): Int {
        return 0
    }
}
