package io.snyk.plugin.events

import com.intellij.util.messages.Topic

interface SnykCliDownloadListener {
    companion object {
        val CLI_DOWNLOAD_TOPIC =
            Topic.create("Snyk CLI download", SnykCliDownloadListener::class.java)
    }

    fun checkCliExistsStarted() {}

    fun checkCliExistsFinished() {}

    fun cliDownloadStarted() {}

    fun cliDownloadFinished(succeed: Boolean = true) {}
}
