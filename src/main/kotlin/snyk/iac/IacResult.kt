package snyk.iac

import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class IacResult(
    allIacVulnerabilities: List<IacIssuesForFile>?,
    error: SnykError?
) : CliResult<IacIssuesForFile>(allIacVulnerabilities, error) {

    var iacScanNeeded: Boolean = false

    override val issuesCount get() = allCliIssues?.sumBy { it.uniqueCount }

    override fun countBySeverity(severity: Severity): Int? {
        return allCliIssues?.sumBy { issuesForFile ->
            issuesForFile.infrastructureAsCodeIssues
                .filter { it.getSeverity() == severity }
                .distinctBy { it.id }
                .size
        }
    }
}
