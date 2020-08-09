package io.snyk.plugin.client

import java.io.{File, FileNotFoundException}
import java.net.URI
import java.time.OffsetDateTime
import java.util
import java.util.UUID
import java.util.Objects.nonNull
import java.util.Objects.isNull

import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationInfo
import io.snyk.plugin.datamodel.{ProjectDependency, SnykVulnResponse}
import io.snyk.plugin.datamodel.SnykVulnResponse.JsonCodecs._
import com.softwaremill.sttp._
import io.circe.parser.decode
import io.circe.{Json, Printer}

import scala.util.{Failure, Success, Try}
import java.nio.file.{Files, Paths}
import java.util.regex.Pattern

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import io.snyk.plugin.IntellijLogging
import io.snyk.plugin.depsource.ProjectType
import io.snyk.plugin.ui.settings.SnykIdeSettings
import monix.execution.atomic.Atomic

/**
  * Represents the connection to the Snyk servers for the security scan.
  */
sealed trait CliClient extends IntellijLogging {
  /** Run a scan on the supplied artifact tree */
  def runScan(
    project: Project,
    settings: SnykIdeSettings,
    projectDependency: ProjectDependency): Try[Seq[SnykVulnResponse]]

  def userInfo(): Try[SnykUserInfo]

  /** For the "standard" client, returns false if we don't have the necessary credentials */
  def isAvailable: Boolean

  /**
    * Check whether Snyk CLI is installed.
    *
    * @return Boolean
    */
  def isCliInstalled(): Boolean

  /**
    * This method will be called before Snyk CLI runRaw().
    *
    * @param project            - IntelliJ project
    * @param projectDependency  - project dependency information
    */
  def prepareProjectBeforeCliCall(project: Project, projectDependency: ProjectDependency): String

  /**
    * Set new ConsoleCommandRunner instance.
    *
    * @param newRunner - new instance
    */
  def setConsoleCommandRunner(newRunner: ConsoleCommandRunner): Unit

  /**
   * Build list of commands for run Snyk CLI command.
   * @param settings           - Snyk IntelliJ settings
   * @param projectDependency  - Information about project dependencies.
   * @return
   */
  def buildCliCommandsList(settings: SnykIdeSettings, projectDependency: ProjectDependency): util.ArrayList[String]

  /**
    * Check is CLI installed by plugin: if CLI file exists in plugin directory.
    *
    * @return Boolean
    */
  def checkIsCliInstalledAutomaticallyByPlugin(): Boolean

  /**
    * Check is CLI installed by user manually: if CLI available via console.
    *
    * @return Boolean
    */
  def checkIsCliInstalledManuallyByUser(): Boolean
}

/**
  * An implementation of `ApiClient` that makes a call to the live Snyk API using the supplied config
  * Note: `config` is by-name, and will be freshly evaluated on each access -
  *       any property depending on it MUST NOT be cached as a `val`
  */
