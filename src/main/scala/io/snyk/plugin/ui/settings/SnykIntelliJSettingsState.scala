package io.snyk.plugin.ui.settings

import java.util.Date

import com.intellij.openapi.components.BaseState

case class SnykIntelliJSettingsState(
  var customEndpointUrl: String,
  var organization: String,
  var ignoreUnknownCA: Boolean,
  var cliVersion: String,
  var lastUpdateDate: Date) extends BaseState() {
}

object SnykIntelliJSettingsState {
  def apply(
    customEndpointUrl: String = "",
    organization: String = "",
    ignoreUnknownCA: Boolean = false,
    cliVersion: String = "",
    lastUpdateDate: Date = null): SnykIntelliJSettingsState =
    new SnykIntelliJSettingsState(customEndpointUrl, organization, ignoreUnknownCA, cliVersion, lastUpdateDate)

  val Empty: SnykIntelliJSettingsState = SnykIntelliJSettingsState()
}
