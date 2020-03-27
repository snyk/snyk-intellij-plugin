package io.snyk.plugin.ui.state

import io.snyk.plugin.datamodel.{ProjectDependency, SnykVulnResponse}

case class PerProjectState(
  depTree: Option[ProjectDependency] = None,
  scanResult: Option[Seq[SnykVulnResponse]] = None
)

