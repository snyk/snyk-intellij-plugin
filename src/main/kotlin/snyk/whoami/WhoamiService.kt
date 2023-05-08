package snyk.whoami

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.services.CliAdapter
import snyk.common.SnykError

/**
 * Wrap work with Snyk CLI for `whoami` command.
 */
@Service
class WhoamiService(project: Project) : CliAdapter<WhoamiOutput, WhoamiResult>(project) {

    fun execute(): WhoamiResult = execute(listOf("whoami"))

    override fun getProductResult(cliIssues: List<WhoamiOutput>?, snykErrors: List<SnykError>): WhoamiResult =
        WhoamiResult(cliIssues, snykErrors)

    override fun sanitizeCliIssues(cliIssues: WhoamiOutput): WhoamiOutput =
        cliIssues.copy()

    override fun getCliIIssuesClass(): Class<WhoamiOutput> = WhoamiOutput::class.java

    override fun buildExtraOptions(): List<String> {
        val options: MutableList<String> = mutableListOf()
        options.add("--experimental")
        return options
    }
}
