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

        val configToStore = folderConfig.copy(folderPath = normalizedAbsolutePath)
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
        return preferredOrg ?: ""
    }
}
