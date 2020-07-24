package io.snyk.plugin.ui.settings

import java.time.LocalDate

import com.intellij.openapi.components.BaseState

case class SnykIntelliJSettingsState(
  var customEndpointUrl: String,
  var organization: String,
  var ignoreUnknownCA: Boolean,
  var cliVersion: String,
  var lastCheckDate: LocalDate,
  var additionalParameters: String) extends BaseState() {
}

object SnykIntelliJSettingsState {
  def apply(
    customEndpointUrl: String = "",
    organization: String = "",
    ignoreUnknownCA: Boolean = false,
    cliVersion: String = "",
    lastCheckDate: LocalDate = null,
    additionalParameters: String = ""): SnykIntelliJSettingsState =
      new SnykIntelliJSettingsState(customEndpointUrl, organization, ignoreUnknownCA, cliVersion, lastCheckDate, additionalParameters)

  val Empty: SnykIntelliJSettingsState = SnykIntelliJSettingsState()
}
