package snyk.oss

import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import snyk.common.SnykError

class OssResult(
    allOssVulnerabilities: List<OssVulnerabilitiesForFile>?,
    error: SnykError?
) : CliResult<OssVulnerabilitiesForFile>(allOssVulnerabilities, error) {

    override val issuesCount = allOssVulnerabilities?.sumBy { it.uniqueCount }

    override fun countBySeverity(severity: Severity): Int? {
        return allCliIssues?.sumBy { vulnerabilitiesForFile ->
            vulnerabilitiesForFile.vulnerabilities
                .filter { it.getSeverity() == severity }
                .distinctBy { it.id }
                .size
        }
    }
}
