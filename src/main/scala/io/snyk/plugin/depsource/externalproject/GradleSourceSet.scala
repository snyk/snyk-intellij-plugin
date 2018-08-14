package io.snyk.plugin.depsource.externalproject

import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData
import io.snyk.plugin.datamodel.MiniTree

case class GradleSourceSet(
  id: String,
  depRoots: Seq[MiniTree[LibraryDependencyData]]
) {
  def toMultiLineString: String =
    (s"Gradle Source Set: $id" +: depRoots.flatMap(_.treeString)).mkString("\n")
}
