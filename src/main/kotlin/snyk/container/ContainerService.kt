package snyk.container

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.services.CliService
import snyk.common.SnykError

@Service
class ContainerService(project: Project) : CliService<ContainerResult>(
    project = project,
    cliCommands = listOf("container", "test")
) {
    override fun getErrorResult(errorMsg: String): ContainerResult =
        ContainerResult(null, SnykError(errorMsg, projectPath))

    override fun convertRawCliStringToCliResult(rawStr: String): ContainerResult =
        try {
            when {
                rawStr == ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER -> {
                    ContainerResult(null, null)
                }
                rawStr.isEmpty() -> {
                    ContainerResult(null, SnykError("CLI failed to produce any output", projectPath))
                }
                rawStr.first() == '[' -> {
                    ContainerResult(Gson().fromJson(rawStr, Array<ContainerIssuesForFile>::class.java), null)
                }
                rawStr.first() == '{' -> {
                    if (isSuccessCliJsonString(rawStr)) {
                        ContainerResult(arrayOf(Gson().fromJson(rawStr, ContainerIssuesForFile::class.java)), null)
                    } else {
                        val cliError = Gson().fromJson(rawStr, CliError::class.java)
                        ContainerResult(null, SnykError(cliError.message, cliError.path))
                    }
                }
                else -> {
                    ContainerResult(null, SnykError(rawStr, projectPath))
                }
            }
        } catch (e: JsonSyntaxException) {
            ContainerResult(null, SnykError(e.message ?: e.toString(), projectPath))
        }

    private fun isSuccessCliJsonString(jsonStr: String): Boolean =
        jsonStr.contains("\"vulnerabilities\":") && !jsonStr.contains("\"error\":")
}
