package snyk.common.lsp.settings

import snyk.common.lsp.ScanCommandConfig

@Suppress("LongParameterList")
fun folderConfig(
  folderPath: String,
  baseBranch: String = "main",
  localBranches: List<String>? = emptyList(),
  additionalParameters: List<String>? = emptyList(),
  additionalEnv: String? = "",
  referenceFolderPath: String? = "",
  preferredOrg: String = "",
  autoDeterminedOrg: String = "",
  orgSetByUser: Boolean = false,
  scanCommandConfig: Map<String, ScanCommandConfig>? = emptyMap(),
): LspFolderConfig {
  val settings = mutableMapOf<String, ConfigSetting>()
  settings[LsFolderSettingsKeys.BASE_BRANCH] = ConfigSetting(value = baseBranch)
  localBranches?.let { settings[LsFolderSettingsKeys.LOCAL_BRANCHES] = ConfigSetting(value = it) }
  additionalParameters?.let {
    settings[LsFolderSettingsKeys.ADDITIONAL_PARAMETERS] = ConfigSetting(value = it)
  }
  additionalEnv?.let {
    settings[LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT] = ConfigSetting(value = it)
  }
  referenceFolderPath?.let {
    settings[LsFolderSettingsKeys.REFERENCE_FOLDER] = ConfigSetting(value = it)
  }
  settings[LsFolderSettingsKeys.PREFERRED_ORG] = ConfigSetting(value = preferredOrg)
  settings[LsFolderSettingsKeys.AUTO_DETERMINED_ORG] = ConfigSetting(value = autoDeterminedOrg)
  settings[LsFolderSettingsKeys.ORG_SET_BY_USER] = ConfigSetting(value = orgSetByUser)
  scanCommandConfig?.let {
    settings[LsFolderSettingsKeys.SCAN_COMMAND_CONFIG] = ConfigSetting(value = it)
  }
  return LspFolderConfig(folderPath = folderPath, settings = settings)
}
