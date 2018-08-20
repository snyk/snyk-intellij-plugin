package io.snyk.plugin.ui
package state


import com.intellij.openapi.project.Project
import io.snyk.plugin.client.{ApiClient, SnykCredentials}
import io.snyk.plugin.datamodel.{SnykMavenArtifact, SnykVulnResponse}
import io.snyk.plugin.depsource.externalproject.ExternProj
import io.snyk.plugin.depsource.{DepTreeProvider, MavenProjectsObservable}
import monix.execution.Ack.Continue
import monix.execution.atomic.Atomic
import monix.reactive.Observable
import monix.execution.Scheduler.Implicits.global
import org.jetbrains.idea.maven.project.MavenProject

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
  val scanInProgress: Atomic[Boolean] = Atomic(false)

  def latestScanForSelectedProject: Option[SnykVulnResponse] =
    projects.get.get(selectedProjectId.get).flatMap(_.scanResult)

  /**
    * Side Effecting: will set `selectedProjectId` if an auto-selection was made
    * @return An optional id if already set, or we were able to pick the first root
    */
  def safeProjectId: Option[String] = {
    Option(selectedProjectId.get).filterNot(_.isEmpty) orElse {
      val optId = rootProjectIds.headOption
      optId foreach { id =>
        println(s"auto-setting selected project: [$id]")
        selectedProjectId := id
      }
      optId
    }
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
      Future{
        scanInProgress := true
        val result = apiClient runOn deps
        projects.transform{ _ + (projectId -> PerProjectState(Some(deps), result.toOption))}
        scanInProgress := false
        println(s"async scan success")
        result
      }.transform(_.flatten)
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

    mavenProjectsObservable subscribe { _ =>
      //flush the cache
      projects := Map.empty
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

    override def reloadWebView(): Unit = ()

    override def externProj: ExternProj = ???
  }

}
