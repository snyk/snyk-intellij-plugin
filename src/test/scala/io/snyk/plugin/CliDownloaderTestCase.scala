package io.snyk.plugin

import java.io.File
import java.time.LocalDate
import java.util

import io.snyk.plugin.client.{CliDownloader, ConsoleCommandRunner, Platform}
import io.snyk.plugin.ui.settings.SnykApplicationSettingsStateService
import io.snyk.plugin.ui.state.SnykPluginState

import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals

import scala.io.{Codec, Source}

class CliDownloaderTestCase extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)

    resetSettings()
  }

  @Test
  def testCliSilentAutoUpdate(): Unit = {
    snykPluginState.cliClient.setConsoleCommandRunner(new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "command not found"
      }
    })

    val currentDate = LocalDate.now()

    applicationSettingsStateService.setCliVersion("1.342.2")
    applicationSettingsStateService.setLastCheckDate(currentDate.minusDays(5))

    val cliDownloader = CliDownloader(snykPluginState)

    val cliFile = cliDownloader.cliFile

    if (!cliFile.exists()) {
      cliFile.createNewFile()
    }

    cliDownloader.cliSilentAutoUpdate()

    val settingsStateService = applicationSettingsStateService

    assertTrue(cliDownloader.cliFile.exists())
    assertEquals(currentDate, settingsStateService.getLastCheckDate)
    assertEquals(cliDownloader.latestReleaseInfo.get.tagName.get,
                 "v" + settingsStateService.getCliVersion)

    cliFile.delete()
  }

  @Test
  def testCliSilentAutoUpdateWhenPreviousUpdateInfoIsNull(): Unit = {
    snykPluginState.cliClient.setConsoleCommandRunner(new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "command not found"
      }
    })

    val currentDate = LocalDate.now()

    applicationSettingsStateService.setCliVersion(null)
    applicationSettingsStateService.setLastCheckDate(null)

    val cliDownloader = CliDownloader(snykPluginState)

    val cliFile = cliDownloader.cliFile

    if (!cliFile.exists()) {
      cliFile.createNewFile()
    }

    cliDownloader.cliSilentAutoUpdate()

    assertTrue(cliDownloader.cliFile.exists())

    assertEquals(currentDate, applicationSettingsStateService.getLastCheckDate)
    assertEquals(cliDownloader.latestReleaseInfo.get.tagName.get,
      "v" + applicationSettingsStateService.getCliVersion)

    cliFile.delete()
  }

  @Test
  def testIsNewVersionAvailable(): Unit = {
    applicationSettingsStateService.setLastCheckDate(LocalDate.now())

    SnykPluginState.mockForProject(currentProject, snykPluginState)

    assertTrue(CliDownloader(snykPluginState).isNewVersionAvailable("1.342.2", "1.345.1"))
    assertTrue(CliDownloader(snykPluginState).isNewVersionAvailable("1.342.2", "2.345.1"))
    assertTrue(CliDownloader(snykPluginState).isNewVersionAvailable("1.345.2", "2.342.9"))

    assertFalse(CliDownloader(snykPluginState).isNewVersionAvailable("2.342.2", "1.342.1"))
    assertFalse(CliDownloader(snykPluginState).isNewVersionAvailable("1.343.1", "1.342.2"))
    assertFalse(CliDownloader(snykPluginState).isNewVersionAvailable("1.342.2", "1.342.1"))

    assertFalse(CliDownloader(snykPluginState).isNewVersionAvailable("1.342.2", "1.342.2"))
  }

  @Test
  def testCheckIsFourDaysPassedSinceLastCheck(): Unit = {
    val todayDate = LocalDate.now()
    val lastCheckDate = todayDate.minusDays(4)

    applicationSettingsStateService.setLastCheckDate(lastCheckDate)

    assertTrue(CliDownloader(snykPluginState).isFourDaysPassedSinceLastCheck)
  }

  @Test
  def testCheckCliInstalledByPlugin(): Unit = {
    setupConsoleCliNotExists()

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    val cliDownloader = CliDownloader(snykPluginState)

    val cliFile = cliDownloader.cliFile

    if (!cliFile.exists()) {
      cliFile.createNewFile()
    }

    assertTrue(cliDownloader.isCliInstalledByPlugin)

    cliFile.delete()
  }

  @Test
  def testGetLastCheckDateFromSettings(): Unit = {
    val lastCheckDate = LocalDate.of(2020, 6, 19)

    applicationSettingsStateService.setLastCheckDate(lastCheckDate)

    val cliDownloader = CliDownloader(snykPluginState)

    assertEquals(lastCheckDate, cliDownloader.lastCheckDate)
  }

  @Test
  def testGetLatestReleasesInformation(): Unit = {
    setupConsoleCliNotExists()

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    val cliFile = new File(snykPluginState.pluginPath, Platform.current.snykWrapperFileName)

    if (cliFile.exists()) {
      cliFile.delete()
    }

    val maybeLatestReleaseInfo = CliDownloader(snykPluginState).requestLatestReleasesInformation

    assertTrue(maybeLatestReleaseInfo.isDefined)
  }

  @Test
  def testDownloadLatestCliRelease(): Unit = {
    setupConsoleCliNotExists()

    val snykPluginState = SnykPluginState.newInstance(currentProject)
    val cliDownloader = CliDownloader(snykPluginState)

    val cliFile = cliDownloader.cliFile

    if (!cliFile.exists()) {
      cliFile.createNewFile()
    }

    cliDownloader.downloadLatestRelease()

    val downloadedFile = new File(snykPluginState.pluginPath, Platform.current.snykWrapperFileName)

    assertTrue(downloadedFile.exists())
    assertEquals(cliDownloader.latestReleaseInfo.get.tagName.get,
                 "v" + snykPluginState.allIdeSettings.cliVersion)

    downloadedFile.delete()
  }

  private def applicationSettingsStateService = {
    SnykApplicationSettingsStateService.getInstance()
  }

  private def resetSettings(): Unit = {
    applicationSettingsStateService.setCliVersion(null)
    applicationSettingsStateService.setLastCheckDate(null)
  }

  private def snykPluginState: SnykPluginState = SnykPluginState.getInstance(currentProject)
}
