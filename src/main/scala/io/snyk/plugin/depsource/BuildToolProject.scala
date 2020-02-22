package io.snyk.plugin.depsource

import java.io.File

import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.project.MavenProject
import java.util

trait BuildToolProject {
  def getGroupId: String

  def getArtifactId: String

  def getVersion: String

  def getPackaging: String

  def getFile: VirtualFile

  def getType: String

  def getProjectDirectoryPath: String

  override def toString: String = {
    val groupName = normalizeString(getGroupId)
    val artifactName = normalizeString(getArtifactId)
    val version = normalizeString(getVersion)

    s"$groupName:$artifactName:$version"
  }

  private def normalizeString(originalString: String): String = {
    if (originalString == null || originalString == "unspecified") {
      ""
    } else {
      originalString
    }
  }
}

case class MavenBuildToolProject(mavenProject: MavenProject, projectDirectoryPath: String) extends BuildToolProject {

  def findDependencies(groupId: String, artifactId: String): util.List[MavenArtifact] = {
    mavenProject.findDependencies(groupId, artifactId)
  }

  override def getGroupId: String = mavenProject.getMavenId.getGroupId

  override def getArtifactId: String = mavenProject.getMavenId.getArtifactId

  override def getVersion: String = mavenProject.getMavenId.getVersion

  override def getPackaging: String = mavenProject.getPackaging

  override def getFile: VirtualFile = mavenProject.getFile

  override def getType: String = ProjectType.MAVEN

  override def getProjectDirectoryPath: String = projectDirectoryPath
}

case class GradleBuildToolProject(moduleData: ModuleData, projectDirectoryPath: String) extends BuildToolProject {

  private val BUILD_GRADLE_FILE_NAME = "build.gradle"

  override def getGroupId: String = moduleData.getGroup

  override def getArtifactId: String = moduleData.getId

  override def getVersion: String = moduleData.getVersion

  override def getPackaging: String = ""

  override def getFile: VirtualFile = {
    import com.intellij.openapi.vfs.VfsUtil

    val gradleBuildFile = new File(moduleData.getLinkedExternalProjectPath + "/" + BUILD_GRADLE_FILE_NAME)

    VfsUtil.findFileByIoFile(gradleBuildFile, true)
  }

  override def getType: String = ProjectType.GRADLE

  override def getProjectDirectoryPath: String = projectDirectoryPath
}
