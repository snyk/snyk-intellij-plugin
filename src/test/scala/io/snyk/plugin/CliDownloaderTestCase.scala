package io.snyk.plugin

import java.io.File
import java.time.LocalDate
import java.util

import com.intellij.openapi.project.Project
import io.snyk.plugin.client.{CliClient, CliDownloader, ConsoleCommandRunner, Platform}
import io.snyk.plugin.depsource.DepTreeProvider
import io.snyk.plugin.depsource.externalproject.ExternProj
import io.snyk.plugin.metrics.SegmentApi
import io.snyk.plugin.ui.settings.SnykPersistentStateComponent
import io.snyk.plugin.ui.state.SnykPluginState
import monix.reactive.Observable
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
  }

  @Test
  def testCliSilentAutoUpdate(): Unit = {
    val consoleCommandRunner = new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "command not found"
      }
    }

    val currentDate = LocalDate.now()

    val mockPluginState = mockSnykPluginState(
      cliVersion = "1.342.2",
      lastCheckDate = currentDate.minusDays(5),
      consoleCommandRunner = consoleCommandRunner)

    SnykPluginState.mockForProject(currentProject, mockPluginState)

    val cliDownloader = CliDownloader(mockPluginState)

    val cliFile = cliDownloader.cliFile

    if (!cliFile.exists()) {
      cliFile.createNewFile()
    }

    cliDownloader.cliSilentAutoUpdate()

    assertTrue(cliDownloader.cliFile.exists())
    assertEquals(currentDate, mockPluginState.intelliJSettingsState.lastCheckDate)
    assertEquals(cliDownloader.latestReleaseInfo.get.tagName.get,
                 "v" + mockPluginState.intelliJSettingsState.cliVersion)

    cliFile.delete()
  }

  @Test
  def testCliSilentAutoUpdateWhenPreviousUpdateInfoIsNull(): Unit = {
    val consoleCommandRunner = new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "command not found"
      }
    }

    val currentDate = LocalDate.now()

    val mockPluginState = mockSnykPluginState(
      cliVersion = null,
      lastCheckDate = null,
      consoleCommandRunner = consoleCommandRunner)

    SnykPluginState.mockForProject(currentProject, mockPluginState)

    val cliDownloader = CliDownloader(mockPluginState)

    val cliFile = cliDownloader.cliFile

    if (!cliFile.exists()) {
      cliFile.createNewFile()
    }

    cliDownloader.cliSilentAutoUpdate()

    assertTrue(cliDownloader.cliFile.exists())
    assertEquals(currentDate, mockPluginState.intelliJSettingsState.lastCheckDate)
    assertEquals(cliDownloader.latestReleaseInfo.get.tagName.get,
      "v" + mockPluginState.intelliJSettingsState.cliVersion)

    cliFile.delete()
  }

  @Test
  def testIsNewVersionAvailable(): Unit = {
    val mockPluginState = mockSnykPluginState(lastCheckDate = LocalDate.now())

    SnykPluginState.mockForProject(currentProject, mockPluginState)

    assertTrue(CliDownloader(mockPluginState).isNewVersionAvailable("1.342.2", "1.345.1"))
    assertTrue(CliDownloader(mockPluginState).isNewVersionAvailable("1.342.2", "2.345.1"))
    assertTrue(CliDownloader(mockPluginState).isNewVersionAvailable("1.345.2", "2.342.9"))

    assertFalse(CliDownloader(mockPluginState).isNewVersionAvailable("2.342.2", "1.342.1"))
    assertFalse(CliDownloader(mockPluginState).isNewVersionAvailable("1.343.1", "1.342.2"))
    assertFalse(CliDownloader(mockPluginState).isNewVersionAvailable("1.342.2", "1.342.1"))

    assertFalse(CliDownloader(mockPluginState).isNewVersionAvailable("1.342.2", "1.342.2"))
  }

  @Test
  def testCheckIsFourDaysPassedSinceLastCheck(): Unit = {
    val todayDate = LocalDate.now()
    val lastCheckDate = todayDate.minusDays(4)

    val mockPluginState = mockSnykPluginState(lastCheckDate = lastCheckDate)

    SnykPluginState.mockForProject(currentProject, mockPluginState)

    assertTrue(CliDownloader(mockPluginState).isFourDaysPassedSinceLastCheck)
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

    SnykPluginState.mockForProject(currentProject, mockSnykPluginState(lastCheckDate = lastCheckDate))

    val snykPluginState = SnykPluginState.newInstance(currentProject)

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
                 "v" + snykPluginState.intelliJSettingsState.cliVersion)

    downloadedFile.delete()
  }

  private def mockSnykPluginState(
    cliVersion: String = "",
    lastCheckDate: LocalDate = null,
    consoleCommandRunner: ConsoleCommandRunner = new ConsoleCommandRunner
  ): SnykPluginState = new SnykPluginState() {

    private val persistentStateComponent =
      SnykPersistentStateComponent(cliVersion = cliVersion, lastCheckDate = lastCheckDate)

    override def getProject: Project = currentProject

    override def cliClient: CliClient = CliClient.newInstance(config(), consoleCommandRunner, pluginPath)

    override def segmentApi: SegmentApi = ???

    override def externProj: ExternProj = ???

    override protected def depTreeProvider: DepTreeProvider = ???

    override def mavenProjectsObservable: Observable[Seq[String]] = ???

    override def gradleProjectsObservable: Observable[Seq[String]] = ???

    override def intelliJSettingsState: SnykPersistentStateComponent = persistentStateComponent
  }
}
