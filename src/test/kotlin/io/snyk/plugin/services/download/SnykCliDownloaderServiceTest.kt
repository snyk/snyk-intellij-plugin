package io.snyk.plugin.services.download

import com.intellij.notification.Notification
import com.intellij.openapi.project.Project
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.getCliFile
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.services.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SnykCliDownloaderServiceTest {
  private val settingsStateService: SnykApplicationSettingsStateService = mockk(relaxed = true)
  private val project: Project = mockk(relaxed = true)

  @Before
  fun setUp() {
    unmockkAll()
    mockkStatic("io.snyk.plugin.UtilsKt")
    mockkObject(SnykBalloonNotificationHelper)
    every { pluginSettings() } returns settingsStateService
    every { SnykBalloonNotificationHelper.showWarn(any(), any(), *anyVararg()) } returns
      mockk<Notification>()
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `requestLatestReleasesInformation should returns null if updates disabled`() {
    val cut = SnykCliDownloaderService()
    every { settingsStateService.manageBinariesAutomatically } returns false

    assertNull(cut.requestLatestReleasesInformation())

    verify { settingsStateService.manageBinariesAutomatically }
    confirmVerified(settingsStateService)
  }

  @Test
  fun `verifyCliIntegrity returns true when manageBinariesAutomatically is false`() {
    val cut = SnykCliDownloaderService()
    every { settingsStateService.manageBinariesAutomatically } returns false

    assertTrue(cut.verifyCliIntegrity(project))
  }

  @Test
  fun `verifyCliIntegrity returns false when storedSha256 is null`() {
    val cut = SnykCliDownloaderService()
    every { settingsStateService.manageBinariesAutomatically } returns true
    every { settingsStateService.cliSha256 } returns null

    assertFalse(cut.verifyCliIntegrity(project))
  }

  @Test
  fun `verifyCliIntegrity returns false when storedSha256 is blank`() {
    val cut = SnykCliDownloaderService()
    every { settingsStateService.manageBinariesAutomatically } returns true
    every { settingsStateService.cliSha256 } returns ""

    assertFalse(cut.verifyCliIntegrity(project))
  }

  @Test
  fun `verifyCliIntegrity returns false when CLI file does not exist`() {
    val cut = SnykCliDownloaderService()
    every { settingsStateService.manageBinariesAutomatically } returns true
    every { settingsStateService.cliSha256 } returns "validsha256hash"

    val nonExistentFile = File("/non/existent/path/snyk-cli")
    every { getCliFile() } returns nonExistentFile

    // Should return false to trigger download when file doesn't exist
    assertFalse(cut.verifyCliIntegrity(project))
  }

  @Test
  fun `verifyCliIntegrity returns true when CLI exists and checksum matches`() {
    val cut = SnykCliDownloaderService()
    val tempFile = File.createTempFile("snyk-cli-test", ".exe")
    try {
      tempFile.writeText("test content")
      val expectedSha = cut.downloader.calculateSha256(tempFile.readBytes())

      every { settingsStateService.manageBinariesAutomatically } returns true
      every { settingsStateService.cliSha256 } returns expectedSha
      every { getCliFile() } returns tempFile

      assertTrue(cut.verifyCliIntegrity(project))
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun `verifyCliIntegrity returns false when CLI exists but checksum does not match`() {
    val cut = SnykCliDownloaderService()
    val tempFile = File.createTempFile("snyk-cli-test", ".exe")
    try {
      tempFile.writeText("test content")

      every { settingsStateService.manageBinariesAutomatically } returns true
      every { settingsStateService.cliSha256 } returns "wrongchecksum"
      every { getCliFile() } returns tempFile

      assertFalse(cut.verifyCliIntegrity(project))
    } finally {
      tempFile.delete()
    }
  }
}
