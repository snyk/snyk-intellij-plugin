package snyk.common.lsp.settings

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import io.snyk.plugin.Severity
import io.snyk.plugin.fromUriToPath
import io.snyk.plugin.pluginSettings
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.annotations.NotNull
import snyk.SnykBundle
import snyk.common.ProductType
import snyk.common.lsp.LanguageServerWrapper

@Suppress("UselessCallOnCollection")
@Service(Service.Level.APP)
class FolderConfigSettings {
  private val logger = Logger.getInstance(FolderConfigSettings::class.java)
  private val configs: MutableMap<String, LspFolderConfig> =
    ConcurrentHashMap<String, LspFolderConfig>()

  @Suppress(
    "UselessCallOnNotNull",
    "USELESS_ELVIS",
    "UNNECESSARY_SAFE_CALL",
    "RedundantSuppression",
  )
  fun addFolderConfig(@NotNull folderConfig: LspFolderConfig) {
    if (folderConfig.folderPath.isNullOrBlank()) return
    val normalizedAbsolutePath = normalizePath(folderConfig.folderPath)
    configs[normalizedAbsolutePath] = folderConfig.copy(folderPath = normalizedAbsolutePath)
  }

  private fun normalizePath(folderPath: String): String {
    val normalizedAbsolutePath = Paths.get(folderPath).normalize().toAbsolutePath().toString()
    return normalizedAbsolutePath
  }

  internal fun getFolderConfig(folderPath: String): LspFolderConfig {
    val normalizedPath = normalizePath(folderPath)
    val folderConfig = configs[normalizedPath] ?: createEmpty(normalizedPath)
    return folderConfig
  }

  private fun createEmpty(normalizedAbsolutePath: String): LspFolderConfig {
    val newConfig =
      LspFolderConfig(
        folderPath = normalizedAbsolutePath,
        settings =
          mapOf(
            LsFolderSettingsKeys.BASE_BRANCH to ConfigSetting(value = "main"),
            LsFolderSettingsKeys.LOCAL_BRANCHES to ConfigSetting(value = emptyList<String>()),
            LsFolderSettingsKeys.ADDITIONAL_PARAMETERS to
              ConfigSetting(value = emptyList<String>()),
            LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT to ConfigSetting(value = ""),
            LsFolderSettingsKeys.REFERENCE_FOLDER to ConfigSetting(value = ""),
            LsFolderSettingsKeys.PREFERRED_ORG to ConfigSetting(value = ""),
            LsFolderSettingsKeys.AUTO_DETERMINED_ORG to ConfigSetting(value = ""),
            LsFolderSettingsKeys.ORG_SET_BY_USER to ConfigSetting(value = false),
            LsFolderSettingsKeys.SCAN_COMMAND_CONFIG to
              ConfigSetting(value = emptyMap<String, Any>()),
          ),
      )
    configs[normalizedAbsolutePath] = newConfig
    return newConfig
  }

  fun getAll(): Map<String, LspFolderConfig> = HashMap(configs)

  fun clear() = configs.clear()

  fun addAll(folderConfigs: List<LspFolderConfig>) =
    folderConfigs.mapNotNull { addFolderConfig(it) }

  /**
   * Gets all folder configs for a project. This method delegates to getFolderConfigs() to ensure
   * only workspace folder configs are returned, avoiding nested folder config issues.
   *
   * @param project the project to get the folder configs for
   * @return the folder configs for workspace folders only (no nested paths)
   */
  fun getAllForProject(project: Project): List<LspFolderConfig> =
    getFolderConfigs(project).sortedBy { it.folderPath }.toList()

