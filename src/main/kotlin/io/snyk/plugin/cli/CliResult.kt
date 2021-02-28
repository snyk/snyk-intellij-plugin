package io.snyk.plugin.cli

import java.time.Instant

class CliResult(var vulnerabilities: Array<CliVulnerabilities>?, var error: CliError?) {
    val timeStamp = Instant.now()

    fun isSuccessful(): Boolean = error == null

    fun issuesCount(): Int = if (vulnerabilities == null) {
            0
        } else {
            vulnerabilities!!.sumBy { it.uniqueCount }
        }
}
