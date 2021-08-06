package snyk.iac

import snyk.common.SnykError
import io.snyk.plugin.cli.CliResult

class IacResult(
    allIacVulnerabilities: Array<IacIssuesForFile>?,
    error: SnykError?
) : CliResult<IacIssuesForFile>(allIacVulnerabilities, error) {

    override val issuesCount = allCliIssues?.sumBy { it.uniqueCount }

    override fun countBySeverity(severity: String): Int? {
        return allCliIssues?.sumBy { issuesForFile ->
            issuesForFile.infrastructureAsCodeIssues
                .filter { it.severity == severity }
                .distinctBy { it.id }
                .size
        }
    }
}
