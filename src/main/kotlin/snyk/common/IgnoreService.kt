package snyk.common

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.CliAdapter
import org.jetbrains.annotations.NotNull
import snyk.iac.IacIssue
import snyk.iac.IacResult

class IgnoreService(
    val consoleCommandRunner: ConsoleCommandRunner,
    project: Project,
    cliCommands: List<String> = emptyList()
) : CliAdapter<IacResult>(project, cliCommands) {

    fun ignore(issue: IacIssue): String {
        val apiToken = pluginSettings().token ?: ""
        val commands = buildCliCommandsList()
        commands.add("ignore")
        commands.add("--id=${issue.id}")
        var output = ""
        ProgressManager.getInstance().run(object : Backgroundable(project, "Executing Snyk CLI for ignore...") {
            override fun run(@NotNull progressIndicator: ProgressIndicator) {
                output = consoleCommandRunner.execute(commands, project.basePath ?: ".", apiToken, project)
            }
        })
        return output
    }

    override fun getErrorResult(errorMsg: String): IacResult {
        TODO("Not yet implemented")
    }

    override fun convertRawCliStringToCliResult(rawStr: String): IacResult {
        TODO("Not yet implemented")
    }
}
