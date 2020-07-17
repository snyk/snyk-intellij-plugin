package io.snyk.plugin.client

import java.nio.charset.Charset
import java.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.execution.{MavenRunner, MavenRunnerParameters}
import org.jetbrains.idea.maven.project.{MavenProject, MavenProjectsManager}

/**
  * Encapsulate work with IntelliJ OpenAPI for ScriptRunnerUtil and MavenRunner.
  */
class ConsoleCommandRunner {

  def execute(commands: util.ArrayList[String], workDirectory: String = "/"): String = {
    val generalCommandLine = new GeneralCommandLine(commands)

    generalCommandLine.setCharset(Charset.forName("UTF-8"))
    generalCommandLine.setWorkDirectory(workDirectory)

    ScriptRunnerUtil.getProcessOutput(generalCommandLine, ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER, 720000)
  }

  def runMavenInstallGoal(project: Project): Unit = {
    if (project.isDisposed) {
      return
    }

    WriteAction.runAndWait(() => {
      val projectsManager = MavenProjectsManager.getInstance(project)

      val explicitProfiles = projectsManager.getExplicitProfiles

      val mavenProjects = projectsManager.getRootProjects
      val mavenProject: MavenProject = mavenProjects.get(0)

      val goals: util.List[String] = new util.ArrayList[String]
      goals.add("install")

      val parameters = new MavenRunnerParameters(true,
        mavenProject.getDirectory,
        mavenProject.getFile.getName,
        goals,
        explicitProfiles.getEnabledProfiles,
        explicitProfiles.getDisabledProfiles)

      val mavenRunner = MavenRunner.getInstance(project)

      mavenRunner.run(parameters, mavenRunner.getSettings, null)
    })
  }
}
