package io.snyk.plugin.client

import java.io.File
import java.net.URL
import java.lang.String.format
import java.time.LocalDate

import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.util.io.HttpRequests
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.snyk.plugin.ui.state.SnykPluginState

import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

sealed class CliDownloader(pluginState: SnykPluginState) {

  def checkCliInstalledByPlugin(): Boolean = {
    val cliClient = pluginState.cliClient

    !cliClient.checkIsCliInstalledManuallyByUser() && cliClient.checkIsCliInstalledAutomaticallyByPlugin()
  }

  def cliSilentAutoUpdate(): Unit = {
    if (checkCliInstalledByPlugin()) {

    }
  }

  def lastCheckDate: LocalDate = {
    val persistentStateComponent = pluginState.intelliJSettingsState

    persistentStateComponent.lastCheckDate
  }

  def requestLatestReleasesInformation: Option[LatestReleaseInfo] = {
    val jsonResponseStr = Try(Source.fromURL(CliDownloader.LatestReleasesUrl)(Codec.UTF8)) match {
      case Success(value) => value.mkString
      case Failure(_) => ""
    }

    decode[LatestReleaseInfo](jsonResponseStr).toOption
  }

  def downloadLatestRelease(): Unit = {
    val latestReleasesInfo = requestLatestReleasesInformation

    ProgressManager.getInstance().run(new Task.Backgroundable(pluginState.getProject, "Download", true) {
      override def run(indicator: ProgressIndicator): Unit = {
        indicator.setIndeterminate(true)
        indicator.pushState()

        try {
          indicator.setText("Downloading latest Snyk CLI release...")

          val snykWrapperFileName = Platform.current.snykWrapperFileName

          val url = new URL(
            format(CliDownloader.LatestReleaseDownloadUrl,
                   latestReleasesInfo.get.tagName.get,
                   snykWrapperFileName)).toString

          val cliFile = CliDownloader.this.cliFile

          HttpRequests
            .request(url)
            .productNameAsUserAgent()
            .saveToFile(cliFile, indicator)

          cliFile.setExecutable(true)
        } finally {
          indicator.popState()
        }
      }
    })
  }

  def cliFile: File = new File(pluginState.pluginPath, Platform.current.snykWrapperFileName)
}

object CliDownloader {
  val LatestReleasesUrl = "https://api.github.com/repos/snyk/snyk/releases/latest"
  val LatestReleaseDownloadUrl = "https://github.com/snyk/snyk/releases/download/%s/%s"

  def apply(pluginState: SnykPluginState): CliDownloader = new CliDownloader(pluginState)
}

case class LatestReleaseInfo(
  id: Option[Long],
  url: Option[String],
  name: Option[String],
  tagName: Option[String]
)

object LatestReleaseInfo {
  implicit val encoder: Encoder[LatestReleaseInfo] =
    Encoder.forProduct4("id", "url", "name", "tag_name")(LatestReleaseInfo.unapply(_).get)
  implicit val decoder: Decoder[LatestReleaseInfo] =
    Decoder.forProduct4("id", "url", "name", "tag_name")(LatestReleaseInfo.apply)
}