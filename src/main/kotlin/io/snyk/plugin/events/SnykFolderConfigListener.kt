package io.snyk.plugin.events

import com.intellij.util.messages.Topic

interface SnykFolderConfigListener {
    companion object {
        val SNYK_FOLDER_CONFIG_TOPIC =
            Topic.create("SnykFolderConfigListener", SnykFolderConfigListener::class.java)
    }

    fun folderConfigsChanged(folderConfigsNotEmpty: Boolean)
}
