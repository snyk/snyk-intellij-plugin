package snyk.iac

import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class IacResult(
    allIacVulnerabilities: List<IacIssuesForFile>?,
    error: SnykError?
) : CliResult<IacIssuesForFile>(allIacVulnerabilities, error) {

    override val issuesCount get() = allCliIssues?.sumBy { it.uniqueCount }

    override fun countBySeverity(severity: String): Int? {
        return allCliIssues?.sumBy { issuesForFile ->
            issuesForFile.infrastructureAsCodeIssues
                .filter { it.severity == severity }
                .distinctBy { it.id }
                .size
        }
    }
}
