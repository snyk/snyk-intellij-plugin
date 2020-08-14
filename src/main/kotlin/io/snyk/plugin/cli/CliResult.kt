package io.snyk.plugin.cli

import java.util.Objects.isNull

class CliResult {
    lateinit var vulnerabilities: Array<Vulnerability>

    var error: CliError? = null

    fun isSuccessful(): Boolean = isNull(error)
}
