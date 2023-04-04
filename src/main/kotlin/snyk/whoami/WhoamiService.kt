package snyk.whoami

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.CliAdapter
import snyk.common.SnykError
import snyk.pluginInfo

/**
 * Wrap work with Snyk CLI for `whoami` command.
 */
@Service
class WhoamiService(project: Project) : CliAdapter<WhoamiIssues, WhoamiResult>(project) {

    fun scan(): WhoamiResult = execute(listOf("whoami"))

    override fun getProductResult(cliIssues: List<WhoamiIssues>?, snykErrors: List<SnykError>): WhoamiResult =
        WhoamiResult(cliIssues, snykErrors)

    override fun sanitizeCliIssues(cliIssues: WhoamiIssues): WhoamiIssues =
        cliIssues.copy()

    override fun getCliIIssuesClass(): Class<WhoamiIssues> = WhoamiIssues::class.java

    override fun buildExtraOptions(): List<String> {
        val options: MutableList<String> = mutableListOf()
        options.add("--experimental")
        return options
    }
}
