package io.snyk.plugin.depsource

import com.intellij.openapi.project.Project
import io.snyk.plugin.datamodel.SnykMavenArtifact
import org.jetbrains.idea.maven.project.{MavenProject, MavenProjectsManager}

import scala.collection.JavaConverters._

trait DepTreeProvider {
  def rootIds: Seq[String]
  def getDepTree(rootId: String): SnykMavenArtifact
  def idToMavenProject(id: String): Option[MavenProject]
}

private class ProjectDepTreeProvider(project: Project) extends DepTreeProvider {

  private[this] def allMavenProjects: Seq[MavenProject] =
    MavenProjectsManager.getInstance(project).getProjects.asScala

  override def rootIds: Seq[String] = allMavenProjects.map(_.toString)

  override def idToMavenProject(id: String): Option[MavenProject] =
    allMavenProjects.find(_.toString == id)

  override def getDepTree(rootId: String): SnykMavenArtifact = {
    val projects = allMavenProjects
    val mp = idToMavenProject(rootId) getOrElse projects.head
    SnykMavenArtifact.fromMavenProject(mp)
  }
}

private class MockDepTreeProvider(val rootIds: Seq[String], mockTree: SnykMavenArtifact) extends DepTreeProvider {
  override def getDepTree(rootId: String): SnykMavenArtifact = mockTree
  override def idToMavenProject(id: String): Option[MavenProject] = None
}

object DepTreeProvider {
  def forProject(project: Project): DepTreeProvider = new ProjectDepTreeProvider(project)
  def mock(
    rootIds: Seq[String] = Seq("dummy root"),
    mockTree: SnykMavenArtifact = SnykMavenArtifact.empty
  ): DepTreeProvider = new MockDepTreeProvider(rootIds, mockTree)
}
