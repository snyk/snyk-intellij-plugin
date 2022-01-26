package snyk.common

import com.intellij.ide.CliResult
import com.intellij.openapi.project.Project
import io.snyk.plugin.services.CliAdapter
import snyk.iac.IacIssue

class IgnoreService(project: Project) : CliAdapter<CliResult>(project) {

    fun ignore(issueId: String) {
        val result = execute(listOf("ignore", "--id=$issueId"))
        if (result.exitCode > 0) {
            throw IgnoreException("Unexpected error (${result.exitCode}) when ignoring $issueId.")
        }
    }

    fun ignoreInstance(issueId: String, path: String) {
        val result = execute(listOf("ignore", "--id=$issueId", "--path='$path'"))
        if (result.exitCode > 0) {
            throw IgnoreException("Unexpected error (${result.exitCode}) when ignoring $issueId.")
        }
    }

    fun buildPath(issue: IacIssue, targetFile: String): String {
        val separator = " > "
        return targetFile + separator + issue.path.joinToString(separator)
    }

    override fun getErrorResult(errorMsg: String): CliResult = CliResult(1, errorMsg)
    override fun convertRawCliStringToCliResult(rawStr: String): CliResult {
        return if (rawStr.isEmpty()) {
            CliResult(0, "Successful")
        } else {
            CliResult(2, "Unexpected Output")
        }
    }

    override fun buildExtraOptions(): List<String> = emptyList()
}
