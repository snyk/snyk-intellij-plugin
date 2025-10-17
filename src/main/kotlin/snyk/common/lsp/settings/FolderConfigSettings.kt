package snyk.common.lsp.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.fromUriToPath
import io.snyk.plugin.getContentRootPaths
import org.jetbrains.annotations.NotNull
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import java.io.File
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
        val additionalParameters = languageServerWrapper.getWorkspaceFoldersFromRoots(project)
            .asSequence()
            .filter { languageServerWrapper.configuredWorkspaceFolders.contains(it) }
            .map { getFolderConfig(it.uri.fromUriToPath().toString()) }
            .filter { it.additionalParameters?.isNotEmpty() ?: false }
            .map { it.additionalParameters?.joinToString(" ") }
            .joinToString(" ")
        return additionalParameters
    }

    /**
     * Gets the preferred organization for the given project by aggregating the folder configs with workspace folder paths.
     * Falls back to global organization setting if folder-specific preferred org is empty.
     * @param project the project to get the preferred organization for
     * @return the preferred organization for the project
     */
    fun getPreferredOrg(project: Project): String {
        // only use folder config with workspace folder path
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        val preferredOrg = languageServerWrapper.getWorkspaceFoldersFromRoots(project)
            .asSequence()
            .filter { languageServerWrapper.configuredWorkspaceFolders.contains(it) }
            .map { getFolderConfig(it.uri.fromUriToPath().toString()) }
            .filter { it.preferredOrg.isNotEmpty() }
            .map { it.preferredOrg }
            .firstOrNull()

        // Fallback to global organization if folder-specific preferred org is empty
        return if (preferredOrg?.isNotEmpty() == true) {
            preferredOrg
        } else {
            // Get global organization from application settings
            val application = com.intellij.openapi.application.ApplicationManager.getApplication()
            if (application != null) {
                val applicationSettings = application.getService(io.snyk.plugin.services.SnykApplicationSettingsStateService::class.java)
                applicationSettings.organization ?: ""
            } else {
                ""
            }
        }
    }

    /**
     * Gets the appropriate organization for the given project based on orgSetByUser flag.
     * Returns autoDeterminedOrg if orgSetByUser is false, otherwise returns preferredOrg.
     * Falls back to global organization setting if folder-specific values are empty.
     * @param project the project to get the organization for
     * @return the appropriate organization for the project
     */
    fun getOrganization(project: Project): String {
        // only use folder config with workspace folder path
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        val folderConfig = languageServerWrapper.getWorkspaceFoldersFromRoots(project)
            .asSequence()
            .filter { languageServerWrapper.configuredWorkspaceFolders.contains(it) }
            .map { getFolderConfig(it.uri.fromUriToPath().toString()) }
            .firstOrNull()

        val folderOrg = if (folderConfig?.orgSetByUser == true) {
            folderConfig.preferredOrg
        } else {
            folderConfig?.autoDeterminedOrg ?: ""
        }

        // Fallback to global organization if folder-specific organization is empty
        return if (folderOrg.isNotEmpty()) {
            folderOrg
        } else {
            // Get global organization from application settings
            val application = com.intellij.openapi.application.ApplicationManager.getApplication()
            if (application != null) {
                val applicationSettings = application.getService(io.snyk.plugin.services.SnykApplicationSettingsStateService::class.java)
                applicationSettings.organization ?: ""
            } else {
                ""
            }
        }
    }

    /**
     * Checks if auto-organization is enabled for the given project.
     * Returns true if orgSetByUser is false (auto-detect enabled), false otherwise.
     * @param project the project to check
     * @return true if auto-organization is enabled
     */
    fun isAutoOrganizationEnabled(project: Project): Boolean {
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        val folderConfig = languageServerWrapper.getWorkspaceFoldersFromRoots(project)
            .asSequence()
            .filter { languageServerWrapper.configuredWorkspaceFolders.contains(it) }
            .map { getFolderConfig(it.uri.fromUriToPath().toString()) }
            .firstOrNull()

        return folderConfig?.orgSetByUser != true
    }

    /**
     * Sets the auto-organization setting for the given project.
     * @param project the project to update
     * @param autoOrganization true to enable auto-organization, false to use preferred organization
     */
    fun setAutoOrganization(project: Project, autoOrganization: Boolean) {
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        val folderConfigs = languageServerWrapper.getWorkspaceFoldersFromRoots(project)
            .asSequence()
            .filter { languageServerWrapper.configuredWorkspaceFolders.contains(it) }
            .map { getFolderConfig(it.uri.fromUriToPath().toString()) }
            .toList()

        folderConfigs.forEach { folderConfig ->
            val updatedConfig = folderConfig.copy(orgSetByUser = !autoOrganization)
            addFolderConfig(updatedConfig)
        }
    }

    /**
     * Sets the organization for the given project.
     * @param project the project to update
     * @param organization the organization to set (empty string to clear)
     */
    fun setOrganization(project: Project, organization: String?) {
        val languageServerWrapper = LanguageServerWrapper.getInstance(project)
        val folderConfigs = languageServerWrapper.getWorkspaceFoldersFromRoots(project)
            .asSequence()
            .filter { languageServerWrapper.configuredWorkspaceFolders.contains(it) }
            .map { getFolderConfig(it.uri.fromUriToPath().toString()) }
            .toList()

        folderConfigs.forEach { folderConfig ->
            val updatedConfig = folderConfig.copy(preferredOrg = organization ?: "")
            addFolderConfig(updatedConfig)
        }
    }
}
