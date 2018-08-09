package io.snyk.plugin.model

import com.intellij.openapi.project.Project
import enumeratum.{Enum, EnumEntry}
import io.snyk.plugin.client.{ApiClient, SnykCredentials}
import io.snyk.plugin.embeddedserver.ParamSet
import io.snyk.plugin.ui.SnykToolWindow
import monix.execution.Ack.Continue
import monix.execution.atomic.Atomic
import monix.reactive.Observable
import monix.execution.Scheduler.Implicits.global
import org.jetbrains.idea.maven.project.MavenProject

import scala.concurrent.Future
import scala.util.Try

sealed trait Flag extends EnumEntry

object Flag extends Enum[Flag] {
  val values = findValues
  case object HideMavenGroups extends Flag
}

case class PerProjectState(
  depTree: Option[SnykMavenArtifact] = None,
  scanResult: Option[SnykVulnResponse] = None
)


/**
  * Central abstraction to the plugin, exposes `Atomic` instances of the relevant bits of
  * stateful information: The current dependency tree, and most recently seen scan results.
  *
  * Exposes core navigation functionality to isolate callers from any dependency on IDE-specific
  * implementations, to support running outside of the IDE for test purposes.
  */
trait SnykPluginState {
  def apiClient: ApiClient
  val projects: Atomic[Map[String,PerProjectState]] = Atomic(Map.empty[String,PerProjectState])
  val credentials: Atomic[Try[SnykCredentials]] = Atomic(SnykCredentials.default)
  val flags: Atomic[Map[Flag, Boolean]] = Atomic(Map.empty[Flag, Boolean])
  val selectedProjectId: Atomic[String] = Atomic("")

  val scanInProgress: Atomic[Boolean] = Atomic(false)

  protected def depTreeProvider: DepTreeProvider

  def rootProjectIds: Seq[String] = depTreeProvider.rootIds

  def setFlagValue(flag: Flag, newVal: Boolean): Unit =
    flags transform { _.updated(flag, newVal) }

  def flagValue(flag: Flag): Boolean = flags.get.getOrElse(flag, false)

  def toggleFlag(flag: Flag): Boolean = {
    flags transform { map =>
      val oldVal = map.getOrElse(flag, false)
      map.updated(flag, !oldVal)
    }
    flags.get(flag)
  }

  def flagStrMap: Map[String, Boolean] = flags.get map { case (k,v) => k.entryName.toLowerCase -> v }

  def mavenProjectsObservable: Observable[Seq[String]]

  def navigateTo(path: String, params: ParamSet): Future[String]
  def showVideo(url: String): Unit
  def navToArtifact(group: String, name: String, projectId: String = selectedProjectId.get): Future[Unit]

  def idToMavenProject(id: String): Option[MavenProject] = depTreeProvider.idToMavenProject(id)

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

  def latestScanForSelectedProject: Option[SnykVulnResponse] =
    projects.get.get(selectedProjectId.get).flatMap(_.scanResult)

  def depTree(
    projectId: String = selectedProjectId.get
  ): SnykMavenArtifact = {
    val pps: Option[PerProjectState] = projects.get.get(projectId)
    val existingDeps = pps.flatMap(_.depTree).getOrElse(depTreeProvider.getDepTree(projectId))
    existingDeps
  }

  def reloadWebView(): Unit
}

object SnykPluginState {
  def forIntelliJ(project: Project, toolWindow: SnykToolWindow): SnykPluginState =
    new IntelliJSnykPluginState(project, toolWindow)

  def mock: SnykPluginState = new MockSnykPluginState
}

private[this] class IntelliJSnykPluginState(project: Project, toolWindow: SnykToolWindow) extends SnykPluginState {
  override val apiClient = ApiClient.standard(credentials.get)
  override val depTreeProvider = DepTreeProvider.forProject(project)
  override def navigateTo(path: String, params: ParamSet): Future[String] = toolWindow.navigateTo(path, params)
  override def showVideo(url: String): Unit = toolWindow.showVideo(url)
  override def navToArtifact(
    group: String,
    name: String,
    projectId: String = selectedProjectId.get
  ): Future[Unit] =
    idToMavenProject(projectId) map { toolWindow.navToArtifact(group, name, _) } getOrElse Future.successful(())

  override def reloadWebView(): Unit = toolWindow.htmlPanel.reload()

  override def mavenProjectsObservable: Observable[Seq[String]] =
    MavenProjectsObservable.forProject(project).map(_.map(_.toString))

  mavenProjectsObservable subscribe {_ =>
    //flush the cache
    projects := Map.empty
    Continue
  }
}

private[this] class MockSnykPluginState(
  val depTreeProvider: DepTreeProvider = DepTreeProvider.mock()
) extends SnykPluginState {
  override val apiClient: ApiClient = ApiClient.mock()

  override def navigateTo(path: String, params: ParamSet): Future[String] = {
    println(s"MockSnykPluginState.navigateTo($path, $params)")
    Future.successful(path)
  }

  override def showVideo(url: String): Unit =
    println(s"MockSnykPluginState.showVideo($url)")

  override def navToArtifact(group: String, name: String, projectId: String = selectedProjectId.get): Future[Unit] = {
    println(s"MockSnykPluginState.navToArtifact($group, $name)")
    Future.successful(())
  }

  override def mavenProjectsObservable: Observable[Seq[String]] =
    Observable.pure(Seq("dummy-root-project"))

  override def reloadWebView(): Unit = ()
}
