package io.snyk.plugin.model

import io.snyk.plugin.embeddedserver.ParamSet
import io.snyk.plugin.ui.SnykToolWindow
import monix.execution.atomic.Atomic

import scala.concurrent.Future

/**
  * Central abstraction to the plugin, exposes `Atomic` instances of the relevant bits of
  * stateful information: The current dependency tree, and most recently seen scan results.
  *
  * Exposes core navigation functionality to isolate callers from any dependency on IDE-specific
  * implementations, to support running outside of the IDE for test purposes.
  */
trait SnykPluginState {
  val depTree: Atomic[DisplayNode] = Atomic(DisplayNode.Empty)
  val latestScanResult: Atomic[SnykVulnResponse] = Atomic(SnykVulnResponse.empty)

  def navigateTo(path: String, params: ParamSet): Future[String]
  def showVideo(url: String): Unit
  def navToArtifact(group: String, name: String): Future[Unit]

  def setLatestScanResult(x: SnykVulnResponse): Unit = latestScanResult set x
  def setDepTree(x: DisplayNode): Unit = depTree set x
}

object SnykPluginState {
  def forIntelliJ(toolWindow: SnykToolWindow): SnykPluginState = new IntelliJSnykPluginState(toolWindow)
  def mock: SnykPluginState = new MockSnykPluginState
}

private[this] class IntelliJSnykPluginState(toolWindow: SnykToolWindow) extends SnykPluginState {
  override def navigateTo(path: String, params: ParamSet): Future[String] = toolWindow.navigateTo(path, params)
  override def showVideo(url: String): Unit = toolWindow.showVideo(url)
  override def navToArtifact(group: String, name: String): Future[Unit] = toolWindow.navToArtifact(group, name)
}

private[this] class MockSnykPluginState extends SnykPluginState {
  override def navigateTo(path: String, params: ParamSet): Future[String] = {
    println(s"MockSnykPluginState.navigateTo($path, $params)")
    Future.successful(path)
  }

  override def showVideo(url: String): Unit =
    println(s"MockSnykPluginState.showVideo($url)")

  override def navToArtifact(group: String, name: String): Future[Unit] = {
    println(s"MockSnykPluginState.navToArtifact($group, $name)")
    Future.successful(())
  }
}
