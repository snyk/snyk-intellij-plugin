package snyk.common.lsp.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import io.snyk.plugin.getContentRootPaths
import io.snyk.plugin.toVirtualFile
import org.jetbrains.annotations.NotNull
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.LanguageServerWrapper
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

@Suppress("UselessCallOnCollection")
@Service
class FolderConfigSettings {
    private val configs: MutableMap<String, FolderConfig> = ConcurrentHashMap<String, FolderConfig>()

    @Suppress("UselessCallOnNotNull", "USELESS_ELVIS", "UNNECESSARY_SAFE_CALL", "RedundantSuppression")
    fun addFolderConfig(@NotNull folderConfig: FolderConfig) {
        if (folderConfig?.folderPath.isNullOrBlank() ?: true) return
        configs[folderConfig.folderPath] = folderConfig
    }

    internal fun getFolderConfig(folderPath: String): FolderConfig {
        val folderConfig = configs[folderPath] ?: createEmpty(folderPath)
        return folderConfig
    }

    private fun createEmpty(folderPath: String): FolderConfig {
        val folderConfig = FolderConfig(folderPath = folderPath, baseBranch = "")
        addFolderConfig(folderConfig)
        return folderConfig
    }

    fun getAll(): Map<String, FolderConfig> {
        return HashMap(configs)
    }

    fun clear() = configs.clear()

    fun addAll(folderConfigs: List<FolderConfig>) = folderConfigs.mapNotNull { addFolderConfig(it) }

    fun getAllForProject(project: Project): List<FolderConfig> =
        project.getContentRootPaths()
            .mapNotNull { getFolderConfig(it.toAbsolutePath().toString()) }
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
            .map { it.uri.toVirtualFile().toNioPath().toString() }
            .map { getFolderConfig(it) }
            .filter { it.additionalParameters?.isNotEmpty() ?: false }
            .map { it.additionalParameters?.joinToString(" ") }
            .joinToString(" ")
        return additionalParameters
    }
}
