package io.snyk.plugin.events

import com.intellij.util.messages.Topic

interface SnykSettingsListener {
    companion object {
        val SNYK_SETTINGS_TOPIC =
            Topic.create("Snyk Settings changed", SnykSettingsListener::class.java)
    }

    fun settingsChanged()

}
