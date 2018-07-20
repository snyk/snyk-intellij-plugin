package io.snyk.plugin

import com.intellij.openapi.project.Project
import io.snyk.plugin.model.SnykMavenArtifact
import org.jetbrains.idea.maven.project.MavenProjectsManager

object EnrichedMethods {

  implicit class RichProject(val p: Project) extends AnyVal {
    def toDepNode: SnykMavenArtifact = {
      val mp = MavenProjectsManager.getInstance(p).getProjects.get(0)
      SnykMavenArtifact.fromMavenProject(mp)
    }
  }

}
