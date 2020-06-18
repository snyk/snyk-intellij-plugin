package io.snyk.plugin

import java.io.File

import io.snyk.plugin.client.{CliDownloader, Platform}
import io.snyk.plugin.ui.state.SnykPluginState
import org.junit.Test
import org.junit.Assert.assertTrue

import scala.io.{Codec, Source}

class CliDownloaderTestCase extends AbstractMavenTestCase() {

  override protected def setUp(): Unit = {
    super.setUp()

    val projectXmlStr = Source.fromResource("sample-pom.xml", getClass.getClassLoader)(Codec.UTF8).mkString

    importProject(projectXmlStr)
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
}
