package io.snyk.plugin.services.download

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getSnykCliDownloaderService
import io.snyk.plugin.resetSettings
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import java.io.IOException

class CliDownloaderErrorHandlerIntegTest : LightPlatformTestCase() {
  private lateinit var progressManager: ProgressManager
  private lateinit var cliDownloaderServiceMock: SnykCliDownloaderService
  private lateinit var cliDownloaderMock: CliDownloader
  private lateinit var projectSpy: Project
  private lateinit var cut: CliDownloaderErrorHandler
  private lateinit var indicator: EmptyProgressIndicator

  override fun setUp() {
    super.setUp()
    clearAllMocks()
    resetSettings(project)
    mockkObject(SnykBalloonNotificationHelper)

    mockkStatic(ProgressManager::class)
    progressManager = spyk(ProgressManager.getInstance())
    every { ProgressManager.getInstance() } returns progressManager

    cliDownloaderServiceMock = mockk()
    mockkStatic("io.snyk.plugin.UtilsKt")
    every { getSnykCliDownloaderService() } returns cliDownloaderServiceMock
    cliDownloaderMock = mockk()
    projectSpy = spyk(project)
    cut = CliDownloaderErrorHandler()
    indicator = EmptyProgressIndicator()
    every { projectSpy.getService(SnykCliDownloaderService::class.java) } returns
      cliDownloaderServiceMock
    every { cliDownloaderServiceMock.downloader } returns cliDownloaderMock
  }

  override fun tearDown() {
    unmockkAll()
    resetSettings(project)
    super.tearDown()
  }

  fun testHandleIOExceptionShouldRetryDownloadAndShowBalloonIfItFails() {
    val e = IOException("ignore me, I'm just a test exception and expected")
    every { cliDownloaderMock.expectedSha(any()) } returns "testVersion"
    every { cliDownloaderMock.downloadFile(any(), any(), any()) } throws e

    cut.handleIOException(e, "testVersion", indicator, projectSpy)

    verify(exactly = 1) { cliDownloaderMock.downloadFile(any(), any(), any()) }
    verify(exactly = 1) { progressManager.run(any<Task.Backgroundable>()) }
    verify(exactly = 1) {
      SnykBalloonNotificationHelper.showError(
        cut.getNetworkErrorNotificationMessage(e),
        projectSpy,
        any(),
        any(),
      )
    }
  }

  fun testHandleChecksumVerificationExceptionShouldRetryDownloadAndShowBalloonIfItFails() {
    val e = ChecksumVerificationException("ignore me, I'm just a test exception and expected")
    every { cliDownloaderMock.expectedSha(any()) } returns "testVersion"
    every { cliDownloaderMock.downloadFile(any(), any(), any()) } throws e

    cut.handleChecksumVerificationException(e, "testVersion", indicator, projectSpy)

    verify(exactly = 1) { cliDownloaderMock.downloadFile(any(), any(), any()) }
    verify(exactly = 1) { progressManager.run(any<Task.Backgroundable>()) }
    verify(exactly = 1) {
      SnykBalloonNotificationHelper.showError(
        cut.getChecksumFailedNotificationMessage(e),
        projectSpy,
        any(),
        any(),
      )
    }
  }
}
