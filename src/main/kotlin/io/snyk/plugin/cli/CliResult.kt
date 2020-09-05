package io.snyk.plugin.cli

class CliResult(var vulnerabilities: Array<CliVulnerabilities>?, var error: CliError?) {
    fun isSuccessful(): Boolean = error == null

    fun issuesCount(): Int = if (vulnerabilities == null) {
            0
        } else {
            var issuesCount = 0

            vulnerabilities!!.forEach { issuesCount += it.uniqueCount }

            issuesCount
        }
}
