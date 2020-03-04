package io.snyk.plugin.ui.state

import io.snyk.plugin.datamodel.{SnykMavenArtifact, SnykVulnResponse}

case class PerProjectState(
  depTree: Option[SnykMavenArtifact] = None,
  scanResult: Option[Seq[SnykVulnResponse]] = None
)

