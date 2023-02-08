package snyk.iac

import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

// List of IaC errors that are not relevant for users. E.g. IaC fails to parse a non-IaC file for certain reasons.
// These should not be surfaced.
private val ignorableErrorCodes = intArrayOf(
    IacError.INVALID_JSON_FILE_ERROR,
    IacError.INVALID_YAML_FILE_ERROR,
    IacError.FAILED_TO_PARSE_INPUT,
    IacError.NOT_RECOGNIZED_OPTION_ERROR_CODE,
    IacError.COULD_NOT_FIND_VALID_IAC_FILES_ERROR_CODE,
    IacError.FAILED_TO_PARSE_TERRAFORM
)

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

    override fun getFirstError(): SnykError? {
        return getVisibleErrors().firstOrNull()
    }

    fun getVisibleErrors(): List<SnykError> {
        return errors.filter { it.code == null || !ignorableErrorCodes.contains(it.code) }
    }
}
