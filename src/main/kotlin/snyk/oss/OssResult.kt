package snyk.oss

import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class OssResult(
    allOssVulnerabilities: List<OssVulnerabilitiesForFile>?,
    errors: List<SnykError> = emptyList()
) : CliResult<OssVulnerabilitiesForFile>(allOssVulnerabilities, errors) {

    override val issuesCount = allOssVulnerabilities?.sumOf { it.uniqueCount }

    override fun countBySeverity(severity: Severity): Int? {
        return allCliIssues?.sumOf { vulnerabilitiesForFile ->
            vulnerabilitiesForFile.vulnerabilities
                .filter { it.getSeverity() == severity }
                .distinctBy { it.id }
                .size
        }
    }
}
