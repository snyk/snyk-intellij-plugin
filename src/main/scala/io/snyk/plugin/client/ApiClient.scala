package io.snyk.plugin.client

import io.snyk.plugin.model.{SnykMavenArtifact, SnykVulnResponse}
import io.circe.parser.decode
import io.snyk.plugin.model.SnykVulnResponse.Decoders._

import scala.io.{Codec, Source}
import scala.util.Try
import com.softwaremill.sttp._

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
private final class StandardApiClient (credentials: => Try[SnykCredentials]) extends ApiClient {
  def isAvailable: Boolean = credentials.isSuccess

  def runRaw(treeRoot: SnykMavenArtifact): Try[String] = credentials map { creds =>
    val jsonReq = SnykClientSerialisation.encodeRoot(treeRoot).noSpaces
    println("ApiClient: Built JSON Request")
    println(jsonReq)

    val apiEndpoint = creds.endpointOrDefault
    val apiToken = creds.api

    val request = sttp.post(uri"$apiEndpoint/v1/vuln/maven")
      .header("Authorization", s"token $apiToken")
      .header("x-is-ci", "false")
      .header("content-type", "application/json")
      .header("user-agent", "Needle/2.1.1 (Node.js v8.11.3; linux x64)")
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
    * Default response as used by `mock`, always returns `sampleResponse.json` from the classpath
    */
  private[this] def defaultMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("sampleResponse.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }

  /**
    * Build a mock client, using the supplied function to provide the mocked response.
    * A default implementation is supplied.
    */
  def mock(mockResponder: SnykMavenArtifact => Try[String] = defaultMockResponder): ApiClient =
    new MockApiClient(mockResponder)
}
