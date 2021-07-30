package snyk.oss

import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.*
import io.snyk.plugin.services.CliService
import snyk.common.SnykError

/**
 * Wrap work with Snyk CLI for OSS (`test` command).
 */
@Service
class OssService(project: Project) : CliService<OssResult>(
    project = project,
    cliCommands = listOf("test")
) {

    override fun getErrorResult(errorMsg: String): OssResult = OssResult(null, SnykError(errorMsg, projectPath))

    /**
     * If result string not contains 'error' string and contain 'vulnerabilities' it says that everything is correct.
     * If result string not contains '{' it means CLI return an error.
     * And if result string contains 'error' and not contain 'vulnerabilities' it means CLI return error in JSON format.
     * if result == [ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER] - CLI scan process was terminated by user..
     */
    override fun convertRawCliStringToCliResult(rawStr: String): OssResult =
        when {
            rawStr == ConsoleCommandRunner.PROCESS_CANCELLED_BY_USER -> OssResult(null, null)
            rawStr.first() == '[' -> {
                OssResult(Gson().fromJson(rawStr, Array<OssVulnerabilitiesForFile>::class.java), null)
            }
            rawStr.first() == '{' -> {
                if (isSuccessCliJsonString(rawStr)) {
                    OssResult(arrayOf(Gson().fromJson(rawStr, OssVulnerabilitiesForFile::class.java)), null)
                } else {
                    val cliError = Gson().fromJson(rawStr, CliError::class.java)
                    OssResult(null, SnykError(cliError.message, cliError.path))
                }
            }
            else -> {
                OssResult(null, SnykError(rawStr, projectPath))
            }
        }

    private fun isSuccessCliJsonString(jsonStr: String): Boolean =
        jsonStr.contains("\"vulnerabilities\":") && !jsonStr.contains("\"error\":")
}
