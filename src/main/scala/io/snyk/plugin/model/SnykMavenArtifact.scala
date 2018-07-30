package io.snyk.plugin.model

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.model.MavenArtifactNode
import org.jetbrains.idea.maven.project.MavenProject

import scala.collection.JavaConverters._

object SnykMavenArtifact {
  def fromMavenArtifactNode(n: MavenArtifactNode): SnykMavenArtifact = {
//    println(s"dep tree for node: ${n.getDependencies}")

    SnykMavenArtifact(
      n.getArtifact.getGroupId,
      n.getArtifact.getArtifactId,
      if(StringUtil.isEmptyOrSpaces(n.getArtifact.getBaseVersion)) n.getArtifact.getVersion
      else n.getArtifact.getBaseVersion,
      n.getArtifact.getType,
      Option(n.getArtifact.getClassifier),
      Option(n.getArtifact.getScope),
      n.getDependencies.asScala.map { fromMavenArtifactNode }
    )
  }

  def fromMavenProject(proj: MavenProject): SnykMavenArtifact = {
    SnykMavenArtifact(
      proj.getMavenId.getGroupId,
      proj.getMavenId.getArtifactId,
      proj.getMavenId.getVersion,
      proj.getPackaging,
      None,
      None,
      proj.getDependencyTree.asScala.map { SnykMavenArtifact.fromMavenArtifactNode }
    )
  }

  val empty: SnykMavenArtifact = SnykMavenArtifact(
    "<none>",
    "<none>",
    "<none>",
    "<none>",
    None,
    None,
    Nil
  )
}

case class SnykMavenArtifact(
  groupId: String,
  artifactId: String,
  version: String,
  packaging: String,
  classifier: Option[String],
  scope: Option[String],
  deps: Seq[SnykMavenArtifact]
) {
  val name: String = s"$groupId:$artifactId"
  val depsMap: Map[String, SnykMavenArtifact] = deps.map(x => x.name -> x).toMap
  def toDisplayNode: DisplayNode = DisplayNode(name, version, deps.map(_.toDisplayNode))
}
