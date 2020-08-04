package io.snyk.plugin.client

import java.nio.charset.Charset
import java.util
import java.util.Objects.nonNull

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.{ProcessAdapter, ProcessEvent, ScriptRunnerUtil}
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import io.snyk.plugin.IntellijLogging
import io.snyk.plugin.embeddedserver.ParamSet
import io.snyk.plugin.ui.state.SnykPluginState
import monix.execution.atomic.Atomic
import org.jetbrains.idea.maven.execution.{MavenRunConfigurationType, MavenRunnerParameters}
import org.jetbrains.idea.maven.project.{MavenProject, MavenProjectsManager}

/**
  * Encapsulate work with IntelliJ OpenAPI for ScriptRunnerUtil and MavenRunner.
  */
class ConsoleCommandRunner extends IntellijLogging {

  val terminationMessage: Atomic[String] = Atomic("")

  def execute(commands: util.ArrayList[String], workDirectory: String = "/"): String = {
    log.info("Enter ConsoleCommandRunner.execute()")
    log.info(s"Commands: $commands")

    val generalCommandLine = new GeneralCommandLine(commands)

    generalCommandLine.setCharset(Charset.forName("UTF-8"))
    generalCommandLine.setWorkDirectory(workDirectory)

    log.info("GeneralCommandLine instance created.")
    log.info("Execute ScriptRunnerUtil.getProcessOutput(...)")

    ScriptRunnerUtil.getProcessOutput(generalCommandLine, ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER, 720000)
  }

  def runMavenInstall(project: Project): Unit =
    WriteAction.runAndWait(() => {
      terminationMessage := ""

      log.info("Enter runInstall()")

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

      execute(project, parameters)
    })

  /**
    * Run Maven task with result.
    *
    * @param parameters - MavenRunnerParameters
    *
    * @return OutputInfo
    */
  def execute(project: Project, parameters: MavenRunnerParameters): Unit = {
    log.info("Enter execute() method")

    FileDocumentManager.getInstance.saveAllDocuments()

    val callback: ProgramRunner.Callback = (descriptor: RunContentDescriptor) => {
      val handler = descriptor.getProcessHandler

      if (nonNull(handler)) {
        log.info("Adding ProcessListener for run handler.")

        handler.addProcessListener(new ProcessAdapter() {
          override def processTerminated(event: ProcessEvent): Unit = {
            log.info("Enter to processTerminated() method.")

            val exitCode = event.getExitCode

            log.info(s"mnv install exit code is $exitCode")

            if (exitCode != 0) {
              log.info("Try to navigate to display error message.")

              terminationMessage := s"[mnv install] failed with exit code $exitCode"

              SnykPluginState.getInstance(project)
                .navigator()
                .navigateTo("/error", ParamSet.Empty.plus("errmsg" -> terminationMessage.get))

              log.info("After error display.")
            }
          }
        })
      }
    }

    log.info("Start MavenRunConfigurationType.runConfiguration.")

    MavenRunConfigurationType.runConfiguration(project, parameters, callback)

    log.info("Return result.")
  }
}

object ConsoleCommandRunner {
  def apply() = new ConsoleCommandRunner
}