package snyk.oss

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.CliAdapter
import snyk.common.SnykError
import java.lang.reflect.Type

/**
 * Wrap work with Snyk CLI for OSS (`test` command).
 */
@Service
class OssService(project: Project) : CliAdapter<OssResult>(project) {

    fun scan(): OssResult = execute(listOf("test"))

    override fun getErrorResult(errorMsg: String): OssResult = OssResult(null, SnykError(errorMsg, projectPath))

    /**
     * If result string not contains 'error' string and contain 'vulnerabilities' it says that everything is correct.
     * If result string not contains '{' it means CLI return an error.
     * And if result string contains 'error' and not contain 'vulnerabilities' it means CLI return error in JSON format.
     * if result == [ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER] - CLI scan process was terminated by user.
     * if result is Empty - CLI fail to run (some notification already done in CLI execution code).
     */
    override fun convertRawCliStringToCliResult(rawStr: String): OssResult =
        try {
            val ossVulnerabilitiesForFileListType: Type =
                object : TypeToken<ArrayList<OssVulnerabilitiesForFile>>() {}.type
            when {
                rawStr == ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER -> {
                    OssResult(null, null)
                }
                rawStr.isEmpty() -> {
                    OssResult(null, SnykError("CLI fail to produce any output", projectPath))
                }
                rawStr.first() == '[' -> {
                    OssResult(Gson().fromJson(rawStr, ossVulnerabilitiesForFileListType), null)
                }
                rawStr.first() == '{' -> {
                    if (isSuccessCliJsonString(rawStr)) {
                        OssResult(listOf(Gson().fromJson(rawStr, OssVulnerabilitiesForFile::class.java)), null)
                    } else {
                        val cliError = Gson().fromJson(rawStr, CliError::class.java)
                        OssResult(null, SnykError(cliError.message, cliError.path))
                    }
                }
                else -> {
                    OssResult(null, SnykError(rawStr, projectPath))
                }
            }
        } catch (e: JsonSyntaxException) {
            OssResult(null, SnykError(e.message ?: e.toString(), projectPath))
        }

    private fun isSuccessCliJsonString(jsonStr: String): Boolean =
        jsonStr.contains("\"vulnerabilities\":") && !jsonStr.contains("\"error\":")

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
