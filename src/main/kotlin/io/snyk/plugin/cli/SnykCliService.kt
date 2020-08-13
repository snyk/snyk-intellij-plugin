package io.snyk.plugin.cli

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.charset.Charset

/**
 * Wrap work with Snyk CLI.
 */
@Service
class SnykCliService(val project: Project) {

    fun scan(): CliResult {
        val projectPath = project?.basePath

        val commands = mutableListOf<String>()

        commands.add("snyk")
        commands.add("--json")
        commands.add("test")

        val generalCommandLine = GeneralCommandLine(commands)

        generalCommandLine.charset = Charset.forName("UTF-8")
        generalCommandLine.setWorkDirectory(projectPath)

        val snykResultJsonStr = ScriptRunnerUtil
            .getProcessOutput(generalCommandLine, ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER, 720000)

        return Gson().fromJson(snykResultJsonStr, CliResult::class.java)
    }
}
