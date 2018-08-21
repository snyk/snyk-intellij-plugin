package io.snyk.plugin.ui
package state




import io.snyk.plugin.client.{ApiClient, SnykCredentials}
import io.snyk.plugin.datamodel.{SnykMavenArtifact, SnykVulnResponse}
import io.snyk.plugin.depsource.externalproject.ExternProj
import io.snyk.plugin.depsource.{DepTreeProvider, MavenProjectsObservable}

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProject

import monix.execution.Scheduler.Implicits.global
import monix.execution.Ack.Continue
import monix.execution.atomic.Atomic
import monix.reactive.Observable
import monix.eval.Task

import scala.concurrent.Future
import scala.io.{Codec, Source}
import scala.util.Try


/**
  * Central abstraction to the plugin, exposes `Atomic` instances of the relevant bits of
  * stateful information: The current dependency tree, and most recently seen scan results.
  *
  * Exposes core navigation functionality to isolate callers from any dependency on IDE-specific
  * implementations, to support running outside of the IDE for test purposes.
  */
trait SnykPluginState {
  //MODEL

  def apiClient: ApiClient
  val projects: Atomic[Map[String,PerProjectState]] = Atomic(Map.empty[String,PerProjectState])
  val credentials: Atomic[Try[SnykCredentials]] = Atomic(SnykCredentials.default)
  val flags: Flags = new Flags
  val selectedProjectId: Atomic[String] = Atomic("")

  def latestScanForSelectedProject: Option[SnykVulnResponse] = for {
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
        println(s"auto-setting selected project: [$id]")
        selectedProjectId := id
      }
      optId
    }
    println(s"safeProjectId is $retval")
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
  def idToMavenProject(id: String): Option[MavenProject] = depTreeProvider.idToMavenProject(id)

  def depTree(
    projectId: String = selectedProjectId.get
  ): SnykMavenArtifact = {
    val pps: Option[PerProjectState] = projects.get.get(projectId)
    val existingDeps = pps.flatMap(_.depTree).getOrElse(depTreeProvider.getDepTree(projectId))
    existingDeps
  }

  //CONTROLLER

  def navigator: Navigator

  def performScan(
    projectId: String = selectedProjectId.get,
    force: Boolean = false
  ): Future[SnykVulnResponse] = {
    val pps: Option[PerProjectState] = projects.get.get(projectId)
    val existingScan: Option[SnykVulnResponse] = pps.flatMap(_.scanResult).filterNot(_.isEmpty)
    if(force || existingScan.isEmpty) {
      //do new scan
      val deps = pps.flatMap(_.depTree).getOrElse(depTreeProvider.getDepTree(projectId))

      //Use a monix task instead of a vanilla future, *specifically* for access to the `timeout` functionality
      val task = Task.eval{
        val result = apiClient runOn deps
        projects.transform{ _ + (projectId -> PerProjectState(Some(deps), result.toOption))}
        println(s"async scan success")
        result
      }

      task
        .timeout(credentials.get.get.timeoutOrDefault)
        .runAsync
        .transform(_.flatten)

    } else Future.successful(existingScan.get)
  }

  def reloadWebView(): Unit
}

object SnykPluginState {
  def forIntelliJ(project: Project, toolWindow: SnykToolWindow): SnykPluginState =
    new IntelliJSnykPluginState(project, toolWindow)

  def mock(
    depTreeProvider: DepTreeProvider = DepTreeProvider.mock(),
    mockResponder: SnykMavenArtifact => Try[String] //= defaultMockResponder
  ): SnykPluginState = new MockSnykPluginState(depTreeProvider, mockResponder)


  private[this] class IntelliJSnykPluginState(project: Project, toolWindow: SnykToolWindow) extends SnykPluginState {
    override val apiClient = ApiClient.standard(credentials.get)
    override val depTreeProvider = DepTreeProvider.forProject(project)

    override val navigator = new Navigator.IntellijNavigator(project, toolWindow, idToMavenProject)
    override def reloadWebView(): Unit = toolWindow.htmlPanel.reload()

    override def mavenProjectsObservable: Observable[Seq[String]] =
      MavenProjectsObservable.forProject(project).map(_.map(_.toString))

    override lazy val externProj: ExternProj = new ExternProj(project)

    mavenProjectsObservable subscribe { list =>
      println(s"updated projects: $list")
      projects := Map.empty
      navigator.navToVulns()
      Continue
    }
  }

  /**
    * Default response as used by `mock`, always returns `sampleResponse.json` from the classpath
    */
  private[this] def defaultMockResponder(treeRoot: SnykMavenArtifact): Try[String] = Try {
    Source.fromResource("sampleResponse.json", getClass.getClassLoader)(Codec.UTF8).mkString
  }

  private[this] class MockSnykPluginState(
    val depTreeProvider: DepTreeProvider,
    val mockResponder: SnykMavenArtifact => Try[String]
  ) extends SnykPluginState {
    override val apiClient: ApiClient = ApiClient.mock(mockResponder)

    override val navigator = new Navigator.MockNavigator

    override def mavenProjectsObservable: Observable[Seq[String]] =
      Observable.pure(Seq("dummy-root-project"))

    projects := Map("dummy-root-project" -> PerProjectState(Some(depTreeProvider.getDepTree(""))))

    override def reloadWebView(): Unit = ()

    override def externProj: ExternProj = ???
  }

}
