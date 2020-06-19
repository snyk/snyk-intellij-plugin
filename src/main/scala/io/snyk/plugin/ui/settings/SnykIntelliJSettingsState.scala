package io.snyk.plugin.ui.settings

import java.time.LocalDate

import com.intellij.openapi.components.BaseState

case class SnykIntelliJSettingsState(
  var customEndpointUrl: String,
  var organization: String,
  var ignoreUnknownCA: Boolean,
  var cliVersion: String,
  var lastCheckDate: LocalDate) extends BaseState() {
}

object SnykIntelliJSettingsState {
  def apply(
    customEndpointUrl: String = "",
    organization: String = "",
    ignoreUnknownCA: Boolean = false,
    cliVersion: String = "",
    lastCheckDate: LocalDate = null): SnykIntelliJSettingsState =
    new SnykIntelliJSettingsState(customEndpointUrl, organization, ignoreUnknownCA, cliVersion, lastCheckDate)

  val Empty: SnykIntelliJSettingsState = SnykIntelliJSettingsState()
}
