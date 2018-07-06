package io.snyk.plugin

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager

fun Project.dependencyTreeRoot(): MavenDepRoot {
    val mp = MavenProjectsManager.getInstance(this).projects.first()
    return MavenDepRoot.fromMavenProject(mp)
}