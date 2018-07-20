package io.snyk.plugin.model

import io.snyk.plugin.ui.SnykHtmlPanel


case class SnykPluginState(
  htmlPanel: Option[SnykHtmlPanel] = None,
  latestScanResult: SnykVulnResponse = SnykVulnResponse.empty
) {
  def withLatestScanResult(x: SnykVulnResponse): SnykPluginState = copy(latestScanResult = x)
  def withHtmlPanel(x: SnykHtmlPanel): SnykPluginState = copy(htmlPanel = Some(x))
}
