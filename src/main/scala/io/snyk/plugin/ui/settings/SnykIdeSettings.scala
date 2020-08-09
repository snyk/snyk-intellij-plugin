package io.snyk.plugin.ui.settings

import java.time.LocalDate

/**
  * Contains application and project settings in one object.
  */
case class SnykIdeSettings(
  var customEndpointUrl: String,
  var organization: String,
  var isIgnoreUnknownCA: Boolean,
  var cliVersion: String,
  var lastCheckDate: LocalDate,
  var additionalParameters: String) {
}

object SnykIdeSettings {
  def apply(
    customEndpointUrl: String = "",
    organization: String = "",
    isIgnoreUnknownCA: Boolean = false,
    cliVersion: String = "",
    lastCheckDate: LocalDate = null,
    additionalParameters: String = ""): SnykIdeSettings =
    new SnykIdeSettings(customEndpointUrl, organization, isIgnoreUnknownCA, cliVersion, lastCheckDate, additionalParameters)

  val Empty: SnykIdeSettings = SnykIdeSettings()
}
