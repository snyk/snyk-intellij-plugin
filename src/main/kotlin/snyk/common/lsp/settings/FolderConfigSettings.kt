package snyk.common.lsp.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.fromUriToPath
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.pluginSettings
import org.jetbrains.annotations.NotNull
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

@Suppress("UselessCallOnCollection")
@Service
class FolderConfigSettings {
    private val configs: MutableMap<String, FolderConfig> = ConcurrentHashMap<String, FolderConfig>()

    @Suppress("UselessCallOnNotNull", "USELESS_ELVIS", "UNNECESSARY_SAFE_CALL", "RedundantSuppression")
    fun addFolderConfig(@NotNull folderConfig: FolderConfig) {
        if (folderConfig.folderPath.isNullOrBlank()) return
        val normalizedAbsolutePath = normalizePath(folderConfig.folderPath)

        // Handle null values from Language Server by providing defaults
        val configToStore = folderConfig.copy(
            folderPath = normalizedAbsolutePath,
            preferredOrg = folderConfig.preferredOrg ?: "",
            autoDeterminedOrg = folderConfig.autoDeterminedOrg ?: "",
            referenceFolderPath = folderConfig.referenceFolderPath ?: ""
        )
        configs[normalizedAbsolutePath] = configToStore
    }

    private fun normalizePath(folderPath: String): String {
        val normalizedAbsolutePath =
            Paths.get(folderPath)
                .normalize()
                .toAbsolutePath()
                .toString()
        return normalizedAbsolutePath
    }

    internal fun getFolderConfig(folderPath: String): FolderConfig {
        val normalizedPath = normalizePath(folderPath)
        val folderConfig = configs[normalizedPath] ?: createEmpty(normalizedPath)
        return folderConfig
    }

    private fun createEmpty(normalizedAbsolutePath: String): FolderConfig {
        val newConfig = FolderConfig(folderPath = normalizedAbsolutePath, baseBranch = "main")
        // Directly add to map, as addFolderConfig would re-normalize and copy, which is redundant here
        // since normalizedAbsolutePath is already what we want for the key and the object's path.
        configs[normalizedAbsolutePath] = newConfig
        return newConfig
    }

    fun getAll(): Map<String, FolderConfig> {
        return HashMap(configs)
    }

    fun clear() = configs.clear()

    fun addAll(folderConfigs: List<FolderConfig>) = folderConfigs.mapNotNull { addFolderConfig(it) }

    fun getAllForProject(project: Project): List<FolderConfig> =
        project.getContentRootPaths()
            .mapNotNull { getFolderConfig(it.toString()) }
            .filterNotNull()
            .stream()
            .sorted()
            .collect(Collectors.toList()).toList()

    /**
     * Gets the additional parameters for the given project by aggregating the folder configs with workspace folder paths.
     * @param project the project to get the additional parameters for
     * @return the additional parameters for the project
     */
    fun getAdditionalParameters(project: Project): String {
        // only use folder config with workspace folder path
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        val additionalParameters = getFolderConfigs(project)
            .filter { it.additionalParameters?.isNotEmpty() ?: false }
            .mapNotNull { it.additionalParameters?.joinToString(" ") }
            .joinToString(" ")
        return additionalParameters
    }

    /**
     * Gets the folder configs for the given project by aggregating the folder configs with workspace folder paths.
     * @param project the project to get the folder configs for
     * @return the folder configs for the project
     */
    fun getFolderConfigs(project: Project): List<FolderConfig> {
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        return languageServerWrapper.getWorkspaceFoldersFromRoots(project, promptForTrust = false)
            .asSequence()
            .filter { languageServerWrapper.configuredWorkspaceFolders.contains(it) }
            .map { getFolderConfig(it.uri.fromUriToPath().toString()) }
            .toList()
    }

    /**
     * Gets the preferred organization for the given project by aggregating the folder configs with workspace folder paths.
     * @param project the project to get the preferred organization for
     * @return the preferred organization for the project
     */
    fun getPreferredOrg(project: Project): String {
        // Note - this will not work for projects with extra content roots outside of the the main workspace folder.
        return getFolderConfigs(project).map { it.preferredOrg }.firstOrNull() ?: ""
    }

    /**
     * Checks if auto-organization is enabled for the given project.
     * Returns true if orgSetByUser is false (auto-detect enabled), false otherwise.
     * @param project the project to check
     * @return true if auto-organization is enabled
     */
    fun isAutoOrganizationEnabled(project: Project): Boolean {
        return getFolderConfigs(project).firstOrNull()?.orgSetByUser != true
    }

    /**
     * Sets the auto-organization setting for the given project.
     * @param project the project to update
     * @param autoOrganization true to enable auto-organization, false to use preferred organization
     */
    fun setAutoOrganization(project: Project, autoOrganization: Boolean) {
        getFolderConfigs(project).forEach { folderConfig ->
            val updatedConfig = folderConfig.copy(orgSetByUser = !autoOrganization)
            addFolderConfig(updatedConfig)
        }
    }

    /**
     * Sets the organization for the given project.
     * @param project the project to update
     * @param organization the organization to set
     */
    fun setOrganization(project: Project, organization: String?) {
        getFolderConfigs(project).forEach { folderConfig ->
            val updatedConfig = folderConfig.copy(preferredOrg = organization ?: "")
            addFolderConfig(updatedConfig)
        }
    }

    // --- Per-folder settings with global fallback ---

    private fun getActiveFolderConfig(project: Project): FolderConfig? =
        getFolderConfigs(project).firstOrNull()

    fun isOssScanEnabled(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.snykOssEnabled ?: pluginSettings().ossScanEnable
    }

    fun isSnykCodeEnabled(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.snykCodeEnabled ?: pluginSettings().snykCodeSecurityIssuesScanEnable
    }

    fun isIacScanEnabled(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.snykIacEnabled ?: pluginSettings().iacScanEnabled
    }

    fun isScanAutomatic(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.scanAutomatic ?: pluginSettings().scanOnSave
    }

    fun isScanNetNew(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.scanNetNew ?: pluginSettings().isDeltaFindingsEnabled()
    }

    fun isCriticalSeverityEnabled(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.enabledSeverities?.critical ?: pluginSettings().criticalSeverityEnabled
    }

    fun isHighSeverityEnabled(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.enabledSeverities?.high ?: pluginSettings().highSeverityEnabled
    }

    fun isMediumSeverityEnabled(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.enabledSeverities?.medium ?: pluginSettings().mediumSeverityEnabled
    }

    fun isLowSeverityEnabled(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.enabledSeverities?.low ?: pluginSettings().lowSeverityEnabled
    }

    fun getRiskScoreThreshold(project: Project): Int {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.riskScoreThreshold ?: pluginSettings().riskScoreThreshold ?: 0
    }

    fun isOpenIssuesEnabled(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.issueViewOpenIssues ?: pluginSettings().openIssuesEnabled
    }

    fun isIgnoredIssuesEnabled(project: Project): Boolean {
        val folderConfig = getActiveFolderConfig(project)
        return folderConfig?.issueViewIgnoredIssues ?: pluginSettings().ignoredIssuesEnabled
    }
}
