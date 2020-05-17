package io.snyk.plugin.ui
package state

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import io.snyk.plugin.client.{CliClient, ConsoleCommandRunner, SnykConfig}
import io.snyk.plugin.datamodel.{ProjectDependency, SnykVulnResponse}
import io.snyk.plugin.depsource.externalproject.ExternProj
import io.snyk.plugin.depsource.{BuildToolProject, DepTreeProvider, GradleProjectsObservable, MavenProjectsObservable}
import io.snyk.plugin.IntellijLogging
import monix.execution.Scheduler.Implicits.global
import monix.execution.Ack.Continue
import monix.execution.atomic.Atomic
import monix.reactive.Observable
import monix.eval.Task
import com.intellij.openapi.project.Project
import io.snyk.plugin.metrics.{MockSegmentApi, SegmentApi}

import scala.concurrent.Future
import scala.io.{Codec, Source}
import scala.util.{Failure, Success, Try}
import io.circe.parser.decode
import io.snyk.plugin.datamodel.SnykVulnResponse.JsonCodecs._
import io.snyk.plugin.ui.settings.{SnykIntelliJSettings, SnykPersistentStateComponent}

/**
  * Central abstraction to the plugin, exposes `Atomic` instances of the relevant bits of
  * stateful information: The current dependency tree, and most recently seen scan results.
  *
  * Exposes core navigation functionality to isolate callers from any dependency on IDE-specific
  * implementations, to support running outside of the IDE for test purposes.
  */
trait SnykPluginState extends IntellijLogging {
  //MODEL

  def getProject: Project
  def cliClient: CliClient
  def segmentApi: SegmentApi
  val projects: Atomic[Map[String,PerProjectState]] = Atomic(Map.empty[String,PerProjectState])
  val config: Atomic[Try[SnykConfig]] = Atomic(SnykConfig.default)
  val flags: Flags = new Flags
  val selectedProjectId: Atomic[String] = Atomic("")

  def latestScanForSelectedProject: Option[Seq[SnykVulnResponse]] = for {
    projId <- safeProjectId
    projState <- projects().get(projId)
    scanResult <- projState.scanResult
  } yield scanResult


  /**
    * Ensure that we have a valid `selectedProjectId`.  If it's empty, or not in `rootProjectIds`
    * (e.g. because source code has changed) then we auto-select the first valid project.
    * Side Effecting: will UPDATE `selectedProjectId` if an auto-selection was made.
    *
    * @return An optional ID if one has been set or can be determined
    */
  def safeProjectId: Option[String] = {
    val retval = Option(selectedProjectId.get).filterNot(_.isEmpty).filter(rootProjectIds.contains) orElse {
      val optId = rootProjectIds.headOption
      optId foreach { id =>
        log.debug(s"auto-setting selected project: [$id]")
        selectedProjectId := id
      }
      optId
    }
    log.debug(s"safeProjectId is $retval")
    retval
  }


  //TODO: Push the whole bally lot into dep provider
  // but should it be referenced from the model... or the controller?
  // Doesn't really seem to be one thing or another
  //
  // ¯\_(ツ)_/¯

  def externProj: ExternProj
  protected def depTreeProvider: DepTreeProvider
  def rootProjectIds: Seq[String] = depTreeProvider.rootIds
  def mavenProjectsObservable: Observable[Seq[String]]
  def gradleProjectsObservable: Observable[Seq[String]]
  def idToBuildToolProject(id: String): Option[BuildToolProject] = depTreeProvider.idToBuildToolProject(id)

  def depTree(
    projectId: String = selectedProjectId.get
  ): Option[ProjectDependency] = {
    val pps: Option[PerProjectState] = projects.get.get(projectId)
    val existingDeps = pps.flatMap(_.depTree).orElse(depTreeProvider.getDepTree(projectId))
    existingDeps
  }

  //CONTROLLER

  val navigator: Atomic[Navigator] = Atomic(Navigator.mock)

  def performScan(
    projectId: String = selectedProjectId(),
    force: Boolean = false
  ): Future[Seq[SnykVulnResponse]] = {
    val pps: Option[PerProjectState] = projects.get.get(projectId)
    val existingScan: Option[Seq[SnykVulnResponse]] = pps.flatMap(_.scanResult).filterNot(_.isEmpty)
    if(force || existingScan.isEmpty) {
      //do new scan
      val deps = pps.flatMap(_.depTree).orElse(depTreeProvider.getDepTree(projectId)) match {
        case Some(x) => Success(x)
        case None => Failure(
          new RuntimeException(
            "No project available, please ensure the IDE has finished parsing any build files."
          )
        )
      }

      //Use a monix task instead of a vanilla future, *specifically* for access to the `timeout` functionality
      val task = Task.eval{
        val project = getProject

        val snykIntelliJSettings: SnykIntelliJSettings = SnykPersistentStateComponent.getInstance(project)

        val result: Try[Seq[SnykVulnResponse]] = deps flatMap (cliClient.runScan(project, snykIntelliJSettings, _))
        val statePair = projectId -> PerProjectState(deps.toOption, result.toOption)
        projects.transform{ _ + statePair}
        segmentApi.track("IntelliJ user ran scan", Map("projectid" -> projectId))
        log.info(s"async scan success")

        DaemonCodeAnalyzer.getInstance(project).restart()

        result
      }

      task
        .timeout(config.get.get.timeoutOrDefault)
        .runAsync
        .transform(_.flatten)

    } else Future.successful(existingScan.get)
  }
}

