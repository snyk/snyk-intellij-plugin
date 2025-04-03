package snyk.common.lsp.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.convertUriToPath
import io.snyk.plugin.getContentRootPaths
import org.jetbrains.annotations.NotNull
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

@Suppress("UselessCallOnCollection")
@Service
class FolderConfigSettings {
    private val configs: MutableMap<Path, FolderConfig> = ConcurrentHashMap<Path, FolderConfig>()

    @Suppress("UselessCallOnNotNull", "USELESS_ELVIS", "UNNECESSARY_SAFE_CALL", "RedundantSuppression")
    fun addFolderConfig(@NotNull folderConfig: FolderConfig) {
        if (folderConfig?.folderPath.isNullOrBlank() ?: true) return
        val folderPath = Paths.get(folderConfig.folderPath)
        configs[folderPath] = folderConfig
    }

    internal fun getFolderConfig(folderPath: Path): FolderConfig {
        val folderConfig = configs[folderPath] ?: createEmpty(folderPath)
        return folderConfig
    }

    private fun createEmpty(folderPath: Path): FolderConfig {
        val folderConfig = FolderConfig(folderPath = folderPath.toAbsolutePath().toString(), baseBranch = "")
        addFolderConfig(folderConfig)
        return folderConfig
    }

    fun getAll(): Map<Path, FolderConfig> {
        return HashMap(configs)
    }

    fun clear() = configs.clear()

    fun addAll(folderConfigs: List<FolderConfig>) = folderConfigs.mapNotNull { addFolderConfig(it) }

    fun getAllForProject(project: Project): List<FolderConfig> =
        project.getContentRootPaths()
            .mapNotNull { getFolderConfig(it.toAbsolutePath()) }
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
        val additionalParameters = LanguageServerWrapper.getInstance().getWorkspaceFoldersFromRoots(project)
            .asSequence()
            .filter { LanguageServerWrapper.getInstance().configuredWorkspaceFolders.contains(it) }
            .map {
                val folderPath = convertUriToPath(it.uri)
                getFolderConfig(folderPath)
            }
            .filter { it.additionalParameters?.isNotEmpty() ?: false }
            .map { it.additionalParameters?.joinToString(" ") }
            .joinToString(" ")
        return additionalParameters
    }
}
