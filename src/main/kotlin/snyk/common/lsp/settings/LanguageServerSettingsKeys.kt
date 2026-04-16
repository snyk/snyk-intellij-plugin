package snyk.common.lsp.settings

object LsSettingsKeys {
  // Machine scope
  const val API_ENDPOINT = "api_endpoint"
  const val AUTHENTICATION_METHOD = "authentication_method"
  const val PROXY_INSECURE = "proxy_insecure"
  const val AUTOMATIC_DOWNLOAD = "automatic_download"
  const val CLI_PATH = "cli_path"
  const val BINARY_BASE_URL = "binary_base_url"
  const val CLI_RELEASE_CHANNEL = "cli_release_channel"
  const val ORGANIZATION = "organization"
  const val AUTOMATIC_AUTHENTICATION = "automatic_authentication"
  const val TRUST_ENABLED = "trust_enabled"
  const val FORMAT = "format"
  const val HOVER_VERBOSITY = "hover_verbosity"
  const val CLIENT_PROTOCOL_VERSION = "client_protocol_version"
  const val TRUSTED_FOLDERS = "trusted_folders"
  const val DEVICE_ID = "device_id"
  const val OS_PLATFORM = "os_platform"
  const val OS_ARCH = "os_arch"
  const val RUNTIME_NAME = "runtime_name"
  const val RUNTIME_VERSION = "runtime_version"

  // Write-only (IDE → LS only, never sent back)
  const val TOKEN = "token"
  const val SEND_ERROR_REPORTS = "send_error_reports"
  const val ENABLE_SNYK_LEARN_CODE_ACTIONS = "enable_snyk_learn_code_actions"
  const val ENABLE_SNYK_OSS_QUICK_FIX_CODE_ACTIONS = "enable_snyk_oss_quick_fix_code_actions"
  const val ENABLE_SNYK_OPEN_BROWSER_ACTIONS = "enable_snyk_open_browser_actions"
}

object LsFolderSettingsKeys {
  // Product enablement (folder-scope)
  const val SNYK_CODE_ENABLED = "snyk_code_enabled"
  const val SNYK_OSS_ENABLED = "snyk_oss_enabled"
  const val SNYK_IAC_ENABLED = "snyk_iac_enabled"
  const val SNYK_SECRETS_ENABLED = "snyk_secrets_enabled"

  // Filters (folder-scope)
  const val SEVERITY_FILTER_CRITICAL = "severity_filter_critical"
  const val SEVERITY_FILTER_HIGH = "severity_filter_high"
  const val SEVERITY_FILTER_MEDIUM = "severity_filter_medium"
  const val SEVERITY_FILTER_LOW = "severity_filter_low"
  const val RISK_SCORE_THRESHOLD = "risk_score_threshold"
  const val CWE_IDS = "cwe_ids"
  const val CVE_IDS = "cve_ids"
  const val RULE_IDS = "rule_ids"
  const val ISSUE_VIEW_OPEN_ISSUES = "issue_view_open_issues"
  const val ISSUE_VIEW_IGNORED_ISSUES = "issue_view_ignored_issues"

  // Scan behaviour (folder-scope)
  const val SCAN_AUTOMATIC = "scan_automatic"
  const val SCAN_NET_NEW = "scan_net_new"

  // Folder-specific settings
  const val BASE_BRANCH = "base_branch"
  const val REFERENCE_BRANCH = "reference_branch"
  const val REFERENCE_FOLDER = "reference_folder"
  const val ADDITIONAL_PARAMETERS = "additional_parameters"
  const val CLI_ADDITIONAL_OSS_PARAMETERS = "cli_additional_oss_parameters"
  const val ADDITIONAL_ENVIRONMENT = "additional_environment"
  const val LOCAL_BRANCHES = "local_branches"
  const val PREFERRED_ORG = "preferred_org"
  const val AUTO_DETERMINED_ORG = "auto_determined_org"
  const val ORG_SET_BY_USER = "org_set_by_user"
  const val SCAN_COMMAND_CONFIG = "scan_command_config"
  const val SAST_SETTINGS = "sast_settings"
}
