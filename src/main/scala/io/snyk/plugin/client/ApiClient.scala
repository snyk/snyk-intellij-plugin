package io.snyk.plugin.client

import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import io.snyk.plugin.datamodel.{SnykMavenArtifact, SnykVulnResponse}
import io.snyk.plugin.datamodel.SnykVulnResponse.JsonCodecs._
import com.softwaremill.sttp._
import io.circe.parser.decode
import io.circe.{Json, Printer}

import scala.util.{Failure, Success, Try}


/**
  * Represents the connection to the Snyk servers for the security scan.
  */
sealed trait ApiClient {
  /** Run a scan on the supplied artifact tree */
  def runScan(treeRoot: SnykMavenArtifact): Try[SnykVulnResponse]
  def userInfo(): Try[SnykUserInfo]
  /** For the "standard" client, returns false if we don't have the necessary credentials */
  def isAvailable: Boolean
}

/**
  * An implementation of `ApiClient` that makes a call to the live Snyk API via the supplied credentials
  * Note: `credentials` is by-name, and will be freshly evaluated on each access -
  *       any property depending on it MUST NOT be cached as a `val`
  */
private final class StandardApiClient(credentials: => Try[SnykCredentials]) extends ApiClient {
  val log = Logger.getInstance(this.getClass)

  def isAvailable: Boolean = credentials.isSuccess

  //Not a Map, we want to preserve ordering
  val sysProps: Seq[(String, String)] = Seq(
    "os.name",
    "os.version",
    "os.arch",
    "java.vm.name",
    "java.vm.version",
    "java.vm.vendor",
    "java.runtime.version",
    "user.language",
  ).map(p => p -> System.getProperties.getProperty(p, ""))

  val ideVersion = Try { ApplicationInfo.getInstance.getFullVersion }.toOption getOrElse "undefined"
  val allProps = sysProps :+ ("ide.version" -> ideVersion)
  val allPropsStr = allProps map { case (k,v) => s"$k=$v" } mkString "; "

  val pluginVersion = getClass.getClassLoader match {
    case pcl: PluginClassLoader => PluginManager.getPlugin(pcl.getPluginId).getVersion
    case _ => "undefined"
  }

  val userAgent = s"SnykIdePlugin/$pluginVersion ($allPropsStr)"

  private[this] val stringifyWithoutNulls: Json => String =
    Printer.noSpaces.copy(dropNullValues = true).pretty

  def retry[T](times: Int)(fn: => Try[T]): Try[T] = {
    fn match {
      case Failure(_) if times > 1 => retry(times-1)(fn)
      case f @ Failure(_) => f
      case s @ Success(_) => s
    }
  }

  private def runRaw(treeRoot: SnykMavenArtifact): Try[String] = credentials flatMap { creds =>
    val jsonReq = stringifyWithoutNulls(SnykClientSerialisation.encodeRoot(treeRoot, creds.org))

    log.debug("ApiClient: Built JSON Request")
    log.debug(jsonReq)

    val apiEndpoint = creds.endpointOrDefault
    val apiToken = creds.api

    val uri = uri"$apiEndpoint/v1/vuln/maven"

    val request = sttp.post(uri)
      .header("Authorization", s"token $apiToken")
      .header("x-is-ci", "false")
      .header("content-type", "application/json")
      .header("user-agent", userAgent)
      .body(jsonReq)

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    retry(3) {
      log.debug(s"Sending... with userAgent = $userAgent")
      val response = request.send()
      log.debug("...Sent")

      response.body match {
        case Left(err) =>
          log.warn(s"Got Error Response: $err")
          log.warn(jsonReq)

          Failure(new RuntimeException(s"Status code ${response.code}, Error: $err"))
        case Right(body) =>
          log.debug("Got Good Response")
          log.trace(body)
          Success(body)
      }
    }
  }

  private def userInfoRaw(): Try[String] = credentials flatMap { creds =>
    val apiEndpoint = creds.endpointOrDefault
    val apiToken = creds.api

    val uri = uri"$apiEndpoint/v1/user"

    val request = sttp.get(uri)
      .header("Authorization", s"token $apiToken")
      .header("Accept", "application/json")
      .header("user-agent", userAgent)

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()

    retry(3) {
       log.debug(s"Getting user info...")
       val response = request.send()
       log.debug("...Sent")

       response.body match {
         case Left(err) =>
           log.warn(s"Got Error Response from user info: $err")
           Failure(new RuntimeException(s"Status code ${response.code}, Error: $err"))
         case Right(body) =>
           log.debug("Got Good Response from user info")
           Success(body)
       }
     }

  }

  def runScan(treeRoot: SnykMavenArtifact): Try[SnykVulnResponse] = for {
    jsonStr <- runRaw(treeRoot)
    json <- decode[SnykVulnResponse](jsonStr).toTry
  } yield json

  def userInfo(): Try[SnykUserInfo] = for {
    jsonStr <- userInfoRaw()
    json <- decode[SnykUserResponse](jsonStr).toTry
  } yield json.user

}

private final class MockApiClient (mockResponder: SnykMavenArtifact => Try[String]) extends ApiClient {
  val isAvailable: Boolean = true
  def runScan(treeRoot: SnykMavenArtifact): Try[SnykVulnResponse] =
    mockResponder(treeRoot) flatMap { str => decode[SnykVulnResponse](str).toTry }
  def userInfo(): Try[SnykUserInfo] = Success {
    val uri = URI.create("https://s.gravatar.com/avatar/XXX/gravatar_l.png")
    SnykUserInfo("mockuser", "mock user", "mock@user", OffsetDateTime.now(), uri, UUID.randomUUID())
  }
}

/**
  * Provides the connection to the Snyk servers for the security scan.
  */
object ApiClient {

  /**
    * Build a "standard" `ApiClient` that connects via the supplied credentials.
    * Note: `credentials` is by-name, and will be re-evaluated on every usage
    */
  def standard(credentials: => Try[SnykCredentials]): ApiClient =
    new StandardApiClient(credentials)

  /**
    * Build a mock client, using the supplied function to provide the mocked response.
    * A default implementation is supplied.
    */
  def mock(mockResponder: SnykMavenArtifact => Try[String]): ApiClient =
    new MockApiClient(mockResponder)
}
