package io.snyk.plugin.depsource.externalproject

import com.intellij.openapi.externalSystem.model.project.ProjectData

package object externalproject {


  implicit class RichProjectData(val underlying: ProjectData) extends AnyVal {
    def group: String = underlying.getGroup
    def id: String = underlying.getId
    def version: String = underlying.getVersion
    def description: String = underlying.getDescription
    def ideProjectFileDirectoryPath: String = underlying.getIdeProjectFileDirectoryPath
    def linkedExternalProjectPath: String = underlying.getLinkedExternalProjectPath
  }

}
