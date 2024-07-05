package snyk.oss

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.CliAdapter
import snyk.common.RelativePathHelper
import snyk.common.SnykError
import snyk.pluginInfo

/**
 * Wrap work with Snyk CLI for OSS (`test` command).
 */
@Service(Service.Level.PROJECT)
class OssService(project: Project) : CliAdapter<OssVulnerabilitiesForFile, OssResult>(project) {

    fun scan(): OssResult = execute(listOf("test"))

    override fun getProductResult(cliIssues: List<OssVulnerabilitiesForFile>?, snykErrors: List<SnykError>): OssResult {
        return OssResult(cliIssues, snykErrors)
    }

    override fun sanitizeCliIssues(cliIssues: OssVulnerabilitiesForFile): OssVulnerabilitiesForFile {
        // .copy() will check nullability of fields
        val virtualFile = cliIssues.virtualFile ?: LocalFileSystem.getInstance().findFileByPath(cliIssues.path)
        // determine relative path for each issue at scan time
        return cliIssues.copy(
            vulnerabilities = cliIssues.vulnerabilities.map { it.copy() },
            project = project,
            virtualFile = virtualFile,
            relativePath = virtualFile?.let { RelativePathHelper().getRelativePath(virtualFile, project) }
        )
    }

    override fun getCliIIssuesClass(): Class<OssVulnerabilitiesForFile> = OssVulnerabilitiesForFile::class.java

    override fun buildExtraOptions(): List<String> {
        val settings = pluginSettings()
        val options: MutableList<String> = mutableListOf()

        options.add("--json")

        val additionalParameters = settings.getAdditionalParameters(project)
        val hasAllProjectsParam = additionalParameters != null && additionalParameters.contains(ALL_PROJECTS_PARAM)

        if (!hasAllProjectsParam) {
            options.add(ALL_PROJECTS_PARAM)
        }

        if (additionalParameters != null && additionalParameters.trim().isNotEmpty()) {
            options.addAll(additionalParameters.trim().split(" "))
        }
        return options
    }

    companion object {
        const val ALL_PROJECTS_PARAM = "--all-projects"
    }
}