object SnykPluginState {
  val pluginStates: Atomic[Map[String, SnykPluginState]] = Atomic(Map.empty[String, SnykPluginState])

  def newInstance(project: Project): SnykPluginState = {
    val pluginStateOption: Option[SnykPluginState] = pluginStates.get.get(project.getName)

    if (pluginStateOption.isEmpty) {
      val snykPluginState = new IntelliJSnykPluginState(project)

      val projectStatePair = project.getName -> snykPluginState

      pluginStates.transform{_ + projectStatePair}

      snykPluginState
    } else {
      pluginStateOption.get
    }
  }

  def getInstance(project: Project): SnykPluginState = pluginStates.get.get(project.getName).get

  def removeForProject(project: Project): Unit = {
    val pluginStateOption: Option[SnykPluginState] = pluginStates.get.get(project.getName)

    if (pluginStateOption.isDefined) {
      pluginStates.transform{_ -- Seq(project.getName)}
    }
  }

  def mockForProject(
    project: Project,
    depTreeProvider: DepTreeProvider = DepTreeProvider.mock(),
    mockResponder: ProjectDependency => Try[String]): SnykPluginState = {

    val snykPluginState = new MockSnykPluginState(depTreeProvider, mockResponder)

    val projectStatePair = project.getName -> snykPluginState

    pluginStates.transform{_ + projectStatePair}

    snykPluginState
  }

  def mock(
    depTreeProvider: DepTreeProvider = DepTreeProvider.mock(),
    mockResponder: ProjectDependency => Try[String] //= defaultMockResponder
  ): SnykPluginState = new MockSnykPluginState(depTreeProvider, mockResponder)

  private[this] class IntelliJSnykPluginState(project: Project) extends SnykPluginState {

    override val cliClient = CliClient.newInstance(config(), new ConsoleCommandRunner)
    override val depTreeProvider = DepTreeProvider.forProject(project)
    override val segmentApi: SegmentApi =
      config().toOption.map(_.disableAnalytics) match {
        case Some(true) => MockSegmentApi
        case _ => SegmentApi(cliClient)
      }

    override def mavenProjectsObservable: Observable[Seq[String]] =
      MavenProjectsObservable.forProject(project).map(_.map(_.toString))

    override def gradleProjectsObservable: Observable[Seq[String]] =
      GradleProjectsObservable.forProject(project).map(_.map(_.toString))

    override lazy val externProj: ExternProj = new ExternProj(project)

    mavenProjectsObservable subscribe { list =>
      log.info(s"updated projects: $list")
      projects := Map.empty
      navigator().navToVulns()
      Continue
    }

    gradleProjectsObservable subscribe { list =>
      log.info(s"updated projects: $list")
      projects := Map.empty
      navigator().navToVulns()
      Continue
    }

    override def getProject: Project = project
  }

  /**
    * Default response as used by `mock`, always returns `sampleResponse.json` from the classpath
    */
  private[this] def defaultMockResponder(treeRoot: ProjectDependency): Try[String] = Try {
    Source.fromResource("sampleResponse.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }

  private[this] class MockSnykPluginState(
    val depTreeProvider: DepTreeProvider,
    val mockResponder: ProjectDependency => Try[String]) extends SnykPluginState {

    override val cliClient: CliClient = CliClient.newMockInstance(mockResponder)
    override val segmentApi: SegmentApi = MockSegmentApi

    override def mavenProjectsObservable: Observable[Seq[String]] = Observable.pure(Seq("dummy-root-project"))

    projects := Map("dummy-root-project" -> PerProjectState(depTreeProvider.getDepTree("")))

    override def externProj: ExternProj = ???

    override def performScan(projectId: String, force: Boolean): Future[Seq[SnykVulnResponse]] =
      Future[Seq[SnykVulnResponse]](Seq.empty)

    override def getProject: Project = ???

    override def gradleProjectsObservable: Observable[Seq[String]] = Observable.pure(Seq("dummy-root-project"))

    override def latestScanForSelectedProject: Option[Seq[SnykVulnResponse]] = {
      val treeRoot: ProjectDependency = ProjectDependency.empty
      val triedResponse = mockResponder(treeRoot) flatMap { str => decode[Seq[SnykVulnResponse]](str).toTry }

      triedResponse.toOption
    }
  }
}
