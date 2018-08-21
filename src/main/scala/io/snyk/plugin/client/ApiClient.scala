package io.snyk.plugin.client

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.{PluginManager, PluginManagerCore}
import com.intellij.openapi.application.ApplicationInfo
import io.snyk.plugin.datamodel.{SnykMavenArtifact, SnykVulnResponse}
import io.circe.parser.decode
import io.snyk.plugin.datamodel.SnykVulnResponse.Decoders._

import scala.io.{Codec, Source}
import scala.util.Try
import com.softwaremill.sttp._
import io.circe.{Json, Printer}

import scala.collection.immutable.ListMap
import scala.collection.mutable

/**
  * Represents the connection to the Snyk servers for the security scan.
  */
sealed trait ApiClient {
  /** Run a scan on the supplied artifact tree */
  def runOn(treeRoot: SnykMavenArtifact): Try[SnykVulnResponse]
  /** For the "standard" client, returns false if we don't have the necessary credentials */
  def isAvailable: Boolean
}

/**
  * An implementation of `ApiClient` that makes a call to the live Snyk API via the supplied credentials
  * Note: `credentials` is by-name, and will be freshly evaluated on each access -
  *       any property depending on it MUST NOT be cached as a `val`
  */
private final class StandardApiClient(credentials: => Try[SnykCredentials]) extends ApiClient {
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



  private[this] val stringNoNulls: Json => String =
    Printer.noSpaces.copy(dropNullValues = true).pretty

  def runRaw(treeRoot: SnykMavenArtifact): Try[String] = credentials map { creds =>
    val jsonReq = stringNoNulls(SnykClientSerialisation.encodeRoot(treeRoot, creds.org))
    println(s"userAgent = $userAgent")
    println("ApiClient: Built JSON Request")
    println(jsonReq)

    val apiEndpoint = creds.endpointOrDefault
    val apiToken = creds.api

    val request = sttp.post(uri"$apiEndpoint/v1/vuln/maven")
      .header("Authorization", s"token $apiToken")
      .header("x-is-ci", "false")
      .header("content-type", "application/json")
      .header("user-agent", userAgent)
      .body(jsonReq)

    implicit val backend: SttpBackend[Id, Nothing] = HttpURLConnectionBackend()
    println("Sending...")
    val response = request.send()
    println("...Sent")

    val ret = response.unsafeBody

    println("Got Response")
    println(ret)

    ret
  }

  def runOn(treeRoot: SnykMavenArtifact): Try[SnykVulnResponse] = for {
    jsonStr <- runRaw(treeRoot)
    json <- decode[SnykVulnResponse](jsonStr).toTry
  } yield json
}

private final class MockApiClient (mockResponder: SnykMavenArtifact => Try[String]) extends ApiClient {
  val isAvailable: Boolean = true
  def runOn(treeRoot: SnykMavenArtifact): Try[SnykVulnResponse] =
    mockResponder(treeRoot) flatMap { str => decode[SnykVulnResponse](str).toTry }
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
