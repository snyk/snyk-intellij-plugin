package snyk.common

import com.intellij.openapi.project.Project
import io.snyk.plugin.Severity
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.services.CliAdapter
import snyk.iac.IacIssue

class IgnoreService(project: Project) : CliAdapter<Unit, IgnoreService.IgnoreResults>(project) {

    fun ignore(issueId: String) {
        val result = execute(listOf("ignore", "--id=$issueId"))
        if (isFail(result)) {
            throw IgnoreException("Unexpected error (${result.getFirstError()?.message}) when ignoring $issueId.")
        }
    }

    fun ignoreInstance(issueId: String, path: String) {
        val result = execute(listOf("ignore", "--id=$issueId", "--path=$path"))
        if (isFail(result)) {
            throw IgnoreException("Unexpected error (${result.getFirstError()?.message}) when ignoring $issueId.")
        }
    }

    private fun isFail(result: IgnoreResults) =
        result.errors.isNotEmpty() && result.getFirstError()?.message != CLI_PRODUCE_NO_OUTPUT

    fun buildPath(issue: IacIssue, targetFile: String): String {
        val separator = " > "
        return targetFile + separator + issue.path.joinToString(separator)
    }

    override fun getProductResult(cliIssues: List<Unit>?, snykErrors: List<SnykError>) =
        IgnoreResults(snykErrors.firstOrNull()?.message)

    override fun sanitizeCliIssues(cliIssues: Unit) = cliIssues

    override fun getCliIIssuesClass(): Class<Unit> = Unit::class.java

    override fun buildExtraOptions(): List<String> = emptyList()

    inner class IgnoreResults(errorMessage: String?) : CliResult<Unit>(
        null,
        errorMessage?.let { listOf(SnykError(it, "")) } ?: emptyList()
    ) {
        override val issuesCount: Int? = null
        override fun countBySeverity(severity: Severity): Int? = null
    }
}

