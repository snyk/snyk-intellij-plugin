package io.snyk.plugin

import java.io.File
import java.util

import io.snyk.plugin.client.{CliDownloader, ConsoleCommandRunner, Platform}
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
  def testGetLatestReleasesInformation(): Unit = {
    SnykPluginState.removeForProject(currentProject)

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    snykPluginState.cliClient.setConsoleCommandRunner(new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "command not found"
      }
    })

    val cliFile = new File(snykPluginState.pluginPath, Platform.current.snykWrapperFileName)

    if (cliFile.exists()) {
      cliFile.delete()
    }

    val maybeLatestReleaseInfo = CliDownloader(snykPluginState).requestLatestReleasesInformation

    assertTrue(maybeLatestReleaseInfo.isDefined)
  }

  @Test
  def testDownloadLatestCliRelease(): Unit = {
    SnykPluginState.removeForProject(currentProject)

    val snykPluginState = SnykPluginState.newInstance(currentProject)

    snykPluginState.cliClient.setConsoleCommandRunner(new ConsoleCommandRunner() {
      override def execute(commands: util.ArrayList[String], workDirectory: String): String = {
        "Command not found"
      }
    })

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
