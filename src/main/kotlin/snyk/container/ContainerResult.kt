package snyk.container

import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class ContainerResult(containerVulnerabilities: List<ContainerIssuesForImage>?, error: SnykError?) :
    CliResult<ContainerIssuesForImage>(
        containerVulnerabilities,
        error
    ) {

    var rescanNeeded: Boolean = false

    override val issuesCount: Int? get() = allCliIssues?.sumBy { it.uniqueCount }

    override fun countBySeverity(severity: String): Int? {
        return allCliIssues?.sumBy { issuesForFile ->
            issuesForFile.vulnerabilities
                .filter { it.severity == severity }
                .distinctBy { it.id }
                .size
        }
    }
}
