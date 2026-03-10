package snyk.common.lsp.settings

object LsSettingsKeys {
  const val SNYK_CODE_ENABLED = "snyk_code_enabled"
  const val SNYK_OSS_ENABLED = "snyk_oss_enabled"
  const val SNYK_IAC_ENABLED = "snyk_iac_enabled"
  const val SNYK_SECRETS_ENABLED = "snyk_secrets_enabled"
  const val PROXY_INSECURE = "proxy_insecure"
  const val API_ENDPOINT = "api_endpoint"
  const val ORGANIZATION = "organization"
  const val SEND_ERROR_REPORTS = "send_error_reports"
  const val AUTOMATIC_DOWNLOAD = "automatic_download"
  const val CLI_PATH = "cli_path"
  const val BINARY_BASE_URL = "binary_base_url"
  const val TOKEN = "token"
  const val AUTOMATIC_AUTHENTICATION = "automatic_authentication"
  const val ENABLED_SEVERITIES = "enabled_severities"
  const val RISK_SCORE_THRESHOLD = "risk_score_threshold"
  const val ISSUE_VIEW_OPEN_ISSUES = "issue_view_open_issues"
  const val ISSUE_VIEW_IGNORED_ISSUES = "issue_view_ignored_issues"
  const val TRUST_ENABLED = "trust_enabled"
  const val SCAN_AUTOMATIC = "scan_automatic"
  const val AUTHENTICATION_METHOD = "authentication_method"
  const val ENABLE_SNYK_OSS_QUICK_FIX_CODE_ACTIONS = "enable_snyk_oss_quick_fix_code_actions"
  const val SCAN_NET_NEW = "scan_net_new"

  // Environment Information
  const val INTEGRATION_NAME = "integration_name"
  const val INTEGRATION_VERSION = "integration_version"
  const val INTEGRATION_ENVIRONMENT = "integration_environment"
  const val INTEGRATION_ENVIRONMENT_VERSION = "integration_environment_version"
  const val DEVICE_ID = "device_id"
  const val OS_PLATFORM = "os_platform"
  const val OS_ARCH = "os_arch"
  const val RUNTIME_NAME = "runtime_name"
  const val RUNTIME_VERSION = "runtime_version"
}

object LsFolderSettingsKeys {
  const val BASE_BRANCH = "base_branch"
  const val ADDITIONAL_ENVIRONMENT = "additional_environment"
  const val ADDITIONAL_PARAMETERS = "additional_parameters"
  const val LOCAL_BRANCHES = "local_branches"
  const val REFERENCE_FOLDER = "reference_folder"
  const val PREFERRED_ORG = "preferred_org"
  const val AUTO_DETERMINED_ORG = "auto_determined_org"
  const val ORG_SET_BY_USER = "org_set_by_user"
  const val SCAN_COMMAND_CONFIG = "scan_command_config"
}
