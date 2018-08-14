package io.snyk.plugin.depsource.externalproject

import java.time.{Instant, LocalDateTime}
import java.util.TimeZone

import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.{ExternalProjectInfo, ProjectSystemId}

class ExternProjInfo(underlying: ExternalProjectInfo) {
  private[this] def timestampToDate(timestamp: Long): LocalDateTime =
    LocalDateTime.ofInstant(
      Instant.ofEpochMilli(timestamp),
      TimeZone.getDefault().toZoneId()
    )
  def sysId: ProjectSystemId = underlying.getProjectSystemId
  def projPath: String = underlying.getExternalProjectPath
  def lastImport: LocalDateTime = timestampToDate(underlying.getLastImportTimestamp)
  def lastSuccessfulImport: LocalDateTime = timestampToDate(underlying.getLastSuccessfulImportTimestamp)
  def structure: SDataNode[ProjectData] = SDataNode fromModelDataNode underlying.getExternalProjectStructure
}