private final class StandardCliClient(
  tryConfig: => Try[SnykConfig],
  aConsoleCommandRunner: ConsoleCommandRunner,
  pluginPath: String) extends CliClient with IntellijLogging {

  val consoleCommandRunner: Atomic[ConsoleCommandRunner] = Atomic(aConsoleCommandRunner)

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

  private def runSnykCli(
    project: Project,
    settings: SnykIdeSettings,
    projectDependency: ProjectDependency): Try[String] = tryConfig flatMap { config =>

    log.info("Enter runSnykCli()")

    log.info("Execute prepareProjectBeforeCliCall(...)")

    prepareProjectBeforeCliCall(project, projectDependency)

    log.info("After prepareProjectBeforeCliCall(...)")

    try {
      val projectPath = project.getBasePath

      log.info(s"Project path $projectPath")

      log.info("Execute requestCli()")

      val snykResultJsonStr = requestCli(projectPath, buildCliCommandsList(settings, projectDependency))

      log.info("Parse cli result.")

      log.info("Check result string.")

      // Check CLI result string for not empty and not empty json array.
      // Check is termination message available.
      if (snykResultJsonStr.isEmpty || snykResultJsonStr.startsWith("[]")) {
        log.info(s"Result string is empty: $snykResultJsonStr")

        val terminationMessage = consoleCommandRunner().terminationMessage()

        log.info(s"Termination message: $terminationMessage")

        if (terminationMessage.nonEmpty) {
          log.info("Return Failure with termination message.")

          return Failure(new Exception(terminationMessage))
        } else {
          log.info("Return Failure with 'Empty CLI result' message.")

          return Failure(new Exception("Empty CLI result"))
        }
      }

      // Description: if project is one module project Snyk CLI will return JSON object.
      // If project is multi-module project Snyk CLI will return array of JSON objects.
      // If project is one module project and Snyk CLI return an error it will return it as JSON object with error
      // property. But if it's multi-module project and Snyk CLI return an error it will return it as plain text without
      // JSON.
      val firstJsonChar = snykResultJsonStr.charAt(0)

      log.info(s"First json char: $firstJsonChar")
      log.info("Recognize result json type by first char.")

      firstJsonChar match {
        case '{' => Success(s"[$snykResultJsonStr]")
        case '[' => Success(snykResultJsonStr)
        case _ => Success(s"[${requestCliForError(projectPath)}]")
      }
    } catch {
      case exception: Exception => {
        log.error(exception)

        Failure(exception)
      }
    }
  }

  override def setConsoleCommandRunner(newRunner: ConsoleCommandRunner): Unit = consoleCommandRunner := newRunner

  override def prepareProjectBeforeCliCall(project: Project, projectDependency: ProjectDependency): String = {
    log.info("Enter prepareProjectBeforeCliCall()")

    if (projectDependency.projectType == ProjectType.MAVEN && projectDependency.isMultiModuleProject) {
      log.info("Call maven install goal()")

      consoleCommandRunner().runMavenInstall(project)

      PrepareProjectStatus.MAVEN_INSTALL_STEP_FINISHED
    } else {
      PrepareProjectStatus.DEFAULT_STEP
    }
  }

  private def requestCliForError(projectPath: String): String = {
    log.info("Enter requestCliForError()")

    val commands: util.ArrayList[String] = new util.ArrayList[String]

    commands.add(snykCliCommandPath)
    commands.add("--json")
    commands.add("test")

    log.info(s"Call requestCli() with parameters $commands")

    requestCli(projectPath, commands)
  }

  private def requestCli(projectPath: String, commands: util.ArrayList[String]): String = {
    log.info("Enter requestCli()")

    if (Files.notExists(Paths.get(projectPath))) {
      log.info(s"Project path not found: $projectPath/pom.xml")

      throw new FileNotFoundException("pom.xml")
    }

    log.info("Execute consoleCommandRunner.execute(...)")

    consoleCommandRunner().execute(commands, projectPath)
  }

  def isCliInstalled(): Boolean = {
    log.info("Check whether Snyk CLI is installed")

    checkIsCliInstalledManuallyByUser() || checkIsCliInstalledAutomaticallyByPlugin()
  }

  override def checkIsCliInstalledManuallyByUser(): Boolean = {
    log.debug("Check whether Snyk CLI is installed by user.")

    val commands: util.ArrayList[String] = new util.ArrayList[String]
    commands.add(snykCliCommandName)
    commands.add("--version")

    try {
      val consoleResultStr = consoleCommandRunner().execute(commands)

      val pattern = Pattern.compile("^\\d+\\.\\d+\\.\\d+")
      val matcher = pattern.matcher(consoleResultStr.trim)

      matcher.matches()
    } catch {
      case exception: Exception => {
        println(exception.getMessage)

        false
      }
    }
  }

  override def checkIsCliInstalledAutomaticallyByPlugin(): Boolean = {
    log.debug("Check whether Snyk CLI is installed by plugin automatically.")

    cliFile.exists()
  }

  private def cliFile: File = new File(pluginPath, Platform.current.snykWrapperFileName)

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

  def runScan(project: Project, settings: SnykIdeSettings, projectDependency: ProjectDependency): Try[Seq[SnykVulnResponse]] = for {
    jsonStr <- runSnykCli(project, settings, projectDependency)
    json <- decode[Seq[SnykVulnResponse]](jsonStr).toTry
  } yield json

  def userInfo(): Try[SnykUserInfo] = for {
    jsonStr <- userInfoRaw()
    json <- decode[SnykUserResponse](jsonStr).toTry
  } yield json.user

  override def buildCliCommandsList(settings: SnykIdeSettings, projectDependency: ProjectDependency): util.ArrayList[String] = {
    log.info("Enter buildCliCommandsList")

    val commands: util.ArrayList[String] = new util.ArrayList[String]
    commands.add(snykCliCommandPath)
    commands.add("--json")

    val customEndpoint = settings.customEndpointUrl

    if (nonNull(customEndpoint) && customEndpoint.nonEmpty) {
      commands.add(s"--api=$customEndpoint")
    }

    if (settings.isIgnoreUnknownCA) {
      commands.add("--insecure")
    }

    val organization = settings.organization

    if (nonNull(organization) && organization.nonEmpty) {
      commands.add(s"--org=$organization")
    }

    val additionalParameters = settings.additionalParameters

    if (nonNull(additionalParameters) && additionalParameters.nonEmpty) {
      commands.add(additionalParameters)
    }

    // Add --all-projects or --all-sub-projects parameter only if no --file parameter.
    // For now CLI not support use both at same time.
    if (checkNotContainsFileParameter(additionalParameters)) {
      projectDependency.projectType match {
        case ProjectType.MAVEN => commands.add("--all-projects")
        case ProjectType.GRADLE => commands.add("--all-sub-projects")
      }
    }

    commands.add("test")

    log.info(s"Cli parameters: $commands")

    commands
  }

  private def checkNotContainsFileParameter(additionalParameters: String): Boolean =
    isNull(additionalParameters) || additionalParameters.isEmpty && !additionalParameters.contains("--file")

  private def snykCliCommandName: String = if (SystemInfo.isWindows) "snyk.cmd" else "snyk"

  private def snykCliCommandPath: String = {
    if (checkIsCliInstalledManuallyByUser())
      snykCliCommandName
    else if (checkIsCliInstalledAutomaticallyByPlugin()) {
      cliFile.getAbsolutePath
    } else {
      throw new RuntimeException("Snyk CLI not installed.")
    }
  }
}

