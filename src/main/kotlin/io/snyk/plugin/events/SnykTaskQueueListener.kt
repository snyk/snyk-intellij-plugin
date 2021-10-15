package io.snyk.plugin.events

import com.intellij.util.messages.Topic

interface SnykTaskQueueListener {
    companion object {
        val TASK_QUEUE_TOPIC =
            Topic.create("Snyk Task Queue", SnykTaskQueueListener::class.java)
    }

    fun stopped(wasOssRunning: Boolean = false, wasSnykCodeRunning: Boolean = false, wasIacRunning: Boolean = false)
}
