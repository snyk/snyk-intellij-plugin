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
import io.snyk.plugin.services.download.HttpRequestHelper
import io.snyk.plugin.services.download.HttpRequestHelper.createRequest
import snyk.common.lsp.LanguageServerWrapper
import java.nio.file.Path

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
    try {
        LanguageServerWrapper.getInstance(project!!).shutdown()
    } catch (_: Exception) {
        // ignore
    }
}

/** low level avoiding download the CLI file */
fun mockCliDownload(): RequestBuilder {
    val requestBuilderMockk = mockk<RequestBuilder>(relaxed = true)
    mockkObject(HttpRequestHelper)
    every { createRequest(any()) } returns requestBuilderMockk
    // release version
    every { requestBuilderMockk.readString() } returns "1.2.3"
    // download
    justRun { requestBuilderMockk.saveToFile(any<Path>(), any()) }
    return requestBuilderMockk
}
