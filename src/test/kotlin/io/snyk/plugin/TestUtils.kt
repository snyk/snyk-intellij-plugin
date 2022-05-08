package io.snyk.plugin

import com.intellij.util.io.RequestBuilder
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.snyk.plugin.services.download.CliDownloader
import io.snyk.plugin.services.download.HttpRequestHelper

/** low level avoiding download the CLI file */
fun mockCliDownload() {
    val requestBuilderMockk = mockk<RequestBuilder>(relaxed = true)
    justRun { requestBuilderMockk.saveToFile(any(), any()) }
    mockkObject(HttpRequestHelper)
    every { HttpRequestHelper.createRequest(CliDownloader.LATEST_RELEASE_DOWNLOAD_URL) } returns requestBuilderMockk
}
