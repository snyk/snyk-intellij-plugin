package snyk.iac

import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.CliService

class IacIgnoreService(val consoleCommandRunner: ConsoleCommandRunner, project: Project, cliCommands: List<String>) :
    CliService<IacResult>(project, cliCommands) {

    fun ignore(issue: IacIssue): String {
        val apiToken = pluginSettings().token ?: ""
        val commands = buildCliCommandsList()
        commands.add("ignore")
        commands.add("--id='${issue.id}'")
        return consoleCommandRunner.execute(commands, project.basePath ?: ".", apiToken, project)
    }

    override fun getErrorResult(errorMsg: String): IacResult {
        TODO("Not yet implemented")
    }

    override fun convertRawCliStringToCliResult(rawStr: String): IacResult {
        TODO("Not yet implemented")
    }
}
