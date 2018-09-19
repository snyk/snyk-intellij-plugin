package io.snyk.plugin
package client

import java.nio.file.{Path, Paths}
import java.util.UUID

import io.circe._
import io.circe.parser._
import io.circe.derivation._
import io.circe.syntax._
import java.io.PrintWriter
import java.net.URI

import com.softwaremill.sttp.{HttpURLConnectionBackend, Id, SttpBackend, SttpBackendOptions, Uri, sttp}

import scala.concurrent.{Future, Promise}
import scala.io.Source
import scala.util.{Success, Try}
import monix.execution.Scheduler.Implicits.{global => scheduler}
import com.intellij.openapi.diagnostic.Logger

import scala.concurrent.duration._
import scala.util.control.NonFatal

object SnykConfig {
  def withResource[T <: AutoCloseable, V](r: => T)(f: T => V): V = {
    val resource: T = r
    require(resource != null, "resource is null")
    var exception: Throwable = null
    try {
      f(resource)
    } catch {
      case NonFatal(e) =>
        exception = e
        throw e
    } finally {
      if (exception != null) {
        try {
          resource.close()
        } catch {
          case NonFatal(suppressed) => exception.addSuppressed(suppressed)
        }
      } else {
        resource.close()
      }
    }
  }


  val log = Logger.getInstance(this.getClass)

  implicit val decoder: Decoder[SnykConfig] = deriveDecoder[SnykConfig]
  implicit val encoder: Encoder[SnykConfig] = deriveEncoder[SnykConfig]

  def defaultConfigFile: Path = Paths.get(System.getProperty("user.home"), ".config/configstore/snyk.json")

  def defaultEndpoint: String = "http://snyk.io/api"
  def defaultTimeout: FiniteDuration = 5.minutes

  def default: Try[SnykConfig] = forPath(defaultConfigFile)

  def forPath(configFilePath: Path): Try[SnykConfig] =
    Try { Source.fromFile(configFilePath.toFile).mkString } flatMap {
      str => decode[SnykConfig](str).toTry
    }

  private[this] def endpointFor(configFilePath: Path): URI =
    new URI(forPath(configFilePath).toOption.flatMap(_.endpoint) getOrElse defaultEndpoint)

  def auth(
    openBrowserFn: URI => Unit,  // e.g. IntelliJ's  BrowserUtil.browse
    configFilePath: Path = defaultConfigFile
  ): Future[SnykConfig] = {
    val endpointUri = endpointFor(configFilePath)
    val newToken = UUID.randomUUID()
    val loginUri = endpointUri.resolve(s"/login?token=$newToken")

    log.debug(s"Will auth at $loginUri")

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend(
      options = SttpBackendOptions.connectionTimeout(5.seconds)
    )

    openBrowserFn(loginUri)

    new AuthPoller(configFilePath, newToken).run()
  }

  class AuthPoller(configFilePath: Path, token: UUID) {
    val endpointUri = endpointFor(configFilePath)
    val pollUri = endpointUri.resolve(s"/api/verify/callback")

    val body: Json = Map("token" -> token.toString).asJson

    private def sendRequest() = sttp.post(Uri(pollUri))
      .header("content-type", "application/json")
      .header("user-agent", "Needle/2.1.1 (Node.js v8.11.3; linux x64)")
      .body(body.noSpaces)
      .readTimeout(5.seconds)
      .send()

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    def run(): Future[SnykConfig] = {
      val p = Promise[SnykConfig]
      queueNext(30, p)
      p.future
    }

    private def queueNext(remainingAttempts: Int, p: Promise[SnykConfig]): Unit = {
      log.debug(s"queuing auth poll, remaining attempts: $remainingAttempts")
      scheduler.scheduleOnce(1.second) { poll(remainingAttempts, p) }
    }

    private def poll(remainingAttempts: Int, p: Promise[SnykConfig]): Unit = {
      if(remainingAttempts <= 0) {
        p.failure(new RuntimeException("Auth token expired"))
      } else {
        log.debug(s"Auth poll attempt $remainingAttempts Sending to $pollUri... ${body.noSpaces}" )

        val tryResponse = Try { sendRequest() }
        tryResponse recover { case x => log.warn(x) }

        val optAuthToken = tryResponse.toOption flatMap { response =>
          val safeBody = response.body

          safeBody.left foreach { err =>
            log.warn(err)
          }

          safeBody.right.toOption flatMap { bodyText =>
            log.debug(s"auth response was: ${response.code} $bodyText")

            if (response.is200) {
              log.debug(s"poll body: $bodyText")

              val optApiToken = for {
                bodyJson <- parse(bodyText).toOption
                bodyJsonObj <- bodyJson.asObject
                okJson <- bodyJsonObj("ok")
                ok <- okJson.asBoolean
                if ok
                apiTokenJson <- bodyJsonObj("api")
                apiToken <- apiTokenJson.asString
              } yield apiToken

              log.debug(s"api token: $optApiToken")

              optApiToken
            } else None
          }
        }

        optAuthToken match {
          case Some(t) =>
            p complete Success(
              SnykConfig(UUID.fromString(t), Some(endpointUri.toString), None, None, None)
            )
          case None => queueNext(remainingAttempts - 1, p)
        }
      }
    }
  }
}

import SnykConfig._

case class SnykConfig(
  api: UUID,
  endpoint: Option[String],
  timeout: Option[Long],
  org: Option[String],
  `disable-analytics`: Option[String]
) {
  val log = Logger.getInstance(this.getClass)

  def endpointOrDefault: String = endpoint getOrElse defaultEndpoint

  def disableAnalytics: Boolean =
    `disable-analytics`.flatMap(str => Try(str.toBoolean).toOption) getOrElse false

  private def normalised: SnykConfig = SnykConfig(
    this.api,
    this.endpoint.filterNot(_ == defaultEndpoint),
    this.timeout.filterNot(_.seconds == defaultTimeout),
    this.org.filterNot(_.isEmpty),
    this.`disable-analytics`
  )

  private val jsonPrinter = Printer.spaces4.copy(dropNullValues = true)

  def writeToFile(path: Path = defaultConfigFile): Try[Unit] = Try {
    val file = path.toFile
    val parentFile = file.getParentFile
    log.debug(s"parent dir is $parentFile")
    parentFile.mkdirs()
    withResource(new PrintWriter(path.toFile)) {
      _.write(jsonPrinter.pretty(normalised.asJson))
    }
  }

  def timeoutOrDefault: FiniteDuration = timeout.map(_.seconds) getOrElse defaultTimeout
}



