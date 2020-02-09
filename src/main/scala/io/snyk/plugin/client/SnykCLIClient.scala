package io.snyk.plugin.client

import java.io.FileNotFoundException
import java.net.URI
import java.nio.charset.Charset
import java.time.OffsetDateTime
import java.util
import java.util.UUID

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ScriptRunnerUtil
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
import java.nio.file.{Files, Path, Paths}

import com.intellij.openapi.project.Project

/**
  * Represents the connection to the Snyk servers for the security scan.
  */
sealed trait SnykCLIClient {
  /** Run a scan on the supplied artifact tree */
  def runScan(project: Project, treeRoot: SnykMavenArtifact): Try[SnykVulnResponse]
  def userInfo(): Try[SnykUserInfo]
  /** For the "standard" client, returns false if we don't have the necessary credentials */
  def isAvailable: Boolean

  /**
    * Check is Snyk CLI is installed.
    *
    * @param consoleCommandRunner - for command execution and possibility to test
    *
    * @return Boolean
    */
  def isCLIInstalled(consoleCommandRunner: ConsoleCommandRunner = new ConsoleCommandRunner): Boolean
}

/**
  * An implementation of `ApiClient` that makes a call to the live Snyk API using the supplied config
  * Note: `config` is by-name, and will be freshly evaluated on each access -
  *       any property depending on it MUST NOT be cached as a `val`
  */
private final class StandardSnykCLIClient(tryConfig: => Try[SnykConfig]) extends SnykCLIClient {
  val log = Logger.getInstance(this.getClass)

  def isAvailable: Boolean = tryConfig.isSuccess

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

  private def runRaw(project: Project, treeRoot: SnykMavenArtifact): Try[String] = tryConfig flatMap { config =>
    log.debug("ApiClient: run Snyk CLI")

    val projectPath = project.getBasePath

    if (Files.notExists(Paths.get(projectPath))) {
      return Failure(new FileNotFoundException("pom.xml"))
    }

    val commands: util.ArrayList[String] = new util.ArrayList[String]
    commands.add("snyk")
    commands.add("--json")
    commands.add("test")

    val generalCommandLine = new GeneralCommandLine(commands)
    generalCommandLine.setCharset(Charset.forName("UTF-8"))
    generalCommandLine.setWorkDirectory(projectPath)

    try {
      val snykResultJsonStr = ScriptRunnerUtil.getProcessOutput(generalCommandLine)

      Success(snykResultJsonStr)
    } catch {
      case e: Exception => {
        println(e.getMessage)

        Failure(e)
      }
    }
  }

  private def userInfoRaw(): Try[String] = tryConfig flatMap { config =>
    val apiEndpoint = config.endpointOrDefault
    val apiToken = config.api

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

  def runScan(project: Project, treeRoot: SnykMavenArtifact): Try[SnykVulnResponse] = for {
    jsonStr <- runRaw(project, treeRoot)
    json <- decode[SnykVulnResponse](jsonStr).toTry
  } yield json

  def userInfo(): Try[SnykUserInfo] = for {
    jsonStr <- userInfoRaw()
    json <- decode[SnykUserResponse](jsonStr).toTry
  } yield json.user

  def isCLIInstalled(consoleCommandRunner: ConsoleCommandRunner = new ConsoleCommandRunner): Boolean = {
    log.debug("Check is Snyk CLI is installed")

    val commands: util.ArrayList[String] = new util.ArrayList[String]
    commands.add("snyk")
    commands.add("--version")

    try {
      val consoleResultStr = consoleCommandRunner.execute(commands)

      consoleResultStr.contains("version")
    } catch {
      case exception: Exception => {
        println(exception.getMessage)

        false
      }
    }
  }
}

private final class MockSnykCLIClient(mockResponder: SnykMavenArtifact => Try[String]) extends SnykCLIClient {
  val isAvailable: Boolean = true
  def runScan(project: Project, treeRoot: SnykMavenArtifact): Try[SnykVulnResponse] =
    mockResponder(treeRoot) flatMap { str => decode[SnykVulnResponse](str).toTry }
  def userInfo(): Try[SnykUserInfo] = Success {
    val uri = URI.create("https://s.gravatar.com/avatar/XXX/gravatar_l.png")
    SnykUserInfo("mockuser", "mock user", "mock@user", OffsetDateTime.now(), uri, UUID.randomUUID())
  }

  def isCLIInstalled(consoleCommandRunner: ConsoleCommandRunner = new ConsoleCommandRunner): Boolean = true
}

/**
  * Provides the connection to the Snyk servers for the security scan.
  */
object SnykCLIClient {

  /**
    * Build a "standard" `ApiClient` that connects via the supplied config.
    * Note: `config` is by-name, and will be re-evaluated on every usage
    */
  def standard(config: => Try[SnykConfig]): SnykCLIClient =
    new StandardSnykCLIClient(config)

  /**
    * Build a mock client, using the supplied function to provide the mocked response.
    * A default implementation is supplied.
    */
  def mock(mockResponder: SnykMavenArtifact => Try[String]): SnykCLIClient =
    new MockSnykCLIClient(mockResponder)
}

/**
  * Encapsulate work with IntelliJ OpenAPI {@link ScriptRunnerUtil}
  */
class ConsoleCommandRunner {

  def execute(commands: util.ArrayList[String], workDirectory: String = "/"): String = {
    val generalCommandLine = new GeneralCommandLine(commands)

    generalCommandLine.setCharset(Charset.forName("UTF-8"))
    generalCommandLine.setWorkDirectory(workDirectory)

    ScriptRunnerUtil.getProcessOutput(generalCommandLine)
  }
}