private final class MockCliClient(
  mockResponder: ProjectDependency => Try[String],
  consoleCommandRunner: ConsoleCommandRunner = new ConsoleCommandRunner) extends CliClient {

  val isAvailable: Boolean = true

  def runScan(project: Project, settings: SnykIdeSettings, treeRoot: ProjectDependency): Try[Seq[SnykVulnResponse]] =
    mockResponder(treeRoot) flatMap { str => decode[Seq[SnykVulnResponse]](str).toTry }

  def userInfo(): Try[SnykUserInfo] = Success {
    val uri = URI.create("https://s.gravatar.com/avatar/XXX/gravatar_l.png")
    SnykUserInfo("mockuser", "mock user", "mock@user", OffsetDateTime.now(), uri, UUID.randomUUID())
  }

  def isCliInstalled(): Boolean = true

  override def prepareProjectBeforeCliCall(project: Project, projectDependency: ProjectDependency): String =
    PrepareProjectStatus.DEFAULT_STEP

  /**
    * Set new ConsoleCommandRunner instance.
    *
    * @param newRunner - new instance
    */
  override def setConsoleCommandRunner(newRunner: ConsoleCommandRunner): Unit = ???

  /**
   * Build list of commands for run Snyk CLI command.
   *
   * @param settings          - Snyk IntelliJ settings
   * @param projectDependency - Information about project dependencies.
   * @return
   */
  override def buildCliCommandsList(
    settings: SnykIdeSettings,
    projectDependency: ProjectDependency): util.ArrayList[String] = new util.ArrayList[String]()

  /**
    * Check is CLI installed by plugin: if CLI file exists in plugin directory.
    *
    * @return Boolean
    */
  override def checkIsCliInstalledAutomaticallyByPlugin(): Boolean = false

  /**
    * Check is CLI installed by user manually: if CLI available via console.
    *
    * @return Boolean
    */
  override def checkIsCliInstalledManuallyByUser(): Boolean = false
}

/**
  * Provides the connection to the Snyk servers for the security scan.
  */
object CliClient {

  /**
    * Build a "standard" `ApiClient` that connects via the supplied config.
    * Note: `config` is by-name, and will be re-evaluated on every usage
    */
  def newInstance(
    config: => Try[SnykConfig],
    consoleCommandRunner: ConsoleCommandRunner = new ConsoleCommandRunner,
    pluginPath: String = ""): CliClient =
      new StandardCliClient(config, consoleCommandRunner, pluginPath)

  /**
    * Build a mock client, using the supplied function to provide the mocked response.
    * A default implementation is supplied.
    */
  def newMockInstance(mockResponder: ProjectDependency => Try[String]): CliClient =
    new MockCliClient(mockResponder)
}

object PrepareProjectStatus {
  val MAVEN_INSTALL_STEP_FINISHED = "MavenInstallStepFinished"
  val DEFAULT_STEP = "DefaultStep"
}