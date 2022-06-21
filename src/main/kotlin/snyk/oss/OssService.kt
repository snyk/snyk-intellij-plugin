package snyk.oss

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.CliAdapter
import snyk.common.SnykError

/**
 * Wrap work with Snyk CLI for OSS (`test` command).
 */
@Service
class OssService(project: Project) : CliAdapter<OssVulnerabilitiesForFile, OssResult>(project) {

    fun scan(): OssResult = execute(listOf("test"))

    override fun getProductResult(cliIssues: List<OssVulnerabilitiesForFile>?, snykErrors: List<SnykError>): OssResult =
        OssResult(cliIssues, snykErrors)

    override fun sanitizeCliIssues(cliIssues: OssVulnerabilitiesForFile): OssVulnerabilitiesForFile =
        // .copy() will check nullability of fields
        cliIssues.copy(
            vulnerabilities = cliIssues.vulnerabilities.map { it.copy() }
        )

    override fun getCliIIssuesClass(): Class<OssVulnerabilitiesForFile> = OssVulnerabilitiesForFile::class.java

    override fun buildExtraOptions(): List<String> {
        val settings = pluginSettings()
        val options: MutableList<String> = mutableListOf()

        options.add("--json")

        val additionalParameters = settings.getAdditionalParameters(project)

        if (additionalParameters != null && additionalParameters.trim().isNotEmpty()) {
            options.addAll(additionalParameters.trim().split(" "))
        }
        return options
    }
}
