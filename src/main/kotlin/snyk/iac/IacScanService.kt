package snyk.iac

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.services.CliAdapter
import snyk.common.SnykError

/**
 * Wrap work with Snyk CLI for IaC (`iac test` command).
 */
@Service(Service.Level.PROJECT)
class IacScanService(project: Project) : CliAdapter<IacIssuesForFile, IacResult>(project) {

    fun scan(): IacResult = execute(listOf("iac", "test"))

    override fun getProductResult(cliIssues: List<IacIssuesForFile>?, snykErrors: List<SnykError>): IacResult =
        IacResult(cliIssues, snykErrors)

    override fun sanitizeCliIssues(cliIssues: IacIssuesForFile): IacIssuesForFile {
        // .copy() will check nullability of fields
        val sanitized = cliIssues.copy(
            infrastructureAsCodeIssues = cliIssues.infrastructureAsCodeIssues.map { it.copy() }
        )

        // determine relative path for each issue at scan time
        sanitized.project = project
        sanitized.relativePath
        return sanitized
    }

    override fun getCliIIssuesClass(): Class<IacIssuesForFile> = IacIssuesForFile::class.java

    override fun isSuccessCliJsonString(jsonStr: String): Boolean =
        jsonStr.contains("\"infrastructureAsCodeIssues\":")

    override fun buildExtraOptions(): List<String> = listOf("--json")
}
