package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.CliResult

interface SnykCliScanListener {
    companion object {
        val CLI_SCAN_TOPIC =
            Topic.create("Snyk CLI scan", SnykCliScanListener::class.java)
    }

    fun scanningStarted()

    fun scanningFinished(cliResult: CliResult)

    fun scanError(cliError: CliError)
}
