package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import io.snyk.plugin.cli.CliError
import io.snyk.plugin.cli.CliResult
import io.snyk.plugin.snykcode.SnykCodeResults

interface SnykScanListener {
    companion object {
        val SNYK_SCAN_TOPIC =
            Topic.create("Snyk scan", SnykScanListener::class.java)
    }

    fun scanningStarted()

    fun scanningCliFinished(cliResult: CliResult)

    fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults)

    fun scanningCliError(cliError: CliError)

    fun scanningSnykCodeError(cliError: CliError)
}
