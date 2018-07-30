package io.snyk.plugin.model

import com.intellij.openapi.project.Project
import io.snyk.plugin.Implicits.RichProject

trait DepTreeProvider {
  def getDepTree(): SnykMavenArtifact
}

private class ProjectDepTreeProvider(project: Project) extends DepTreeProvider {
  override def getDepTree(): SnykMavenArtifact = project.toDepNode
}

object DepTreeProvider {
  def forProject(project: Project): DepTreeProvider = new ProjectDepTreeProvider(project)
  def mock: DepTreeProvider = () => SnykMavenArtifact.empty
}
