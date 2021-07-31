package snyk.iac

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.services.CliService
import snyk.common.SnykError

/**
 * Wrap work with Snyk CLI for IaC (`iac test` command).
 */
@Service
class IacService(project: Project) : CliService<IacResult>(
    project = project,
    cliCommands = listOf("iac", "test")
) {

    override fun getErrorResult(errorMsg: String): IacResult = IacResult(null, SnykError(errorMsg, projectPath))

    override fun convertRawCliStringToCliResult(rawStr: String): IacResult =
        when {
            rawStr == ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER -> IacResult(null, null)
            rawStr.first() == '[' -> {
                IacResult(Gson().fromJson(rawStr, Array<IacIssuesForFile>::class.java), null)
            }
            rawStr.first() == '{' -> {
                if (isSuccessCliJsonString(rawStr)) {
                    IacResult(arrayOf(Gson().fromJson(rawStr, IacIssuesForFile::class.java)), null)
                } else {
                    val cliError = Gson().fromJson(rawStr, CliError::class.java)
                    IacResult(null, SnykError(cliError.message, cliError.path))
                }
            }
            else -> {
                IacResult(null, SnykError(rawStr, projectPath))
            }
        }

    private fun isSuccessCliJsonString(jsonStr: String): Boolean =
        jsonStr.contains("\"infrastructureAsCodeIssues\":") && !jsonStr.contains("\"error\":")
}
