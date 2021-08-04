package snyk.oss

import snyk.common.SnykError
import io.snyk.plugin.cli.CliResult

class OssResult(
    allOssVulnerabilities: Array<OssVulnerabilitiesForFile>?,
    error: SnykError?
) : CliResult<OssVulnerabilitiesForFile>(allOssVulnerabilities, error) {

    override val issuesCount = allOssVulnerabilities?.sumBy { it.uniqueCount }

    override fun countBySeverity(severity: String): Int? {
        return allCliIssues?.sumBy { vulnerabilitiesForFile ->
            vulnerabilitiesForFile.vulnerabilities
                .filter { it.severity == severity }
                .distinctBy { it.id }
                .size
        }
    }
}
