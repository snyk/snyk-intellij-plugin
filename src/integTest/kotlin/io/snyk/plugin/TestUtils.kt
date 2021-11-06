package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykProjectSettingsStateService

fun setupDummyCliFile() {
    val cliFile = getCliFile()

    if (!cliFile.exists()) {
        if (!cliFile.parentFile.exists()) cliFile.mkdirs()
        cliFile.createNewFile()
    }
}

fun removeDummyCliFile() {
    val cliFile = getCliFile()
    if (cliFile.exists()) {
        cliFile.delete()
    }
}

fun resetSettings(project: Project?) {
    val application = ApplicationManager.getApplication()
    application.replaceService(
        SnykApplicationSettingsStateService::class.java,
        SnykApplicationSettingsStateService(),
        application
    )
    project?.replaceService(
        SnykProjectSettingsStateService::class.java,
        SnykProjectSettingsStateService(),
        project
    )
}
