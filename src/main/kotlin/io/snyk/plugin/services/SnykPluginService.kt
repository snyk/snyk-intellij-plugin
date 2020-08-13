package io.snyk.plugin.services

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service
class SnykPluginService(val project: Project) {

    fun getPluginPath() = PathManager.getPluginsPath() + "/snyk-intellij-plugin"
}
