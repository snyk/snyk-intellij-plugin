package io.snyk.plugin.client

import java.io.File
import java.net.URL
import java.lang.String.format
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Objects.isNull

import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.util.io.HttpRequests
import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.snyk.plugin.ui.state.SnykPluginState
import monix.execution.atomic.Atomic

import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}

sealed class CliDownloader(pluginState: SnykPluginState) {

  private val latestReleaseInfoAtomic: Atomic[Option[LatestReleaseInfo]] = Atomic(Option.empty[LatestReleaseInfo])

  def isNewVersionAvailable(currentCliVersion: String, newCliVersion: String): Boolean = {
    if (isNull(currentCliVersion ) || currentCliVersion.isEmpty) {
      return true
    }

    @scala.annotation.tailrec
    def checkIsNewVersionAvailable(
      currentCliVersionNumbers: Array[String],
      newCliVersionNumbers: Array[String]): Boolean =

      if (currentCliVersionNumbers.length > 0 && newCliVersionNumbers.length > 0) {
        val newVersionNumber = newCliVersionNumbers.head.toInt
        val currentVersionNumber = currentCliVersionNumbers.head.toInt

        newVersionNumber compareTo currentVersionNumber match {
          case 0 => checkIsNewVersionAvailable(currentCliVersionNumbers.tail, newCliVersionNumbers.tail)
          case compareResult: Int => if (compareResult > 0) true else false
        }
      } else {
        false
      }

    checkIsNewVersionAvailable(currentCliVersion.split('.'), newCliVersion.split('.'))
  }

  def isFourDaysPassedSinceLastCheck: Boolean = {
    val previousDate = this.lastCheckDate

    if (isNull(previousDate)) {
      return true
    }

    ChronoUnit.DAYS.between(previousDate, LocalDate.now) >= CliDownloader.NumberOfDaysBetweenReleaseCheck
  }

  def isCliInstalledByPlugin: Boolean = {
    val cliClient = pluginState.cliClient

    !cliClient.checkIsCliInstalledManuallyByUser() && cliClient.checkIsCliInstalledAutomaticallyByPlugin()
  }

  def cliSilentAutoUpdate(): Unit = {
    if (isCliInstalledByPlugin && isFourDaysPassedSinceLastCheck) {
      val releaseInfo = requestLatestReleasesInformation

      val intelliJSettingsState = pluginState.intelliJSettingsState

      if (releaseInfo.isDefined
        && releaseInfo.get.tagName.isDefined
        && isNewVersionAvailable(intelliJSettingsState.cliVersion, cliVersionNumbers(releaseInfo.get.tagName.get))) {

        downloadLatestRelease()

        intelliJSettingsState.setLastCheckDate(LocalDate.now())
      }
    }
  }

  def lastCheckDate: LocalDate = pluginState.intelliJSettingsState.lastCheckDate

  def requestLatestReleasesInformation: Option[LatestReleaseInfo] = {
    val jsonResponseStr = Try(Source.fromURL(CliDownloader.LatestReleasesUrl)(Codec.UTF8)) match {
      case Success(value) => value.mkString
      case Failure(_) => ""
    }

    latestReleaseInfoAtomic := decode[LatestReleaseInfo](jsonResponseStr).toOption

    latestReleaseInfoAtomic.get
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

          val cliVersionOption = latestReleasesInfo.get.tagName

          cliVersionOption match {
            case Some(cliVersion) =>
              val url = new URL(
                format(CliDownloader.LatestReleaseDownloadUrl,
                       cliVersion,
                       snykWrapperFileName)).toString

              val cliFile = CliDownloader.this.cliFile

              if (cliFile.exists()) {
                cliFile.delete()
              }

              HttpRequests
                .request(url)
                .productNameAsUserAgent()
                .saveToFile(cliFile, indicator)

              cliFile.setExecutable(true)

              pluginState.intelliJSettingsState.setCliVersion(cliVersionNumbers(cliVersion))
              pluginState.intelliJSettingsState.setLastCheckDate(LocalDate.now())
            case _ =>
          }
        } finally {
          indicator.popState()
        }
      }
    })
  }

  def cliFile: File = new File(pluginState.pluginPath, Platform.current.snykWrapperFileName)

  def latestReleaseInfo: Option[LatestReleaseInfo] = latestReleaseInfoAtomic.get

  /**
    * Clear version number: v1.143.1 => 1.143.1

    * @param sourceVersion - source cli version string
    *
    * @return String
    */
  private def cliVersionNumbers(sourceVersion: String): String = sourceVersion.substring(1, sourceVersion.length)
}

object CliDownloader {
  val LatestReleasesUrl = "https://api.github.com/repos/snyk/snyk/releases/latest"
  val LatestReleaseDownloadUrl = "https://github.com/snyk/snyk/releases/download/%s/%s"

  val NumberOfDaysBetweenReleaseCheck = 4

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