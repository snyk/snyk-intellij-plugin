package snyk.iac

import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

// List of IaC errors that are not relevant for users. E.g. IaC fails to parse a non-IaC file for certain reasons.
// These should not be surfaced.
private val IgnorableErrorCodes = intArrayOf(1021, 2105)

class IacResult(
    allIacVulnerabilities: List<IacIssuesForFile>?,
    errors: List<SnykError> = emptyList()
) : CliResult<IacIssuesForFile>(allIacVulnerabilities, errors) {

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

    fun getVisibleErrors(): List<SnykError> {
        return errors.filter { it.code == null || !IgnorableErrorCodes.contains(it.code) }
    }
}
