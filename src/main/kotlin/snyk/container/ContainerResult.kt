package snyk.container

import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class ContainerResult(
    containerVulnerabilities: Array<ContainerIssuesForFile>?,
    error: SnykError?
) : CliResult<ContainerIssuesForFile>(containerVulnerabilities, error) {

    override val issuesCount = containerVulnerabilities?.sumBy { it.uniqueCount }

    override fun countBySeverity(severity: String): Int? {
        return allCliIssues?.sumBy { issuesForFile ->
            issuesForFile.vulnerabilities
                .filter { it.severity == severity }
                .distinctBy { it.id }
                .size
        }
    }
}
