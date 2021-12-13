package snyk.container

import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class ContainerResult(
    containerVulnerabilities: List<ContainerIssuesForFile>?,
    error: SnykError?
) : CliResult<ContainerIssuesForFile>(containerVulnerabilities, error) {

    override val issuesCount: Int? get () = allCliIssues?.sumBy { it.uniqueCount }

    override fun countBySeverity(severity: String): Int? {
        return allCliIssues?.sumBy { issuesForFile ->
            issuesForFile.vulnerabilities
                .filter { it.severity == severity }
                .distinctBy { it.id }
                .size
        }
    }
}
