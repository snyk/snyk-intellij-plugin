package io.snyk.plugin.depsource

import com.intellij.openapi.externalSystem.model.{DataNode, ExternalProjectInfo, ProjectKeys, ProjectSystemId}
import com.intellij.openapi.externalSystem.model.project.{ModuleData, ProjectData}
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import io.snyk.plugin.datamodel.SnykMavenArtifact
import org.jetbrains.idea.maven.project.{MavenProject, MavenProjectsManager}
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

import scala.collection.JavaConverters._

trait DepTreeProvider {
  def rootIds: Seq[String]
  def getDepTree(rootId: String): Option[SnykMavenArtifact]
  def idToMavenProject(id: String): Option[MavenProject]
  def idToBuildToolProject(id: String): Option[BuildToolProject]
}

private class ProjectDepTreeProvider(project: Project) extends DepTreeProvider {
  private[this] def getAllBuildToolProjects: Seq[BuildToolProject] = {
    val mavenProjects = MavenProjectsManager.getInstance(project).getProjects.asScala

    if (mavenProjects.nonEmpty) {
      mavenProjects.map(mavenProject => {
        MavenBuildToolProject(mavenProject)
      })
    } else {
      val gradleProjects = GradleSettings.getInstance(project).getLinkedProjectsSettings.asScala

      gradleProjects.map(gradleSettings => {
        val projectPath = gradleSettings.getExternalProjectPath

        val projectData = findProjectData(project, GradleConstants.SYSTEM_ID, projectPath)

        val gradleModuleData = ExternalSystemApiUtil.find(projectData, ProjectKeys.MODULE)

        GradleBuildToolProject(gradleModuleData.getData)
      }).toList
    }
  }

  override def rootIds: Seq[String] = getAllBuildToolProjects.map(_.toString)

  override def idToMavenProject(id: String): Option[MavenProject] = ???

  override def idToBuildToolProject(id: String): Option[BuildToolProject] =
    getAllBuildToolProjects.find(_.toString == id)

  override def getDepTree(rootId: String): Option[SnykMavenArtifact] = {
    val projects = getAllBuildToolProjects
    val maybeBuildToolProject = idToBuildToolProject(rootId) orElse projects.headOption

    maybeBuildToolProject map SnykMavenArtifact.fromBuildToolProject
  }

  def findGradleModuleData(project: Project, projectPath: String): DataNode[ModuleData] = {
    val projectNode = findProjectData(project, GradleConstants.SYSTEM_ID, projectPath)

    val predicate = (node: DataNode[ModuleData]) => projectPath == node.getData.getLinkedExternalProjectPath

    //    ExternalSystemApiUtil.find(projectNode, ProjectKeys.MODULE, predicate)
    ExternalSystemApiUtil.find(projectNode, ProjectKeys.MODULE)
  }

  def findProjectData(project: Project, systemId: ProjectSystemId, projectPath: String): DataNode[ProjectData] = {
    val projectInfo = findProjectInfo(project, systemId, projectPath)

    if (projectInfo == null) {
      return null
    }

    projectInfo.getExternalProjectStructure
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
  override def idToMavenProject(id: String): Option[MavenProject] = None

  override def idToBuildToolProject(id: String): Option[BuildToolProject] = None
}

object DepTreeProvider {
  def forProject(project: Project): DepTreeProvider = new ProjectDepTreeProvider(project)
  def mock(
            rootIds: Seq[String] = Seq("dummy root"),
            mockTree: SnykMavenArtifact = SnykMavenArtifact.empty
          ): DepTreeProvider = new MockDepTreeProvider(rootIds, mockTree)
}
