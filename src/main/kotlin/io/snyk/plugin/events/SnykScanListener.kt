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

    fun scanningFinished(cliResult: CliResult)

    fun scanningSnykCodeFinished(snykCodeResults: SnykCodeResults)

    fun scanError(cliError: CliError)
}