package io.snyk.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import com.intellij.util.io.RequestBuilder
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.services.SnykProjectSettingsStateService
import io.snyk.plugin.services.download.CliDownloader
import io.snyk.plugin.services.download.HttpRequestHelper

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

/** low level avoiding download the CLI file */
fun mockCliDownload() {
    val requestBuilderMockk = mockk<RequestBuilder>(relaxed = true)
    justRun { requestBuilderMockk.saveToFile(any(), any()) }
    mockkObject(HttpRequestHelper)
    every { HttpRequestHelper.createRequest(CliDownloader.LATEST_RELEASE_DOWNLOAD_URL) } returns requestBuilderMockk
}
