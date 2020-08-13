package io.snyk.plugin

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import io.snyk.plugin.settings.SnykApplicationSettingsStateService

fun getApplicationSettingsStateService(): SnykApplicationSettingsStateService =
    service<SnykApplicationSettingsStateService>()

fun getPluginPath() = PathManager.getPluginsPath() + "/snyk-intellij-plugin"
