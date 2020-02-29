package io.snyk.plugin.depsource

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.{DataNode, ExternalProjectInfo, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import io.snyk.plugin.datamodel.SnykMavenArtifact
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

import scala.collection.JavaConverters._

trait DepTreeProvider {
  def rootIds: Seq[String]
  def getDepTree(rootId: String): Option[SnykMavenArtifact]
  def idToBuildToolProject(id: String): Option[BuildToolProject]
}

private class ProjectDepTreeProvider(project: Project) extends DepTreeProvider {

  private val logger = Logger.getInstance(this.getClass)

  private[this] def isMavenProject: Boolean = !MavenProjectsManager.getInstance(project).getProjects.isEmpty

  private[this] def isGradleProject: Boolean = !GradleSettings.getInstance(project).getLinkedProjectsSettings.isEmpty

  private[this] def getMavenBuildToolProjects: Seq[BuildToolProject] = {
    val mavenProjects = MavenProjectsManager.getInstance(project).getProjects.asScala

    mavenProjects.map(mavenProject => {
      MavenBuildToolProject(mavenProject, project.getBasePath)
    })
  }

  private[this] def getGradleBuildToolProjects: Seq[BuildToolProject] = {
    val gradleProjects = GradleSettings.getInstance(project).getLinkedProjectsSettings.asScala

    gradleProjects.map(gradleSettings => {
      val projectPath = gradleSettings.getExternalProjectPath

      val projectDataOption = findProjectData(project, GradleConstants.SYSTEM_ID, projectPath)

      if (projectDataOption.isDefined) {
        val gradleModuleData = ExternalSystemApiUtil.find(projectDataOption.get, ProjectKeys.MODULE)

        GradleBuildToolProject(gradleModuleData.getData, projectPath)
      } else {
        val emptyModuleData = new ModuleData(
          project.getName,
          new ProjectSystemId(ProjectType.GRADLE),
          ProjectType.GRADLE,
          project.getName,
          projectPath,
          projectPath)

        GradleBuildToolProject(emptyModuleData, projectPath)
      }
    }).toList
  }

  private[this] def getAllBuildToolProjects: Seq[BuildToolProject] = {
    if (isMavenProject) {
      getMavenBuildToolProjects
    } else if (isGradleProject) {
      getGradleBuildToolProjects
    } else {
      logger.info("Project type is not supported.")

      Seq()
    }
  }

  override def rootIds: Seq[String] = getAllBuildToolProjects.map(_.toString)

  override def idToBuildToolProject(id: String): Option[BuildToolProject] =
    getAllBuildToolProjects.find(_.toString == id)

  override def getDepTree(rootId: String): Option[SnykMavenArtifact] = {
    val projects = getAllBuildToolProjects
    val maybeBuildToolProject = idToBuildToolProject(rootId) orElse projects.headOption

    maybeBuildToolProject map SnykMavenArtifact.fromBuildToolProject
  }

  def findGradleModuleData(project: Project, projectPath: String): Option[DataNode[ModuleData]] = {
    val projectNodeOption = findProjectData(project, GradleConstants.SYSTEM_ID, projectPath)

    if (projectNodeOption.isDefined) {
      Option(ExternalSystemApiUtil.find(projectNodeOption.get, ProjectKeys.MODULE))
    } else {
      Option.empty
    }
  }

  def findProjectData(project: Project, systemId: ProjectSystemId, projectPath: String): Option[DataNode[ProjectData]] = {
    val projectInfo = findProjectInfo(project, systemId, projectPath)

    if (projectInfo == null) {
      return Option.empty
    }

    Option(projectInfo.getExternalProjectStructure)
  }

  def findProjectInfo(project: Project, systemId: ProjectSystemId, projectPath: String): ExternalProjectInfo = {
    val settings = ExternalSystemApiUtil.getSettings(project, systemId)

    val linkedProjectSettings = settings.getLinkedProjectSettings(projectPath)

    val rootProjectPath = linkedProjectSettings.getExternalProjectPath
    ProjectDataManager.getInstance.getExternalProjectData(project, systemId, rootProjectPath)
  }
}

private class MockDepTreeProvider(val rootIds: Seq[String], mockTree: SnykMavenArtifact) extends DepTreeProvider {
  override def getDepTree(rootId: String): Option[SnykMavenArtifact] = Some(mockTree)

  override def idToBuildToolProject(id: String): Option[BuildToolProject] = None
}

object DepTreeProvider {
  def forProject(project: Project): DepTreeProvider = new ProjectDepTreeProvider(project)
  def mock(rootIds: Seq[String] = Seq("dummy root"),
           mockTree: SnykMavenArtifact = SnykMavenArtifact.empty): DepTreeProvider = new MockDepTreeProvider(rootIds, mockTree)
}