  /**
   * Gets the additional parameters for the given project by aggregating the folder configs with
   * workspace folder paths.
   *
   * @param project the project to get the additional parameters for
   * @return the additional parameters for the project
   */
  fun getAdditionalParameters(project: Project): String {
    // only use folder config with workspace folder path
    val additionalParameters =
      getFolderConfigs(project)
        .map { config ->
          (config.settings?.get(LsFolderSettingsKeys.ADDITIONAL_PARAMETERS)?.value as? List<*>)
            ?.filterIsInstance<String>() ?: emptyList()
        }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.joinToString(" ") }
    return additionalParameters
  }

  /**
   * Gets the folder configs for the given project by aggregating the folder configs with workspace
   * folder paths.
   *
   * @param project the project to get the folder configs for
   * @return the folder configs for the project
   */
  fun getFolderConfigs(project: Project): List<LspFolderConfig> {
    val languageServerWrapper = LanguageServerWrapper.getInstance(project)
    return languageServerWrapper
      .getWorkspaceFoldersFromRoots(project, promptForTrust = false)
      .asSequence()
      .filter { languageServerWrapper.configuredWorkspaceFolders.contains(it) }
      .map { getFolderConfig(it.uri.fromUriToPath().toString()) }
      .toList()
  }

  /**
   * Gets the preferred organization for the given project by aggregating the folder configs with
   * workspace folder paths.
   *
   * @param project the project to get the preferred organization for
   * @return the preferred organization for the project
   */
  fun getPreferredOrg(project: Project): String {
    // Note - this will not work for projects with extra content roots outside of the the main
    // workspace folder.
    return getFolderConfigs(project)
      .firstOrNull()
      ?.settings
      ?.get(LsFolderSettingsKeys.PREFERRED_ORG)
      ?.value as? String ?: ""
  }

  /**
   * Checks if auto-organization is enabled for the given project. Returns true if orgSetByUser is
   * false (auto-detect enabled), false otherwise.
   *
   * @param project the project to check
   * @return true if auto-organization is enabled
   */
  fun isAutoOrganizationEnabled(project: Project): Boolean =
    getFolderConfigs(project).firstOrNull()?.let {
      !(it.settings?.get(LsFolderSettingsKeys.ORG_SET_BY_USER)?.value as? Boolean ?: false)
    } ?: true

  /**
   * Sets the auto-organization setting for the given project.
   *
   * @param project the project to update
   * @param autoOrganization true to enable auto-organization, false to use preferred organization
   */
  fun setAutoOrganization(project: Project, autoOrganization: Boolean) {
    getFolderConfigs(project).forEach { folderConfig ->
      val updatedConfig =
        folderConfig.withSetting(
          LsFolderSettingsKeys.ORG_SET_BY_USER,
          !autoOrganization,
          changed = true,
        )
      addFolderConfig(updatedConfig)
    }
  }

  /**
   * Sets the organization for the given project.
   *
   * @param project the project to update
   * @param organization the organization to set
   */
  fun setOrganization(project: Project, organization: String?) {
    getFolderConfigs(project).forEach { folderConfig ->
      val updatedConfig =
        folderConfig.withSetting(
          LsFolderSettingsKeys.PREFERRED_ORG,
          organization ?: "",
          changed = true,
        )
      addFolderConfig(updatedConfig)
    }
  }

  /**
   * Migrates folder configs by handling nested folder configs with user interaction. A nested
   * folder config is one where the folderPath is a subdirectory of a workspace folder.
   *
   * Scenarios handled:
   * 1. Sub-config with default values: silently remove
   * 2. Single sub-config with non-default values: prompt user to merge into parent or discard
   * 3. Multiple sub-configs with conflicting values: keep subs, offer to remove parent
   *
   * @param project the project to migrate folder configs for
   * @return the number of folder configs removed
   */
  fun migrateNestedFolderConfigs(project: Project): Int {
    val languageServerWrapper = LanguageServerWrapper.getInstance(project)
    val workspaceFolderPaths =
      languageServerWrapper
        .getWorkspaceFoldersFromRoots(project, promptForTrust = false)
        .map {
          Paths.get(it.uri.fromUriToPath().toString()).normalize().toAbsolutePath().toString()
        }
        .toSet()

    if (workspaceFolderPaths.isEmpty()) {
      logger.debug("No workspace folders found, skipping migration")
      return 0
    }

    var removedCount = 0

    // Process each workspace folder and its nested configs
    for (workspacePath in workspaceFolderPaths) {
      val parentConfig = configs[workspacePath] ?: continue

      // Find all nested configs under this workspace folder
      val nestedConfigs =
        configs.keys
          .filter { configPath ->
            configPath != workspacePath && isPathNestedUnder(configPath, workspacePath)
          }
          .mapNotNull { path -> configs[path]?.let { path to it } }
          .toMap()

      if (nestedConfigs.isEmpty()) continue

      // Separate nested configs into those with default values and those with custom values
      val customNestedConfigs =
        nestedConfigs.filter { (_, config) -> hasNonDefaultValues(config, parentConfig) }

      // Remove nested configs with default values silently
      val defaultNestedConfigs = nestedConfigs.keys - customNestedConfigs.keys
      for (path in defaultNestedConfigs) {
        logger.info("Removing nested folder config with default values: $path")
        configs.remove(path)
        removedCount++
      }

      if (customNestedConfigs.isEmpty()) continue

      // Check if multiple custom configs have conflicting values between each other
      val hasConflictingSubConfigs =
        customNestedConfigs.size > 1 && hasConflictingConfigs(customNestedConfigs.values.toList())

      if (hasConflictingSubConfigs) {
        // Multiple conflicting sub-configs: offer to remove parent
        removedCount +=
          handleMultipleConflictingConfigs(project, workspacePath, customNestedConfigs)
      } else {
        // Single sub-config (or multiple with same values): prompt to merge or discard
        for ((subPath, subConfig) in customNestedConfigs) {
          removedCount +=
            handleSingleCustomSubConfig(project, workspacePath, parentConfig, subPath, subConfig)
        }
      }
    }

    if (removedCount > 0) {
      logger.info("Migrated $removedCount folder configs for project ${project.name}")
    }

    return removedCount
  }

  /**
   * Handles a single sub-config with non-default values. Prompts user to merge into parent, use
   * parent values, or keep as is.
   */
  private fun handleSingleCustomSubConfig(
    project: Project,
    parentPath: String,
    parentConfig: LspFolderConfig,
    subPath: String,
    subConfig: LspFolderConfig,
  ): Int {
    val choice = promptForSingleSubConfigMigration(project, parentPath, subPath)

    return when (choice) {
      MigrationChoice.MERGE_INTO_PARENT -> {
        logger.info("Merging sub-config $subPath into parent $parentPath")
        val mergedConfig = mergeConfigs(parentConfig, subConfig)
        configs[parentPath] = mergedConfig
        configs.remove(subPath)
        1
      }
      MigrationChoice.USE_PARENT_VALUES -> {
        logger.info("Discarding sub-config $subPath, using parent values")
        configs.remove(subPath)
        1
      }
      MigrationChoice.KEEP_AS_IS -> {
        logger.info("Keeping sub-config $subPath as is")
        0
      }
      else -> 0
    }
  }

  /**
   * Handles multiple sub-configs with conflicting values. Offers to remove parent and keep
   * sub-configs.
   */
  private fun handleMultipleConflictingConfigs(
    project: Project,
    parentPath: String,
    customNestedConfigs: Map<String, LspFolderConfig>,
  ): Int {
    val choice =
      promptForMultipleConflictingMigration(project, parentPath, customNestedConfigs.keys.toList())

    return when (choice) {
      MigrationChoice.REMOVE_PARENT -> {
        logger.info("Removing parent config $parentPath, keeping sub-configs")
        configs.remove(parentPath)
        1
      }
      MigrationChoice.KEEP_ALL -> {
        logger.info("Keeping all configs including parent $parentPath")
        0
      }
      else -> 0
    }
  }

  /** Prompts user for single sub-config migration choice. */
  internal fun promptForSingleSubConfigMigration(
    project: Project,
    parentPath: String,
    subPath: String,
  ): MigrationChoice {
    var choice = MigrationChoice.KEEP_AS_IS

    invokeAndWaitIfNeeded {
      val title = SnykBundle.message("snyk.folderConfig.migration.title")
      val message =
        SnykBundle.message(
          "snyk.folderConfig.migration.singleSubConfig.message",
          parentPath,
          subPath,
        )
      val mergeButton =
        SnykBundle.message("snyk.folderConfig.migration.singleSubConfig.mergeButton")
      val discardButton =
        SnykBundle.message("snyk.folderConfig.migration.singleSubConfig.discardButton")
      val keepButton =
        SnykBundle.message("snyk.folderConfig.migration.singleSubConfig.cancelButton")

      val result =
        Messages.showDialog(
          project,
          message,
          title,
          arrayOf(mergeButton, discardButton, keepButton),
          0,
          Messages.getQuestionIcon(),
        )

      choice =
        when (result) {
          0 -> MigrationChoice.MERGE_INTO_PARENT
          1 -> MigrationChoice.USE_PARENT_VALUES
          else -> MigrationChoice.KEEP_AS_IS
        }
    }

    return choice
  }

  /** Prompts user for multiple conflicting configs migration choice. */
  internal fun promptForMultipleConflictingMigration(
    project: Project,
    parentPath: String,
    subPaths: List<String>,
  ): MigrationChoice {
    var choice = MigrationChoice.KEEP_ALL

    invokeAndWaitIfNeeded {
      val title = SnykBundle.message("snyk.folderConfig.migration.multipleConflicting.title")
      val message =
        SnykBundle.message(
          "snyk.folderConfig.migration.multipleConflicting.message",
          parentPath,
          subPaths.joinToString(", "),
        )
      val removeParentButton =
        SnykBundle.message("snyk.folderConfig.migration.multipleConflicting.removeParentButton")
      val keepAllButton =
        SnykBundle.message("snyk.folderConfig.migration.multipleConflicting.keepAllButton")

      val result =
        Messages.showDialog(
          project,
          message,
          title,
          arrayOf(removeParentButton, keepAllButton),
          1,
          Messages.getWarningIcon(),
        )

      choice =
        when (result) {
          0 -> MigrationChoice.REMOVE_PARENT
          else -> MigrationChoice.KEEP_ALL
        }
    }

    return choice
  }

  /**
   * Checks if a config has non-default values that differ from the parent config. Compares
   * user-configurable fields: baseBranch, referenceFolderPath, additionalParameters, additionalEnv,
   * preferredOrg, orgSetByUser, scanCommandConfig.
   */
  internal fun hasNonDefaultValues(
    config: LspFolderConfig,
    parentConfig: LspFolderConfig,
  ): Boolean =
    COMPARABLE_SETTING_KEYS.any { key ->
      config.settings?.get(key)?.value != parentConfig.settings?.get(key)?.value
    }

  /** Checks if multiple configs have conflicting values between each other. */
  internal fun hasConflictingConfigs(configs: List<LspFolderConfig>): Boolean {
    if (configs.size < 2) return false

    val first = configs.first()
    return configs.drop(1).any { config ->
      COMPARABLE_SETTING_KEYS.any { key ->
        config.settings?.get(key)?.value != first.settings?.get(key)?.value
      }
    }
  }

  /**
   * Merges sub-config values into parent config. Sub-config values take precedence over parent
   * values.
   */
  internal fun mergeConfigs(
    parentConfig: LspFolderConfig,
    subConfig: LspFolderConfig,
  ): LspFolderConfig {
    // Merge sub-config settings into parent, sub-config values take precedence
    val mergedSettings = (parentConfig.settings ?: emptyMap()).toMutableMap()
    subConfig.settings?.forEach { (key, value) -> mergedSettings[key] = value }
    return parentConfig.copy(settings = mergedSettings)
  }

  /**
   * Checks if a path is nested under another path.
   *
   * @param childPath the potential child path
   * @param parentPath the potential parent path
   * @return true if childPath is a subdirectory of parentPath
   */
  internal fun isPathNestedUnder(childPath: String, parentPath: String): Boolean {
    val normalizedChild = Paths.get(childPath).normalize().toAbsolutePath()
    val normalizedParent = Paths.get(parentPath).normalize().toAbsolutePath()

    // Child must start with parent path and be longer (not equal)
    return normalizedChild.startsWith(normalizedParent) && normalizedChild != normalizedParent
  }

  /** Enum representing the user's choice during migration. */
  enum class MigrationChoice {
    MERGE_INTO_PARENT,
    USE_PARENT_VALUES,
    KEEP_AS_IS,
    REMOVE_PARENT,
    KEEP_ALL
  }

  /**
   * Resolves the severity filter for a file by finding its containing workspace folder and checking
   * the folder config. Returns null if no folder-level override exists.
   */
  fun getSeverityFilterForFile(severity: Severity, file: VirtualFile, project: Project): Boolean? {
    val filePath = file.path
    val folderConfig = findContainingFolderConfig(filePath, project) ?: return null
    val key =
      when (severity) {
        Severity.CRITICAL -> LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL
        Severity.HIGH -> LsFolderSettingsKeys.SEVERITY_FILTER_HIGH
        Severity.MEDIUM -> LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM
        Severity.LOW -> LsFolderSettingsKeys.SEVERITY_FILTER_LOW
        else -> return null
      }
    return folderConfig.settings?.get(key)?.value as? Boolean
  }

  /**
   * Sets the per-folder severity filter on every workspace folder of [project]. Marks each folder's
   * `severity_filter_*` key as explicitly changed so [LanguageServerWrapper.getSettings] forwards
   * `changed = true` to snyk-ls.
   *
   * @return true if at least one workspace folder config was updated, false when [project] has no
   *   folder configs (the caller should silently no-op rather than mutate global flags).
   */
  fun setSeverityEnabledForProject(
    project: Project,
    severity: Severity,
    enabled: Boolean,
  ): Boolean {
    val key =
      when (severity) {
        Severity.CRITICAL -> LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL
        Severity.HIGH -> LsFolderSettingsKeys.SEVERITY_FILTER_HIGH
        Severity.MEDIUM -> LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM
        Severity.LOW -> LsFolderSettingsKeys.SEVERITY_FILTER_LOW
        else -> return false
      }
    val folderConfigs = getFolderConfigs(project)
    if (folderConfigs.isEmpty()) return false
    val ps = pluginSettings()
    folderConfigs.forEach { fc ->
      addFolderConfig(fc.withSetting(key, enabled, changed = true))
      ps.markExplicitlyChanged(fc.folderPath, key)
    }
    return true
  }

  /**
   * Whether this severity is effectively enabled for the **Snyk tool window of this project**:
   * aggregates [getFolderConfigs] (workspace folders for [project] only). If at least one folder
   * enables the level, the toolbar shows it as enabled. Per-folder values fall back to
   * [globalSeverityEnabled] when that workspace folder has no explicit `severity_filter_*` key.
   */
  fun isSeverityEnabledForProjectToolWindow(
    severity: Severity,
    project: Project,
    globalSeverityEnabled: Boolean,
  ): Boolean {
    val key =
      when (severity) {
        Severity.CRITICAL -> LsFolderSettingsKeys.SEVERITY_FILTER_CRITICAL
        Severity.HIGH -> LsFolderSettingsKeys.SEVERITY_FILTER_HIGH
        Severity.MEDIUM -> LsFolderSettingsKeys.SEVERITY_FILTER_MEDIUM
        Severity.LOW -> LsFolderSettingsKeys.SEVERITY_FILTER_LOW
        else -> return false
      }
    val folderConfigs = getFolderConfigs(project)
    if (folderConfigs.isEmpty()) return globalSeverityEnabled
    return folderConfigs.any { fc ->
      (fc.settings?.get(key)?.value as? Boolean) ?: globalSeverityEnabled
    }
  }

  /**
   * Sets the per-folder product enablement on every workspace folder of [project]. Marks each
   * folder's `snyk_*_enabled` key as explicitly changed so [LanguageServerWrapper.getSettings]
   * forwards `changed = true` to snyk-ls.
   *
   * @return true if at least one workspace folder config was updated, false when [project] has no
   *   folder configs (the caller should silently no-op rather than mutate global flags).
   */
  fun setProductEnabledForProject(
    project: Project,
    productType: ProductType,
    enabled: Boolean,
  ): Boolean {
    val key = productEnablementKey(productType) ?: return false
    val folderConfigs = getFolderConfigs(project)
    if (folderConfigs.isEmpty()) return false
    val ps = pluginSettings()
    folderConfigs.forEach { fc ->
      addFolderConfig(fc.withSetting(key, enabled, changed = true))
      ps.markExplicitlyChanged(fc.folderPath, key)
    }
    return true
  }

  /**
   * Whether this product is effectively enabled for the **Snyk tool window of this project**:
   * aggregates [getFolderConfigs] (workspace folders for [project] only). If at least one folder
   * enables the product, the toolbar shows it as enabled. Per-folder values fall back to
   * [globalProductEnabled] when that workspace folder has no explicit `snyk_*_enabled` key.
   */
  fun isProductEnabledForProjectToolWindow(
    productType: ProductType,
    project: Project,
    globalProductEnabled: Boolean,
  ): Boolean {
    val key = productEnablementKey(productType) ?: return false
    val folderConfigs = getFolderConfigs(project)
    if (folderConfigs.isEmpty()) return globalProductEnabled
    return folderConfigs.any { fc ->
      (fc.settings?.get(key)?.value as? Boolean) ?: globalProductEnabled
    }
  }

  private fun productEnablementKey(productType: ProductType): String? =
    when (productType) {
      ProductType.OSS -> LsFolderSettingsKeys.SNYK_OSS_ENABLED
      ProductType.CODE_SECURITY -> LsFolderSettingsKeys.SNYK_CODE_ENABLED
      ProductType.IAC -> LsFolderSettingsKeys.SNYK_IAC_ENABLED
      ProductType.SECRETS -> LsFolderSettingsKeys.SNYK_SECRETS_ENABLED
    }

  private fun findContainingFolderConfig(filePath: String, project: Project): LspFolderConfig? {
    val normalizedFilePath = normalizePath(filePath)
    val lsWrapper = LanguageServerWrapper.getInstance(project)
    val workspaceFolders = lsWrapper.configuredWorkspaceFolders
    val matchingFolder =
      workspaceFolders
        .mapNotNull { wf ->
          val folderPath = normalizePath(wf.uri.fromUriToPath().toString())
          if (normalizedFilePath.startsWith(folderPath)) folderPath else null
        }
        .maxByOrNull { it.length }
    return matchingFolder?.let { configs[it] }
  }

  companion object {
    private val COMPARABLE_SETTING_KEYS =
      listOf(
        LsFolderSettingsKeys.BASE_BRANCH,
        LsFolderSettingsKeys.REFERENCE_FOLDER,
        LsFolderSettingsKeys.ADDITIONAL_PARAMETERS,
        LsFolderSettingsKeys.ADDITIONAL_ENVIRONMENT,
        LsFolderSettingsKeys.PREFERRED_ORG,
        LsFolderSettingsKeys.ORG_SET_BY_USER,
        LsFolderSettingsKeys.SCAN_COMMAND_CONFIG,
      )
  }
}
