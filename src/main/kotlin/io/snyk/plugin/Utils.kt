package io.snyk.plugin

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.snyk.plugin.cli.Platform
import io.snyk.plugin.cli.SnykCliService
import io.snyk.plugin.settings.SnykApplicationSettingsStateService
import java.io.File

fun getCli(project: Project): SnykCliService = project.service()

fun getCliFile() = File(getPluginPath(), Platform.current().snykWrapperFileName)

fun getApplicationSettingsStateService(): SnykApplicationSettingsStateService = service()

fun getPluginPath() = PathManager.getPluginsPath() + "/snyk-intellij-plugin"

val <T> List<T>.tail: List<T>
    get() = drop(1)

val <T> List<T>.head: T
    get() = first()
