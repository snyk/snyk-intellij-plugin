package io.snyk.plugin.datamodel

import com.intellij.openapi.util.text.StringUtil
import io.snyk.plugin.depsource.BuildToolProject
import org.jetbrains.idea.maven.model.MavenArtifactNode
import org.jetbrains.idea.maven.project.MavenProject

import scala.collection.JavaConverters._

object ProjectDependency {
  def fromMavenArtifactNode(n: MavenArtifactNode): ProjectDependency = {
    //    log.debug(s"dep tree for node: ${n.getDependencies}")

    ProjectDependency(
      n.getArtifact.getGroupId,
      n.getArtifact.getArtifactId,
      if(StringUtil.isEmptyOrSpaces(n.getArtifact.getBaseVersion)) n.getArtifact.getVersion
      else n.getArtifact.getBaseVersion,
      n.getArtifact.getType,
      Option(n.getArtifact.getClassifier),
      Option(n.getArtifact.getScope),
      n.getDependencies.asScala.map { fromMavenArtifactNode },
      "Maven"
    )
  }

  def fromMavenProject(proj: MavenProject): ProjectDependency = {
    ProjectDependency(
      proj.getMavenId.getGroupId,
      proj.getMavenId.getArtifactId,
      proj.getMavenId.getVersion,
      proj.getPackaging,
      None,
      None,
      proj.getDependencyTree.asScala.map { ProjectDependency.fromMavenArtifactNode },
      "Maven"
    )
  }

  def fromBuildToolProject(buildToolProject: BuildToolProject): ProjectDependency = {
    ProjectDependency(
      buildToolProject.getGroupId,
      buildToolProject.getArtifactId,
      buildToolProject.getVersion,
      buildToolProject.getPackaging,
      None,
      None,
      List.empty[ProjectDependency],
      buildToolProject.getType
    )
  }

  val empty: ProjectDependency = ProjectDependency(
    "<none>",
    "<none>",
    "<none>",
    "<none>",
    None,
    None,
    Nil,
    ""
  )
}

case class ProjectDependency(
    groupId: String,
    artifactId: String,
    version: String,
    packaging: String,
    classifier: Option[String],
    scope: Option[String],
    deps: Seq[ProjectDependency],
    projectType: String) {
  val name: String = s"$groupId:$artifactId"
  val depsMap: Map[String, ProjectDependency] = deps.map(x => x.name -> x).toMap
}
