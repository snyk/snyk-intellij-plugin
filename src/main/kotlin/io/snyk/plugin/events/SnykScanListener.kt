package io.snyk.plugin.events

import com.intellij.util.messages.Topic
import snyk.common.SnykError
import snyk.container.ContainerResult

interface SnykScanListener {
    companion object {
        val SNYK_SCAN_TOPIC =
            Topic.create("Snyk scan", SnykScanListener::class.java)
    }

    fun scanningStarted()

    fun scanningContainerFinished(containerResult: ContainerResult)

    fun scanningContainerError(snykError: SnykError)
}
