package snyk.container

import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class ContainerResult(
    containerVulnerabilities: List<ContainerIssuesForImage>?,
    errors: List<SnykError> = emptyList()
) : CliResult<ContainerIssuesForImage>(containerVulnerabilities, errors) {

    var rescanNeeded: Boolean = false

    override val issuesCount: Int? get() = allCliIssues?.sumOf { it.uniqueCount }

    override fun countBySeverity(severity: Severity): Int? {
        return allCliIssues?.sumOf { issuesForFile ->
            issuesForFile.vulnerabilities
                .filter { it.getSeverity() == severity }
                .distinctBy { it.id }
                .size
        }
    }
}
