package snyk.iac

import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class IacResult(
    allIacVulnerabilities: Array<IacIssuesForFile>?,
    error: SnykError?
) : CliResult<IacIssuesForFile>(allIacVulnerabilities, error) {

    override val issuesCount = allCliIssues?.sumBy { it.infrastructureAsCodeIssues.size }

    override fun countBySeverity(severity: String): Int? {
        return allCliIssues?.sumBy { issuesForFile ->
            issuesForFile.infrastructureAsCodeIssues
                .filter { it.severity == severity }
                .size
        }
    }
}
