package io.snyk.plugin

import java.io.File
import java.time.LocalDate

import com.intellij.openapi.project.Project
import io.snyk.plugin.client.{CliClient, CliDownloader, Platform}
import io.snyk.plugin.depsource.DepTreeProvider
import io.snyk.plugin.depsource.externalproject.ExternProj
import io.snyk.plugin.metrics.SegmentApi
import io.snyk.plugin.ui.settings.SnykPersistentStateComponent
import io.snyk.plugin.ui.state.SnykPluginState
import monix.reactive.Observable
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals

import scala.io.{Codec, Source}

class CliDownloaderTestCase extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)
  }

  @Test
  def testCheckIsFourDaysPassedSinceLastCheck(): Unit = {
    val todayDate = LocalDate.now()
    val lastCheckDate = todayDate.minusDays(4)

    val mockPluginState = mockSnykPluginState(lastCheckDate)

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

    assertTrue(cliDownloader.checkCliInstalledByPlugin())

    cliFile.delete()
  }

  @Test
  def testGetLastCheckDateFromSettings(): Unit = {
    val lastCheckDate = LocalDate.of(2020, 6, 19)

    SnykPluginState.mockForProject(currentProject, mockSnykPluginState(lastCheckDate))

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

    val cliFile = new File(snykPluginState.pluginPath, Platform.current.snykWrapperFileName)

    if (cliFile.exists()) {
      cliFile.delete()
    }

    CliDownloader(snykPluginState).downloadLatestRelease()

    val downloadedFile = new File(snykPluginState.pluginPath, Platform.current.snykWrapperFileName)

    assertTrue(downloadedFile.exists())

    downloadedFile.delete()
  }

  private def mockSnykPluginState(date: LocalDate): SnykPluginState = new SnykPluginState() {
    override def getProject: Project = currentProject

    override def cliClient: CliClient = ???

    override def segmentApi: SegmentApi = ???

    override def externProj: ExternProj = ???

    override protected def depTreeProvider: DepTreeProvider = ???

    override def mavenProjectsObservable: Observable[Seq[String]] = ???

    override def gradleProjectsObservable: Observable[Seq[String]] = ???

    override def intelliJSettingsState: SnykPersistentStateComponent = SnykPersistentStateComponent(lastCheckDate = date)
  }
}
